package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class AuctionMatcher extends Matcher{
    @Override
    public MatchResult match (Security security, Order order){
        return this.match(security);
    }
    public MatchResult match(Security security) {
        int openingPrice = security.getLatestMatchingPrice();
        int tradableQuantity = 0;
        boolean isMatchingOver = false;
        OrderBook orderBook = security.getOrderBook();
        LinkedList<Order> sellQueueCopy = new LinkedList<>(orderBook.getSellQueue());
        LinkedList<Trade> trades = new LinkedList<>();

        while (!isAuctionOver(isMatchingOver, sellQueueCopy, orderBook)) {
            Order sellOrder = sellQueueCopy.getFirst();
            while (orderBook.hasOrderOfType(sellOrder.getSide().opposite()) && sellOrder.getQuantity() > 0) {
                Order buyOrder = orderBook.matchWithFirstWithPrice(sellOrder.getSide(), openingPrice);
                if (buyOrder == null) {
                    isMatchingOver = true;
                    break;
                }
                int tradeQuantity = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                addNewTrade(openingPrice, trades, sellOrder, buyOrder, tradeQuantity);
                tradableQuantity += tradeQuantity;

                increaseBuyBrokerCredit(buyOrder, Math.abs(tradeQuantity * (openingPrice - sellOrder.getPrice())));
                decreaseOrderQuantity(sellOrder, buyOrder);
                removeSmallerOrder(orderBook, sellOrder, buyOrder);
            }
            sellQueueCopy.removeFirst();
        }

        return MatchResult.executedAuction(trades, openingPrice, tradableQuantity);
    }

    private boolean isAuctionOver(boolean isMatchingOver, LinkedList<Order> sellQueueCopy, OrderBook orderBook) {
        return isMatchingOver || sellQueueCopy.isEmpty() ||
                !orderBook.hasOrderOfType(Side.BUY) || !orderBook.hasOrderOfType(Side.SELL);
    }

    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        if (brokerDoesNotHaveEnoughCredit(order))
            return MatchResult.notEnoughCredit(order.getSecurity().getLatestMatchingPrice());

        OrderBook orderBook = order.getSecurity().getOrderBook();

        decreaseBuyBrokerCredit(order);
        orderBook.enqueue(order);
        //ToDo what happens when there's no orders in the orderbook or when none of them match?
        int newOpeningPrice = calculateOpeningPrice(orderBook);
        int tradableQuantity = calculateTradableQuantity(newOpeningPrice, orderBook);
        return MatchResult.queuedInAuction(order, newOpeningPrice, tradableQuantity);
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

    //private boolean isMatchingOver(OrderBook orderBook) {
    //    return !orderBook.hasOrderOfType(Side.BUY) || !orderBook.hasOrderOfType(Side.SELL);
    //}
    @Override
    protected void removeSmallerOrder(OrderBook orderBook, Order sellOrder, Order buyOrder) {
        if (sellOrder.getQuantity() == 0)
            removeZeroQuantityOrder(orderBook, sellOrder);
        if (buyOrder.getQuantity() == 0)
            removeZeroQuantityOrder(orderBook, buyOrder);
    }

}