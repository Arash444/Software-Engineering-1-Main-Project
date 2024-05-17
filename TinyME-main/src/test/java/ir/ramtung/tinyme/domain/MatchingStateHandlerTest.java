package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.config.MockedJMSTestConfig;
import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.MatcherStateHandler;
import ir.ramtung.tinyme.domain.service.OrderHandler;
import ir.ramtung.tinyme.messaging.EventPublisher;
import ir.ramtung.tinyme.messaging.Message;
import ir.ramtung.tinyme.messaging.event.MatchingStateRqRejectedEvent;
import ir.ramtung.tinyme.messaging.event.OrderAcceptedEvent;
import ir.ramtung.tinyme.messaging.event.SecurityStateChangedEvent;
import ir.ramtung.tinyme.messaging.event.TradeEvent;
import ir.ramtung.tinyme.messaging.request.ChangingMatchingStateRq;
import ir.ramtung.tinyme.messaging.request.MatchingState;
import ir.ramtung.tinyme.repository.BrokerRepository;
import ir.ramtung.tinyme.repository.SecurityRepository;
import ir.ramtung.tinyme.repository.ShareholderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static ir.ramtung.tinyme.domain.entity.Side.BUY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(MockedJMSTestConfig.class)
@DirtiesContext
public class MatchingStateHandlerTest {
    @Autowired
    MatcherStateHandler matcherStateHandler;
    @Autowired
    EventPublisher eventPublisher;
    @Autowired
    SecurityRepository securityRepository;
    @Autowired
    BrokerRepository brokerRepository;
    @Autowired
    ShareholderRepository shareholderRepository;
    private Security security;
    private Shareholder sellShareholder;
    private Shareholder buyShareholder;
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

        sellShareholder = Shareholder.builder().build();
        sellShareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(sellShareholder);

        buyShareholder = Shareholder.builder().build();
        buyShareholder.incPosition(security, 100_000);
        shareholderRepository.addShareholder(buyShareholder);

