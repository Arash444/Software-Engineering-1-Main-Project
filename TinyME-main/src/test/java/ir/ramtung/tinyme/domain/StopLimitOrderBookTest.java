package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitOrderBookTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private LocalDateTime mockedNow;
    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        mockedNow = LocalDateTime.of(2024, 4, 27, 16, 30);
        List<StopLimitOrder> stopLimitOrders = Arrays.asList(
                new StopLimitOrder(1, security, BUY, 304, 15700,
                        broker, shareholder, mockedNow, OrderStatus.NEW, 15600,0),
                new StopLimitOrder(2, security, BUY, 43, 15500,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  15400,0),
                new StopLimitOrder(3, security, BUY, 445, 15450,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  15555,0),
                new StopLimitOrder(4, security, BUY, 526, 15450,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  16000,0),
                new StopLimitOrder(5, security, BUY, 1000, 15400,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  14000,0),
                new StopLimitOrder(6, security, SELL, 350, 15800,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  5600,0),
                new StopLimitOrder(7, security, SELL, 285, 15810,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  5800,0),
                new StopLimitOrder(8, security, SELL, 800, 15810,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  5499,0),
                new StopLimitOrder(9, security, SELL, 340, 15820,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  4400,0),
                new StopLimitOrder(10, security, SELL, 65, 15820,
                        broker, shareholder, mockedNow, OrderStatus.NEW,  8000,0)
        );
        stopLimitOrders.forEach(order -> security.getStopLimitOrderBook().enqueue(order));
    }
    @Test
    void min_buy_stop_price_is_correct(){
        assertThat(security.getStopLimitOrderBook().getMinBuyStopPrice()).isEqualTo(14000);
    }
    @Test
    void max_sell_stop_price_is_correct(){
        assertThat(security.getStopLimitOrderBook().getMaxSellStopPrice()).isEqualTo(8000);
    }
    @Test
    void remove_lowest_buy_min_buy_stop_price_is_correct(){
        security.getStopLimitOrderBook().removeFirst(BUY);
        assertThat(security.getStopLimitOrderBook().getMinBuyStopPrice()).isEqualTo(15400);
    }
    @Test
    void remove_highest_sell_max_sell_stop_price_is_correct(){
        security.getStopLimitOrderBook().removeFirst(SELL);
        assertThat(security.getStopLimitOrderBook().getMaxSellStopPrice()).isEqualTo(5800);
    }
    @Test
    void find_first_buy_order_correctly(){
        StopLimitOrder actualFirstBuyOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(15500);
        StopLimitOrder expectedFirstBuyOrder = new StopLimitOrder(5, security, BUY, 1000, 15400,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 14000,0);
        assertThat(actualFirstBuyOrder).isEqualTo(expectedFirstBuyOrder);
    }
    @Test
    void find_first_sell_order_correctly(){
        StopLimitOrder actualFirstSellOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(5500);
        StopLimitOrder expectedFirstSellOrder = new StopLimitOrder(10, security, SELL, 65, 15820,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 8000,0);
        assertThat(actualFirstSellOrder).isEqualTo(expectedFirstSellOrder);
    }
    @Test
    void remove_lowest_buy_stop_price_find_first_buy_order_correctly() {
        security.getStopLimitOrderBook().removeFirst(BUY);
        StopLimitOrder expectedFirstBuyOrder = new StopLimitOrder(2, security, BUY, 43, 15500,
                broker, shareholder, mockedNow, OrderStatus.QUEUED, 15400,0);
        StopLimitOrder actualFirstBuyOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(15500);
        assertThat(actualFirstBuyOrder).isEqualTo(expectedFirstBuyOrder);
    }
    @Test
    void remove_highest_sell_stop_price_find_first_sell_order_correctly() {
        security.getStopLimitOrderBook().removeFirst(SELL);
        StopLimitOrder expectedFirstSellOrder = new StopLimitOrder(7, security, SELL, 285, 15810,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 5800,0);
        StopLimitOrder actualFirstSellOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(5500);
        assertThat(actualFirstSellOrder).isEqualTo(expectedFirstSellOrder);
    }
    @Test
    void remove_random_buy_find_first_buy_order_correctly(){
        security.getStopLimitOrderBook().removeByOrderId(BUY, 3);
        StopLimitOrder actualFirstBuyOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(15500);
        StopLimitOrder expectedFirstBuyOrder = new StopLimitOrder(5, security, BUY, 1000, 15400,
                broker, shareholder, mockedNow, OrderStatus.QUEUED, 14000,0);
        assertThat(actualFirstBuyOrder).isEqualTo(expectedFirstBuyOrder);
    }
    @Test
    void remove_random_sell_find_first_sell_order_correctly(){
        security.getStopLimitOrderBook().removeByOrderId(SELL, 8);
        StopLimitOrder actualFirstSellOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(5500);
        StopLimitOrder expectedFirstSellOrder = new StopLimitOrder(10, security, SELL, 65, 15820,
                broker, shareholder, mockedNow, OrderStatus.QUEUED, 8000,0);
        assertThat(actualFirstSellOrder).isEqualTo(expectedFirstSellOrder);
    }
    @Test
    void find_first_fails_due_to_last_trading_price_between_main_and_high(){
        StopLimitOrder actualFirstOrder = security.getStopLimitOrderBook().findFirstActivatedOrder(10000);
        assertThat(actualFirstOrder).isNull();
    }
    @Test
    void stop_limit_orders_enqueue_correctly(){
        List<StopLimitOrder> expectedBuyQueue = Arrays.asList(
                new StopLimitOrder(5, security, BUY, 1000, 15400,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 14000,0),
                new StopLimitOrder(2, security, BUY, 43, 15500,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 15400,0),
                new StopLimitOrder(3, security, BUY, 445, 15450,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 15555,0),
                new StopLimitOrder(1, security, BUY, 304, 15700,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 15600,0),
                new StopLimitOrder(4, security, BUY, 526, 15450,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 16000,0)
        );
        List<StopLimitOrder> expectedSellQueue = Arrays.asList(
                new StopLimitOrder(10, security, SELL, 65, 15820,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 8000,0),
                new StopLimitOrder(7, security, SELL, 285, 15810,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 5800,0),
                new StopLimitOrder(6, security, SELL, 350, 15800,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 5600,0),
                new StopLimitOrder(8, security, SELL, 800, 15810,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 5499,0),
                new StopLimitOrder(9, security, SELL, 340, 15820,
                        broker, shareholder, mockedNow, OrderStatus.QUEUED, 4400,0)
        );
        LinkedList<Order> actualBuyQueue= security.getStopLimitOrderBook().getBuyQueue();
        LinkedList<Order> actualSellQueue = security.getStopLimitOrderBook().getSellQueue();
        assertIterableEquals(expectedBuyQueue, actualBuyQueue);
        assertIterableEquals(expectedSellQueue, actualSellQueue);
    }
}
