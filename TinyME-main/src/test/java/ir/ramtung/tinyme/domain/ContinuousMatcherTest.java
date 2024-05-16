package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class ContinuousMatcherTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private StopLimitOrderbook stopLimitOrderBook;
    private List<Order> orders;
    @Autowired
    private ContinuousMatcher continuousMatcher;
    private LocalDateTime mockedNow;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        stopLimitOrderBook = security.getStopLimitOrderBook();
        mockedNow = LocalDateTime.of(2024, 4, 27, 16, 30);
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder,mockedNow, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder,mockedNow,0),
                new Order(3, security, BUY, 445, 15450, broker, shareholder,mockedNow, 0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder,mockedNow, 0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder, mockedNow,0),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder,mockedNow, 0),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder,mockedNow, 0),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder, mockedNow,0),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder,mockedNow, 0),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder,mockedNow, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void new_sell_order_matches_completely_with_part_of_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 100, 15600, broker, shareholder, 0);
        Trade trade = new Trade(security, 15700, 100, orders.get(0), order);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(204);
    }

    @Test
    void new_sell_order_matches_partially_with_the_first_buy() {
        Order order = new Order(11, security, Side.SELL, 500, 15600, broker, shareholder, 0);
        Trade trade = new Trade(security, 15700, 304, orders.get(0), order);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(196);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(2);
    }

    @Test
    void new_sell_order_matches_partially_with_two_buys() {
        Order order = new Order(11, security, Side.SELL, 500, 15500, broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15700, 304, orders.get(0), order);
        Trade trade2 = new Trade(security, 15500, 43, orders.get(1), order.snapshotWithQuantity(196));
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(153);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(3);
    }

    @Test
    void new_buy_order_does_not_match() {
        Order order = new Order(11, security, BUY, 2000, 15500, broker, shareholder, 0);
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder()).isEqualTo(order);
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void iceberg_order_in_queue_matched_completely_after_three_rounds() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, BUY, 450, 15450, broker, shareholder, 200, 0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder, 0),
                new Order(3, security, BUY, 1000, 15400, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Order order = new Order(4, security, Side.SELL, 600, 15450, broker, shareholder, 0);
        List<Trade> trades = List.of(
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(600)),
                new Trade(security, 15450, 70, orders.get(1).snapshotWithQuantity(70), order.snapshotWithQuantity(400)),
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), order.snapshotWithQuantity(330)),
                new Trade(security, 15450, 50, orders.get(0).snapshotWithQuantity(50), order.snapshotWithQuantity(130))
        );

        MatchResult result = continuousMatcher.match(order);

        assertThat(result.remainder().getQuantity()).isEqualTo(80);
        assertThat(result.trades()).isEqualTo(trades);
    }

    @Test
    void insert_iceberg_and_match_until_quantity_is_less_than_peak_size() {
        security = Security.builder().isin("TEST").build();
        shareholder.incPosition(security, 1_000);
        security.getOrderBook().enqueue(
                new Order(1, security, Side.SELL, 100, 10, broker, shareholder, 0)
        );

        Order order = new IcebergOrder(1, security, BUY, 120 , 10, broker, shareholder, 40, 0);
        MatchResult result = continuousMatcher.execute(order, false);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.trades()).hasSize(1);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(0);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(20);

    }
    @Test
    void new_buy_order_with_min_exe_quantity_not_enough_traded_quantity_rejected() {
        Order order = new Order(11, security, Side.BUY, 2000, 15800,
                broker, shareholder, 500);
        int initial_buy_queue =  security.getOrderBook().getBuyQueue().size();
        MatchResult result = continuousMatcher.execute(order, false);
        int new_buy_queue = security.getOrderBook().getBuyQueue().size();

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY);
        assertThat(new_buy_queue).isEqualTo(initial_buy_queue);
        assertThat(result.trades()).isEmpty();
    }
    @Test
    void new_sell_order_with_min_exe_quantity_not_enough_traded_quantity_rejected() {
        Order order = new Order(11, security, Side.SELL, 2000, 15700,
                broker, shareholder, 500);
        int initial_sell_queue =  security.getOrderBook().getSellQueue().size();
        MatchResult result = continuousMatcher.execute(order, false);
        int new_sell_queue = security.getOrderBook().getSellQueue().size();

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY);
        assertThat(new_sell_queue).isEqualTo(initial_sell_queue);
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void new_buy_order_with_min_exe_quantity_no_trade_rejected() {
        Order order = new Order(11, security, Side.BUY, 2000, 15500,
                broker, shareholder, 500);
        int initial_buy_queue =  security.getOrderBook().getBuyQueue().size();
        MatchResult result = continuousMatcher.execute(order, false);
        int new_buy_queue = security.getOrderBook().getBuyQueue().size();

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY);
        assertThat(new_buy_queue).isEqualTo(initial_buy_queue);
        assertThat(result.trades()).isEmpty();
    }
    @Test
    void new_sell_order_with_min_exe_quantity_no_trade_quantity_rejected() {
        Order order = new Order(11, security, Side.SELL, 2000, 15900,
                broker, shareholder, 500);
        int initial_sell_queue =  security.getOrderBook().getSellQueue().size();
        MatchResult result = continuousMatcher.execute(order, false);
        int new_sell_queue = security.getOrderBook().getSellQueue().size();

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_TRADED_QUANTITY);
        assertThat(new_sell_queue).isEqualTo(initial_sell_queue);
        assertThat(result.trades()).isEmpty();
    }

    @Test
    void new_buy_order_with_min_exe_quantity_enough_traded_quantity() {
        Order order = new Order(11, security, Side.BUY, 2000, 15800,
                broker, shareholder, 100);
        Trade trade1 = new Trade(security, 15800, 350, orders.get(5), order);
        int initial_buy_queue =  security.getOrderBook().getBuyQueue().size();
        MatchResult result = continuousMatcher.execute(order, false);
        int new_buy_queue = security.getOrderBook().getBuyQueue().size();

        assertThat(new_buy_queue).isEqualTo(initial_buy_queue + 1);
        assertThat(result.remainder().getQuantity()).isEqualTo(1650);
        assertThat(result.trades()).containsExactly(trade1);
    }
    @Test
    void new_stop_limit_order_has_not_been_activated() {
        StopLimitOrder order = new StopLimitOrder(11, security, Side.SELL, 2000, 15500,
                broker, shareholder, 100,0);
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.remainder()).isEqualTo(order);
        assertThat(result.trades()).isEmpty();
    }
    @Test
    void new_stop_limit_order_has_been_activated() {
        StopLimitOrder order = new StopLimitOrder(11, security, Side.SELL, 500, 15500,
                broker, shareholder,mockedNow, 55100,0);
        Trade trade1 = new Trade(security, 15700, 304, orders.get(0), order.convertToOrder());
        Trade trade2 = new Trade(security, 15500, 43, orders.get(1),
                order.convertToOrder().snapshotWithQuantity(196));
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.remainder().getQuantity()).isEqualTo(153);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(3);
    }
    @Test
    void new_sell_order_buy_stop_limit_order_should_not_trade_but_others_should() {
        StopLimitOrder stopLimitOrder1 = new StopLimitOrder(11, security, Side.BUY, 2000, 15750,
                broker, shareholder, 51000,0);
        StopLimitOrder stopLimitOrder2 = new StopLimitOrder(12, security, Side.BUY, 2000, 15750,
                broker, shareholder, 51000,0);
        stopLimitOrderBook.enqueue(stopLimitOrder1);
        stopLimitOrderBook.enqueue(stopLimitOrder2);

        Order order = new Order(12, security, Side.SELL, 500, 15500, broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15700, 304, orders.get(0), order);
        Trade trade2 = new Trade(security, 15500, 43, orders.get(1), order.snapshotWithQuantity(196));
        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(153);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getStopLimitOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(11);
    }
    @Test
    void new_sell_order_buy_activated_stop_limit_order_should_trade() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(11, security, Side.BUY, 100, 15750,
                broker, shareholder,mockedNow, 100,0);
        Order order = new Order(12, security, Side.SELL, 500, 15500, broker, shareholder,mockedNow, 0);
        Trade trade1 = new Trade(security, 15750, 100, stopLimitOrder.convertToOrder(), order);
        continuousMatcher.execute(stopLimitOrder, false);
        Trade trade2 = new Trade(security, 15700, 304, orders.get(0), order.snapshotWithQuantity(400));
        Trade trade3 = new Trade(security, 15500, 43, orders.get(1), order.snapshotWithQuantity(96));
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.remainder().getQuantity()).isEqualTo(53);
        assertThat(result.trades()).containsExactly(trade1, trade2, trade3);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getOrderId()).isEqualTo(3);
    }
    @Test
    void new_buy_order_does_not_match_same_last_traded_price() {
        Order order = new Order(11, security, BUY, 2000, 15500, broker, shareholder, 0);
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.getLastTradedPrice()).isEqualTo(security.getLastTradedPrice());
    }
    @Test
    void new_buy_order_rollbacks_last_trade_price() {
        Order order = new Order(11, security, Side.BUY, 2000, 15500,
                broker, shareholder, 500);
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.getLastTradedPrice()).isEqualTo(security.getLastTradedPrice());
    }
    @Test
    void new_buy_order_matches_partially_with_the_entire_sell_queue_only_last_traded_price() {
        Order order = new Order(11, security, BUY, 2000, 15850, broker, shareholder, 0);
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.getLastTradedPrice()).isEqualTo(15820);
    }
    @Test
    void new_sell_order_matches_partially_with_the_entire_buy_queue_only_last_traded_price() {
        Order order = new Order(11, security, SELL, 3000, 15000, broker, shareholder, 0);
        MatchResult result = continuousMatcher.execute(order, false);
        assertThat(result.getLastTradedPrice()).isEqualTo(15400);
    }

    @Test
    void new_buy_order_matches_partially_with_the_entire_sell_queue_except_stop_limit_order() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(12, security, SELL, 2000, 15810, broker, shareholder, 100,0);
        stopLimitOrderBook.enqueue(stopLimitOrder);

        Order order = new Order(11, security, BUY, 2000, 15820, broker, shareholder, 0);
        List<Trade> trades = new ArrayList<>();
        int totalTraded = 0;
        for (Order o : orders.subList(5, 10)) {
            trades.add(new Trade(security, o.getPrice(), o.getQuantity(),
                    order.snapshotWithQuantity(order.getQuantity() - totalTraded), o));
            totalTraded += o.getQuantity();
        }

        MatchResult result = continuousMatcher.match(order);
        assertThat(result.remainder().getQuantity()).isEqualTo(160);
        assertThat(result.trades()).isEqualTo(trades);
        assertThat(security.getStopLimitOrderBook().getSellQueue().getFirst().getOrderId()).isEqualTo(12);
    }
}
