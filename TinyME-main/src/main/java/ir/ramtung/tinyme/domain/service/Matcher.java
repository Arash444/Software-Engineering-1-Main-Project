package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public abstract class Matcher {

    public abstract MatchResult execute(Order order, Boolean isAmendOrder);
    public abstract MatchResult match(Security security, Order order);
    protected abstract void removeSmallerOrder(OrderBook orderBook, Order order1, Order order2);
    protected void replenishIcebergOrder(OrderBook orderBook, Order order) {
        if (order instanceof IcebergOrder icebergOrder) {
            icebergOrder.decreaseQuantity(order.getQuantity());
            icebergOrder.replenish();
            if (icebergOrder.getQuantity() > 0)
                orderBook.enqueue(icebergOrder);
        }
    }
    protected void addNewTrade(int price, LinkedList<Trade> trades, Order newOrder, Order matchingOrder, int tradeQuantity) {
        Trade trade = new Trade(newOrder.getSecurity(), price, tradeQuantity, newOrder, matchingOrder);
        trade.increaseSellersCredit();
        trades.add(trade);
    }

    protected void decreaseOrderQuantity(Order order1, Order order2) {
        int minQuantity = Math.min(order1.getQuantity(), order2.getQuantity());
        order1.decreaseQuantity(minQuantity);
        order2.decreaseQuantity(minQuantity);
    }
    protected void removeZeroQuantityOrder(OrderBook orderBook, Order order) {
        orderBook.removeFirst(order.getSide());
        replenishIcebergOrder(orderBook, order);
    }
    protected boolean brokerDoesNotHaveEnoughCredit(Order order) {
        return order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue());
    }
    protected void decreaseBuyBrokerCredit(Order order) {
        if (order.getSide() == Side.BUY)
            order.getBroker().decreaseCreditBy(order.getValue());
    }
    protected void decreaseBuyBrokerCredit(Order order, Trade trade) {
        if (order.getSide() == Side.BUY)
            trade.decreaseBuyersCredit();
    }
    protected void increaseBuyBrokerCredit(Order order, long creditChange) {
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(creditChange);
    }

}
