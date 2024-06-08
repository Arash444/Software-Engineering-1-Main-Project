package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class ShareholderValidator extends BaseValidator {
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.shareholder() == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);

        validateNext(context, errors);
    }
}
