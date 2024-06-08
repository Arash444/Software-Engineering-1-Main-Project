package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class MinimumExecutionQuantityValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE);
        if (context.enterOrderRq().getMinimumExecutionQuantity() > context.enterOrderRq().getQuantity())
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY);

        validateNext(context, errors);
    }
}