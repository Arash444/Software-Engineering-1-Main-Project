package ir.ramtung.tinyme.domain.service;

import ir.ramtung.tinyme.domain.entity.*;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Service
public abstract class Matcher {

    public abstract MatchResult execute(Order order, Boolean isAmendOrder);

    public MatchResult matchAllOrders(Security security) {
        return null;
    }
}
