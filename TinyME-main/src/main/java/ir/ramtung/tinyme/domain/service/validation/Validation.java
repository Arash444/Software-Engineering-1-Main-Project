package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class Validation {
    private final BaseValidator validationChain;

    public Validation() {
        validationChain = createValidationChain();
    }

    private BaseValidator createValidationChain() {
        BaseValidator orderValidator = new GeneralOrderValidator();
        BaseValidator icebergOrderValidator = new IcebergOrderValidator();
        BaseValidator minimumExecutionQuantityValidator= new MinimumExecutionQuantityValidator();
        BaseValidator stopLimitOrderValidator= new StopLimitOrderValidator();
        BaseValidator orderTypeConsistencyValidator = new OrderTypeConsistencyValidator();
        BaseValidator brokerValidator = new BrokerValidator();
        BaseValidator shareholderValidator = new ShareholderValidator();
        BaseValidator securityValidator = new SecurityValidator();
        BaseValidator auctionValidator = new AuctionValidator();
        BaseValidator updateOrderValidator = new UpdateOrderValidator();

        orderValidator.setNextValidator(icebergOrderValidator);
        icebergOrderValidator.setNextValidator(minimumExecutionQuantityValidator);
        minimumExecutionQuantityValidator.setNextValidator(stopLimitOrderValidator);
        stopLimitOrderValidator.setNextValidator(orderTypeConsistencyValidator);
        orderTypeConsistencyValidator.setNextValidator(brokerValidator);
        brokerValidator.setNextValidator(shareholderValidator);
        shareholderValidator.setNextValidator(securityValidator);
        securityValidator.setNextValidator(auctionValidator);
        auctionValidator.setNextValidator(updateOrderValidator);

        return orderValidator;
    }

    public void validateEnterOrderRq(EnterOrderRq enterOrderRq, Security security,
                                     Broker broker, Shareholder shareholder) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        ValidationContext context = new ValidationContext(enterOrderRq, security, broker, shareholder);

        validationChain.validateWithCondition(context, errors);

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}