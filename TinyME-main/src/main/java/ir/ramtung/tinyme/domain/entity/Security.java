package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private StopLimitOrderbook stopLimitOrderBook = new StopLimitOrderbook();
    @Builder.Default
    private int lastTradedPrice = 15000;

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions(lastTradedPrice);
        Order order;
        if (enterOrderRq.getPeakSize() != 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopPrice() != 0) {
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getStopPrice());
        }
        else
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getMinimumExecutionQuantity());

        MatchResult matchResult = matcher.execute(order, false);
        lastTradedPrice = matchResult.getLastTradedPrice();
        return matchResult;
    }
    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order != null)
            deleteNormalOrder(order, deleteOrderRq);
        else {
            StopLimitOrder stopLimitOrder = (StopLimitOrder) stopLimitOrderBook.findByOrderId(deleteOrderRq.getSide(),
                    deleteOrderRq.getOrderId());
            if (stopLimitOrder != null)
                deleteStopLimitOrder(stopLimitOrder, deleteOrderRq);
            else
                throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        }
    }
    public MatchResult activateOrder(StopLimitOrder originalOrder, StopLimitOrder order, Matcher matcher){
        preprocessStopLimitOrder(order, originalOrder);
        Order newOrder = order.convertToOrder();
        MatchResult matchResult = matcher.execute(newOrder, false);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            rollbackStopLimitOrder(originalOrder);
        }
        lastTradedPrice = matchResult.getLastTradedPrice();
        return matchResult;
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            order = stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CANNOT_CHANGE_MIN_EXE_QUANTITY);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);

        boolean have_changed_triggered_order_stop_price = order instanceof StopLimitOrder stopLimitOrder
                && stopLimitOrder.canTrade() && (stopLimitOrder.getStopPrice() != updateOrderRq.getStopPrice());
        boolean have_added_stop_price_to_non_stop_limit_order = !(order instanceof StopLimitOrder)
                && updateOrderRq.getStopPrice() != 0;
        if (have_added_stop_price_to_non_stop_limit_order || have_changed_triggered_order_stop_price)
            throw new InvalidRequestException(Message.CANNOT_CHANGE_STOP_PRICE);

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions(lastTradedPrice);

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize())
                || !order.canTrade());

        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);

        if (!order.canTrade() && (order instanceof StopLimitOrder stopLimitOrder)) {
            if(stopLimitOrder.hasReachedStopPrice(lastTradedPrice)) {
                stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
                return activateOrder((StopLimitOrder) originalOrder, stopLimitOrder, matcher);
            }
        }
        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(originalOrder.getValue());
        }
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of(), lastTradedPrice, false);
        }
        else
            order.markAsNew();
        if(!order.canTrade())
            stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        else
            orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.execute(order, true);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            if(!order.canTrade())
                stopLimitOrderBook.enqueue(originalOrder);
            else
                orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        lastTradedPrice = matchResult.getLastTradedPrice();
        return matchResult;
    }
    public List<StopLimitOrder> findActivatedOrders() {
        List<StopLimitOrder> ordersToActivate = new LinkedList<>();
        StopLimitOrder activatedOrder = stopLimitOrderBook.findFirstActivatedOrder(lastTradedPrice);
        while (activatedOrder != null) {
            ordersToActivate.add(activatedOrder);
            stopLimitOrderBook.removeFirst(activatedOrder.getSide());
            activatedOrder = stopLimitOrderBook.findFirstActivatedOrder(lastTradedPrice);
        }
        return ordersToActivate;
    }
    private void deleteNormalOrder(Order order, DeleteOrderRq deleteOrderRq)
    {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }
    private void deleteStopLimitOrder(StopLimitOrder stopLimitOrder, DeleteOrderRq deleteOrderRq)
    {
        if (stopLimitOrder.getSide() == Side.BUY)
            stopLimitOrder.getBroker().increaseCreditBy(stopLimitOrder.getValue());
        stopLimitOrderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    private void rollbackStopLimitOrder(StopLimitOrder originalOrder) {
        stopLimitOrderBook.enqueue(originalOrder);
        if (originalOrder.getSide() == Side.BUY) {
            originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
        }
    }
    private void preprocessStopLimitOrder(StopLimitOrder order, StopLimitOrder originalOrder) {

        if (order.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(originalOrder.getValue());
        }

    }

}
