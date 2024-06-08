package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class GeneralOrderValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (context.enterOrderRq().getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (context.enterOrderRq().getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);

        validateNext(context, errors);
    }
}