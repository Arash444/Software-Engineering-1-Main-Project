package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
    @Setter
    @Builder.Default
    private MatchingState matchingState = MatchingState.CONTINUOUS;
    @Builder.Default
    private int lastTradedPrice = 15000;
    @Setter
    @Builder.Default
    private int openingPrice = -1;

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions(lastTradedPrice, openingPrice);
        Order order;
        if (enterOrderRq.getPeakSize() != 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopPrice() != 0) {
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getStopPrice(), enterOrderRq.getRequestId());
        }
        else
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),
                    enterOrderRq.getMinimumExecutionQuantity());

        MatchResult matchResult = matcher.execute(order, false);
        updateSecurityPrices(matchResult);
        return matchResult;
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        StopLimitOrder stopLimitOrder = (StopLimitOrder) stopLimitOrderBook.findByOrderId
                (deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order != null)
            deleteNormalOrder(order);
        else
            deleteStopLimitOrder(stopLimitOrder);
    }
    public MatchResult activateOrder(StopLimitOrder originalOrder, StopLimitOrder order, Matcher matcher){
        preprocessStopLimitOrder(order, originalOrder);
        Order newOrder = order.convertToOrder();
        MatchResult matchResult = matcher.execute(newOrder, false);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            rollbackStopLimitOrder(originalOrder);
        }
        updateSecurityPrices(matchResult);
        return matchResult;
    }
    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = getOrderByID(updateOrderRq);
        //validateUpdateOrderRequest(order, updateOrderRq);
        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions(lastTradedPrice, openingPrice);
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
            return MatchResult.executedContinuous(null, List.of(), lastTradedPrice, false);
        }
        else
            order.markAsNew();

        removeOrderByID(updateOrderRq,order);
        MatchResult matchResult = matcher.execute(order, true);

        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            enqueueOrder(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }

        updateSecurityPrices(matchResult);
        return matchResult;
    }


    private void enqueueOrder(Order originalOrder){
        if(!originalOrder.canTrade())
            stopLimitOrderBook.enqueue(originalOrder);
        else
            orderBook.enqueue(originalOrder);
    }

    private void removeOrderByID(EnterOrderRq updateOrderRq, Order order){
        if(!order.canTrade())
            stopLimitOrderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        else
            orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());

    }

    private Order getOrderByID(EnterOrderRq updateOrderRq) {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            order = stopLimitOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        return order;
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
    private void deleteNormalOrder(Order order)
    {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(order.getSide(), order.getOrderId());
    }
    private void deleteStopLimitOrder(StopLimitOrder stopLimitOrder)
    {
        if (stopLimitOrder.getSide() == Side.BUY)
            stopLimitOrder.getBroker().increaseCreditBy(stopLimitOrder.getValue());
        stopLimitOrderBook.removeByOrderId(stopLimitOrder.getSide(), stopLimitOrder.getOrderId());
    }

    private void rollbackStopLimitOrder(StopLimitOrder originalOrder) {
        stopLimitOrderBook.enqueue(originalOrder);
        if (originalOrder.getSide() == Side.BUY) {
            originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
        }
    }
    private void preprocessStopLimitOrder(StopLimitOrder order, StopLimitOrder originalOrder) {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(originalOrder.getValue());
    }
    private void updateSecurityPrices(MatchResult matchResult) {
        lastTradedPrice = matchResult.getLastTradedPrice();
        openingPrice = matchResult.getOpeningPrice();
    }
    public MatchResult openAuction(AuctionMatcher auctionMatcher) {
        MatchResult matchResult = auctionMatcher.match(this);
        updateSecurityPrices(matchResult);
        return matchResult;
    }
    public MatchResult updateOpeningPrice(AuctionMatcher auctionMatcher) {
        MatchResult matchResult = auctionMatcher.updateOpeningPrice(this);
        updateSecurityPrices(matchResult);
        return matchResult;
    }
}
