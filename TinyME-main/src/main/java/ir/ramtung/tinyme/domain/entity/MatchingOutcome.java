package ir.ramtung.tinyme.domain.entity;

public enum MatchingOutcome {
    EXECUTED,
    NOT_ENOUGH_CREDIT,
    NOT_ENOUGH_POSITIONS,
    NOT_ENOUGH_TRADED_QUANTITY,
    STOP_LIMIT_ORDERS_CANNOT_ENTER_AUCTIONS,
    ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY
}
