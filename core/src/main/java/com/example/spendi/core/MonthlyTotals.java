package com.example.spendi.core;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** A monthly accumulator, independent of UI search and filters. Amounts are USD. */
public final class MonthlyTotals {
    private final YearMonth month;
    // BigDecimal prevents overflow when the sum exceeds a single stored long.
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expenses = BigDecimal.ZERO;

    public MonthlyTotals(YearMonth month) {
        this.month = Objects.requireNonNull(month, "month");
    }

    public static boolean belongsToMonth(String isoDate, YearMonth month) {
        Objects.requireNonNull(month, "month");
        if (isoDate == null) {
            return false;
        }
        try {
            return YearMonth.from(LocalDate.parse(isoDate)).equals(month);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    public void add(long amountMinor, String type, String isoDate) {
        if (!belongsToMonth(isoDate, month)) {
            return;
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal amount = BigDecimal.valueOf(amountMinor, 2);
        if ("INCOME".equals(type)) {
            income = income.add(amount);
        } else if ("EXPENSE".equals(type)) {
            expenses = expenses.add(amount);
        } else {
            throw new IllegalArgumentException("Unknown transaction type");
        }
    }

    public BigDecimal income() { return income; }
    public BigDecimal expenses() { return expenses; }
    public BigDecimal balance() { return income.subtract(expenses); }

    public BigDecimal remainingBudget(long budgetMinor) {
        if (budgetMinor <= 0) {
            throw new IllegalArgumentException("Budget must be positive");
        }
        return BigDecimal.valueOf(budgetMinor, 2).subtract(expenses);
    }
}
