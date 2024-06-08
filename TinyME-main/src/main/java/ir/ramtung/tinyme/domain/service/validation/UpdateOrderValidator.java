package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;

import java.util.List;

public class UpdateOrderValidator extends BaseValidator {
    @Override
    protected boolean canValidate(ValidationContext context) {
        return context.security() != null && context.enterOrderRq().getRequestType() == OrderEntryType.UPDATE_ORDER;
    }
    @Override
    protected void validate(ValidationContext context, List<String> errors) {
        Order order = getOrderByID(context.security().getOrderBook(),
                context.security().getStopLimitOrderBook(), context.enterOrderRq());
        if (order != null) {
            validateExecutionQuantity(order, context.enterOrderRq(), errors);
            validateOrderType(order, context.enterOrderRq(), errors);
            validateStopPrice(order, context.enterOrderRq(), errors);
        }
        else
            errors.add(Message.ORDER_ID_NOT_FOUND);

        validateNext(context, errors);
    }

    private void validateExecutionQuantity(Order order, EnterOrderRq updateOrderRq, List<String> errors) {
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity()) {
            errors.add(Message.CANNOT_CHANGE_MIN_EXE_QUANTITY);
        }
    }

    private void validateOrderType(Order order, EnterOrderRq updateOrderRq, List<String> errors) {
        if (order instanceof IcebergOrder && updateOrderRq.getPeakSize() == 0) {
            errors.add(Message.INVALID_PEAK_SIZE);
        }
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0) {
            errors.add(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        }
    }

    private void validateStopPrice(Order order, EnterOrderRq updateOrderRq, List<String> errors) {
        if (!(order instanceof StopLimitOrder) && updateOrderRq.getStopPrice() != 0) {
            errors.add(Message.CANNOT_CHANGE_STOP_PRICE);
        }
    }
    private Order getOrderByID(OrderBook orderBook, StopLimitOrderbook stopLimitOrderBook, EnterOrderRq updateOrderRq) {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            order = stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        return order;
    }

}
