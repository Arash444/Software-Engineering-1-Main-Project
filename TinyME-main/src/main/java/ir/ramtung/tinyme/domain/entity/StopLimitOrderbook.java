package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.LinkedList;
@Getter
public class StopLimitOrderbook extends OrderBook {
    private int lastTradedPrice;
    public StopLimitOrderbook() {
        super();
        lastTradedPrice = 15500;
    }
    public StopLimitOrder findFirstActivatedOrder(int newLastTradedPrice)
    {
        StopLimitOrder firstOrder;
        if (isTradingPriceAscending(newLastTradedPrice))
            firstOrder = (StopLimitOrder) getBuyQueue().getFirst();
        else
            firstOrder = (StopLimitOrder) getSellQueue().getFirst();
        if (firstOrder.hasReachedStopPrice(newLastTradedPrice))
            return firstOrder;
        else
            return null;
    }
    public void updateLastTradedPrice(int newLastTradedPrice) {lastTradedPrice = newLastTradedPrice;}
    private boolean isTradingPriceAscending(int newLastTradedPrice) {return newLastTradedPrice > lastTradedPrice;}
}
