package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.MatchingStateRqRejectedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.ChangingMatchingStateRq;
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
        try {
            validateChangingMatchingStateRq(matchingStateRq);
        } catch (InvalidRequestException ex) {
            eventPublisher.publish(new MatchingStateRqRejectedEvent(matchingStateRq.getSecurityIsin(), ex.getReasons()));
            return;
        }

        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        MatchResult matchResult = null;
        if(shouldOpenAuction(security.getMatchingState())) {
            matchResult = security.openAuction(auctionMatcher);
        }
        publishChangingMatchingStateRqEvents(matchingStateRq.getTargetState(), security, matchResult);
        activateStopLimitOrders(matchingStateRq.getTargetState(), security, security.getMatchingState());
        updateOpeningPrice(matchingStateRq.getTargetState(), security);
        security.setMatchingState(matchingStateRq.getTargetState());
    }

    private void activateStopLimitOrders(MatchingState targetState, Security security, MatchingState currentState) {
        if(shouldOpenAuction(currentState)) {
            if (targetState == MatchingState.AUCTION)
                stopLimitOrderActivator.handleStopLimitOrderActivation(security, auctionMatcher, eventPublisher);
            else
                stopLimitOrderActivator.handleStopLimitOrderActivation(security, continuousMatcher, eventPublisher);
        }
    }

    private void updateOpeningPrice(MatchingState targetState, Security security) {
        if(shouldUpdateOpeningPrice(targetState))
            security.updateOpeningPrice(auctionMatcher);
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

    private boolean shouldOpenAuction(MatchingState currentState){
        return currentState == MatchingState.AUCTION;
    }
    private boolean shouldUpdateOpeningPrice(MatchingState targetState){
        return targetState == MatchingState.AUCTION;
    }
}
