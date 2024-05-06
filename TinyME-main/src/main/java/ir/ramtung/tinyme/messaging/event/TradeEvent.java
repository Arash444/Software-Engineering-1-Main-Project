package ir.ramtung.tinyme.messaging.event;

import ir.ramtung.tinyme.domain.entity.Trade;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class TradeEvent extends Event {
    private LocalDateTime time;
    private String securityIsin;
    private int price;
    private int quantity;
    private long buyID;
    private long sellID;

    public TradeEvent(Trade trade) {
        time = LocalDateTime.now();
        securityIsin = getSecurityIsin();
        price = trade.getPrice();
        quantity = trade.getQuantity();
        buyID = trade.getBuy().getOrderId();
        sellID = trade.getSell().getOrderId();
    }
}
