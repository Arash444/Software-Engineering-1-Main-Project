package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.util.List;

public class OrderTypeConsistencyValidator extends BaseValidator{
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        if (context.enterOrderRq().getStopPrice() != 0 &&
                context.enterOrderRq().getMinimumExecutionQuantity() != 0)
            errors.add(Message.CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY);
        if (context.enterOrderRq().getStopPrice() != 0 &&
                context.enterOrderRq().getPeakSize() != 0)
            errors.add(Message.CANNOT_BE_ICEBERG_AND_STOP_LIMIT);
        //if (context.enterOrderRq().getPeakSize() != 0 &&
        //        context.enterOrderRq().getMinimumExecutionQuantity() != 0)
        //    errors.add(Message.CANNOT_HAVE_BOTH_PEAK_SIZE_AND_MIN_EXE_QUANTITY);

        validateNext(context, errors);
    }
}
