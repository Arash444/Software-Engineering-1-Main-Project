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
    private long stopLimitRequestID;

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int stopPrice, long stopLimitRequestID) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, 0);
        this.stopPrice = stopPrice;
        this.stopLimitRequestID = stopLimitRequestID;
    }
    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker,
                          Shareholder shareholder, int stopPrice, long stopLimitRequestID) {
        super(orderId, security, side, quantity, price, broker, shareholder,
                LocalDateTime.now(), OrderStatus.NEW, 0);
        this.stopPrice = stopPrice;
        this.stopLimitRequestID = stopLimitRequestID;
    }

    public StopLimitOrder(long orderId, Security security, Side side, int quantity, int price,
                          Broker broker, Shareholder shareholder, LocalDateTime entryTime, int stopPrice, long stopLimitRequestID) {
        super(orderId, security, side, quantity, price, broker, shareholder,
                entryTime, OrderStatus.NEW, 0);
        this.stopPrice = stopPrice;
        this.stopLimitRequestID = stopLimitRequestID;
    }

    public Order convertToOrder()
    {
        return new Order(orderId, security, side, quantity, price, broker, shareholder,
                LocalDateTime.now(), OrderStatus.NEW, 0);
    }

    @Override
    public Order snapshot() {
        return new StopLimitOrder(orderId, security, side, quantity, price, broker, shareholder, entryTime,
                OrderStatus.SNAPSHOT, stopPrice, stopLimitRequestID);
    }

    @Override
    public Order snapshotWithQuantity(int newQuantity) {
        return new StopLimitOrder(orderId, security, side, newQuantity, price, broker, shareholder, entryTime,
                OrderStatus.SNAPSHOT, stopPrice, stopLimitRequestID);
    }

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        stopPrice = updateOrderRq.getStopPrice();
        stopLimitRequestID =  updateOrderRq.getOrderId();

    }

    public boolean hasReachedStopPrice(int last_traded_price){
        if (this.getSide() == Side.BUY && last_traded_price >= stopPrice)
            return true;
        else if (this.getSide() == Side.SELL && last_traded_price <= stopPrice)
            return true;
        return false;
    }
    @Override
    public boolean queuesBefore(Order order) {
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
        if (stopLimitOrder.getSide() == Side.BUY) {
            return stopPrice < stopLimitOrder.getStopPrice();
        } else {
            return stopPrice > stopLimitOrder.getStopPrice();
        }
    }
    @Override
    public boolean canTrade(){ return false; }
}
