package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class AuctionMatcher extends Matcher{
    public static final int INVALID_PRICE = -1;
    @Override
    public MatchResult match (Security security, Order order){
        return this.match(security);
    }
    public MatchResult match(Security security) {
        int tradableQuantity = 0;
        boolean isMatchingOver = false;
        LinkedList<Order> sellQueueCopy = new LinkedList<>(security.getOrderBook().getSellQueue());
        LinkedList<Trade> trades = new LinkedList<>();

        while (!isAuctionOver(isMatchingOver, sellQueueCopy, security)) {
            Order sellOrder = sellQueueCopy.getFirst();
            while (security.getOrderBook().hasOrderOfType(sellOrder.getSide().opposite()) && sellOrder.getQuantity() > 0) {
                Order buyOrder = security.getOrderBook().matchWithFirst(sellOrder.getSide(), security.getOpeningPrice());
                if (buyOrder == null) {
                    isMatchingOver = true;
                    break;
                }
                int tradeQuantity = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                tradableQuantity += tradeQuantity;
                matchTheTwoOrders(security.getOpeningPrice(), security.getOrderBook(), trades, sellOrder, buyOrder, tradeQuantity);
            }
            sellQueueCopy.removeFirst();
        }
        return MatchResult.executedAuction(trades, getLastTradedPriceAfterMatch(security),
                tradableQuantity, security.getOpeningPrice());
    }
    public MatchResult updateOpeningPrice(Security security) {
        int newOpeningPrice = calculateOpeningPrice(security.getOrderBook());
        return MatchResult.updateOpeningPrice(security.getLastTradedPrice(), newOpeningPrice,
                calculateTradableQuantity(security.getOrderBook(), newOpeningPrice));
    }
    private int getLastTradedPriceAfterMatch(Security security) {
        if(security.getOpeningPrice() != INVALID_PRICE)
            return security.getOpeningPrice();
        else
            return security.getLastTradedPrice();
    }

    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        if (brokerDoesNotHaveEnoughCredit(order))
            return MatchResult.notEnoughCredit(order.getSecurity().getLastTradedPrice(), order.getSecurity().getOpeningPrice());

        decreaseBuyBrokerCredit(order);
        order.getSecurity().getOrderBook().enqueue(order);
        int newOpeningPrice = calculateOpeningPrice(order.getSecurity().getOrderBook());
        return MatchResult.queuedInAuction(order, order.getSecurity().getLastTradedPrice(),
                calculateTradableQuantity(order.getSecurity().getOrderBook(),newOpeningPrice),
                newOpeningPrice);
    }
    @Override
    protected void matchTheTwoOrders(int openingPrice, OrderBook orderBook, LinkedList<Trade> trades, Order sellOrder, Order buyOrder, int tradeQuantity) {
        addNewTrade(openingPrice, trades, sellOrder, buyOrder, tradeQuantity);
        adjustBrokerCredit(buyOrder, trades.getLast(), Math.abs(tradeQuantity * (openingPrice - sellOrder.getPrice())));
        decreaseOrderQuantity(sellOrder, buyOrder);
        removeZeroQuantityOrder(orderBook, sellOrder, buyOrder);
        replenishIcebergOrder(orderBook, sellOrder, buyOrder);
        adjustShareholderPositions(trades);
    }

    private boolean isAuctionOver(boolean isMatchingOver, LinkedList<Order> sellQueueCopy, Security security) {
        return isMatchingOver || sellQueueCopy.isEmpty() || security.getOpeningPrice() == -1 ||
                !security.getOrderBook().hasOrderOfType(Side.BUY)
                || !security.getOrderBook().hasOrderOfType(Side.SELL);
    }

    public int calculateOpeningPrice(OrderBook orderBook) {
        int maxTradebleQuantity = 0, newOpeningPrice = -1;
        int lowestPrice = orderBook.getLowestPriorityOrderPrice(Side.BUY);
        int highestPrice = orderBook.getLowestPriorityOrderPrice(Side.SELL);

        for (int openingPrice = lowestPrice; openingPrice <= highestPrice; openingPrice++){
            int tradebleQuantity = calculateTradableQuantity(orderBook, openingPrice);
            if(tradebleQuantity > maxTradebleQuantity) {
                maxTradebleQuantity = tradebleQuantity;
                newOpeningPrice = openingPrice;
            }
        }
        return newOpeningPrice;
    }
    private int calculateTradableQuantity(OrderBook orderBook, int newOpeningPrice) {
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
}