package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.*;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static ir.ramtung.tinyme.domain.entity.Side.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class StopLimitOrderTest {
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
    private Broker sell_broker;
    private Broker buy_broker;

    @BeforeEach
    void setup() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        buy_broker = Broker.builder().brokerId(1).build();
        sell_broker = Broker.builder().brokerId(2).build();
        buy_broker.increaseCreditBy(100_000_000L);
        sell_broker.increaseCreditBy(100_000_000L);
        brokerRepository.addBroker(buy_broker);
        brokerRepository.addBroker(sell_broker);
    }

    @Test
    void new_order_triggers_buy_stop_limit_order_and_trades_partially_validate_events() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15700,2 );
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getStopLimitOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));
        Order matchingSellOrderNew = new Order(4, security, SELL, 300, 15600,
                sell_broker, shareholder, 0);

        Trade trade2 = new Trade(security, 15800, 350,
                matchingStopLimitOrder, matchingSellOrder);

        Trade trade1 = new Trade(security, 15700, 300,
                matchingBuyOrder, matchingSellOrderNew);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 4, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(2, 1, List.of(new TradeDTO(trade2))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_order_triggers_buy_stop_limit_order_matches_with_sell_partially_check_credit() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15700, 0);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getStopLimitOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        int new_sell_credit = 100000000 +  (15700 * 300) + (15800 * 350) ;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 + (400 * 15900) - (15800 * 350) - (50 * 15900);
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void new_order_triggers_sell_stop_limit_order_validate_events() {
        Order buyOrder = new Order(2, security, BUY, 350, 15100, buy_broker,
                shareholder, 0);
        Order sellOrder = new Order(3, security, SELL, 300, 15300,
                sell_broker, shareholder, 0);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, SELL, 300, 15000,
                sell_broker, shareholder, 15400, 2);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(buyOrder);
        security.getOrderBook().enqueue(sellOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.BUY, 300, 15400,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));
        Order newBuyOrder = new Order(4, security, BUY, 200, 15000,
                buy_broker, shareholder, 0);

        Trade trade1 = new Trade(security, 15300, 300,
                newBuyOrder, sellOrder);

        Trade trade2 = new Trade(security, 15100, 300,
                stopLimitOrder, buyOrder);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 4, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(2, 1, List.of(new TradeDTO(trade2))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_order_triggers_sell_stop_limit_order_check_credit() {
        Order buyOrder = new Order(2, security, BUY, 350, 15100, buy_broker,
                shareholder, 0);
        Order sellOrder = new Order(3, security, SELL, 300, 15300,
                sell_broker, shareholder, 0);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, SELL, 300, 15000,
                sell_broker, shareholder, 15400, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(buyOrder);
        security.getOrderBook().enqueue(sellOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.BUY, 300, 15400,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        int new_sell_credit = 100000000 + (300 * 15300) + 15100 * 300;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 - 300 * 15300;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }

    @Test
    void stop_limit_order_activates_when_price_reaches_stop_for_buy_order() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 1000, 0);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getStopLimitOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        assertThat(security.getStopLimitOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getStopLimitOrderBook().getSellQueue()).isEmpty();

    }

    @Test
    void stop_limit_order_does_not_activate_when_price_below_stop_for_sell_order() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 551554264, 0);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getStopLimitOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        assertThat(matchingStopLimitOrder.canTrade()).isFalse();
    }

    @Test
    void new_order_triggers_stop_limit_order_which_triggers_another_stop_limit_order_check_events() {
        StopLimitOrder firstStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15500, 1 );

        StopLimitOrder secondStopLimitOrder = new StopLimitOrder(2, security, BUY, 400, 15900,
                buy_broker, shareholder, 15900, 2);

        Order firstMatchingSellOrder = new Order(3, security, Side.SELL, 10, 15800, sell_broker,
                shareholder, 0);

        Order secondMatchingOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getStopLimitOrderBook().enqueue(firstStopLimitOrder);
        security.getOrderBook().enqueue(firstMatchingSellOrder);
        security.getOrderBook().enqueue(secondMatchingOrder);
        security.getStopLimitOrderBook().enqueue(secondStopLimitOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                5, LocalDateTime.now(), Side.BUY, 5, 15800,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        verify(eventPublisher).publish((new OrderActivatedEvent(1, 1)));
        verify(eventPublisher).publish((new OrderActivatedEvent(2, 2)));
    }

    @Test
    void new_order_triggers_stop_limit_order_which_triggers_another_stop_limit_order_check_credit() {
        StopLimitOrder firstStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15500, 0);

        StopLimitOrder secondStopLimitOrder = new StopLimitOrder(2, security, BUY, 400, 15900,
                buy_broker, shareholder, 15900, 0);

        Order firstMatchingSellOrder = new Order(3, security, Side.SELL, 5, 15800, sell_broker,
                shareholder, 0);

        Order secondMatchingOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getStopLimitOrderBook().enqueue(firstStopLimitOrder);
        security.getStopLimitOrderBook().enqueue(secondStopLimitOrder);
        security.getOrderBook().enqueue(firstMatchingSellOrder);
        security.getOrderBook().enqueue(secondMatchingOrder);


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                5, LocalDateTime.now(), Side.BUY, 5, 15800,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        long new_sell_credit = 100_000_000L + (15800 * 5 + 15900 * 5);
        long new_buy_credit = 100_000_000L +(-15800 * 5 + 15900 * 400 - 15900 * 5 - 395 * 15900);
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }

    @Test
    void new_buy_stop_limit_order_does_not_activate_broker_does_not_have_enough_credit_check_rollback() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                1, LocalDateTime.now(), Side.BUY, 1000, 15800,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 20000));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();

        long new_buy_credit = 100_000_000L - 15800 * 1000 ;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void new_buy_stop_limit_order_broker_does_not_activate_has_enough_credit_check_credit_and_event() {
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                5, LocalDateTime.now(), Side.BUY, 1000000, 15800000,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 100));

        verify(eventPublisher).publish(new OrderRejectedEvent(1, 5, List.of(Message.BUYER_HAS_NOT_ENOUGH_CREDIT)));
        assertThat(buy_broker.getCredit()).isEqualTo(100_000_000L);
    }
    @Test
    void new_sell_stop_limit_order_activated_from_the_start_and_trades_check_events() {
        Order matchingBuyOrder = new Order(1, security, BUY, 500, 15850, buy_broker,
                shareholder, 0);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                2, LocalDateTime.now(), Side.SELL, 1000, 15800,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 15000));
        Order stopLimitSellOrder = new StopLimitOrder(2, security, SELL, 1000, 15850, sell_broker,
                shareholder, 15000,0);
        Trade trade1 = new Trade(security, 15850, 500,
                stopLimitSellOrder, matchingBuyOrder);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 2));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 2));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 2, List.of(new TradeDTO(trade1))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_buy_stop_limit_order_activated_from_the_start_and_trades_check_credit() {
        Order matchingSellOrder = new Order(1, security, Side.SELL, 500, 15800, sell_broker,
                shareholder, 0);
        security.getOrderBook().enqueue(matchingSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                2, LocalDateTime.now(), Side.BUY, 1000, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 15000));

        int new_sell_credit = 100000000 + 500 * 15800;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 - 500 * 15800 - 500 * 15850;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void new_sell_stop_limit_order_activated_from_the_start_and_trades_check_orderbook() {
        Order matchingBuyOrder = new Order(1, security, BUY, 500, 15850, buy_broker,
                shareholder, 0);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                2, LocalDateTime.now(), Side.SELL, 1000, 15800,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 15000));

        assertThat(security.getStopLimitOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getStopLimitOrderBook().getSellQueue()).isEmpty();
    }
    @Test
    void new_sell_stop_limit_order_activated_from_the_start_no_trade_check_events() {
        Order matchingBuyOrder = new Order(1, security, BUY, 500, 15850, buy_broker,
                shareholder, 0);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                2, LocalDateTime.now(), Side.SELL, 1000, 15900,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 15000));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 2));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 2));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_buy_stop_limit_order_activated_from_the_start_no_trade_check_events() {
        Order sellOrder = new Order(1, security, Side.SELL, 500, 15800, sell_broker,
                shareholder, 0);
        security.getOrderBook().enqueue(sellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                2, LocalDateTime.now(), Side.BUY, 1000, 15750,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 15000));

        int new_buy_credit = 100000000- 1000 * 15750;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
        assertThat(sell_broker.getCredit()).isEqualTo(100000000);
    }
    @Test
    void invalid_update_changing_stop_price_non_stop_limit_order() {
        Order beforeUpdate = new Order(1, security, Side.BUY, 500, 15450,
                buy_broker, shareholder, 0);
        security.getOrderBook().enqueue(beforeUpdate);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 500, 15450, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 500));

        ArgumentCaptor<OrderRejectedEvent> orderRejectedCaptor = ArgumentCaptor.forClass(OrderRejectedEvent.class);
        verify(eventPublisher).publish(orderRejectedCaptor.capture());
        OrderRejectedEvent outputEvent = orderRejectedCaptor.getValue();
        assertThat(outputEvent.getErrors()).containsOnly(
                Message.CANNOT_CHANGE_STOP_PRICE
        );
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_activates_with_trade_check_events()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 400, 15500,
                buy_broker, shareholder, 50000, 0);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 500, 15450,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(matchedSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 15500, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 10000));
        Trade trade1 = new Trade(security, 15450, 300,
                matchedSellOrder, stopLimitOrder);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1,
                List.of(new TradeDTO(trade1))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_activates_with_trade_check_credit()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 400, 15500,
                buy_broker, shareholder, 50000, 0);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 200, 15450,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(matchedSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 15500, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 10000));

        int new_sell_credit = 100000000 + 200 * 15450;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 + 400 * 15500 - 15450 * 200 - 100 * 15500;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_activates_with_trade_almost_0_broker_credit_check_credit()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 400, 15500,
                buy_broker, shareholder, 50000, 0);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 500, 5450,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(matchedSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 7000, 15500, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 10000));

        int new_sell_credit = 100000000 + 500 * 5450;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 + 400 * 15500 - 5450 * 500 - 6500 * 15500;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_activates_no_trade()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        Order sellOrder = new Order(2, security, Side.SELL, 500, 15550,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(sellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 15500, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 10000));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();
        assertThat(buy_broker.getCredit()).isEqualTo(100000000);
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_activates_no_trade_check_orderbook()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        Order sellOrder = new Order(2, security, Side.SELL, 500, 15550,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(sellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 15500, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 10000));

        Set<Long> expectedBuyOrderIds = new HashSet<>(List.of(stopLimitOrder.getOrderId()));
        LinkedList<Order> buyQueue = security.getOrderBook().getBuyQueue();
        Set<Long> actualBuyOrderIds = buyQueue.stream()
                .map(Order::getOrderId)
                .collect(Collectors.toSet());
        Set<Long> expectedSellOrderIds = new HashSet<>(List.of(sellOrder.getOrderId()));
        LinkedList<Order> sellQueue = security.getOrderBook().getSellQueue();
        Set<Long> actualSellOrderIds = sellQueue.stream()
                .map(Order::getOrderId)
                .collect(Collectors.toSet());

        assertThat(security.getStopLimitOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getStopLimitOrderBook().getSellQueue()).isEmpty();
        assertThat(expectedSellOrderIds).containsAll(actualSellOrderIds);
        assertThat(expectedBuyOrderIds).containsAll(actualBuyOrderIds);
    }
    @Test
    void
    update_buy_stop_limit_order_change_stop_price_does_not_activate()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 200, 15450,
                sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(matchedSellOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 300, 155000, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 20000));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(1, 1));
        int new_buy_credit = 100000000 + 300 * 15500 - 300 * 155000;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void
    update_buy_stop_limit_order_change_quantity_and_price_does_not_activate()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(), 1,
                LocalDateTime.now(), Side.BUY, 1000, 18000, buy_broker.getBrokerId(),
                shareholder.getShareholderId(), 0, 0, 50000));


        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();
        int new_buy_credit = 100000000 + 15500 * 300 - 1000 * 18000;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void delete_stop_limit_buy_order_deletes_successfully_and_increases_credit() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);

        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 1));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderDeletedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();
        int new_buy_credit = 100000000 + 300 * 15500;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void delete_stop_limit_sell_order_deletes_successfully() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.SELL, 300, 15500,
                sell_broker, shareholder, 50000, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);

        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.SELL, 1));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderDeletedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();
        assertThat(sell_broker.getCredit()).isEqualTo(100000000);
    }
    @Test
    void invalid_stop_limit_delete_with_order_id_not_found() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000, 0);
        security.getStopLimitOrderBook().enqueue(stopLimitOrder);

        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.BUY, 2));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderRejectedEvent(1, 2,
                List.of(Message.ORDER_ID_NOT_FOUND)));
        inOrder.verifyNoMoreInteractions();
        assertThat(buy_broker.getCredit()).isEqualTo(100000000);
    }
    @Test
    void new_order_triggers_two_sell_orders_each_trigger_two_check_order_of_events() {
        Order order1 = new Order(1, security, BUY, 10, 15900, buy_broker, shareholder, 0);
        Order order2 = new Order(2, security, BUY, 50, 15750, buy_broker, shareholder, 0);
        Order order3 = new Order(3, security, BUY, 50, 15700, buy_broker, shareholder, 0);
        StopLimitOrder order4 = new StopLimitOrder(4, security, SELL, 50, 14750, sell_broker, shareholder, 15800, 1);
        StopLimitOrder order5 = new StopLimitOrder(5, security, SELL, 50, 14810, sell_broker, shareholder, 15900, 2);
        StopLimitOrder order6 = new StopLimitOrder(6, security, SELL, 50, 14850, sell_broker, shareholder, 15740, 3);
        StopLimitOrder order7 = new StopLimitOrder(7, security, SELL, 50, 14900, sell_broker, shareholder, 15970, 4);
        StopLimitOrder order8 = new StopLimitOrder(8, security, SELL, 50, 15000, sell_broker, shareholder, 15700, 5);
        StopLimitOrder order9 = new StopLimitOrder(9, security, SELL, 50, 15500, sell_broker, shareholder, 15850, 6);

        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getStopLimitOrderBook().enqueue(order7);
        security.getStopLimitOrderBook().enqueue(order8);
        security.getStopLimitOrderBook().enqueue(order9);
        security.getOrderBook().enqueue(order1);
        security.getOrderBook().enqueue(order2);
        security.getOrderBook().enqueue(order3);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(),
                10, LocalDateTime.now(), SELL, 10, 15850,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Order matchingSellOrderNew = new Order(10, security, SELL, 10, 15850,
                sell_broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15900, 10,
                matchingSellOrderNew, order1);
        Trade trade2 = new Trade(security, 15750, 50,
                order7, order2);
        Trade trade3 = new Trade(security, 15700, 50,
                order5, order3);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(7, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(7, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(4, 7));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(4, 7, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 5));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(2, 5, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(6, 9));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(3, 6));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(5, 8));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void new_order_triggers_two_buy_orders_each_trigger_two_check_order_of_events() {
        StopLimitOrder order1 = new StopLimitOrder(1, security, BUY, 50, 16100, buy_broker, shareholder, 15820,1);
        StopLimitOrder order2 = new StopLimitOrder(2, security, BUY, 50, 15970, buy_broker, shareholder, 15950,2);
        StopLimitOrder order3 = new StopLimitOrder(3, security, BUY, 50, 15960, buy_broker, shareholder, 15810,3);
        StopLimitOrder order4 = new StopLimitOrder(4, security, BUY, 50, 14950, buy_broker, shareholder, 15970,4);
        StopLimitOrder order5 = new StopLimitOrder(5, security, BUY, 50, 14810, buy_broker, shareholder, 15940,5);
        StopLimitOrder order6 = new StopLimitOrder(6, security, BUY, 50, 14800, buy_broker, shareholder, 15980,6);
        Order order7 = new Order(7, security, Side.SELL, 10, 15820, sell_broker, shareholder, 0);
        Order order8 = new Order(8, security, Side.SELL, 50, 15950, sell_broker, shareholder, 0);
        Order order9 = new Order(9, security, Side.SELL, 50, 16000, sell_broker, shareholder, 0);

        security.getStopLimitOrderBook().enqueue(order1);
        security.getStopLimitOrderBook().enqueue(order2);
        security.getStopLimitOrderBook().enqueue(order3);
        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getOrderBook().enqueue(order7);
        security.getOrderBook().enqueue(order8);
        security.getOrderBook().enqueue(order9);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(7, security.getIsin(),
                10, LocalDateTime.now(), Side.BUY, 10, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Order matchingBuyOrderNew = new Order(10, security, BUY, 10, 15850,
                buy_broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15820, 10,
                matchingBuyOrderNew, order7);
        Trade trade2 = new Trade(security, 15950, 50,
                order3, order8);
        Trade trade3 = new Trade(security, 16000, 50,
                order1, order9);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(7, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(7, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(3, 3));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(3, 3, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(5, 5));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 2));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(4, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(6, 6));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void update_order_triggers_two_sell_orders_each_trigger_two_check_order_of_events() {
        Order order1 = new Order(1, security, BUY, 10, 15900, buy_broker, shareholder, 0);
        Order order2 = new Order(2, security, BUY, 50, 15750, buy_broker, shareholder, 0);
        Order order3 = new Order(3, security, BUY, 50, 15700, buy_broker, shareholder, 0);
        StopLimitOrder order4 = new StopLimitOrder(4, security, SELL, 50, 14750, sell_broker, shareholder, 15800, 1);
        StopLimitOrder order5 = new StopLimitOrder(5, security, SELL, 50, 14810, sell_broker, shareholder, 15900, 2);
        StopLimitOrder order6 = new StopLimitOrder(6, security, SELL, 50, 14850, sell_broker, shareholder, 15740, 3);
        StopLimitOrder order7 = new StopLimitOrder(7, security, SELL, 50, 14900, sell_broker, shareholder, 15970, 4);
        StopLimitOrder order8 = new StopLimitOrder(8, security, SELL, 50, 15000, sell_broker, shareholder, 15700, 5);
        StopLimitOrder order9 = new StopLimitOrder(9, security, SELL, 50, 15500, sell_broker, shareholder, 15850, 6);
        Order order10 = new Order(10, security, SELL, 10, 16850, sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(order1);
        security.getOrderBook().enqueue(order2);
        security.getOrderBook().enqueue(order3);
        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getStopLimitOrderBook().enqueue(order7);
        security.getStopLimitOrderBook().enqueue(order8);
        security.getStopLimitOrderBook().enqueue(order9);
        security.getOrderBook().enqueue(order10);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(7, security.getIsin(),
                10, LocalDateTime.now(), SELL, 10, 15850,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Trade trade1 = new Trade(security, 15900, 10,
                order10, order1);
        Trade trade2 = new Trade(security, 15750, 50,
                order7, order2);
        Trade trade3 = new Trade(security, 15700, 50,
                order5, order3);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(7, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(7, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(4, 7));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(4, 7, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 5));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(2, 5, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(6, 9));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(3, 6));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(5, 8));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void update_order_triggers_two_buy_orders_each_trigger_two_check_order_of_events() {
        StopLimitOrder order1 = new StopLimitOrder(1, security, BUY, 50, 16100, buy_broker, shareholder, 15820,1);
        StopLimitOrder order2 = new StopLimitOrder(2, security, BUY, 50, 15970, buy_broker, shareholder, 15950, 2);
        StopLimitOrder order3 = new StopLimitOrder(3, security, BUY, 50, 15960, buy_broker, shareholder, 15810, 3);
        StopLimitOrder order4 = new StopLimitOrder(4, security, BUY, 50, 14950, buy_broker, shareholder, 15970, 4);
        StopLimitOrder order5 = new StopLimitOrder(5, security, BUY, 50, 14810, buy_broker, shareholder, 15940, 5);
        StopLimitOrder order6 = new StopLimitOrder(6, security, BUY, 50, 14800, buy_broker, shareholder, 15980, 6);
        Order order7 = new Order(7, security, Side.SELL, 10, 15820, sell_broker, shareholder, 0);
        Order order8 = new Order(8, security, Side.SELL, 50, 15950, sell_broker, shareholder, 0);
        Order order9 = new Order(9, security, Side.SELL, 50, 16000, sell_broker, shareholder, 0);
        Order order10 = new Order(10, security, BUY, 10, 15000, buy_broker, shareholder, 0);

        security.getStopLimitOrderBook().enqueue(order1);
        security.getStopLimitOrderBook().enqueue(order2);
        security.getStopLimitOrderBook().enqueue(order3);
        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getOrderBook().enqueue(order7);
        security.getOrderBook().enqueue(order8);
        security.getOrderBook().enqueue(order9);
        security.getOrderBook().enqueue(order10);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(7, security.getIsin(),
                10, LocalDateTime.now(), Side.BUY, 10, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Trade trade1 = new Trade(security, 15820, 10,
                order10, order7);
        Trade trade2 = new Trade(security, 15950, 50,
                order3, order8);
        Trade trade3 = new Trade(security, 16000, 50,
                order1, order9);

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderUpdatedEvent(7, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(7, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(3, 3));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(3, 3, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(5, 5));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(2, 2));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(4, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(6, 6));
        inOrder.verifyNoMoreInteractions();
    }


    @Test
    void new_order_triggers_two_buy_orders_each_trigger_two_check_orderbook() {
        StopLimitOrder order1 = new StopLimitOrder(1, security, BUY, 50, 16100, buy_broker, shareholder, 15820, 0);
        StopLimitOrder order2 = new StopLimitOrder(2, security, BUY, 50, 15970, buy_broker, shareholder, 15950, 0);
        StopLimitOrder order3 = new StopLimitOrder(3, security, BUY, 50, 15960, buy_broker, shareholder, 15810, 0);
        StopLimitOrder order4 = new StopLimitOrder(4, security, BUY, 50, 14950, buy_broker, shareholder, 15970, 0);
        StopLimitOrder order5 = new StopLimitOrder(5, security, BUY, 50, 14810, buy_broker, shareholder, 15940, 0);
        StopLimitOrder order6 = new StopLimitOrder(6, security, BUY, 50, 14800, buy_broker, shareholder, 15980, 0);
        Order order7 = new Order(7, security, Side.SELL, 10, 15820, sell_broker, shareholder, 0);
        Order order8 = new Order(8, security, Side.SELL, 50, 15950, sell_broker, shareholder, 0);
        Order order9 = new Order(9, security, Side.SELL, 50, 16000, sell_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(order1);
        security.getStopLimitOrderBook().enqueue(order2);
        security.getStopLimitOrderBook().enqueue(order3);
        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getOrderBook().enqueue(order7);
        security.getOrderBook().enqueue(order8);
        security.getOrderBook().enqueue(order9);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                10, LocalDateTime.now(), Side.BUY, 10, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Set<Long> expectedOrderIds = new HashSet<>(Arrays.asList(order2.getOrderId(), order4.getOrderId(),
                order5.getOrderId(), order6.getOrderId()));
        LinkedList<Order> buyQueue = security.getOrderBook().getBuyQueue();
        Set<Long> actualOrderIds = buyQueue.stream()
                .map(Order::getOrderId)
                .collect(Collectors.toSet());

        assertThat(security.getStopLimitOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getStopLimitOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getOrderBook().getSellQueue()).isEmpty();
        assertThat(actualOrderIds).containsAll(expectedOrderIds);
    }
    @Test
    void update_order_triggers_two_buy_orders_each_trigger_two_check_orderbook() {
        StopLimitOrder order1 = new StopLimitOrder(1, security, BUY, 50, 16100, buy_broker, shareholder, 15820, 0);
        StopLimitOrder order2 = new StopLimitOrder(2, security, BUY, 50, 15970, buy_broker, shareholder, 15950, 0);
        StopLimitOrder order3 = new StopLimitOrder(3, security, BUY, 50, 15960, buy_broker, shareholder, 15810,0);
        StopLimitOrder order4 = new StopLimitOrder(4, security, BUY, 50, 14950, buy_broker, shareholder, 15970,0);
        StopLimitOrder order5 = new StopLimitOrder(5, security, BUY, 50, 14810, buy_broker, shareholder, 15940,0);
        StopLimitOrder order6 = new StopLimitOrder(6, security, BUY, 50, 14800, buy_broker, shareholder, 15980,0);
        Order order7 = new Order(7, security, Side.SELL, 10, 15820, sell_broker, shareholder, 0);
        Order order8 = new Order(8, security, Side.SELL, 50, 15950, sell_broker, shareholder, 0);
        Order order9 = new Order(9, security, Side.SELL, 50, 16000, sell_broker, shareholder, 0);
        Order order10 = new Order(10, security, BUY, 10, 15000, buy_broker, shareholder, 0);
        security.getStopLimitOrderBook().enqueue(order1);
        security.getStopLimitOrderBook().enqueue(order2);
        security.getStopLimitOrderBook().enqueue(order3);
        security.getStopLimitOrderBook().enqueue(order4);
        security.getStopLimitOrderBook().enqueue(order5);
        security.getStopLimitOrderBook().enqueue(order6);
        security.getOrderBook().enqueue(order7);
        security.getOrderBook().enqueue(order8);
        security.getOrderBook().enqueue(order9);
        security.getOrderBook().enqueue(order10);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, security.getIsin(),
                10, LocalDateTime.now(), Side.BUY, 10, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Set<Long> expectedOrderIds = new HashSet<>(Arrays.asList(order2.getOrderId(), order4.getOrderId(),
                order5.getOrderId(), order6.getOrderId()));
        LinkedList<Order> buyQueue = security.getOrderBook().getBuyQueue();
        Set<Long> actualOrderIds = buyQueue.stream()
                .map(Order::getOrderId)
                .collect(Collectors.toSet());

        assertThat(security.getStopLimitOrderBook().getBuyQueue()).isEmpty();
        assertThat(security.getStopLimitOrderBook().getSellQueue()).isEmpty();
        assertThat(security.getOrderBook().getSellQueue()).isEmpty();
        assertThat(actualOrderIds).containsAll(expectedOrderIds);
    }
}