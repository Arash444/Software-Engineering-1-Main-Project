package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.OrderBook;
import ir.ramtung.tinyme.domain.entity.Side;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Service
public class AuctionMatcher extends Matcher{
    @Override
    public MatchResult match(Order newOrder) {
        //ToDo
        return null;
    }
    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        int latestMatchingPrice = order.getSecurity().getLatestMatchingPrice();

        if (order.getSide() == Side.BUY) {
            if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    return MatchResult.notEnoughCredit(latestMatchingPrice);
            }
            order.getBroker().decreaseCreditBy(order.getValue());
        }

        OrderBook orderBook = order.getSecurity().getOrderBook();
        orderBook.enqueue(order);
        //ToDo what happens when there's no orders in the orderbook or when none of them match?
        int newOpeningPrice = calculateOpeningPrice(orderBook);
        int tradableQuantity = calculateTradableQuantity(newOpeningPrice, orderBook);
        return MatchResult.queuedInAuction(order, newOpeningPrice, tradableQuantity);
    }

    private int calculateOpeningPrice(OrderBook orderBook) {
        int maxTradebleQuantity = 0, newOpeningPrice = 0;
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
    /*private List<Integer> findAllOrderPrices(OrderBook orderBook){
        List<Integer> orderPrices = new ArrayList<>();
        for(Order buyOrder : orderBook.getBuyQueue()){
            orderPrices.add(buyOrder.getPrice());
        }
        for(Order sellOrder : orderBook.getSellQueue()){
            orderPrices.add(sellOrder.getPrice());
        }
        Collections.sort(orderPrices);
        return orderPrices;
    }*/
}
