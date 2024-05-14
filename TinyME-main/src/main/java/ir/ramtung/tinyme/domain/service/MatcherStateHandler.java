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

import java.util.LinkedList;
import java.util.List;

public class MatcherStateHandler {
    SecurityRepository securityRepository;
    EventPublisher eventPublisher;
    ContinuousMatcher continuousMatcher;
    AuctionMatcher auctionMatcher;

    public MatcherStateHandler(SecurityRepository securityRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.eventPublisher = eventPublisher;
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
        if(shouldOpenAuction(currentState, targetState))
            matchResult = security.openAuction(auctionMatcher);
        security.setMatchingState(matchingStateRq.getTargetState());

        publishChangingMatchingStateRqEvents(targetState, security, matchResult);
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
}
