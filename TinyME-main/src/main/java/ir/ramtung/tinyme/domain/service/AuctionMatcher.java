package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class AuctionMatcher extends Matcher{
    public MatchResult matchAllOrders(Security security) {
        int openingPrice = security.getLatestMatchingPrice(), tradableQuantity = 0;
        OrderBook orderBook = security.getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        LinkedList<Order> sellQueue = orderBook.getSellQueue();
        boolean isMatchingOver = false;

        while(!isMatchingOver && !sellQueue.isEmpty()) {
            Order newOrder = sellQueue.getFirst();
            while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
                Order matchingOrder = orderBook.matchWithFirstWithPrice(newOrder.getSide(), openingPrice);
                if (matchingOrder == null) {
                    isMatchingOver = true;
                    break;
                }

                int tradeQuantity = Math.min(newOrder.getQuantity(), matchingOrder.getQuantity());
                tradableQuantity += tradeQuantity;
                Trade trade = new Trade(newOrder.getSecurity(), openingPrice, tradeQuantity
                        , newOrder, matchingOrder);
                trade.increaseSellersCredit();
                trades.add(trade);
                
                decreaseCreditOfBuyOrderBroker(newOrder, matchingOrder, openingPrice, tradeQuantity);

                if (newOrder.getQuantity() >= matchingOrder.getQuantity()) {
                    newOrder.decreaseQuantity(matchingOrder.getQuantity());
                    orderBook.removeFirst(matchingOrder.getSide());
                    if (matchingOrder instanceof IcebergOrder icebergOrder) {
                        icebergOrder.decreaseQuantity(matchingOrder.getQuantity());
                        icebergOrder.replenish();
                        if (icebergOrder.getQuantity() > 0)
                            orderBook.enqueue(icebergOrder);
                    }
                } else {
                    matchingOrder.decreaseQuantity(newOrder.getQuantity());
                    newOrder.makeQuantityZero();
                }
            }
            sellQueue.removeFirst();
        }

        return MatchResult.executedAuction(trades, openingPrice, tradableQuantity);
    }

    private void decreaseCreditOfBuyOrderBroker(Order newOrder, Order matchingOrder,
                                                int openingPrice, int tradeQuantity) {
        if (newOrder.getSide() == Side.BUY)
            newOrder.getBroker().increaseCreditBy(Math.abs(tradeQuantity * (openingPrice - newOrder.getPrice())));
        else if (matchingOrder.getSide() == Side.BUY)
            matchingOrder.getBroker().increaseCreditBy(Math.abs(tradeQuantity * (openingPrice - newOrder.getPrice())));
    }

    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        if (brokerDoesNotHaveEnoughCredit(order))
            return MatchResult.notEnoughCredit(order.getSecurity().getLatestMatchingPrice());

        OrderBook orderBook = order.getSecurity().getOrderBook();

        adjustBrokerCredit(order);
        orderBook.enqueue(order);
        //ToDo what happens when there's no orders in the orderbook or when none of them match?
        int newOpeningPrice = calculateOpeningPrice(orderBook);
        int tradableQuantity = calculateTradableQuantity(newOpeningPrice, orderBook);
        return MatchResult.queuedInAuction(order, newOpeningPrice, tradableQuantity);
    }

    private static void adjustBrokerCredit(Order order) {
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
    }

    private int calculateOpeningPrice(OrderBook orderBook) {
        int maxTradebleQuantity = -1, newOpeningPrice = -1;
        int lowestPrice = orderBook.getLowestPriorityOrderPrice(Side.BUY);
        int highestPrice = orderBook.getLowestPriorityOrderPrice(Side.SELL);

        for (int openingPrice = lowestPrice; openingPrice <= highestPrice; openingPrice++){
            int tradebleQuantity = calculateTradableQuantity(openingPrice, orderBook);
            if(tradebleQuantity > maxTradebleQuantity) {
                maxTradebleQuantity = tradebleQuantity;
                newOpeningPrice = openingPrice;
            }
        }

        return newOpeningPrice;
    }

    private int calculateTradableQuantity(int newOpeningPrice, OrderBook orderBook) {
        LinkedList<Order> matchingBuyOrders = orderBook.findAllMatchingOrdersWithPrice(newOpeningPrice, Side.BUY);
        LinkedList<Order> matchingSellOrders = orderBook.findAllMatchingOrdersWithPrice(newOpeningPrice, Side.SELL);

        int totalBuyQuantity = matchingBuyOrders.stream()
                .mapToInt(Order::getQuantity)
                .sum();
        int totalSellQuantity = matchingSellOrders.stream()
                .mapToInt(Order::getQuantity)
                .sum();

        return Math.min(totalBuyQuantity, totalSellQuantity);
    }

    private static boolean brokerDoesNotHaveEnoughCredit(Order order) {
        return order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue());
    }
}
