package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.request.ChangingMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.SecurityRepository;

public class MatcherStateHandler {
    SecurityRepository securityRepository;
    EventPublisher eventPublisher;
    Matcher matcher;

    public MatcherStateHandler(SecurityRepository securityRepository, EventPublisher eventPublisher, Matcher matcher) {
        this.securityRepository = securityRepository;
        this.eventPublisher = eventPublisher;
        this.matcher = matcher;
    }
    public void handleChangingMatchingStateRq(ChangingMatchingStateRq matchingStateRq){
        MatchResult matchResult = null;
        MatchingState targetState = matchingStateRq.getTargetState();
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        if(security == null)
            return;
        MatchingState currentState = security.getMatchingState();
        if(shouldOpenAuction(currentState, targetState)){
            matchResult = security.openAuction(matcher);
        }
        security.setMatchingState(matchingStateRq.getTargetState());
        publishChangingMatchingStateRqEvents(targetState, security, matchResult);
        //ToDo: See if the opening price has to be published when we change from one state to another
    }

    private void publishChangingMatchingStateRqEvents(MatchingState targetState, Security security, MatchResult matchResult) {
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), targetState));
        publishTradeEvents(matchResult);
    }

    private void publishTradeEvents(MatchResult matchResult) {
        if(matchResult != null){
            for (Trade trade : matchResult.trades()){
                eventPublisher.publish(new TradeEvent(trade));
            }
        }
    }

    private boolean shouldOpenAuction(MatchingState currentState, MatchingState newState){
        return currentState == MatchingState.AUCTION &&
                (newState == MatchingState.AUCTION || newState == MatchingState.CONTINUOUS);
    }
}
