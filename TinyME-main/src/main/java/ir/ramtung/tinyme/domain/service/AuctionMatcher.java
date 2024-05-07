package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Side;

public class AuctionMatcher extends Matcher{
    public MatchResult match(Order newOrder) {
        //ToDo
        return null;
    }
    @Override
    public MatchResult execute(Order order, Boolean isAmendOrder) {
        int lastTradedPrice = order.getSecurity().getLastTradedPrice();
        if (!order.canTrade())
            return MatchResult.stopLimitOrdersCannotEnterAuctions(lastTradedPrice);
        if (order.getMinimumExecutionQuantity() != 0)
            return MatchResult.ordersInAuctionCannotHaveMinimumExecutionQuantity(lastTradedPrice);
        if (order.getSide() == Side.BUY) {
            if (!order.getBroker().hasEnoughCredit(order.getValue())) {
                    return MatchResult.notEnoughCredit(lastTradedPrice);
            }
            order.getBroker().decreaseCreditBy(order.getValue());
        }
        order.getSecurity().getOrderBook().enqueue(order);
        return MatchResult.executed(order, null, lastTradedPrice, false);
    }
}
