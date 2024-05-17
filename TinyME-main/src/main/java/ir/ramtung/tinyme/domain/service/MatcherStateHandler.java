package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.MatchingStateRqRejectedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangingMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.SecurityRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
@Service
public class MatcherStateHandler {
    SecurityRepository securityRepository;
    EventPublisher eventPublisher;
    ContinuousMatcher continuousMatcher;
    AuctionMatcher auctionMatcher;
    StopLimitOrderActivator stopLimitOrderActivator;

    public MatcherStateHandler(SecurityRepository securityRepository, EventPublisher eventPublisher,
                               ContinuousMatcher continuousMatcher, AuctionMatcher auctionMatcher,
                               StopLimitOrderActivator stopLimitOrderActivator) {
        this.securityRepository = securityRepository;
        this.eventPublisher = eventPublisher;
        this.continuousMatcher = continuousMatcher;
        this.auctionMatcher = auctionMatcher;
        this.stopLimitOrderActivator = stopLimitOrderActivator;
    }
    public void handleChangingMatchingStateRq(ChangingMatchingStateRq matchingStateRq){
        MatchResult matchResult = null;
        MatchingState targetState = matchingStateRq.getTargetState();
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());

        try {
            validateChangingMatchingStateRq(matchingStateRq);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new MatchingStateRqRejectedEvent(security.getIsin(), ex.getReasons()));
            return;
        }

        MatchingState currentState = security.getMatchingState();
        if(shouldOpenAuction(currentState, targetState)) {
            matchResult = security.openAuction(auctionMatcher);
        }
        security.setMatchingState(matchingStateRq.getTargetState());
        publishChangingMatchingStateRqEvents(targetState, security, matchResult);

        if(shouldOpenAuction(currentState, targetState))
            activateStopLimitOrders(targetState, security);
        if(continuousToAuction(currentState, targetState))
            security.updateOpeningPrice(auctionMatcher);
    }

    private void activateStopLimitOrders(MatchingState targetState, Security security) {
        if(targetState == MatchingState.AUCTION)
            stopLimitOrderActivator.handleStopLimitOrderActivation(-1, security, auctionMatcher, eventPublisher);
        else
            stopLimitOrderActivator.handleStopLimitOrderActivation(-1, security, continuousMatcher, eventPublisher);
    }

    private void validateChangingMatchingStateRq(ChangingMatchingStateRq matchingStateRq) throws InvalidRequestException {
        List<String> errors = new LinkedList<>();
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        if (security == null)
            errors.add(Message.UNKNOWN_SECURITY_ISIN);
        if (!errors.isEmpty())
            throw new InvalidRequestException(errors);
    }

    private void publishChangingMatchingStateRqEvents(MatchingState targetState, Security security, MatchResult matchResult) {
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), targetState));
        publishTradeEvents(matchResult);
    }

    private void publishTradeEvents(MatchResult matchResult) {
        if(matchResult != null){
            for (Trade trade : matchResult.trades())
                eventPublisher.publish(new TradeEvent(trade));
        }
    }

    private boolean shouldOpenAuction(MatchingState currentState, MatchingState newState){
        return currentState == MatchingState.AUCTION &&
                (newState == MatchingState.AUCTION || newState == MatchingState.CONTINUOUS);
    }
    private boolean continuousToAuction(MatchingState currentState, MatchingState newState){
        return currentState == MatchingState.CONTINUOUS && newState == MatchingState.AUCTION;
    }
}
