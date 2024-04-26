package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;

public class StopLimitOrderbook extends OrderBook {
    public StopLimitOrderbook() {
    }

    @Override
    public Order matchWithFirst(Side side) {
        var queue = getQueue(newOrder.getSide().opposite());
        if (newOrder.matches(queue.getFirst()))
            return queue.getFirst();
        else
            return null;
    }
}
