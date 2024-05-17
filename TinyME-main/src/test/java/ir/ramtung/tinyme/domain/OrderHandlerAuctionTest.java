package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.OpeningPriceEvent;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderRejectedEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class OrderHandlerAuctionTest {
    @Autowired
    OrderHandler orderHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder shareholder;
    private Broker buyBroker;
    private Broker sellBroker;
    private List<Order> orders;
    private OrderBook orderBook;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        security.setMatchingState(MatchingState.AUCTION);
        orderBook = security.getOrderBook();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        buyBroker = Broker.builder().brokerId(1).build();
        sellBroker = Broker.builder().brokerId(2).build();
        sellBroker.increaseCreditBy(10_000_000L);
        buyBroker.increaseCreditBy(10_000_000L);
        brokerRepository.addBroker(buyBroker);
        brokerRepository.addBroker(sellBroker);
    }
    @Test
    void invalid_new_order_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(), -1,
                LocalDateTime.now(), Side.SELL, -2, 0, -1, -1, -1, -1, -1));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE,
                Message.UNKNOWN_BROKER_ID,
                Message.UNKNOWN_SHAREHOLDER_ID,
                Message.ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE,
                Message.ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY,
                Message.ORDER_STOP_PRICE_NOT_POSITIVE,
                Message.CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY,
                Message.CANNOT_BE_ICEBERG_AND_STOP_LIMIT,
                Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS,
                Message.ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY
        );
    }
    @Test
    void invalid_update_with_multiple_errors() {
        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), -1, LocalDateTime.now(),
                Side.SELL, -2, 0, -1, shareholder.getShareholderId(), -1, -1, -1));
        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getOrderId()).isEqualTo(-1);
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.UNKNOWN_BROKER_ID,
                Message.INVALID_ORDER_ID,
                Message.ORDER_PRICE_NOT_POSITIVE,
                Message.ORDER_QUANTITY_NOT_POSITIVE,
                Message.INVALID_PEAK_SIZE,
                Message.ORDER_MIN_EXE_QUANTITY_NOT_POSITIVE,
                Message.ORDER_MIN_EXE_QUANTITY_MORE_THAN_TOTAL_QUANTITY,
                Message.ORDER_STOP_PRICE_NOT_POSITIVE,
                Message.CANNOT_HAVE_BOTH_STOP_PRICE_AND_MIN_EXE_QUANTITY,
                Message.CANNOT_BE_ICEBERG_AND_STOP_LIMIT,
                Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS,
                Message.ORDERS_IN_AUCTION_CANNOT_HAVE_MIN_EXE_QUANTITY
        );
    }
    @Test
    void stop_limit_order_delete_rejected() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 1000, 15500, buyBroker, shareholder, 1000, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 1));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.STOP_LIMIT_ORDERS_CANNOT_INTERACT_WITH_AUCTIONS)));
    }
    @Test
    void new_sell_order_queued_single_order() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(),
                Side.SELL, 300, 15450, sellBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L);
    }
    @Test
    void new_buy_order_queued_single_order() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(),
                Side.BUY, 300, 15450, buyBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L - 300 * 15450);
    }
    @Test
    void new_buy_order_rejected_not_enough_credit() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 1, LocalDateTime.now(),
                Side.BUY, 3000, 154500, buyBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderRejectedEvent(1, 1, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L);
    }
    @Test
    void mixed_buy_sell_queue_add_new_buy_order_multiple_potential_opening_price_opening_price_is_last_traded_price() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15010, buyBroker, shareholder, 0),
                new Order(2, security, Side.SELL, 200, 15010, sellBroker, shareholder, 0),
                new Order(3, security, Side.SELL, 200, 14995, sellBroker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 4, LocalDateTime.now(),
                Side.BUY, 200, 14995, buyBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 4));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15000, 200));

    }
    @Test
    void mixed_buy_sell_queue_add_new_buy_order_multiple_potential_opening_price_opening_price_is_the_one_closer_to_last_traded_price() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15500, buyBroker, shareholder, 0),
                new Order(2, security, Side.SELL, 200, 15500, sellBroker, shareholder, 0),
                new Order(3, security, Side.SELL, 200, 15995, sellBroker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 4, LocalDateTime.now(),
                Side.BUY, 200, 15995, buyBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 4));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), 15500, 200));

    }
    @Test
    void no_opening_price_no_one_matches() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, buyBroker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, buyBroker, shareholder, 0),
                new Order(3, security, Side.SELL, 350, 18500, sellBroker, shareholder, 0),
                new Order(4, security, Side.SELL, 100, 18600, sellBroker, shareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, "ABC", 5, LocalDateTime.now(),
                Side.BUY, 200, 15995, buyBroker.getBrokerId(), shareholder.getShareholderId(), 0, 0, 0));
        verify(eventPublisher).publish(new OrderAcceptedEvent(1, 5));
        verify(eventPublisher).publish(new OpeningPriceEvent(security.getIsin(), -1, 0));
    }

}
