package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public abstract class Matcher {

    public abstract MatchResult execute(Order order);
    public abstract MatchResult match(Security security, Order order);
    protected abstract void matchTheTwoOrders(int price, OrderBook orderBook, LinkedList<Trade> trades, Order order1, Order order2, int tradeQuantity);
    protected void addNewTrade(int price, LinkedList<Trade> trades, Order newOrder, Order matchingOrder, int tradeQuantity) {
        Trade trade = new Trade(newOrder.getSecurity(), price, tradeQuantity, newOrder, matchingOrder);
        trades.add(trade);
    }
    protected static boolean shareholderDoesNotHaveEnoughPosition(Order order) {
        return order.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(order.getSecurity(),
                        order.getSecurity().getOrderBook().totalSellQuantityByShareholder(order.getShareholder())
                                + order.getQuantity());
    }

    protected void decreaseOrderQuantity(Order order1, Order order2) {
        int minQuantity = Math.min(order1.getQuantity(), order2.getQuantity());
        order1.decreaseQuantity(minQuantity);
        order2.decreaseQuantity(minQuantity);
    }
    protected void removeZeroQuantityOrder(OrderBook orderBook, Order order) {
        if(order.getQuantity() == 0)
            orderBook.removeFirst(order.getSide());
    }
    protected void removeZeroQuantityOrder(OrderBook orderBook, Order order1, Order order2) {
        removeZeroQuantityOrder(orderBook, order1);
        removeZeroQuantityOrder(orderBook, order2);
    }
    protected void adjustShareholderPositions(LinkedList<Trade> trades) {
        if (!trades.isEmpty()) {
            for (Trade trade : trades) {
                trade.getBuy().getShareholder().incPosition(trade.getSecurity(), trade.getQuantity());
                trade.getSell().getShareholder().decPosition(trade.getSecurity(), trade.getQuantity());
            }
        }
    }
    protected boolean brokerDoesNotHaveEnoughCredit(Order order) {
        return order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue());
    }
    protected void decreaseBuyBrokerCredit(Order order) {
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
    }
    protected void adjustBrokerCredit(Order order, Trade trade) {
        trade.increaseSellersCredit();
        if (order.getSide() == Side.BUY)
            trade.decreaseBuyersCredit();
    }
    protected void adjustBrokerCredit(Order order, Trade trade, long creditChange) {
        trade.increaseSellersCredit();
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(creditChange);
    }
    protected void replenishIcebergOrder(OrderBook orderBook, Order order) {
        if (order instanceof IcebergOrder icebergOrder) {
            icebergOrder.replenish();
            if (icebergOrder.getQuantity() > 0 &&
                    orderBook.findByOrderId(icebergOrder.getSide(), icebergOrder.getOrderId()) == null)
                orderBook.enqueue(icebergOrder);
        }
    }
    protected void replenishIcebergOrder(OrderBook orderBook, Order order1, Order order2) {
        replenishIcebergOrder(orderBook, order1);
        replenishIcebergOrder(orderBook, order2);
    }

}
