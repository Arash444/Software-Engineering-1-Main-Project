package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.AuctionMatcher;
import ir.ramtung.tinyme.domain.service.ContinuousMatcher;
import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
class SecurityTest {
    private Security security;
    private Broker broker;
    private Shareholder shareholder;
    private List<Order> orders;
    @Autowired
    ContinuousMatcher continuousMatcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        broker = Broker.builder().brokerId(0).credit(1_000_000L).build();
        shareholder = Shareholder.builder().shareholderId(0).build();
        shareholder.incPosition(security, 100_000);
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new Order(3, security, BUY, 445, 15450, broker, shareholder, 0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder, 0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder, 0),
                new Order(6, security, Side.SELL, 350, 15800, broker, shareholder, 0),
                new Order(7, security, Side.SELL, 285, 15810, broker, shareholder, 0),
                new Order(8, security, Side.SELL, 800, 15810, broker, shareholder, 0),
                new Order(9, security, Side.SELL, 340, 15820, broker, shareholder, 0),
                new Order(10, security, Side.SELL, 65, 15820, broker, shareholder, 0)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void reducing_quantity_does_not_change_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3,
                LocalDateTime.now(), BUY, 440, 15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getQuantity()).isEqualTo(440);
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void increasing_quantity_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY, 450,
                15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void changing_price_changes_priority() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1, LocalDateTime.now(), BUY, 300,
                15450, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(300);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getPrice()).isEqualTo(15450);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(1);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(2);
    }

    @Test
    void changing_price_causes_trades_to_happen() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), Side.SELL,
                350, 15700, 0, 0, 0, 0, 0);
        assertThatNoException().isThrownBy(() ->
                assertThat(security.updateOrder(updateOrderRq, continuousMatcher).trades()).isNotEmpty()
        );
    }

    @Test
    void updating_non_existing_order_fails() {
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 6, LocalDateTime.now(), BUY,
                350, 15700, 0, 0, 0, 0, 0);
        assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
    }

    @Test
    void delete_order_works() {
        DeleteOrderRq deleteOrderRq = new DeleteOrderRq(1, security.getIsin(), Side.SELL, 6);
        security.deleteOrder(deleteOrderRq);
        assertThat(security.getOrderBook().getBuyQueue()).isEqualTo(orders.subList(0, 5));
        assertThat(security.getOrderBook().getSellQueue()).isEqualTo(orders.subList(6, 10));
    }

    @Test
    void increasing_iceberg_peak_size_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().credit(1_000_000L).build();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new IcebergOrder(3, security, BUY, 445, 15450, broker, shareholder, 100, 0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder, 0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder, 0)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY,
                445, 15450, 0, 0, 150, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(3).getQuantity()).isEqualTo(150);
        assertThat(security.getOrderBook().getBuyQueue().get(3).getOrderId()).isEqualTo(3);
    }

    @Test
    void decreasing_iceberg_quantity_to_amount_larger_than_peak_size_does_not_changes_priority() {
        security = Security.builder().build();
        broker = Broker.builder().build();
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, broker, shareholder, 0),
                new IcebergOrder(3, security, BUY, 445, 15450, broker, shareholder, 100, 0),
                new Order(4, security, BUY, 526, 15450, broker, shareholder, 0),
                new Order(5, security, BUY, 1000, 15400, broker, shareholder, 0)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
        EnterOrderRq updateOrderRq = EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 3, LocalDateTime.now(), BUY,
                300, 15450, 0, 0, 100, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateOrderRq, continuousMatcher));
        assertThat(security.getOrderBook().getBuyQueue().get(2).getOrderId()).isEqualTo(3);
    }

    @Test
    void update_iceberg_that_loses_priority_with_no_trade_works() {
        security = Security.builder().isin("TEST").build();
        broker = Broker.builder().brokerId(1).credit(100).build();

        security.getOrderBook().enqueue(
                new IcebergOrder(1, security, BUY, 100, 9, broker, shareholder, 10, 0)
        );

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(), BUY,
                100, 10, 0, 0, 10, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateReq, continuousMatcher));

        assertThat(broker.getCredit()).isEqualTo(0);
        assertThat(security.getOrderBook().getBuyQueue().get(0).getOrderId()).isEqualTo(1);
    }

    @Test
    void update_iceberg_order_decrease_peak_size() {
        security = Security.builder().isin("TEST").build();
        security.getOrderBook().enqueue(
                new IcebergOrder(1, security, BUY, 20, 10, broker, shareholder, 10, 0)
        );

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(2, security.getIsin(), 1, LocalDateTime.now(), BUY,
                20, 10, 0, 0, 5, 0, 0);
        assertThatNoException().isThrownBy(() -> security.updateOrder(updateReq, continuousMatcher));

        assertThat(security.getOrderBook().getBuyQueue().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    void update_iceberg_order_price_leads_to_match_as_new_order() throws InvalidRequestException {
        security = Security.builder().isin("TEST").build();
        shareholder.incPosition(security, 1_000);
        orders = List.of(
                new Order(1, security, BUY, 15, 10, broker, shareholder, 0),
                new Order(2, security, BUY, 20, 10, broker, shareholder, 0),
                new Order(3, security, BUY, 40, 10, broker, shareholder, 0),
                new IcebergOrder(4, security, SELL, 30, 12, broker, shareholder, 10, 0)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        EnterOrderRq updateReq = EnterOrderRq.createUpdateOrderRq(5, security.getIsin(), 4, LocalDateTime.now(), SELL,
                30, 10, 0, 0, 10, 0, 0);

        MatchResult result = security.updateOrder(updateReq, continuousMatcher);

        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.trades()).hasSize(2);
        assertThat(result.remainder().getQuantity()).isZero();
    }
    @Test
    void update_opening_price_works_no_one_matches(){
        MatchResult result = security.updateOpeningPrice(new AuctionMatcher());
        assertThat(result.getTradableQuantity()).isEqualTo(0);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(-1);
        assertThat(security.getLastTradedPrice()).isEqualTo(15000);
        assertThat(security.getOpeningPrice()).isEqualTo(-1);
    }
    @Test
    void update_opening_price_works_mixed_sell_buy_queue(){
        Security security1 = Security.builder().isin("TEST").build();
        orders = List.of(
                new Order(1, security, BUY, 200, 15500, broker, shareholder, 0),
                new Order(2, security, BUY, 600, 15995, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 200, 15500, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 500, 15995, broker, shareholder, 0)
        );
        orders.forEach(order -> security1.getOrderBook().enqueue(order));
        MatchResult result = security1.updateOpeningPrice(new AuctionMatcher());

        assertThat(result.getTradableQuantity()).isEqualTo(600);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(15995);
        assertThat(security1.getLastTradedPrice()).isEqualTo(15000);
        assertThat(security1.getOpeningPrice()).isEqualTo(15995);
    }

    @Test
    void opening_auction_works_mixed_sell_buy_queue(){
        Security security1 = Security.builder().isin("TEST").build();
        orders = List.of(
                new Order(1, security, BUY, 200, 15500, broker, shareholder, 0),
                new Order(2, security, BUY, 600, 15995, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 200, 15500, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 500, 15995, broker, shareholder, 0)
        );
        orders.forEach(order -> security1.getOrderBook().enqueue(order));
        security1.setOpeningPrice(15995);
        MatchResult result = security1.openAuction(new AuctionMatcher());

        assertThat(result.getTradableQuantity()).isEqualTo(600);
        assertThat(result.getLastTradedPrice()).isEqualTo(15995);
        assertThat(result.getOpeningPrice()).isEqualTo(15995);
        assertThat(security1.getLastTradedPrice()).isEqualTo(15995);
        assertThat(security1.getOpeningPrice()).isEqualTo(15995);
    }
    @Test
    void update_security_prices_works_when_adding_an_order_to_an_auction(){
        Security security1 = Security.builder().isin("TEST").build();
        orders = List.of(
                new Order(1, security, BUY, 200, 15500, broker, shareholder, 0),
                new Order(3, security, Side.SELL, 200, 15500, broker, shareholder, 0),
                new Order(4, security, Side.SELL, 500, 15995, broker, shareholder, 0)
        );
        orders.forEach(order -> security1.getOrderBook().enqueue(order));
        broker.increaseCreditBy(10_000_000L);
        MatchResult result = security1.newOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 5, LocalDateTime.now(),
                Side.BUY, 600, 15995, broker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0),
                broker, shareholder, new AuctionMatcher());

        assertThat(result.getTradableQuantity()).isEqualTo(600);
        assertThat(result.getLastTradedPrice()).isEqualTo(15000);
        assertThat(result.getOpeningPrice()).isEqualTo(15995);
        assertThat(security1.getLastTradedPrice()).isEqualTo(15000);
        assertThat(security1.getOpeningPrice()).isEqualTo(15995);
    }

}