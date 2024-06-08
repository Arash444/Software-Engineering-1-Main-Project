package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class BrokerValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.broker() == null)
            errors.add(Message.UNKNOWN_BROKER_ID);

        validateNext(context, errors);
    }
}
