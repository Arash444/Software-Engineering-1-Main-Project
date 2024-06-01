package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class Validation {
    private List<String> auctionEnterOrderErrors(EnterOrderRq enterOrderRq) {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getStopPrice() != 0)
            errors.add(Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS);
        if (enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY);
        return errors;
    }
    public void validateEnterOrderRq(EnterOrderRq enterOrderRq, OrderHandler orderHandler) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();

        validateOrder(enterOrderRq, errors);
        validateMEQ(enterOrderRq, errors);
        validateStopLimitPriceSign(enterOrderRq, errors);
        validateStopPriceWithMEQ(enterOrderRq, errors);
        validateStopLimitWithPeak(enterOrderRq, errors);
        validateIceberg(enterOrderRq, errors);
        validateBroker(enterOrderRq, orderHandler, errors);
        validateShareholder(enterOrderRq, orderHandler, errors);

        boolean securityIsValid = validateSecurity(enterOrderRq, orderHandler, errors);
        if(enterOrderRq.getRequestType() == OrderEntryType.UPDATE_ORDER && securityIsValid) {
            Security security = orderHandler.securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            validateUpdateOrderRequest(security, enterOrderRq, errors);
        }

        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private static void validateOrder(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
    }

    private static void validateIceberg(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
    }

    private static void validateStopLimitWithPeak(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getPeakSize() != 0)
            errors.add(Message.CANNOT_BE_ICEBERG_AND_STOP_LIMIT);
    }

    private static void validateBroker(EnterOrderRq enterOrderRq, OrderHandler orderHandler, List<String> errors) {
        if (orderHandler.brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
    }

    private static void validateShareholder(EnterOrderRq enterOrderRq, OrderHandler orderHandler, List<String> errors) {
        if (orderHandler.shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
    }

    private boolean validateSecurity(EnterOrderRq enterOrderRq, OrderHandler orderHandler, List<String> errors) {
        Security security = orderHandler.securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null) {
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
            return false;
        }
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
            if (security.getMatchingState() == MatchingState.AUCTION)
                errors.addAll(auctionEnterOrderErrors(enterOrderRq));
        }
        return true;
    }

    private static void validateStopLimitPriceSign(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getStopPrice() < 0)
            errors.add(Message.ORDER_STOP_PRICE_NOT_POSITIVE);
    }

    private static void validateStopPriceWithMEQ(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY);
    }

    private void validateMEQ(EnterOrderRq enterOrderRq, List<String> errors) {
        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity())
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY);
    }
    public void validateUpdateOrderRequest(Security security, EnterOrderRq updateOrderRq, List<String> errors) {
        OrderBook orderBook = security.getOrderBook();
        StopLimitOrderbook stopLimitOrderBook = security.getStopLimitOrderBook();
        Order order = getOrderByID(orderBook, stopLimitOrderBook, updateOrderRq);
        boolean orderIdIsValid = validateOrderIdExists(order, errors);
        if(orderIdIsValid) {
            validateExecutionQuantity(order, updateOrderRq, errors);
            validateOrderType(order, updateOrderRq, errors);
            validateStopPrice(order, updateOrderRq, errors);
        }
    }

    private boolean validateOrderIdExists(Order order, List<String> errors) {
        if (order == null) {
            errors.add(Message.ORDER_ID_NOT_FOUND);
            return false;
        }
        return true;
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
