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

        sell_broker = Broker.builder().brokerId(1).build();
        buy_broker = Broker.builder().brokerId(2).build();
        sell_broker.increaseCreditBy(100_000_000L);
        buy_broker.increaseCreditBy(100_000_000L);
        brokerRepository.addBroker(sell_broker);
        brokerRepository.addBroker(buy_broker);
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

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 4)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 4, List.of(new TradeDTO(trade1))));
        verify(eventPublisher).publish((new OrderActivatedEvent(1, 1)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade2))));
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

        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 4)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 4, List.of(new TradeDTO(trade1))));
        verify(eventPublisher).publish((new OrderActivatedEvent(1, 1)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 1, List.of(new TradeDTO(trade2))));
    }
    @Test
    void new_order_triggers_sell_stop_limit_order_check_credit() {
        Order buyOrder = new Order(2, security, BUY, 350, 15100, buy_broker,
                shareholder, 0);
        Order sellOrder = new Order(3, security, SELL, 300, 15300,
                sell_broker, shareholder, 0);
        StopLimitOrder stopLimitOrder = new StopLimitOrder(1, security, SELL, 300, 15000,
                sell_broker, shareholder, 15400);
        security.getOrderBook().enqueue(buyOrder);
        security.getOrderBook().enqueue(stopLimitOrder);
        security.getOrderBook().enqueue(sellOrder);
        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                4, LocalDateTime.now(), Side.BUY, 300, 15200,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        int new_sell_credit = 100000000;
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        int new_buy_credit = 100000000 ;
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    @Test
    void invalid_update_changing_stop_price_non_stop_limit_order() {
        Order beforeUpdate = new Order(1, security, Side.BUY, 500, 15450,
                buy_broker, shareholder, 0);
        security.getOrderBook().enqueue(beforeUpdate);

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1,
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

        orderHandler.handleEnterOrder(EnterOrderRq.createUpdateOrderRq(1, "ABC", 1,
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
        Order matchingSellOrderNew = new Order(4, security, SELL, 300, 15600,
                sell_broker, shareholder, 0);

        assertThat(matchingStopLimitOrder.hasBeenTriggered()).isTrue();

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
        Order matchingSellOrderNew = new Order(4, security, SELL, 300, 15600,
                sell_broker, shareholder, 0);

        assertThat(matchingStopLimitOrder.hasBeenTriggered()).isFalse();
    }

    @Test
    void new_order_triggers_stop_limit_order_which_triggers_another_stop_limit_order_check_activation() {
        StopLimitOrder firstStopLimitOrder = new StopLimitOrder(1, security, BUY, 400, 15900,
                buy_broker, shareholder, 15500);

        StopLimitOrder secondStopLimitOrder = new StopLimitOrder(2, security, BUY, 400, 15900,
                buy_broker, shareholder, 15900);

        Order firstMatchingSellOrder = new Order(3, security, Side.SELL, 10, 15800, sell_broker,
                shareholder, 0);

        Order sencondMatchingSellOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getOrderBook().enqueue(firstStopLimitOrder);
        security.getOrderBook().enqueue(firstMatchingSellOrder);
        security.getOrderBook().enqueue(sencondMatchingSellOrder);
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

        Order sencondMatchingSellOrder = new Order(4, security, Side.SELL, 5, 15900, sell_broker,
                shareholder, 0);

        security.getOrderBook().enqueue(firstStopLimitOrder);
        security.getOrderBook().enqueue(secondStopLimitOrder);
        security.getOrderBook().enqueue(firstMatchingSellOrder);
        security.getOrderBook().enqueue(sencondMatchingSellOrder);


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                5, LocalDateTime.now(), Side.BUY, 5, 15800,
                buy_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        long new_sell_credit = 100_000_000L + (15800 * 5 + 15900 * 5);
        long new_buy_credit = 100_000_000L +(-15800 * 5 + 15900 * 400 - 15900 * 5 - 395 * 15900);
        assertThat(sell_broker.getCredit()).isEqualTo(new_sell_credit);
        assertThat(buy_broker.getCredit()).isEqualTo(new_buy_credit);
    }
    //reject test buyer

}