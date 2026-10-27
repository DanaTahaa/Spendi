package com.example.spendi.backend;

import com.example.spendi.core.AmountParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class Models {
    private Models() { }
    public record TransactionWrite(String amount, String type, String category, String date, String note, Long version) {
        public ValidatedTransaction validate() {
            long minor = positiveAmount(amount);
            if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) throw ApiException.invalid("type must be INCOME or EXPENSE");
            if (category == null || category.isBlank() || category.trim().codePointCount(0, category.trim().length()) > 60) throw ApiException.invalid("category must contain 1 to 60 characters");
            LocalDate day;
            try {
                if (date == null || !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
                day = LocalDate.parse(date);
                if (day.getYear() < 1900) throw new IllegalArgumentException();
            } catch (RuntimeException exception) { throw ApiException.invalid("date must be an ISO date between 1900 and 9999"); }
            String safeNote = note == null ? "" : note.trim();
            if (safeNote.codePointCount(0, safeNote.length()) > 1000) throw ApiException.invalid("note must not exceed 1000 characters");
            return new ValidatedTransaction(minor, type, category.trim(), day, safeNote);
        }
    }
    public record ValidatedTransaction(long amountMinor, String type, String category, LocalDate date, String note) { }
    public record TransactionView(UUID id, String amount, String currency, String type, String category, String date, String note, long version) { }
    public record Created(TransactionView transaction, boolean replayed) { }
    public record Page(List<TransactionView> items, int limit, int offset) { }
    public record BudgetWrite(String amount, Long version) { }
    public record BudgetView(String month, String amount, String currency, long version) { }
    public record Summary(String month, String currency, String income, String expenses, String balance, String budget, String remaining) { }

    public static long positiveAmount(String input) {
        try { return AmountParser.parsePositiveMinor(input); }
        catch (AmountParser.InvalidAmountException exception) { throw ApiException.invalid("Invalid amount: " + exception.error().name()); }
    }
    public static YearMonth month(String text) {
        try {
            if (text == null || !text.matches("[0-9]{4}-[0-9]{2}")) throw new IllegalArgumentException();
            YearMonth month = YearMonth.parse(text);
            if (month.getYear() < 1900 || month.getYear() > 9999) throw new IllegalArgumentException();
            return month;
        } catch (RuntimeException exception) { throw ApiException.invalid("month must be YYYY-MM between 1900 and 9999"); }
    }
    public static long expectedVersion(Long version) {
        if (version == null || version <= 0 || version == Long.MAX_VALUE) throw ApiException.invalid("A positive version is required");
        return version;
    }
    public static String money(long minor) { return BigDecimal.valueOf(minor, 2).toPlainString(); }
    public static String money(BigDecimal minor) { return minor.movePointLeft(2).setScale(2).toPlainString(); }
}
