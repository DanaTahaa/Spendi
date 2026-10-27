package com.example.spendi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpenseAnalyzer {

    public static Map<String, Long> calculateByCategory(
            List<Transaction> transactions) {

        Map<String, Long> totals = new HashMap<>();

        for (Transaction transaction : transactions) {

            if ("EXPENSE".equals(transaction.type)) {

                String category = transaction.category;
                long amount = transaction.amountMinor;

                long current = totals.getOrDefault(category, 0L);

                totals.put(category, Math.addExact(current, amount));
            }
        }

        return totals;
    }
}
