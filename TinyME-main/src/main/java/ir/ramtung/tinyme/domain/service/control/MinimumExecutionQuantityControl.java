package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Component;

@Component
public class MinimumExecutionQuantityControl implements MatchingControl {
    public MatchingOutcome canAcceptMatching(Order order, MatchResult result) {
        int total_traded_quantity = result.trades().stream().mapToInt(Trade::getQuantity).sum();
        if (total_traded_quantity >= order.getMinimumExecutionQuantity())
            return MatchingOutcome.EXECUTED;
        else return MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY;
    }
}
