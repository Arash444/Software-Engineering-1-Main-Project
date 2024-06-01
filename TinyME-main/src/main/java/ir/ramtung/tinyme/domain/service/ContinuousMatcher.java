package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public class ContinuousMatcher extends Matcher {
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
                rollbackTradesBuy(newOrder, trades);
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
        if (shareholderDoesNotHaveEnoughPosition(order))
            return MatchResult.notEnoughPositions(previous_last_traded_price, previous_opening_price);
        if (!order.canTrade()) {
            StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
            if(!stopLimitOrder.hasReachedStopPrice(previous_last_traded_price))
                return handleNonActivatedOrder(stopLimitOrder, previous_last_traded_price);
            else {
                order = stopLimitOrder.convertToOrder();
                hasActivatedOrder = true;
            }
        }

        MatchResult result = match(order);
        if (result.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT)
            return result;
        int total_traded_quantity = result.trades().stream().mapToInt(Trade::getQuantity).sum();
        if (result.remainder().getQuantity() > 0 && (total_traded_quantity >= order.getMinimumExecutionQuantity()
                || isAmendOrder)) {
            if (order.getSide() == Side.BUY) {
                if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    rollbackTradesBuy(order, result.trades());
                    return MatchResult.notEnoughCredit(previous_last_traded_price, -1);
                }
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            order.getSecurity().getOrderBook().enqueue(result.remainder());
        }

        else if (total_traded_quantity < order.getMinimumExecutionQuantity()) {
            if (order.getSide() == Side.BUY)
                rollbackTradesBuy(order, result.trades());
            else if (order.getSide() == Side.SELL)
                rollbackTradesSell(order, result.trades());
            return MatchResult.notEnoughTradedQuantity(previous_last_traded_price);
        }
        adjustShareholderPositions(result.trades());
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

    private void rollbackTradesBuy(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.BUY;
        newOrder.getBroker().increaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());
        trades.forEach(trade -> trade.getSell().getBroker().decreaseCreditBy(trade.getTradedValue()));

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
            newOrder.getSecurity().getOrderBook().restoreSellOrder(it.previous().getSell());
        }
    }

    private void rollbackTradesSell(Order newOrder, LinkedList<Trade> trades) {
        assert newOrder.getSide() == Side.SELL;
        newOrder.getBroker().decreaseCreditBy(trades.stream().mapToLong(Trade::getTradedValue).sum());

        ListIterator<Trade> it = trades.listIterator(trades.size());
        while (it.hasPrevious()) {
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
