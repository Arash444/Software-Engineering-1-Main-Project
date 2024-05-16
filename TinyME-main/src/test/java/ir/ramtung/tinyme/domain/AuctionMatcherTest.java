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
}
