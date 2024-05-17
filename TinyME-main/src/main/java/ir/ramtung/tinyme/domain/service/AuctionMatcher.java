package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
        LinkedList<Trade> trades = new LinkedList<>();

        while (!isAuctionOver(isMatchingOver, security)) {
            Order sellOrder = security.getOrderBook().getSellQueue().getFirst();
            if(!sellOrder.matchesWithPrice(security.getOpeningPrice()))
                break;
            while (security.getOrderBook().hasOrderOfType(sellOrder.getSide().opposite()) && sellOrder.getQuantity() > 0) {
                Order buyOrder = security.getOrderBook().getBuyQueue().getFirst();
                if (!buyOrder.matchesWithPrice(security.getOpeningPrice())) {
                    isMatchingOver = true;
                    break;
                }
                int tradeQuantity = Math.min(sellOrder.getQuantity(), buyOrder.getQuantity());
                tradableQuantity += tradeQuantity;
                matchTheTwoOrders(security.getOpeningPrice(), security.getOrderBook(), trades, sellOrder, buyOrder, tradeQuantity);
                if (sellOrder instanceof IcebergOrder)
                    break;
            }
        }
        adjustShareholderPositions(trades);
        return MatchResult.executedAuction(trades, getLastTradedPriceAfterMatch(security),
                tradableQuantity, security.getOpeningPrice());
    }

    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        if (brokerDoesNotHaveEnoughCredit(order))
            return MatchResult.notEnoughCredit(order.getSecurity().getLastTradedPrice(), order.getSecurity().getOpeningPrice());

        decreaseBuyBrokerCredit(order);
        order.getSecurity().getOrderBook().enqueue(order);
        int newOpeningPrice = calculateOpeningPrice(order.getSecurity().getOrderBook(), order.getSecurity().getLastTradedPrice());
        return MatchResult.queuedInAuction(order, order.getSecurity().getLastTradedPrice(),
                calculateTradableQuantity(order.getSecurity().getOrderBook(),newOpeningPrice),
                newOpeningPrice);
    }
    @Override
    protected void matchTheTwoOrders(int openingPrice, OrderBook orderBook, LinkedList<Trade> trades, Order sellOrder, Order buyOrder, int tradeQuantity) {
        addNewTrade(openingPrice, trades, sellOrder, buyOrder, tradeQuantity);
        adjustBrokerCredit(buyOrder, trades.getLast(), Math.abs(tradeQuantity * (openingPrice - buyOrder.getPrice())));
        decreaseOrderQuantity(sellOrder, buyOrder);
        removeZeroQuantityOrder(orderBook, sellOrder, buyOrder);
        replenishIcebergOrder(orderBook, sellOrder, buyOrder);
    }

    private boolean isAuctionOver(boolean isMatchingOver, Security security) {
        return isMatchingOver || security.getOpeningPrice() == INVALID_PRICE || !security.getOrderBook().hasOrderOfType(Side.BUY)
                || !security.getOrderBook().hasOrderOfType(Side.SELL);
    }

    public int calculateOpeningPrice(OrderBook orderBook, int lastTradedPrice) {
        int maxTradeableQuantity = 1;
        ArrayList<Integer> potentialPrices = new ArrayList<>();
        int lowestPrice = orderBook.getLowestPriorityOrderPrice(Side.BUY);
        int highestPrice = orderBook.getLowestPriorityOrderPrice(Side.SELL);
        if (lowestPrice == INVALID_PRICE || highestPrice == INVALID_PRICE)
            return INVALID_PRICE;

        for (int openingPrice = lowestPrice; openingPrice <= highestPrice; openingPrice++) {
            int tradeableQuantity = calculateTradableQuantity(orderBook, openingPrice);
            if (tradeableQuantity >= maxTradeableQuantity) {
                if (tradeableQuantity > maxTradeableQuantity) {
                    maxTradeableQuantity = tradeableQuantity;
                    potentialPrices.clear();
                }
                potentialPrices.add(openingPrice);
            }
        }
        if (potentialPrices.isEmpty())
            return INVALID_PRICE;
        return openingPriceClosestToLastTradedPrice(lastTradedPrice, potentialPrices);
    }

    private int openingPriceClosestToLastTradedPrice(int lastTradedPrice, ArrayList<Integer> potentialPrices) {
        int closestPrice = lastTradedPrice;
        int minDifference = Integer.MAX_VALUE;
        for (int price : potentialPrices) {
            int difference = Math.abs(price - lastTradedPrice);
            if (difference < minDifference) {
                minDifference = difference;
                closestPrice = price;
            }
            else if (difference == minDifference) {
                closestPrice = lastTradedPrice;
            }
        }
        return closestPrice;
    }

    private int calculateTradableQuantity(OrderBook orderBook, int newOpeningPrice) {
        LinkedList<Order> matchingBuyOrders = orderBook.findAllMatchingOrdersWithPrice(newOpeningPrice, Side.BUY);
        LinkedList<Order> matchingSellOrders = orderBook.findAllMatchingOrdersWithPrice(newOpeningPrice, Side.SELL);

        int totalBuyQuantity = matchingBuyOrders.stream()
                .mapToInt(Order::getTotalQuantity)
                .sum();
        int totalSellQuantity = matchingSellOrders.stream()
                .mapToInt(Order::getTotalQuantity)
                .sum();

        return Math.min(totalBuyQuantity, totalSellQuantity);
    }
    public MatchResult updateOpeningPrice(Security security) {
        int newOpeningPrice = calculateOpeningPrice(security.getOrderBook(), security.getLastTradedPrice());
        return MatchResult.updateOpeningPrice(security.getLastTradedPrice(),
                calculateTradableQuantity(security.getOrderBook(), newOpeningPrice), newOpeningPrice);
    }
    private int getLastTradedPriceAfterMatch(Security security) {
        if(security.getOpeningPrice() != INVALID_PRICE)
            return security.getOpeningPrice();
        else
            return security.getLastTradedPrice();
    }
}