package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class IcebergOrderValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getPeakSize() < 0 ||
                context.enterOrderRq().getPeakSize() >= context.enterOrderRq().getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);

        validateNext(context, errors);
    }
}
