package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.TradeDTO;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.OrderActivatedEvent;
import ir.ramtung.tinyme.messaging.event.OrderDeletedEvent;
import ir.ramtung.tinyme.messaging.event.OrderExecutedEvent;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private Broker sell_broker, buy_broker;
    private Shareholder shareholder;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        securityRepository.clear();
        brokerRepository.clear();
        shareholderRepository.clear();

        security = Security.builder().isin("ABC").build();
        securityRepository.addSecurity(security);

        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(shareholder);

        buy_broker = Broker.builder().credit(100_000_000L).build();
        sell_broker = Broker.builder().credit(100_000_000L).build();

        brokerRepository.addBroker(buy_broker);
        brokerRepository.addBroker(sell_broker);
        orders = Arrays.asList(
                new StopLimitOrder(11, security, BUY, 200, 15900, buy_broker, shareholder, 15600),
                new Order(1, security, BUY, 304, 15700, buy_broker, shareholder, 0),
                new Order(2, security, BUY, 43, 15500, buy_broker, shareholder, 0),
                new Order(3, security, BUY, 445, 15450, buy_broker, shareholder, 0),
                new Order(4, security, BUY, 526, 15450, buy_broker, shareholder, 0),
                new Order(5, security, BUY, 1000, 15400, buy_broker, shareholder, 0),
                new Order(6, security, Side.SELL, 350, 15800, sell_broker, shareholder, 0),
                new Order(7, security, Side.SELL, 285, 15810, sell_broker, shareholder, 0),
                new Order(8, security, Side.SELL, 800, 15810, sell_broker, shareholder, 0),
                new Order(9, security, Side.SELL, 340, 15820, sell_broker, shareholder, 0),
                new Order(10, security, Side.SELL, 65, 15820, sell_broker, shareholder, 0),
                new StopLimitOrder(12, security, SELL, 200, 15850, sell_broker, shareholder, 15400)
        );
        orders.forEach(order -> security.getOrderBook().enqueue(order));
    }

    @Test
    void new_order_triggers_stop_limit_order_and_trades() {
        StopLimitOrder matchingStopLimitOrder = new StopLimitOrder(11, security, BUY, 200, 15800,
                buy_broker, shareholder, 15700);
        Order matchingSellOrder = new Order(6, security, Side.SELL, 350, 15800, sell_broker,
                shareholder, 0);
        StopLimitOrder matchingSellOrder1 = new StopLimitOrder(200, security, SELL, 300, 15600,
                sell_broker, shareholder, 0);
        Order matchingBuyOrder= new Order(1, security, BUY, 304, 15700, buy_broker, shareholder,
                0);


        orderHandler.handleEnterOrder(EnterOrderRq.createNewOrderRq(1, security.getIsin(),
                200, LocalDateTime.now(), Side.SELL, 300, 15600,
                sell_broker.getBrokerId(), shareholder.getShareholderId(), 0,
                0, 0));

        Trade trade2 = new Trade(security, matchingStopLimitOrder.getPrice(), matchingStopLimitOrder.getQuantity(),
                matchingStopLimitOrder, matchingSellOrder);

        Trade trade1 = new Trade(security, matchingBuyOrder.getPrice(), matchingSellOrder1.getQuantity(),
                matchingBuyOrder, matchingSellOrder1);

        //assertThat(sell_broker.getCredit()).isEqualTo(100_000_000L);
        verify(eventPublisher).publish((new OrderAcceptedEvent(1, 200)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 200, List.of(new TradeDTO(trade1))));
        verify(eventPublisher).publish((new OrderActivatedEvent(1, 11)));
        verify(eventPublisher).publish(new OrderExecutedEvent(1, 11, List.of(new TradeDTO(trade2))));
    }

}