package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class StopLimitOrderValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getStopPrice() < 0)
            errors.add(Message.ORDER_STOP_PRICE_NOT_POSITIVE);

        validateNext(context, errors);
    }
}
