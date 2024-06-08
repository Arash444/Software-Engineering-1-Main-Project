package ir.ramtung.tinyme.domain.service.validation;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import lombok.Getter;

public record ValidationContext(EnterOrderRq enterOrderRq, Security security, Broker broker, Shareholder shareholder) {}