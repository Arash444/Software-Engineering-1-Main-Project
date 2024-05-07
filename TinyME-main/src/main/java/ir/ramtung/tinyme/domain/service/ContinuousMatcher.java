package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class ContinuousMatcher extends Matcher {
    private boolean hasActivatedOrder = false;
    @Override
    public MatchResult match(Order newOrder) {
        OrderBook orderBook = newOrder.getSecurity().getOrderBook();
        LinkedList<Trade> trades = new LinkedList<>();
        int previous_last_traded_price = newOrder.getSecurity().getLatestMatchingPrice();
        int last_traded_price = previous_last_traded_price;
        while (orderBook.hasOrderOfType(newOrder.getSide().opposite()) && newOrder.getQuantity() > 0) {
            Order matchingOrder = orderBook.matchWithFirst(newOrder);
            if (matchingOrder == null)
                break;

            Trade trade = new Trade(newOrder.getSecurity(), matchingOrder.getPrice(),
                    Math.min(newOrder.getQuantity(), matchingOrder.getQuantity()), newOrder, matchingOrder);
            if (newOrder.getSide() == Side.BUY) {
                if (trade.buyerHasEnoughCredit())
                    trade.decreaseBuyersCredit();
                else {
                    rollbackTradesBuy(newOrder, trades);
                    return MatchResult.notEnoughCredit(previous_last_traded_price);
                }
            }
            trade.increaseSellersCredit();
            trades.add(trade);
            last_traded_price = matchingOrder.getPrice();

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
        return MatchResult.executed(newOrder, trades, last_traded_price, hasActivatedOrder);
    }
    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        int previous_last_traded_price = order.getSecurity().getLatestMatchingPrice();
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
                    return MatchResult.notEnoughCredit(previous_last_traded_price);
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
        if (!result.trades().isEmpty()) {
            for (Trade trade : result.trades()) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
        return result;
    }


    private MatchResult handleNonActivatedOrder(StopLimitOrder stopLimitOrder, int previous_last_traded_price) {
        if (stopLimitOrder.getSide() == Side.BUY) {
            if (!stopLimitOrder.getBroker().hasEnoughCredit(stopLimitOrder.getValue())) {
                return MatchResult.notEnoughCredit(previous_last_traded_price);
            }
            stopLimitOrder.getBroker().decreaseCreditBy(stopLimitOrder.getValue());
        }
        stopLimitOrder.getSecurity().getStopLimitOrderBook().enqueue(stopLimitOrder);
        return MatchResult.executed(stopLimitOrder, List.of(), previous_last_traded_price, false);
    }

}
