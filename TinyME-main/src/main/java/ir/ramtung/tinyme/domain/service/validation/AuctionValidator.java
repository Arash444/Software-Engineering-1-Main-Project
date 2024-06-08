package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import java.util.List;

public class AuctionValidator extends BaseValidator {
    @Override
    protected boolean canValidate(ValidationContext context) {
        return context.security() != null && context.security().getMatchingState() == MatchingState.AUCTION;
    }

    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getStopPrice() != 0)
            errors.add(Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS);
        if (context.enterOrderRq().getMinimumExecutionQuantity() != 0)
            errors.add(Message.ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY);

        validateNext(context, errors);
    }
}