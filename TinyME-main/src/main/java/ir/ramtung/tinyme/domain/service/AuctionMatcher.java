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
        int sellOrderCount = orderBook.getSellQueue().size();
        boolean isMatchingOver = false;
        while(!isMatchingOver && sellOrderCount != 0) {
            Order newOrder = orderBook.getSellQueue().getFirst();
            while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
                Order matchingOrder = orderBook.matchWithFirstWithPrice(newOrder.getSide(), openingPrice);
                if (matchingOrder == null) {
                    isMatchingOver = true;
                    break;
                }

                int tradeQuantity = Math.min(newOrder.getQuantity(), matchingOrder.getQuantity());
                addNewTrade(openingPrice, trades, newOrder, matchingOrder, tradeQuantity);
                tradableQuantity += tradeQuantity;

                adjustCreditOfBuyOrderBroker(matchingOrder, Math.abs(tradeQuantity * (openingPrice - newOrder.getPrice())));
                adjustOrderQuantityRemoveSmallerOrder(orderBook, newOrder, matchingOrder);
            }
            sellOrderCount--;
        }

        return MatchResult.executedAuction(trades, openingPrice, tradableQuantity);
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

    private void adjustCreditOfBuyOrderBroker(Order order, long creditChange) {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(creditChange);
    }

    @Override
    protected void adjustOrderQuantityRemoveSmallerOrder(OrderBook orderBook, Order newOrder, Order matchingOrder) {
        if (newOrder.getQuantity() > matchingOrder.getQuantity()) {
            decreaseOrderQuantity(newOrder, matchingOrder, matchingOrder.getQuantity());
            removeZeroQuantityOrder(orderBook, matchingOrder);
            replenishIcebergOrder(orderBook, matchingOrder);
        }
        else if (newOrder.getQuantity() < matchingOrder.getQuantity()) {
            decreaseOrderQuantity(matchingOrder, newOrder, newOrder.getQuantity());
            removeZeroQuantityOrder(orderBook, newOrder);
            replenishIcebergOrder(orderBook, newOrder);
        }
        else{
            decreaseOrderQuantity(matchingOrder, newOrder, newOrder.getQuantity());
            removeZeroQuantityOrder(orderBook, newOrder);
            removeZeroQuantityOrder(orderBook, matchingOrder);
            replenishIcebergOrder(orderBook, newOrder);
            replenishIcebergOrder(orderBook, matchingOrder);
        }
    }

    private void decreaseOrderQuantity(Order newOrder, Order matchingOrder, int quantity) {
        newOrder.decreaseQuantity(quantity);
        matchingOrder.decreaseQuantity(quantity);
    }

}
