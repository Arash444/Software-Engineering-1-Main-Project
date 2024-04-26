package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final int lastTradedPrice;
    private final boolean activatedOrder;


    public static MatchResult executed(Order remainder, List<Trade> trades, int lastTradedPrice, boolean activatedOrder) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades), lastTradedPrice, activatedOrder);
    }

    public static MatchResult notEnoughCredit(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), lastTradedPrice, false);
    }
    public static MatchResult notEnoughPositions(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), lastTradedPrice, false);
    }
    public static MatchResult notEnoughTradedQuantity(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY, null, new LinkedList<>(), lastTradedPrice, false);
    }
    private MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades, int lastTradedPrice, boolean activatedOrder) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.lastTradedPrice = lastTradedPrice;
        this.activatedOrder = activatedOrder;
    }

    public MatchingOutcome outcome() {
        return outcome;
    }
    public Order remainder() {
        return remainder;
    }
    public int getLastTradedPrice() {
        return lastTradedPrice;
    }

    public LinkedList<Trade> trades() {
        return trades;
    }

    public boolean hasOrderBeenActivated() {return activatedOrder;}

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MatchResult) obj;
        return Objects.equals(this.remainder, that.remainder) &&
                Objects.equals(this.trades, that.trades);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remainder, trades);
    }

    @Override
    public String toString() {
        return "MatchResult[" +
                "remainder=" + remainder + ", " +
                "trades=" + trades + ']';
    }


}
