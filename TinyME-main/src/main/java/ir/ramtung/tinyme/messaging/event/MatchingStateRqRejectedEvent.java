package ir.ramtung.tinyme.messaging.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class MatchingStateRqRejectedEvent extends Event {
    private String securityId;
    private List<String> errors;
}
