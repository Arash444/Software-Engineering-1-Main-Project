package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
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
    ContinuousMatcher continuousMatcher;
    AuctionMatcher auctionMatcher;
    StopLimitOrderActivator stopLimitOrderActivator;

    public OrderHandler(SecurityRepository securityRepository, BrokerRepository brokerRepository,
                        ShareholderRepository shareholderRepository, EventPublisher eventPublisher,
                        ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher,
                        StopLimitOrderActivator stopLimitOrderActivator) {
        this.securityRepository = securityRepository;
        this.brokerRepository = brokerRepository;
        this.shareholderRepository = shareholderRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
        this.stopLimitOrderActivator = stopLimitOrderActivator;
    }

    public void handleEnterOrder(EnterOrderRq enterOrderRq) {
        long requestId = enterOrderRq.getRequestId();
        long orderId = enterOrderRq.getOrderId();
        try {
            validateEnterOrderRq(enterOrderRq);

            Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
            Broker broker = brokerRepository.findBrokerById(enterOrderRq.getBrokerId());
            Shareholder shareholder = shareholderRepository.findShareholderById(enterOrderRq.getShareholderId());
            MatchResult matchResult;
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER) 
                matchResult = enterNewOrder(enterOrderRq, security, broker, shareholder, security.getMatchingState());
            else
                matchResult = enterUpdateOrder(enterOrderRq, security, security.getMatchingState());

            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_CREDIT) {
                eventPublisher.publish(new OrderRejectedEvent(requestId, orderId,
                        List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_POSITIONS) {
                eventPublisher.publish(new OrderRejectedEvent(requestId, orderId,
                        List.of(Message.SELLER_HAS_NOT_ENOUGH_POSITIONS)));
                return;
            }
            if (matchResult.outcome() == MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY) {
                eventPublisher.publish(new OrderRejectedEvent(requestId, orderId,
                        List.of(Message.NOT_ENOUGH_TRADED_QUANTITY)));
                return;
            }
            if (enterOrderRq.getRequestType() == OrderEntryType.NEW_ORDER)
                eventPublisher.publish(new OrderAcceptedEvent(requestId, orderId));
            else
                eventPublisher.publish(new OrderUpdatedEvent(requestId, orderId));
            if (matchResult.hasOrderBeenActivated())
                eventPublisher.publish(new OrderActivatedEvent(requestId, orderId));
            if (!matchResult.trades().isEmpty()) {
                eventPublisher.publish(new OrderExecutedEvent(requestId, orderId,
                        matchResult.trades().stream().map(TradeDTO::new).collect(Collectors.toList())));
                stopLimitOrderActivator.handleStopLimitOrderActivation(security, continuousMatcher, eventPublisher);
            }
            if (security.getMatchingState() == MatchingState.AUCTION)
                eventPublisher.publish(new OpeningPriceEvent(security.getIsin(), matchResult.getOpeningPrice(),
                        matchResult.getTradableQuantity()));

        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new OrderRejectedEvent(requestId, orderId, ex.getReasons()));
        }
    }

    private MatchResult enterUpdateOrder(EnterOrderRq enterOrderRq, Security security, MatchingState currentMatchingState) throws InvalidRequestException {
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
            eventPublisher.publish(new OrderRejectedEvent(deleteOrderRq.getRequestId(), deleteOrderRq.getOrderId(), ex.getReasons()));
        }
    }

    private void validateEnterOrderRq(EnterOrderRq enterOrderRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getOrderId() <= 0)
            errors.add(Message.INVALID_ORDER_ID);
        if (enterOrderRq.getQuantity() <= 0)
            errors.add(Message.ORDER_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getPrice() <= 0)
            errors.add(Message.ORDER_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() < 0)
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE);
        if (enterOrderRq.getMinimumExecutionQuantity() > enterOrderRq.getQuantity())
            errors.add(Message.ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY);
        if (enterOrderRq.getStopPrice() < 0)
            errors.add(Message.ORDER_STOP_PRICE_NOT_POSITIVE);
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY);
        if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getPeakSize() != 0)
            errors.add(Message.CANNOT_BE_ICEBERG_AND_STOP_LIMIT);
        Security security = securityRepository.findSecurityByIsin(enterOrderRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        else {
            if (enterOrderRq.getQuantity() % security.getLotSize() != 0)
                errors.add(Message.QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE);
            if (enterOrderRq.getPrice() % security.getTickSize() != 0)
                errors.add(Message.PRICE_NOT_MULTIPLE_OF_TICK_SIZE);
            if (security.getMatchingState() == MatchingState.AUCTION)
                errors.addAll(auctionEnterOrderErrors(enterOrderRq));
        }
        if (brokerRepository.findBrokerById(enterOrderRq.getBrokerId()) == null)
            errors.add(Message.UNKNOWN_BROKER_ID);
        if (shareholderRepository.findShareholderById(enterOrderRq.getShareholderId()) == null)
            errors.add(Message.UNKNOWN_SHAREHOLDER_ID);
        if (enterOrderRq.getPeakSize() < 0 || enterOrderRq.getPeakSize() >= enterOrderRq.getQuantity())
            errors.add(Message.INVALID_PEAK_SIZE);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private List<String> auctionEnterOrderErrors(EnterOrderRq enterOrderRq) {
        List<String> errors = new LinkedList<>();
        if (enterOrderRq.getStopPrice() != 0)
            errors.add(Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS);
        if (enterOrderRq.getMinimumExecutionQuantity() != 0)
            errors.add(Message.ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY);
        return errors;
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
