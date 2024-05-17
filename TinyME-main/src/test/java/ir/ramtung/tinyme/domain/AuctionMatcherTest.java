package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class AuctionMatcherTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private AuctionMatcher auctionMatcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        security.setMatchingState(MatchingState.AUCTION);
        auctionMatcher = new AuctionMatcher();
    }

    @Test
    void open_auction_all_sell_queue_matches() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 65, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15600, 65, orders.get(0), orders.get(2));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(239);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_all_buy_queue_matches() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 300, 15700, broker, shareholder, 0),
                new Order(2, security, Side.SELL, 350, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 100, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15500, 300, orders.get(0), orders.get(1));
        security.setOpeningPrice(15500);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue()).hasSize(2);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(50);
    }

    @Test
    void open_auction_no_one_matches() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 350, 18500, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 100, 18600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue()).hasSize(2);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(304);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(350);
    }

    @Test
    void open_auction_partial_match() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 350, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 300, 15600, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 100, 18600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15600, 300, orders.get(0), orders.get(2));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(50);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(100);
    }

    @Test
    void open_auction_no_orders() {
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_single_order() {
        orders = List.of(
                new Order(1, security, BUY, 100, 15700, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(100);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_partial_match_with_multiple_orders() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 100, 15600, broker, shareholder, 0),
                new Order(3, security, BUY, 50, 15500, broker, shareholder, 0),
                new Order(4, security, BUY, 60, 15400, broker, shareholder, 0),
                new Order(5, security, Side.SELL, 300, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade1 = new Trade(security, 15600, 200, orders.get(0), orders.get(4));
        Trade trade2 = new Trade(security, 15600, 100, orders.get(1), orders.get(4).snapshotWithQuantity(100));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(50);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_mixed_buy_sell_queue() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 100, 15650, broker, shareholder, 0),
                new Order(3, security, BUY, 50, 15300, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 150, 15200, broker, shareholder, 0),
                new Order(5, security, Side.SELL, 200, 15500, broker, shareholder, 0),
                new Order(6, security, Side.SELL, 100, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade1 = new Trade(security, 15600, 150, orders.get(0), orders.get(3));
        Trade trade2 = new Trade(security, 15600, 50,
                orders.get(0).snapshotWithQuantity(50), orders.get(4));
        Trade trade3 = new Trade(security, 15600, 100,
                orders.get(1), orders.get(4).snapshotWithQuantity(150));
        security.setOpeningPrice(15600); //ToDo check if it works
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade1, trade2, trade3);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(50);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(50);
    }

    @Test
    void iceberg_buy_order_in_queue_matched_completely_after_three_rounds() {
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, BUY, 450, 15450, broker, shareholder, 200, 0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder, 0),
                new Order(3, security, BUY, 1000, 15400, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 600, 15450, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 200, orders.get(0), orders.get(3));
        Trade trade2 = new Trade(security, 15450, 70,
                orders.get(1), orders.get(3).snapshotWithQuantity(400));
        Trade trade3 = new Trade(security, 15450, 200,
                orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(330));
        Trade trade4 = new Trade(security, 15450, 50,
                orders.get(0).snapshotWithQuantity(50), orders.get(3).snapshotWithQuantity(130));


        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).containsExactly(trade1, trade2, trade3, trade4);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(1000);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(80);
    }

    @Test
    void iceberg_buy_order_match_until_quantity_is_less_than_peak_size() {
        orderBook = security.getOrderBook();
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.SELL, 150, 15450, broker, shareholder, 0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder, 0),
                new IcebergOrder(3, security, BUY, 120 , 15450, broker, shareholder, 50, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 70, orders.get(1), orders.get(0));
        Trade trade2 = new Trade(security, 15450, 50,
                orders.get(2), orders.get(0).snapshotWithQuantity(80));
        Trade trade3 = new Trade(security, 15450, 30,
                orders.get(2).snapshotWithQuantity(70), orders.get(0).snapshotWithQuantity(30));

        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).containsExactly(trade1, trade2, trade3);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(40);
    }
    @Test
    void iceberg_sell_order_match_until_quantity_is_less_than_peak_size() {
        orderBook = security.getOrderBook();
        List<Order> orders = Arrays.asList(
                new Order(1, security, BUY, 180, 15450, broker, shareholder, 0),
                new IcebergOrder(2, security, Side.SELL, 150 , 15450, broker, shareholder, 70, 0),
                new Order(3, security, Side.SELL, 50, 15450, broker, shareholder, 0)
                );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 70, orders.get(1), orders.get(0));
        Trade trade2 = new Trade(security, 15450, 50,
                orders.get(2), orders.get(0).snapshotWithQuantity(110));
        Trade trade3 = new Trade(security, 15450, 60,
                orders.get(1).snapshotWithQuantity(80), orders.get(0).snapshotWithQuantity(60));

        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).containsExactly(trade1, trade2, trade3);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue()).hasSize(1);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(20);
    }
    @Test
    void iceberg_sell_order_in_queue_matched_completely_after_three_rounds() {
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.SELL, 450, 15450, broker, shareholder, 200, 0),
                new Order(2, security, Side.SELL, 70, 15450, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 1000, 15500, broker, shareholder, 0),
                new Order(4, security, BUY, 600, 15450, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 200, orders.get(0), orders.get(3));
        Trade trade2 = new Trade(security, 15450, 70,
                orders.get(1), orders.get(3).snapshotWithQuantity(400));
        Trade trade3 = new Trade(security, 15450, 200,
                orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(330));
        Trade trade4 = new Trade(security, 15450, 50,
                orders.get(0).snapshotWithQuantity(50), orders.get(3).snapshotWithQuantity(130));


        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).containsExactly(trade1, trade2, trade3, trade4);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(80);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(1000);
    }
    @Test
    void iceberg_sell_and_buy_order_with_remaining_sell_quantity_less_than_peak_size() {
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.SELL, 600, 15450, broker, shareholder, 400, 0),
                new Order(2, security, Side.SELL, 100, 15450, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 1000, 15500, broker, shareholder, 0),
                new IcebergOrder(4, security, BUY, 400, 15450, broker, shareholder, 300, 0),
                new Order(5, security, BUY, 100, 15450, broker, shareholder, 0),
                new Order(6, security, BUY, 120 , 15400, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 300, orders.get(0), orders.get(3));
        Trade trade2 = new Trade(security, 15450, 100, orders.get(0).snapshotWithQuantity(300), orders.get(4));
        Trade trade3 = new Trade(security, 15450, 100,
                orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(100));

        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).containsExactly(trade1, trade2, trade3);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(3);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(120);
        assertThat(security.getOrderBook().getSellQueue().get(0).getQuantity()).isEqualTo(100);
    }
    @Test
    void open_auction_complete_match_two_orders() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 300, 15500, broker, shareholder, 0),
                new Order(2, security, Side.SELL, 300, 15500, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15500, 300, orders.get(0), orders.get(1));
        security.setOpeningPrice(15500);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(0);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(0);
    }
}
