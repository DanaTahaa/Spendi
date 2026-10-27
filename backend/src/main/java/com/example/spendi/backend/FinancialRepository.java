package com.example.spendi.backend;

import com.example.spendi.backend.db.generated.tables.records.TransactionsRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import static com.example.spendi.backend.Models.*;
import static com.example.spendi.backend.db.generated.Tables.*;

/** All queries include the subject derived from authentication, never from request JSON. */
public final class FinancialRepository {
    private final DSLContext db;
    public FinancialRepository(DataSource source) {
        db = DSL.using(source, SQLDialect.POSTGRES, new Settings().withRenderSchema(false));
    }

    public Created create(String owner, String key, TransactionWrite request) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}")) throw ApiException.invalid("A 1 to 128 character Idempotency-Key is required");
        if (request.version() != null) throw ApiException.invalid("version is not accepted when creating a transaction");
        ValidatedTransaction value = request.validate();
        String hash = hash(Json.write(List.of(value.amountMinor(), value.type(), value.category(), value.date().toString(), value.note())));
        return db.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            int reserved = tx.insertInto(IDEMPOTENCY_REQUESTS)
                    .set(IDEMPOTENCY_REQUESTS.OWNER_SUBJECT, owner).set(IDEMPOTENCY_REQUESTS.REQUEST_KEY, key)
                    .set(IDEMPOTENCY_REQUESTS.REQUEST_HASH, hash).set(IDEMPOTENCY_REQUESTS.RESPONSE_BODY, "")
                    .onConflict(IDEMPOTENCY_REQUESTS.OWNER_SUBJECT, IDEMPOTENCY_REQUESTS.REQUEST_KEY).doNothing().execute();
            if (reserved == 0) {
                // PostgreSQL's unique index waits for the first transaction to commit or roll back.
                var stored = tx.selectFrom(IDEMPOTENCY_REQUESTS).where(IDEMPOTENCY_REQUESTS.OWNER_SUBJECT.eq(owner))
                        .and(IDEMPOTENCY_REQUESTS.REQUEST_KEY.eq(key)).fetchSingle();
                if (!hash.equals(stored.getRequestHash())) throw new ApiException(409, "idempotency_conflict", "This key was already used with a different request");
                return new Created(Json.read(stored.getResponseBody(), TransactionView.class), true);
            }
            var row = tx.insertInto(TRANSACTIONS).set(TRANSACTIONS.ID, UUID.randomUUID())
                    .set(TRANSACTIONS.OWNER_SUBJECT, owner).set(TRANSACTIONS.AMOUNT_MINOR, value.amountMinor())
                    .set(TRANSACTIONS.KIND, value.type()).set(TRANSACTIONS.CATEGORY, value.category())
                    .set(TRANSACTIONS.OCCURRED_ON, value.date()).set(TRANSACTIONS.NOTE, value.note())
                    .returning().fetchSingle();
            TransactionView response = view(row);
            tx.update(IDEMPOTENCY_REQUESTS).set(IDEMPOTENCY_REQUESTS.RESPONSE_BODY, Json.write(response))
                    .where(IDEMPOTENCY_REQUESTS.OWNER_SUBJECT.eq(owner)).and(IDEMPOTENCY_REQUESTS.REQUEST_KEY.eq(key)).execute();
            return new Created(response, false);
        });
    }

    public TransactionView get(String owner, UUID id) {
        var row = db.selectFrom(TRANSACTIONS).where(TRANSACTIONS.OWNER_SUBJECT.eq(owner)).and(TRANSACTIONS.ID.eq(id)).fetchOne();
        if (row == null) throw ApiException.missing();
        return view(row);
    }

    public Page list(String owner, YearMonth month, int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 10000) throw ApiException.invalid("limit must be 1..100 and offset 0..10000");
        var rows = db.selectFrom(TRANSACTIONS).where(TRANSACTIONS.OWNER_SUBJECT.eq(owner))
                .and(TRANSACTIONS.OCCURRED_ON.ge(month.atDay(1))).and(TRANSACTIONS.OCCURRED_ON.lt(month.plusMonths(1).atDay(1)))
                .orderBy(TRANSACTIONS.OCCURRED_ON.desc(), TRANSACTIONS.ID.desc()).limit(limit).offset(offset).fetch();
        return new Page(rows.stream().map(FinancialRepository::view).toList(), limit, offset);
    }

    public TransactionView update(String owner, UUID id, TransactionWrite request) {
        var value = request.validate();
        long version = expectedVersion(request.version());
        get(owner, id);
        var updated = db.update(TRANSACTIONS).set(TRANSACTIONS.AMOUNT_MINOR, value.amountMinor())
                .set(TRANSACTIONS.KIND, value.type()).set(TRANSACTIONS.CATEGORY, value.category())
                .set(TRANSACTIONS.OCCURRED_ON, value.date()).set(TRANSACTIONS.NOTE, value.note())
                .set(TRANSACTIONS.VERSION, TRANSACTIONS.VERSION.plus(1L))
                .where(TRANSACTIONS.OWNER_SUBJECT.eq(owner)).and(TRANSACTIONS.ID.eq(id)).and(TRANSACTIONS.VERSION.eq(version))
                .returning().fetchOne();
        if (updated == null) throw ApiException.conflict();
        return view(updated);
    }

    public void delete(String owner, UUID id, long version) {
        expectedVersion(version);
        get(owner, id);
        if (db.deleteFrom(TRANSACTIONS).where(TRANSACTIONS.OWNER_SUBJECT.eq(owner)).and(TRANSACTIONS.ID.eq(id))
                .and(TRANSACTIONS.VERSION.eq(version)).execute() == 0) throw ApiException.conflict();
    }

    public BudgetView budget(String owner, YearMonth month) {
        var row = db.selectFrom(BUDGETS).where(BUDGETS.OWNER_SUBJECT.eq(owner)).and(BUDGETS.MONTH.eq(month.toString())).fetchOne();
        if (row == null) throw ApiException.missing();
        return new BudgetView(row.getMonth(), money(row.getAmountMinor()), "USD", row.getVersion());
    }

    public BudgetView saveBudget(String owner, YearMonth month, BudgetWrite request) {
        long minor = positiveAmount(request.amount());
        if (request.version() == null || request.version() == 0) {
            int inserted = db.insertInto(BUDGETS).set(BUDGETS.OWNER_SUBJECT, owner).set(BUDGETS.MONTH, month.toString())
                    .set(BUDGETS.AMOUNT_MINOR, minor).onConflict(BUDGETS.OWNER_SUBJECT, BUDGETS.MONTH).doNothing().execute();
            if (inserted == 0) throw ApiException.conflict();
            return new BudgetView(month.toString(), money(minor), "USD", 1);
        }
        long version = expectedVersion(request.version());
        budget(owner, month);
        var row = db.update(BUDGETS).set(BUDGETS.AMOUNT_MINOR, minor).set(BUDGETS.VERSION, BUDGETS.VERSION.plus(1L))
                .where(BUDGETS.OWNER_SUBJECT.eq(owner)).and(BUDGETS.MONTH.eq(month.toString())).and(BUDGETS.VERSION.eq(version))
                .returning().fetchOne();
        if (row == null) throw ApiException.conflict();
        return new BudgetView(month.toString(), money(row.getAmountMinor()), "USD", row.getVersion());
    }

    public Summary summary(String owner, YearMonth month) {
        return db.transactionResult(configuration -> {
            DSLContext tx = DSL.using(configuration);
            // Both reads must describe one PostgreSQL snapshot, including concurrent budget edits.
            tx.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            var sum = DSL.sum(TRANSACTIONS.AMOUNT_MINOR);
            var totals = tx.select(TRANSACTIONS.KIND, sum).from(TRANSACTIONS)
                    .where(TRANSACTIONS.OWNER_SUBJECT.eq(owner)).and(TRANSACTIONS.OCCURRED_ON.ge(month.atDay(1)))
                    .and(TRANSACTIONS.OCCURRED_ON.lt(month.plusMonths(1).atDay(1))).groupBy(TRANSACTIONS.KIND).fetch();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;
            for (var row : totals) {
                if ("INCOME".equals(row.value1())) income = row.value2();
                else expenses = row.value2();
            }
            Long budget = tx.select(BUDGETS.AMOUNT_MINOR).from(BUDGETS).where(BUDGETS.OWNER_SUBJECT.eq(owner))
                    .and(BUDGETS.MONTH.eq(month.toString())).fetchOne(BUDGETS.AMOUNT_MINOR);
            return new Summary(month.toString(), "USD", money(income), money(expenses), money(income.subtract(expenses)),
                    budget == null ? null : money(budget), budget == null ? null : money(BigDecimal.valueOf(budget).subtract(expenses)));
        });
    }

    public boolean ready() {
        try { return Integer.valueOf(1).equals(db.selectOne().fetchOne(0, Integer.class)); }
        catch (RuntimeException exception) { return false; }
    }

    private static TransactionView view(TransactionsRecord row) {
        return new TransactionView(row.getId(), money(row.getAmountMinor()), row.getCurrency(), row.getKind(),
                row.getCategory(), row.getOccurredOn().toString(), row.getNote(), row.getVersion());
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
