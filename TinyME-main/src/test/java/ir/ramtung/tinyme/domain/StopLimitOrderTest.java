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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
                buy_broker, shareholder, 15700);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getOrderBook().enqueue(matchingStopLimitOrder);
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
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade2))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_order_triggers_buy_stop_limit_order_matches_with_sell_partially_check_credit() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15700);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getOrderBook().enqueue(matchingStopLimitOrder);
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
                sell_broker, shareholder, 15400);
        security.getOrderBook().enqueue(stopLimitOrder);
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
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade2))));
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    void new_order_triggers_sell_stop_limit_order_check_credit() {
        Order buyOrder = new Order(2, security, BUY, 350, 15100, buy_broker,
                shareholder, 0);
        Order sellOrder = new Order(3, security, SELL, 300, 15300,
                sell_broker, shareholder, 0);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, SELL, 300, 15000,
                sell_broker, shareholder, 15400);
        security.getOrderBook().enqueue(stopLimitOrder);
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
                buy_broker, shareholder, 1000);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        assertThat(matchingStopLimitOrder.canTrade()).isTrue();

    }

    @Test
    void stop_limit_order_does_not_activate_when_price_below_stop_for_sell_order() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 551554264);
        Order matchingSellOrder = new Order(2, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        Order matchingBuyOrder= new Order(3, security, BUY, 304, 15700, buy_broker, shareholder,
                0);
        security.getOrderBook().enqueue(matchingStopLimitOrder);
        security.getOrderBook().enqueue(matchingSellOrder);
        security.getOrderBook().enqueue(matchingBuyOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        assertThat(matchingStopLimitOrder.canTrade()).isFalse();
    }

    @Test
    void new_order_triggers_stop_limit_order_which_triggers_another_stop_limit_order_check_activation() {
        StopLimitOrder firstStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15500);

        StopLimitOrder secondStopLimitOrder = new StopLimitOrder(2, security, BUY, 400, 15900,
                buy_broker, shareholder, 15900);

        Order firstMatchingSellOrder = new Order(3, security, Side.SELL, 10, 15800, sell_broker,
                shareholder, 0);

        Order secondMatchingOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getOrderBook().enqueue(firstStopLimitOrder);
        security.getOrderBook().enqueue(firstMatchingSellOrder);
        security.getOrderBook().enqueue(secondMatchingOrder);
        security.getOrderBook().enqueue(secondStopLimitOrder);

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                5, LocalDateTime.now(), Side.BUY, 5, 15800,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        verify(eventPublisher).publish((new OrderActivatedEvent(1, 1)));
        verify(eventPublisher).publish((new OrderActivatedEvent(1, 2)));
    }

    @Test
    void new_order_triggers_stop_limit_order_which_triggers_another_stop_limit_order_check_credit() {
        StopLimitOrder firstStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15500);

        StopLimitOrder secondStopLimitOrder = new StopLimitOrder(2, security, BUY, 400, 15900,
                buy_broker, shareholder, 15900);

        Order firstMatchingSellOrder = new Order(3, security, Side.SELL, 5, 15800, sell_broker,
                shareholder, 0);

        Order secondMatchingOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getOrderBook().enqueue(firstStopLimitOrder);
        security.getOrderBook().enqueue(secondStopLimitOrder);
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
                shareholder, 15000);
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
    void invalid_update_changing_stop_price_stop_limit_order() {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 100, 15750,
                buy_broker, shareholder, LocalDateTime.now(), OrderStatus.NEW, 100, true);
        security.getOrderBook().enqueue(stopLimitOrder);

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
                buy_broker, shareholder, 50000);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 500, 15450,
                sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(stopLimitOrder);
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
                buy_broker, shareholder, 50000);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 200, 15450,
                sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(stopLimitOrder);
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
                buy_broker, shareholder, 50000);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 500, 5450,
                sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(stopLimitOrder);
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
                buy_broker, shareholder, 50000);
        Order sellOrder = new Order(2, security, Side.SELL, 500, 15550,
                sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(stopLimitOrder);
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
    update_buy_stop_limit_order_change_stop_price_does_not_activate()
    {
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, Side.BUY, 300, 15500,
                buy_broker, shareholder, 50000);
        Order matchedSellOrder = new Order(2, security, Side.SELL, 200, 15450,
                sell_broker, shareholder, 0);
        security.getOrderBook().enqueue(stopLimitOrder);
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
                buy_broker, shareholder, 50000);
        security.getOrderBook().enqueue(stopLimitOrder);

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
                buy_broker, shareholder, 50000);
        security.getOrderBook().enqueue(stopLimitOrder);

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
                sell_broker, shareholder, 50000);
        security.getOrderBook().enqueue(stopLimitOrder);

        orderHandler.handleDeleteOrder(new DeleteOrderRq(1, security.getIsin(), Side.SELL, 1));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderDeletedEvent(1, 1));
        inOrder.verifyNoMoreInteractions();
        assertThat(sell_broker.getCredit()).isEqualTo(100000000);
    }
    @Test
    void new_order_triggers_two_sell_orders_each_trigger_two_check_order_of_events() {
        List<Order> orders = Arrays.asList(
                new Order(1, security, BUY, 10, 15900, buy_broker, shareholder, 0),
                new Order(2, security, BUY, 50, 15750, buy_broker, shareholder, 0),
                new Order(3, security, BUY, 50, 15700, buy_broker, shareholder, 0),
                new StopLimitOrder(4, security, SELL, 50, 14750, sell_broker, shareholder, 15800),
                new StopLimitOrder(5, security, SELL, 50, 14810, sell_broker, shareholder, 15900),
                new StopLimitOrder(6, security, SELL, 50, 14850, sell_broker, shareholder, 15740),
                new StopLimitOrder(7, security, SELL, 50, 14900, sell_broker, shareholder, 15970),
                new StopLimitOrder(8, security, SELL, 50, 15000, sell_broker, shareholder, 15700),
                new StopLimitOrder(9, security, SELL, 50, 15500, sell_broker, shareholder, 15850)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                10, LocalDateTime.now(), SELL, 10, 15850,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Order matchingSellOrderNew = new Order(10, security, SELL, 10, 15850,
                sell_broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15900, 10,
                matchingSellOrderNew, orders.get(0));
        Trade trade2 = new Trade(security, 15750, 50,
                orders.get(6), orders.get(1));
        Trade trade3 = new Trade(security, 15700, 50,
                orders.get(4), orders.get(2));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 7));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 7, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 5));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 5, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 9));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 6));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 8));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void new_order_triggers_two_buy_orders_each_trigger_two_check_order_of_events() {
        List<Order> orders = Arrays.asList(
                new StopLimitOrder(1, security, BUY, 50, 16100, buy_broker, shareholder, 15820),
                new StopLimitOrder(2, security, BUY, 50, 15970, buy_broker, shareholder, 15950),
                new StopLimitOrder(3, security, BUY, 50, 15960, buy_broker, shareholder, 15810),
                new StopLimitOrder(4, security, BUY, 50, 14950, buy_broker, shareholder, 15970),
                new StopLimitOrder(5, security, BUY, 50, 14810, buy_broker, shareholder, 15940),
                new StopLimitOrder(6, security, BUY, 50, 14800, buy_broker, shareholder, 15980),
                new Order(7, security, Side.SELL, 10, 15820, sell_broker, shareholder, 0),
                new Order(8, security, Side.SELL, 50, 15950, sell_broker, shareholder, 0),
                new Order(9, security, Side.SELL, 50, 16000, sell_broker, shareholder, 0)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));

        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                10, LocalDateTime.now(), Side.BUY, 10, 15850,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Order matchingBuyOrderNew = new Order(10, security, BUY, 10, 15850,
                buy_broker, shareholder, 0);
        Trade trade1 = new Trade(security, 15820, 10,
                matchingBuyOrderNew, orders.get(6));
        Trade trade2 = new Trade(security, 15950, 50,
                orders.get(2), orders.get(7));
        Trade trade3 = new Trade(security, 16000, 50,
                orders.get(0), orders.get(8));

        InOrder inOrder = inOrder(eventPublisher);
        inOrder.verify(eventPublisher).publish(new OrderAcceptedEvent(1, 10));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 10, List.of(new TradeDTO(trade1))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 3));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 3, List.of(new TradeDTO(trade2))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 1));
        inOrder.verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade3))));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 5));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 2));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 4));
        inOrder.verify(eventPublisher).publish(new OrderActivatedEvent(1, 6));
        inOrder.verifyNoMoreInteractions();
    }
}