        buyBroker = Broker.builder().brokerId(1).build();
        sellBroker = Broker.builder().brokerId(2).build();
        sellBroker.increaseCreditBy(10_000_000L);
        buyBroker.increaseCreditBy(10_000_000L);
        brokerRepository.addBroker(buyBroker);
        brokerRepository.addBroker(sellBroker);
    }

    @Test
    void unknown_security_error_published() {
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq("UNKNOWN", MatchingState.AUCTION));
        verify(eventPublisher).publish(new MatchingStateRqRejectedEvent("UNKNOWN", Collections.singletonList(Message.UNKNOWN_SECURITY_ISIN)));
    }

    @Test
    void change_continuous_to_continuous(){
        security.setMatchingState(MatchingState.CONTINUOUS);
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
    }
    @Test
    void change_continuous_to_auction(){
        security.setMatchingState(MatchingState.CONTINUOUS);
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));
    }
    @Test
    void change_auction_to_continuous(){
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
    }
    @Test
    void change_auction_to_auction(){
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.AUCTION));
        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.AUCTION));
    }
    @Test
    void change_from_auction_open_auction_all_sell_queue_matches_check_trade_events() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, buyBroker, buyShareholder, 0),
                new Order(2, security, BUY, 43, 15500, buyBroker, buyShareholder, 0),
                new Order(3, security, Side.SELL, 65, 15600, sellBroker, sellShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15600);
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent(new Trade(security, 15600, 65, orders.get(0), orders.get(2))));
    }
    @Test
    void change_from_auction_open_auction_all_sell_queue_matches_check_credit_and_position() {
        orders = Arrays.asList(
                new Order(1, security, BUY, 304, 15700, buyBroker, buyShareholder, 0),
                new Order(2, security, BUY, 43, 15500, buyBroker, buyShareholder, 0),
                new Order(3, security, Side.SELL, 65, 15600, sellBroker, sellShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15600);
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L + 100 * 65);
        assertThat(sellBroker.getCredit()).isEqualTo(10_000_000L + 15600 * 65);
        assertThat(sellShareholder.hasEnoughPositionsOn(security, 100_000 - 65)).isTrue();
        assertThat(buyShareholder.hasEnoughPositionsOn(security, 100_000 + 65)).isTrue();
    }
    @Test
    void change_from_auction_open_auction_mixed_buy_sell_queue_check_trade_event() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15700, buyBroker, buyShareholder, 0),
                new Order(2, security, BUY, 100, 15650, buyBroker, buyShareholder, 0),
                new Order(3, security, BUY, 50, 15300, buyBroker, buyShareholder, 0),
                new Order(4, security, Side.SELL, 150, 15200, sellBroker, sellShareholder, 0),
                new Order(5, security, Side.SELL, 200, 15500, sellBroker, sellShareholder, 0),
                new Order(6, security, Side.SELL, 100, 15600, sellBroker, sellShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15600); //ToDo check if it works
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent(new Trade(security, 15600, 150, orders.get(0), orders.get(3))));
        verify(eventPublisher).publish(new TradeEvent(new Trade(security, 15600, 50,
                orders.get(0).snapshotWithQuantity(50), orders.get(4))));
        verify(eventPublisher).publish(new TradeEvent(new Trade(security, 15600, 100,
                orders.get(1), orders.get(4).snapshotWithQuantity(150))));
    }
    @Test
    void change_from_auction_open_auction_mixed_buy_sell_queue_check_credit_and_position() {
        orders = List.of(
                new Order(1, security, BUY, 200, 15700, buyBroker, buyShareholder, 0),
                new Order(2, security, BUY, 100, 15650, buyBroker, buyShareholder, 0),
                new Order(3, security, BUY, 50, 15300, buyBroker, buyShareholder, 0),
                new Order(4, security, Side.SELL, 150, 15200, sellBroker, sellShareholder, 0),
                new Order(5, security, Side.SELL, 200, 15500, sellBroker, sellShareholder, 0),
                new Order(6, security, Side.SELL, 100, 15600, sellBroker, sellShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15600); //ToDo check if it works
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L + 100 * 200 + 100 * 50 );
        assertThat(sellBroker.getCredit()).isEqualTo(10_000_000L + 15600 * 300);
        assertThat(sellShareholder.hasEnoughPositionsOn(security, 100_000 - 300)).isTrue();
        assertThat(buyShareholder.hasEnoughPositionsOn(security, 100_000 + 300)).isTrue();
    }
    @Test
    void from_auction_iceberg_sell_and_buy_order_with_remaining_sell_quantity_less_than_peak_size_check_events() {
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.SELL, 600, 15450, sellBroker, sellShareholder, 400, 0),
                new Order(2, security, Side.SELL, 100, 15450, sellBroker, sellShareholder, 0),
                new Order(3, security, Side.SELL, 1000, 15500, sellBroker, sellShareholder, 0),
                new IcebergOrder(4, security, BUY, 400, 15450, buyBroker, buyShareholder, 300, 0),
                new Order(5, security, BUY, 100, 15450, buyBroker, buyShareholder, 0),
                new Order(6, security, BUY, 120 , 15400, buyBroker, buyShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);

        Trade trade1 = new Trade(security, 15450, 300, orders.get(0), orders.get(3));
        Trade trade2 = new Trade(security, 15450, 100, orders.get(0).snapshotWithQuantity(300), orders.get(4));
        Trade trade3 = new Trade(security, 15450, 100,
                orders.get(0).snapshotWithQuantity(200), orders.get(3).snapshotWithQuantity(100));
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        verify(eventPublisher).publish(new SecurityStateChangedEvent(security.getIsin(), MatchingState.CONTINUOUS));
        verify(eventPublisher).publish(new TradeEvent(trade1));
        verify(eventPublisher).publish(new TradeEvent(trade2));
        verify(eventPublisher).publish(new TradeEvent(trade3));
    }
    @Test
    void from_auction_iceberg_sell_and_buy_order_with_remaining_sell_quantity_less_than_peak_size_check_credit_and_positions() {
        orders = Arrays.asList(
                new IcebergOrder(1, security, Side.SELL, 600, 15450, sellBroker, sellShareholder, 400, 0),
                new Order(2, security, Side.SELL, 100, 15450, sellBroker, sellShareholder, 0),
                new Order(3, security, Side.SELL, 1000, 15500, sellBroker, sellShareholder, 0),
                new IcebergOrder(4, security, BUY, 400, 15500, buyBroker, buyShareholder, 300, 0),
                new Order(5, security, BUY, 100, 15500, buyBroker, buyShareholder, 0),
                new Order(6, security, BUY, 120 , 15400, buyBroker, buyShareholder, 0)
        );
        orders.forEach(order -> orderBook.enqueue(order));
        security.setOpeningPrice(15450);
        matcherStateHandler.handleChangingMatchingStateRq(new ChangingMatchingStateRq(security.getIsin(), MatchingState.CONTINUOUS));

        assertThat(buyBroker.getCredit()).isEqualTo(10_000_000L + 50 * 500);
        assertThat(sellBroker.getCredit()).isEqualTo(10_000_000L + 15450 * 500);
        assertThat(sellShareholder.hasEnoughPositionsOn(security, 100_000 - 500)).isTrue();
        assertThat(buyShareholder.hasEnoughPositionsOn(security, 100_000 + 500)).isTrue();
    }

}
