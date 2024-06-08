package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public final class MatchResult {
    private final MatchingOutcome outcome;
    private final Order remainder;
    private final LinkedList<Trade> trades;
    private final int lastTradedPrice;
    private final boolean hasActivatedOrder;
    private final int tradableQuantity;
    private final int openingPrice;


    public static MatchResult executedContinuous(Order remainder, List<Trade> trades, int lastTradedPrice,
                                                 boolean hasActivatedOrder) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(trades), lastTradedPrice,
                hasActivatedOrder, 0, -1);
    }
    public static MatchResult executedAuction(List<Trade> trades, int lastTradedPrice,
                                                 int tradableQuantity, int openingPrice) {
        return new MatchResult(MatchingOutcome.EXECUTED, null, new LinkedList<>(trades), lastTradedPrice,
                false, tradableQuantity, openingPrice);
    }
    public static MatchResult queuedInAuction(Order remainder, int lastTradedPrice, int tradableQuantity, int openingPrice) {
        return new MatchResult(MatchingOutcome.EXECUTED, remainder, new LinkedList<>(), lastTradedPrice,
                false, tradableQuantity, openingPrice);
    }
    public static MatchResult updateOpeningPrice(int lastTradedPrice, int tradableQuantity, int openingPrice) {
        return new MatchResult(MatchingOutcome.UPDATE_OPENING_PRICE, null, new LinkedList<>(),
                lastTradedPrice,false, tradableQuantity, openingPrice);
    }
    public static MatchResult notEnoughCredit(int lastTradedPrice, int openingPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_CREDIT, null, new LinkedList<>(), lastTradedPrice,
                false, 0, openingPrice);
    }
    public static MatchResult notEnoughPositions(int lastTradedPrice, int openingPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_POSITIONS, null, new LinkedList<>(), lastTradedPrice,
                false, 0, openingPrice);
    }
    public static MatchResult notEnoughTradedQuantity(int lastTradedPrice) {
        return new MatchResult(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY, null, new LinkedList<>(),
                lastTradedPrice, false, 0, -1);
    }
    public MatchResult(MatchingOutcome outcome, Order remainder, LinkedList<Trade> trades,
                        int lastTradedPrice, boolean hasActivatedOrder, int tradableQuantity, int openingPrice) {
        this.outcome = outcome;
        this.remainder = remainder;
        this.trades = trades;
        this.lastTradedPrice = lastTradedPrice;
        this.hasActivatedOrder = hasActivatedOrder;
        this.tradableQuantity = tradableQuantity;
        this.openingPrice = openingPrice;
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
    public int getTradableQuantity() {
        return tradableQuantity;
    }
    public int getOpeningPrice() {
        return openingPrice;
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
