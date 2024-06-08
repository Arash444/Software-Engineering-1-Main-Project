package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.validation.Validation;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.messaging.request.OrderEntryType;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderHandler {
    SecurityRepository securityRepository;
    BrokerRepository brokerRepository;
    ShareholderRepository shareholderRepository;
    EventPublisher eventPublisher;
    OrderEventPublisher orderEventPublisher;
    ContinuousMatcher continuousMatcher;
    AuctionMatcher auctionMatcher;
    StopLimitOrderActivator stopLimitOrderActivator;
    Validation validation;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                        ShareholderRepository shareholderRepository, OrderEventPublisher orderEventPublisher,
                        ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher,
                        StopLimitOrderActivator stopLimitOrderActivator, Validation validation, EventPublisher eventPublisher) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
        this.stopLimitOrderActivator = stopLimitOrderActivator;
        this.validation = validation;
        this.eventPublisher = eventPublisher;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
        Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
        try {
            validation.validateEnterOrderRq(enterOrderRq, security, broker, shareholder);
        } catch (InvalidRequestException ex) {
            orderEventPublisher.publishErrors(enterOrderRq.getRequestId(), enterOrderRq.getOrderId(), ex.getReasons());
            return;
        }
        MatchResult matchResult;
        matchResult = getMatchResult(enterOrderRq, security, broker, shareholder);
        orderEventPublisher.publishEvents(enterOrderRq, security, matchResult);
        if (!matchResult.trades().isEmpty()) {
            activateStopLimitOrders(security);
        }
    }

    private void activateStopLimitOrders(Security security) {
        if (security.getMatchingState() == MatchingState.AUCTION)
            stopLimitOrderActivator.handleStopLimitOrderActivation(security, auctionMatcher, eventPublisher);
        else
            stopLimitOrderActivator.handleStopLimitOrderActivation(security, continuousMatcher, eventPublisher);
    }

    private MatchResult getMatchResult(EnterOrderRq enterOrderRq, Security security, Broker broker, Shareholder shareholder) {
        MatchResult matchResult;
        if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
            matchResult = enterNewOrder(enterOrderRq, security, broker, shareholder, security.getMatchingState());
        else
            matchResult = enterUpdateOrder(enterOrderRq, security, security.getMatchingState());
        return matchResult;
    }

    private MatchResult enterUpdateOrder(EnterOrderRq enterOrderRq, Security security, MatchingState currentMatchingState) {
        MatchResult matchResult;
        if (currentMatchingState == MatchingState.AUCTION)
            matchResult = security.updateOrder(enterOrderRq, auctionMatcher);
        else
            matchResult = security.updateOrder(enterOrderRq, continuousMatcher);
        return matchResult;
    }

    private MatchResult enterNewOrder(EnterOrderRq enterOrderRq, Security security, Broker broker, Shareholder shareholder, MatchingState currentMatchingState) {
        MatchResult matchResult;
        if(currentMatchingState == MatchingState.AUCTION)
            matchResult = security.newOrder(enterOrderRq, broker, shareholder, auctionMatcher);
        else
            matchResult = security.newOrder(enterOrderRq, broker, shareholder, continuousMatcher);
        return matchResult;
    }

    public void handleDeleteOrder(DeleteOrderRq deleteOrderRq) {
        try {
            validateDeleteOrderRq(deleteOrderRq);
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            security.deleteOrder(deleteOrderRq);
            eventPublisher.publish(new OrderDeletedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId()));
            if(security.getMatchingState() == MatchingState.AUCTION) {
                MatchResult matchResult = security.updateOpeningPrice(auctionMatcher);
                eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), matchResult.getOpeningPrice(),
                        matchResult.getTradableQuantity()));
            }

        } catch (InvalidRequestException ex) {
            orderEventPublisher.publishErrors(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons());
        }
    }

    private void validateDeleteOrderRq(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (deleteOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin()) == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            Security security = securityRepository.findSecurityByIsin(deleteOrderRq.getSecurityIsin());
            Order deleteOrder = security.getOrderBook().findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            StopLimitOrder stopLimitDeleteOrder = (StopLimitOrder) security.getStopLimitOrderBook().findByOrderId(deleteOrderRq.getSide(),
                    deleteOrderRq.getOrderId());
            if (deleteOrder == null && stopLimitDeleteOrder == null)
                errors.add(Message.ORDER_ID_NOT_FOUND);
            if (security.getMatchingState() == MatchingState.AUCTION && stopLimitDeleteOrder != null)
                errors.add(Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS);
        }
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }
}
