
package com.example.spendi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ExpenseAnalyzerTest {

    // Test 1: Empty transactions list
    @Test
    public void emptyTransactionsShouldReturnEmptyTotals() {

        List<Transaction> transactions = new ArrayList<>();

        Map<String, Long> result =
                ExpenseAnalyzer.calculateByCategory(transactions);

        assertEquals(0, result.size());
    }

    // Test 2: Calculate expenses by category
    @Test
    public void shouldCalculateExpensesByCategory() {

        List<Transaction> transactions = new ArrayList<>();

        Transaction food1 = new Transaction(
                2000, "EXPENSE", "Food", "2026-10-13", "Lunch"
        );

        Transaction food2 = new Transaction(
                1500, "EXPENSE", "Food", "2026-10-13", "Coffee"
        );

        Transaction transport = new Transaction(
                1000, "EXPENSE", "Transport", "2026-10-13", "Taxi"
        );

        transactions.add(food1);
        transactions.add(food2);
        transactions.add(transport);

        Map<String, Long> result =
                ExpenseAnalyzer.calculateByCategory(transactions);

        assertEquals(Long.valueOf(3500), result.get("Food"));

        assertEquals(Long.valueOf(1000), result.get("Transport"));

        assertEquals(2, result.size());
    }

    @Test
    public void shouldIgnoreIncomeTransactions() {

        List<Transaction> transactions = new ArrayList<>();

        Transaction salary = new Transaction(
                500000, "INCOME", "Salary",
                "2026-10-13", "Monthly salary"
        );

        Transaction food = new Transaction(
                2500, "EXPENSE", "Food",
                "2026-10-13", "Lunch"
        );

        transactions.add(salary);
        transactions.add(food);

        Map<String, Long> result =
                ExpenseAnalyzer.calculateByCategory(transactions);

        assertEquals(Long.valueOf(2500), result.get("Food"));

        assertEquals(false, result.containsKey("Salary"));

        assertEquals(1, result.size());
    }

    @Test(expected = ArithmeticException.class)
    public void shouldDetectAmountOverflow() {

        List<Transaction> transactions = new ArrayList<>();

        Transaction expense1 = new Transaction(
                Long.MAX_VALUE,
                "EXPENSE",
                "Food",
                "2026-10-13",
                "Large expense"
        );

        Transaction expense2 = new Transaction(
                1,
                "EXPENSE",
                "Food",
                "2026-10-13",
                "Extra expense"
        );

        transactions.add(expense1);
        transactions.add(expense2);

        ExpenseAnalyzer.calculateByCategory(transactions);
    }

}
