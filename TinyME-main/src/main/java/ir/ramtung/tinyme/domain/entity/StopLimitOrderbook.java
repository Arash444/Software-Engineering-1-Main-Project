package ir.ramtung.tinyme.domain.entity;

import lombok.Getter;

import java.util.List;
import java.util.ListIterator;

@Getter
public class StopLimitOrderbook extends OrderBook {
    private int maxSellStopPrice;
    private int minBuyStopPrice;
    public StopLimitOrderbook() {
        super();
        maxSellStopPrice = 0;
        minBuyStopPrice = Integer.MAX_VALUE;
    }
    @Override
    public void enqueue(Order order) {
        super.enqueue(order);
        updateMinMaxStopPrice(order.getSide());
    }
    @Override
    public void removeFirst(Side side){
        super.removeFirst(side);
        updateMinMaxStopPrice(side);
    }
    @Override
    public void removeByOrderId(Side side, long orderId){
        super.removeByOrderId(side, orderId);
        updateMinMaxStopPrice(side);
    }
    public StopLimitOrder findFirstActivatedOrder(int lastTradedPrice)
    {
        StopLimitOrder firstOrder;
        if (!getSellQueue().isEmpty() && maxSellStopPriceIsHigherThan(lastTradedPrice) )
            firstOrder = (StopLimitOrder) getSellQueue().getFirst();
        else if (!getBuyQueue().isEmpty() && minBuyStopPriceIsLowerThan(lastTradedPrice))
            firstOrder = (StopLimitOrder) getBuyQueue().getFirst();
        else
            return null;
        if (firstOrder.hasReachedStopPrice(lastTradedPrice))
            return firstOrder;
        else
            return null;
    }
    private void updateMinMaxStopPrice(Side side)  {
        if(side == Side.BUY)
            updateMinBuyStopPrice();
        else
            updateMaxSellStopPrice();
    }
    private void updateMaxSellStopPrice() {
        if(!getSellQueue().isEmpty()) {
            StopLimitOrder stopLimitOrder = (StopLimitOrder) getSellQueue().getFirst();
            maxSellStopPrice = stopLimitOrder.getStopPrice();
        }
        else
            maxSellStopPrice = 0;
    }
    private void updateMinBuyStopPrice() {
        if(!getBuyQueue().isEmpty()) {
            StopLimitOrder stopLimitOrder = (StopLimitOrder) getBuyQueue().getFirst();
            minBuyStopPrice = stopLimitOrder.getStopPrice();
        }
        else
            minBuyStopPrice = Integer.MAX_VALUE;
    }
    public void updateLastTradedPrice(int LastTradedPrice) {};
    private boolean maxSellStopPriceIsHigherThan(int lastTradedPrice) {return maxSellStopPrice >= lastTradedPrice;}
    private boolean minBuyStopPriceIsLowerThan(int lastTradedPrice) {return minBuyStopPrice <= lastTradedPrice;}
}
