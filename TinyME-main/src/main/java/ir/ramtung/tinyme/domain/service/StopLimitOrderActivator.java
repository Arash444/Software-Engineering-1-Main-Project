package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Security;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StopLimitOrderActivator {
    public void handleStopLimitOrderActivation(long requestId, Security security, Matcher matcher, EventPublisher eventPublisher) {
        List<StopLimitOrder> ordersToActivate = security.findActivatedOrders();
        while (!ordersToActivate.isEmpty()) {
            StopLimitOrder stopLimitOrder = ordersToActivate.get(0);
            StopLimitOrder originalOrder = (StopLimitOrder) stopLimitOrder.snapshot();
            MatchResult matchResult = security.activateOrder(originalOrder, stopLimitOrder, matcher);
            publishRelevantEvents(stopLimitOrder.getStopLimitRequestID(), stopLimitOrder, matchResult, eventPublisher);
            findNewActivatedOrders(security, ordersToActivate, matchResult);
            ordersToActivate.remove(0);
        }
    }

    private void findNewActivatedOrders(Security security, List<StopLimitOrder> ordersToActivate,
                                        MatchResult matchResult) {
        if (!matchResult.trades().isEmpty()) {
            List<StopLimitOrder> newOrdersToActivate = security.findActivatedOrders();
            ordersToActivate.addAll(newOrdersToActivate);
        }
    }

    private void publishRelevantEvents(long requestID, StopLimitOrder stopLimitOrder, MatchResult matchResult, EventPublisher eventPublisher) {
        if (matchResult.outcome() == MatchingOutcome.EXECUTED) {
            eventPublisher.publish(new OrderActivatedEvent(requestID, stopLimitOrder.getOrderId()));
        } else if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            eventPublisher.publish(new OrderRejectedEvent(requestID, stopLimitOrder.getOrderId(),
                    List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        } else if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(requestID, stopLimitOrder.getOrderId(),
                    List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
        }
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(requestID, stopLimitOrder.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }
}
