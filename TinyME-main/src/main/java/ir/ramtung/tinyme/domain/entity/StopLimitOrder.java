package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{
    private int stopPrice;
    private boolean hasBeenTriggered;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = false;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice, boolean isTriggered) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = isTriggered;
    }

    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime,
                OrderStatus.SNAPSHOT, stopPrice, hasBeenTriggered);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new StopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime,
                OrderStatus.SNAPSHOT, stopPrice, hasBeenTriggered);
    }


    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {

}

    public void checkStopPriceReached(int last_traded_price){
        if (hasBeenTriggered)
            return;
        else if (this.getSide() == Side.BUY && last_traded_price >= stopPrice){
            hasBeenTriggered = true;
        }
        else if (this.getSide() == Side.SELL && last_traded_price <= stopPrice){
            hasBeenTriggered = true;
        }
        else {
            return;
        }
    }


}
