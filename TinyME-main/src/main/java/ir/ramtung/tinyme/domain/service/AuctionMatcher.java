package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.OrderBook;
import ir.ramtung.tinyme.domain.entity.Side;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

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

        return MatchResult.executed(order, new LinkedList<>(), calculateOpeningPrice(), false);
    }

    private int calculateOpeningPrice() {
        return 0;
    }
}
