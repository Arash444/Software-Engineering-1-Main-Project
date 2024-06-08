package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.springframework.stereotype.Component;

@Component
public class OwnershipControl implements MatchingControl {
    public MatchingOutcome canStartMatching(Order order, MatchingState matchingState) {
        if (shareholderDoesNotHaveEnoughPosition(order))
            return MatchingOutcome.NOT_ENOUGH_POSITIONS;
        return MatchingOutcome.EXECUTED;
    }
    public void matchingAccepted(Order order, MatchResult result) {
        for (Trade trade : result.trades()) {
            trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
            trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
        }
    }
    private boolean shareholderDoesNotHaveEnoughPosition(Order order) {
        return order.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(order.getSecurity(),
                        order.getSecurity().getOrderBook().totalSellQuantityByShareholder(order.getShareholder())
                                + order.getQuantity());
    }
}
