package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public abstract class Matcher {

    public abstract MatchResult execute(Order order, Boolean isAmendOrder);
    public MatchResult matchAllOrders(Security security) {
        return null;
    }
    protected abstract void adjustOrderQuantityRemoveSmallerOrder(OrderBook orderBook, Order newOrder, Order matchingOrder);
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
    protected boolean brokerDoesNotHaveEnoughCredit(Order order) {
        return order.getSide() == Side.BUY && !order.getBroker().hasEnoughCredit(order.getValue());
    }

    protected void removeZeroQuantityOrder(OrderBook orderBook, Order order) {
        orderBook.removeFirst(order.getSide());
        order.makeQuantityZero();
    }

}
