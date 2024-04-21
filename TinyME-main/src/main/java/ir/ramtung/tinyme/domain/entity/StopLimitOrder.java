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
    private boolean hasJustBeenActivated;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = false;
    }
    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, LocalDateTime.now(), OrderStatus.NEW, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = false;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice, boolean isTriggered) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = isTriggered;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price,
                          Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder,
                entryTime, OrderStatus.NEW, 0);
        this.stopPrice = stopPrice;
        this.hasBeenTriggered = false;
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
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        stopPrice = updateOrderRq.getStopPrice();
}

    public boolean checkStopPriceReached(int last_traded_price){
        if (this.getSide() == Side.BUY && last_traded_price >= stopPrice){
            hasJustBeenActivated = true;
            hasBeenTriggered = true;
            return true;
        }
        else if (this.getSide() == Side.SELL && last_traded_price <= stopPrice){
            hasJustBeenActivated = true;
            hasBeenTriggered = true;
            return true;
        }
        return false;
    }
    public void deactivate() {
        hasJustBeenActivated = false;
        hasBeenTriggered = false;
    }

    public void deactivateJustBeenActivatedBool(){ hasJustBeenActivated = false; }
    @Override
    public boolean canTrade(){ return hasBeenTriggered; }
    public boolean hasBeenTriggered(){ return hasBeenTriggered; }
    public boolean hasJustBeenActivated(){ return hasJustBeenActivated; }

    public void activate() { hasBeenTriggered = true; }

    }
