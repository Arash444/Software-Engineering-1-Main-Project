package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final int lastTradedPrice;
    private boolean justBeenActivated;

    public static MatchResult executed(Order remainder, List<Trade> trades, int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades), lastTradedPrice);
    }

    public static MatchResult notEnoughCredit(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), lastTradedPrice);
    }
    public static MatchResult notEnoughPositions(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), lastTradedPrice);
    }
    public static MatchResult notEnoughTradedQuantity(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY, null, new LinkedList<>(), lastTradedPrice);
    }
    private MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades, int lastTradedPrice) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.lastTradedPrice = lastTradedPrice;
        this.justBeenActivated = false;

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

    public void activate() {justBeenActivated = true;}

    public boolean hasJustBeenActivated() {return justBeenActivated;}

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
