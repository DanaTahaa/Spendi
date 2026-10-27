package com.example.spendi.core;

import org.junit.Test;
import java.math.BigDecimal;
import java.time.YearMonth;
import static org.junit.Assert.*;

public class MonthlyTotalsTest {
    @Test public void emptyMonthHasZeroTotalsAndFullBudget() {
        MonthlyTotals totals = new MonthlyTotals(YearMonth.of(2027, 7));
        money("0", totals.income());
        money("0", totals.expenses());
        money("0", totals.balance());
        money("50.00", totals.remainingBudget(5000));
    }

    @Test public void totalsUseAllMonthlyEntriesAndSeparateIncomeFromExpenses() {
        MonthlyTotals totals = new MonthlyTotals(YearMonth.of(2027, 7));
        totals.add(10000, "INCOME", "2027-07-01");
        totals.add(1025, "EXPENSE", "2027-07-01");
        totals.add(2000, "EXPENSE", "2027-07-31");
        totals.add(99999, "EXPENSE", "2027-06-30");
        totals.add(99999, "INCOME", "2027-08-01");
        money("100.00", totals.income());
        money("30.25", totals.expenses());
        money("69.75", totals.balance());
        money("19.75", totals.remainingBudget(5000));
        money("0", totals.remainingBudget(3025));
        money("-0.25", totals.remainingBudget(3000));
    }

    @Test public void handlesLeapYearAndRejectsInvalidDates() {
        YearMonth month = YearMonth.of(2028, 2);
        assertTrue(MonthlyTotals.belongsToMonth("2028-02-29", month));
        assertFalse(MonthlyTotals.belongsToMonth("2027-02-29", YearMonth.of(2027, 2)));
        assertFalse(MonthlyTotals.belongsToMonth("2028-03-01", month));
        assertFalse(MonthlyTotals.belongsToMonth("2028-02-30", month));
        assertFalse(MonthlyTotals.belongsToMonth("bad-date", month));
        assertFalse(MonthlyTotals.belongsToMonth(null, month));
    }

    @Test public void monthIncludesYear() {
        assertFalse(MonthlyTotals.belongsToMonth("2026-12-31", YearMonth.of(2027, 12)));
        assertTrue(MonthlyTotals.belongsToMonth("2027-01-01", YearMonth.of(2027, 1)));
    }

    @Test public void ignoresLegacyInvalidDates() {
        MonthlyTotals totals = new MonthlyTotals(YearMonth.of(2027, 7));
        totals.add(1000, "EXPENSE", "corrupt");
        money("0", totals.expenses());
    }

    @Test public void aggregateDoesNotOverflowLong() {
        MonthlyTotals totals = new MonthlyTotals(YearMonth.of(2027, 7));
        totals.add(Long.MAX_VALUE, "EXPENSE", "2027-07-01");
        totals.add(Long.MAX_VALUE, "EXPENSE", "2027-07-02");
        money("184467440737095516.14", totals.expenses());
        money("-184467440737095516.14", totals.balance());
    }

    @Test public void rejectsInvalidDomainValues() {
        MonthlyTotals totals = new MonthlyTotals(YearMonth.of(2027, 7));
        assertThrows(IllegalArgumentException.class, () -> totals.add(0, "EXPENSE", "2027-07-01"));
        assertThrows(IllegalArgumentException.class, () -> totals.add(-1, "INCOME", "2027-07-01"));
        assertThrows(IllegalArgumentException.class, () -> totals.add(1, "OTHER", "2027-07-01"));
        assertThrows(IllegalArgumentException.class, () -> totals.remainingBudget(0));
        assertThrows(IllegalArgumentException.class, () -> totals.remainingBudget(-1));
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
