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
    }

    @Test
    void openAuctionAllSellQueueMatches(){
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 65, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15600, 65, orders.get(0), orders.get(2));
        security.setLatestMatchingPrice(15600);
        MatchResult result = auctionMatcher.matchAllOrders(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(result.getTradableQuantity()).isEqualTo(65);
        assertThat(security.getOrderBook().getBuyQueue().getFirst().getQuantity()).isEqualTo(239);
        assertThat(security.getOrderBook().getSellQueue().isEmpty()).isEqualTo(true);
    }

    @Test
    void openAuctionAllBuyQueueMatches(){
        orders = Arrays.asList(
                new Order(1, security, BUY, 300, 15700, broker, shareholder, 0),
                new Order(2, security, Side.SELL, 350, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 100, 15600, broker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        Trade trade = new Trade(security, 15500, 300, orders.get(0), orders.get(1));
        security.setLatestMatchingPrice(15500);
        MatchResult result = auctionMatcher.matchAllOrders(security);
        assertThat(result.trades()).containsExactly(trade);
        assertThat(result.getTradableQuantity()).isEqualTo(300);
        assertThat(security.getOrderBook().getBuyQueue().isEmpty()).isEqualTo(true);
        assertThat(security.getOrderBook().getSellQueue().getFirst().getQuantity()).isEqualTo(50);
    }

}
