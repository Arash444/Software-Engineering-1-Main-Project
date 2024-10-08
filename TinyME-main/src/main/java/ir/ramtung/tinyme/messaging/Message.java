package ir.ramtung.tinyme.messaging;

public class Message {
    public static final String INVALID_ORDER_ID = "Invalid order ID";
    public static final String ORDER_QUANTITY_NOT_POSITIVE = "Order quantity is not-positive";
    public static final String ORDER_PRICE_NOT_POSITIVE = "Order price is not-positive";
    public static final String ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE = "Order minimum execution quantity is non-positive";
    public static final String ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY = "Order minimun execution is more than total quantity";
    public static final String UNKNOWN_SECURITY_ISIN = "Unknown security ISIN";
    public static final String ORDER_ID_NOT_FOUND = "Order ID not found in the order book";
    public static final String INVALID_PEAK_SIZE = "Iceberg order peak size is out of range";
    public static final String CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER = "Cannot specify peak size for a non-iceberg order";
    public static final String UNKNOWN_BROKER_ID = "Unknown broker ID";
    public static final String UNKNOWN_SHAREHOLDER_ID = "Unknown shareholder ID";
    public static final String BUYER_HAS_NOT_ENOUGH_CREDIT = "Buyer has not enough credit";
    public static final String QUANTITY_NOT_MULTIPLE_OF_LOT_SIZE = "Quantity is not a multiple of security lot size";
    public static final String PRICE_NOT_MULTIPLE_OF_TICK_SIZE = "Price is not a multiple of security tick size";
    public static final String SELLER_HAS_NOT_ENOUGH_POSITIONS = "Seller has not enough positions";
    public static final String CANNOT_CHANGE_MIN_EXE_QUANTITY = "Cannot change minimum execution quantity";
    public static final String NOT_ENOUGH_TRADED_QUANTITY = "Not enough traded quantity";
    public static final String ORDER_STOP_PRICE_NOT_POSITIVE = "Order stop price is not positive";
    public static final String CANNOT_CHANGE_STOP_PRICE = "Cannot change stop price";
    public static final String CANNOT_HAVE_BOTH_PEAK_SIZE_AND_MIN_EXE_QUANTITY = "Cannot have both a peak size " +
            "and a minimum execution Quantity";
    public static final String CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY = "Cannot have both a stop price " +
            "and a minimum execution Quantity";
    public static final String CANNOT_BE_ICEBERG_AND_STOP_LIMIT = "Cannot be both an iceberg and a stop limit order";
    public static final String STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS = "Stop limit orders cannot interact with auctions";
    public static final String ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY = "Orders in auction cannot have " +
            "a minimum execution quantity";


}
