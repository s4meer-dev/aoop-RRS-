package com.irctc.rrs.inventory.internal;

import com.irctc.rrs.common.exception.QuotaEligibilityException;
import com.irctc.rrs.common.model.QuotaCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class QuotaStrategyRegistry {

    private final Map<QuotaCode, QuotaStrategy> strategies = new EnumMap<>(QuotaCode.class);

    public QuotaStrategyRegistry(List<QuotaStrategy> implementations) {
        for (QuotaStrategy strategy : implementations) {
            strategies.put(strategy.getQuotaCode(), strategy);
        }
    }

    public QuotaStrategy resolve(QuotaCode code) {
        QuotaStrategy strategy = strategies.get(code);
        if (strategy == null) {
            throw new QuotaEligibilityException("Unsupported quota code: " + code);
        }
        return strategy;
    }
}
