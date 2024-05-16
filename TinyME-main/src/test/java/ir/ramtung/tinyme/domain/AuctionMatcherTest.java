package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import org.junit.jupiter.api.BeforeEach;
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
        assertThat(result.getTradableQuantity()).isEqualTo(65);
        assertThat(result.getLastTradedPrice()).isEqualTo(15600);
        assertThat(result.getOpeningPrice()).isEqualTo(15600);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(239);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isEqualTo(true);
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
        assertThat(result.getTradableQuantity()).isEqualTo(300);
        assertThat(result.getLastTradedPrice()).isEqualTo(15500);
        assertThat(result.getOpeningPrice()).isEqualTo(15500);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
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
        assertThat(result.getTradableQuantity()).isEqualTo(0);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(-1);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(304);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(350);
    }

    @Test
    void open_auction_partial_match() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 300, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 350, 15600, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 100, 18600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15600, 300, orders.get(0), orders.get(2));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(result.getTradableQuantity()).isEqualTo(300);
        assertThat(result.getLastTradedPrice()).isEqualTo(15600);
        assertThat(result.getOpeningPrice()).isEqualTo(15600);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(43);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(50);
    }

    @Test
    void open_auction_no_orders() {
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades().isEmpty()).isTrue();
        assertThat(result.getTradableQuantity()).isEqualTo(0);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(-1);
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
        assertThat(result.getTradableQuantity()).isEqualTo(0);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(-1);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(100);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_partial_match_with_multiple_orders() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 100, 15600, broker, shareholder, 0),
                new Order(3, security, BUY, 50, 15500, broker, shareholder, 0),
                new Order(4, security, BUY, 50, 15400, broker, shareholder, 0),
                new Order(5, security, Side.SELL, 300, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade1 = new Trade(security, 15600, 200, orders.get(0), orders.get(4));
        Trade trade2 = new Trade(security, 15600, 100, orders.get(1), orders.get(4));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(result.getTradableQuantity()).isEqualTo(300);
        assertThat(result.getLastTradedPrice()).isEqualTo(15600);
        assertThat(result.getOpeningPrice()).isEqualTo(15600);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_single_order_with_zero_quantity() {
        orders = List.of(
                new Order(1, security, BUY, 0, 15700, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades().isEmpty()).isTrue();
        assertThat(result.getTradableQuantity()).isEqualTo(0);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(-1);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isTrue();
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void open_auction_mixed_buy_sell_queue() {
        orders = List.of(
                new Order(1, security, BUY, 300, 15700, broker, shareholder, 0),
                new Order(2, security, Side.SELL, 200, 15600, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 100, 15500, broker, shareholder, 0),
                new Order(4, security, BUY, 100, 15400, broker, shareholder, 0),
                new Order(5, security, BUY, 50, 15300, broker, shareholder, 0),
                new Order(6, security, Side.SELL, 150, 15200, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade1 = new Trade(security, 15600, 200, orders.get(0), orders.get(1));
        Trade trade2 = new Trade(security, 15600, 100, orders.get(0), orders.get(2));
        security.setOpeningPrice(15600);
        MatchResult result = auctionMatcher.match(security);
        assertThat(result.trades()).containsExactly(trade1, trade2);
        assertThat(result.getTradableQuantity()).isEqualTo(300);
        assertThat(result.getLastTradedPrice()).isEqualTo(15600);
        assertThat(result.getOpeningPrice()).isEqualTo(15600);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(150);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isTrue();
    }

    @Test
    void iceberg_order_in_queue_matched_completely_after_three_rounds() {
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new IcebergOrder(1, security, BUY, 450, 15450, broker, shareholder, 200, 0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder, 0),
                new Order(3, security, BUY, 1000, 15400, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 600, 15450, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        List<Trade> trades = List.of(
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(600)),
                new Trade(security, 15450, 70, orders.get(1).snapshotWithQuantity(70), orders.get(3).snapshotWithQuantity(400)),
                new Trade(security, 15450, 200, orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(330)),
                new Trade(security, 15450, 50, orders.get(0).snapshotWithQuantity(50), orders.get(3).snapshotWithQuantity(130))
        );

        AuctionMatcher auctionMatcher = new AuctionMatcher();
        MatchResult result = auctionMatcher.match(security);

        assertThat(result.trades()).isEqualTo(trades);
    }

    @Test
    void insert_iceberg_and_match_until_quantity_is_less_than_peak_size() {
        orderBook = security.getOrderBook();
        List<Order> orders = Arrays.asList(
                new Order(1, security, Side.SELL, 150, 10, broker, shareholder, 0),
                new Order(2, security, BUY, 70, 15450, broker, shareholder, 0),
                new IcebergOrder(3, security, BUY, 120 , 10, broker, shareholder, 40, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        AuctionMatcher auctionMatcher = new AuctionMatcher();
        MatchResult result = auctionMatcher.match(security);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.trades()).hasSize(1);
        assertThat(security.getOrderBook().getSellQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue()).hasSize(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(40);
    }

}
