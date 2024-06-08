package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public abstract class BaseValidator {
    protected BaseValidator nextValidator;

    public void setNextValidator(BaseValidator nextValidator) {
        this.nextValidator = nextValidator;
    }

    protected boolean canValidate(ValidationContext context) {
        return true;
    }

    protected abstract void validate(ValidationContext context, List<String> errors);

    protected void validateNext(ValidationContext context, List<String> errors) {
        if (nextValidator != null) {
            nextValidator.validateWithCondition(context, errors);
        }
    }

    public void validateWithCondition(ValidationContext context, List<String> errors) {
        if (canValidate(context)) {
            validate(context, errors);
        } else {
            validateNext(context, errors);
        }
    }
}