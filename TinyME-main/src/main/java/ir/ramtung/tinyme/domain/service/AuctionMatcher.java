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
        int tradableQuantity = 0; //ToDo fetch the real quantity
        int latestMatchingPrice = order.getSecurity().getLatestMatchingPrice();
        if (!order.canTrade())
            return MatchResult.stopLimitOrdersCannotEnterAuctions(latestMatchingPrice);
        if (order.getMinimumExecutionQuantity() != 0)
            return MatchResult.ordersInAuctionCannotHaveMinimumExecutionQuantity(latestMatchingPrice);

        if (order.getSide() == Side.BUY) {
            if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    return MatchResult.notEnoughCredit(latestMatchingPrice);
            }
            order.getBroker().decreaseCreditBy(order.getValue());
        }

        OrderBook orderBook = order.getSecurity().getOrderBook();
        orderBook.enqueue(order);
        //int newOpeningPrice = calculateOpeningPrice(orderBook);
        return MatchResult.queuedInAuction(order, latestMatchingPrice, tradableQuantity);
    }

    /*private int calculateOpeningPrice(OrderBook orderBook) {
        List<Integer> orderPrices = findAllOrderPrices(orderBook);
        int maxTradebleQuantity = 0, newOpeningPrice = 0;
        for (int openingPrice : orderPrices){
            int tradebleQuantity = findTradableQuantity();
            if(tradebleQuantity > maxTradebleQuantity) {
                maxTradebleQuantity = tradebleQuantity;
                newOpeningPrice = openingPrice;
            }
        }
        return newOpeningPrice, maxTradebleQuantity;
    }*/
    private List<Integer> findAllOrderPrices(OrderBook orderBook){
        List<Integer> orderPrices = new ArrayList<>();
        for(Order buyOrder : orderBook.getBuyQueue()){
            orderPrices.add(buyOrder.getPrice());
        }
        for(Order sellOrder : orderBook.getSellQueue()){
            orderPrices.add(sellOrder.getPrice());
        }
        Collections.sort(orderPrices);
        return orderPrices;
    }
}
