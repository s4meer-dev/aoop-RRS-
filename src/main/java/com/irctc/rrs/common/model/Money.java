package com.irctc.rrs.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable INR monetary value object preventing floating-point drift.
 */
public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative: " + amount);
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(double value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money multiply(double factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
    }

    public Money percentage(double percent) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String toString() {
        return "INR " + amount.toPlainString();
    }
}
