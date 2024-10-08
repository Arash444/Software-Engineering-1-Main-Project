package ir.ramtung.tinyme.domain.service.control;

import ir.ramtung.tinyme.domain.entity.MatchResult;
import ir.ramtung.tinyme.domain.entity.MatchingOutcome;
import ir.ramtung.tinyme.domain.entity.Order;
import ir.ramtung.tinyme.domain.entity.Trade;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import java.util.LinkedList;

public interface MatchingControl {
    default MatchingOutcome canStartMatching(Order order, MatchingState matchingState) { return MatchingOutcome.EXECUTED; }
    default void matchingStarted(Order order) {}
    default MatchingOutcome canAcceptMatching(Order order, MatchResult result) { return MatchingOutcome.EXECUTED; }
    default void matchingAccepted(Order order, MatchResult result) {}

    default MatchingOutcome canTrade(Order newOrder, Trade trade) { return MatchingOutcome.EXECUTED; }
    default void tradeAccepted(Order newOrder, Trade trade) {}

    default void rollbackTrades(Order newOrder, LinkedList<Trade> trades) {}
}
