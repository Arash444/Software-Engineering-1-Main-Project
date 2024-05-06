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
        MatchingState targetState = matchingStateRq.getTargetState();
        Security security = securityRepository.findSecurityByIsin(matchingStateRq.getSecurityIsin());
        MatchingState currentState = security.getMatchingState();

        MatchResult matchResult = null;
        if(shouldOpenAuction(currentState, targetState)){
            matchResult = security.openAuction(matcher);
        }

        security.setMatchingState(matchingStateRq.getTargetState());
        eventPublisher.publish(new SecurityStateChangedEvent(security.getIsin(), targetState));

        if(matchResult != null){
            for (Trade trade : matchResult.trades()){
                eventPublisher.publish(new TradeEvent(trade));
            }
        }
        //ToDo: See if the opening price has to be published when we change from one state to another

    }
    private boolean shouldOpenAuction(MatchingState currentState, MatchingState newState){
        return currentState == MatchingState.AUCTION &&
                (newState == MatchingState.AUCTION || newState == MatchingState.CONTINUOUS);
    }
}
