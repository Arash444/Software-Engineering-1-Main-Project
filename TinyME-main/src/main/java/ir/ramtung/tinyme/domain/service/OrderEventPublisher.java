package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
@Service
public class OrderEventPublisher {
    EventPublisher eventPublisher;

    public OrderEventPublisher(EventPublisher eventPublisher){
        this.eventPublisher = eventPublisher;
    }
    public void publishEvents(EnterOrderRq enterOrderRq, Security security, MatchResult matchResult) {
        publishRejectedEvents(enterOrderRq, matchResult);
        publishAcceptedEvents(enterOrderRq);
        publishActivatedEvents(enterOrderRq, matchResult);
        publishExecutedEvents(enterOrderRq, matchResult);
        publishOpeningPriceEvent(security, matchResult);
    }

    private void publishActivatedEvents(EnterOrderRq enterOrderRq, MatchResult matchResult) {
        if (matchResult.hasOrderBeenActivated())
            eventPublisher.publish(new OrderActivatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }

    private void publishOpeningPriceEvent(Security security, MatchResult matchResult) {
        if (security.getMatchingState() == MatchingState.AUCTION)
            eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), matchResult.getOpeningPrice(),
                    matchResult.getTradableQuantity()));
    }

    private void publishExecutedEvents(EnterOrderRq enterOrderRq, MatchResult matchResult) {
        if (!matchResult.trades().isEmpty()) {
            eventPublisher.publish(new OrderExecutedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
        }
    }

    private void publishAcceptedEvents(EnterOrderRq enterOrderRq) {
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            eventPublisher.publish(new OrderAcceptedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
        else
            eventPublisher.publish(new OrderUpdatedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId()));
    }

    private void publishRejectedEvents(EnterOrderRq enterOrderRq, MatchResult matchResult) {
        if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        }
        else if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
        }
        else if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY) {
            eventPublisher.publish(new OrderRejectedEvent(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(),
                    List.of(Message.NOT_ENOUGH_TRADED_QUANTITY)));
        }
    }

    public void publishErrors(long requestId, long orderId, List<String> reasons) {
        eventPublisher.publish(new OrderRejectedEvent(requestId, orderId, reasons));
    }
}
