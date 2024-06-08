package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.control.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class ContinuousMatcher extends Matcher {
    @Autowired
    private MatchingControlList controls;
    private boolean hasActivatedOrder = false;
    @Override
    public MatchResult match(Security security, Order order){
        return this.match(order);
    }
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        int previousLastTradedPrice = newOrder.getSecurity().getLastTradedPrice();
        int lastTradedPrice = previousLastTradedPrice;
        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;
            int tradeQuantity = Math.min(newOrder.getQuantity(), matchingOrder.getQuantity());
            addNewTrade(matchingOrder.getPrice(), trades, newOrder, matchingOrder, tradeQuantity);
            if (newOrder.getSide() == Side.BUY && !trades.getLast().buyerHasEnoughCredit()){
                rollbackTrades(newOrder, trades);
                return MatchResult.notEnoughCredit(previousLastTradedPrice, -1);
            }
            lastTradedPrice = matchingOrder.getPrice();
            matchTheTwoOrders(-1, orderBook, trades,
                    matchingOrder, newOrder, -1);
        }
        return MatchResult.executedContinuous(newOrder, trades, lastTradedPrice, hasActivatedOrder);
    }
    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        int previous_last_traded_price = order.getSecurity().getLastTradedPrice();
        int previous_opening_price = order.getSecurity().getOpeningPrice();
        if (!order.canTrade()) {
            StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
            if(!stopLimitOrder.hasReachedStopPrice(previous_last_traded_price))
                return handleNonActivatedOrder(stopLimitOrder, previous_last_traded_price);
            else {
                order = stopLimitOrder.convertToOrder();
                hasActivatedOrder = true;
            }
        }
        MatchingOutcome outcome = controls.canStartMatching(order);
        if (outcome != MatchingOutcome.EXECUTED)
            return new MatchResult(outcome, order, new LinkedList<>(), previous_last_traded_price,
                    false, 0, previous_opening_price);

        controls.matchingStarted(order);

        MatchResult result = match(order);
        if (result.outcome() != MatchingOutcome.EXECUTED)
            return result;

        outcome = controls.canAcceptMatching(order, result);
        if (outcome != MatchingOutcome.EXECUTED) {
            controls.rollbackTrades(order, result.trades());
            rollbackTrades(order, result.trades());
            return new MatchResult(outcome, order, new LinkedList<>(), previous_last_traded_price,
                    false, 0, previous_opening_price);
        }

        if (result.remainder().getQuantity() > 0) {
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }

        controls.matchingAccepted(order, result);
        return result;
    }

    @Override
    protected void matchTheTwoOrders(int price, OrderBook orderBook, LinkedList<Trade> trades,
                                     Order matchingOrder, Order newOrder, int tradeQuantity) {
        adjustBrokerCredit(newOrder, trades.getLast());
        decreaseOrderQuantity(newOrder, matchingOrder);
        removeZeroQuantityOrder(orderBook, matchingOrder);
        replenishIcebergOrder(orderBook, matchingOrder);
    }
    private void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {
        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            if (newOrder.getSide() == Side.BUY)
                newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
            else
                newOrder.getSecurity().getOrderBook().restoreBuyOrder(it.previous().getBuy());
        }
    }
    private MatchResult handleNonActivatedOrder(StopLimitOrder stopLimitOrder, int previous_last_traded_price) {
        if (stopLimitOrder.getSide() == Side.BUY) {
            if (!stopLimitOrder.getBroker().hasEnoughCredit(stopLimitOrder.getValue())) {
                return MatchResult.notEnoughCredit(previous_last_traded_price, -1);
            }
            stopLimitOrder.getBroker().decreaseCreditBy(stopLimitOrder.getValue());
        }
        stopLimitOrder.getSecurity().getStopLimitOrderBook().enqueue(stopLimitOrder);
        return MatchResult.executedContinuous(stopLimitOrder, List.of(), previous_last_traded_price, false);
    }

}
