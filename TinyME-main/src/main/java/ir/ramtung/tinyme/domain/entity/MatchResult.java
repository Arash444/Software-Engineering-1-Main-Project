package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final int latestMatchingPrice;
    private final boolean hasActivatedOrder;
    private final int tradableQuantity;


    public static MatchResult executed(Order remainder, List<Trade> trades, int latestMatchingPrice,
                                       boolean hasActivatedOrder) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades), latestMatchingPrice,
                hasActivatedOrder, 0);
    }
    public static MatchResult queuedInAuction(Order remainder, int latestMatchingPrice, int tradableQuantity) {
        return new MatchResult(MatchingOutcome.QUEUED_IN_AUCTION, remainder, new LinkedList<>(), latestMatchingPrice,
                false, tradableQuantity);
    }
    public static MatchResult notEnoughCredit(int latestMatchingPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), latestMatchingPrice,
                false, 0);
    }
    public static MatchResult notEnoughPositions(int latestMatchingPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), latestMatchingPrice,
                false, 0);
    }
    public static MatchResult notEnoughTradedQuantity(int latestMatchingPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY, null, new LinkedList<>(),
                latestMatchingPrice, false, 0);
    }
    private MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades,
                        int latestMatchingPrice, boolean hasActivatedOrder, int tradableQuantity) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.latestMatchingPrice = latestMatchingPrice;
        this.hasActivatedOrder = hasActivatedOrder;
        this.tradableQuantity = tradableQuantity;
    }

    public MatchingOutcome outcome() {
        return outcome;
    }
    public Order remainder() {
        return remainder;
    }
    public int getLatestMatchingPrice() {
        return latestMatchingPrice;
    }
    public int getTradableQuantity() {
        return tradableQuantity;
    }

    public LinkedList<Trade> trades() {
        return trades;
    }

    public boolean hasOrderBeenActivated() {return hasActivatedOrder;}


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
