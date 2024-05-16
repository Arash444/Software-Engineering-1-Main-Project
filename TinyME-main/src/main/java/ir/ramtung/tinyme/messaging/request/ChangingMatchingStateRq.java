package ir.ramtung.tinyme.messaging.request;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChangingMatchingStateRq {
    private String securityIsin;
    private MatchingState targetState;

    public ChangingMatchingStateRq(String SecurityIsin, MatchingState TargetState) {
        securityIsin = SecurityIsin;
        targetState = TargetState;
    }
}
