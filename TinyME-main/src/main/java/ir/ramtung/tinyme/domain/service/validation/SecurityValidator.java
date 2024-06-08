package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import java.util.List;

public class SecurityValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.security() == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (context.enterOrderRq().getQuantity() % context.security().getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (context.enterOrderRq().getPrice() % context.security().getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
        }
        validateNext(context, errors);
    }
}
