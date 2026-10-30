package com.example.spendi;

import android.content.Context;
import com.example.spendi.client.ApiClient;
import com.example.spendi.client.ApiException;
import com.example.spendi.core.AmountParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** Explicit local/cloud sources. Cloud requests never import, overwrite, or silently fall back to Room data. */
public final class DataRepository {
    private final Context context;
    private final ConnectionSettings.Snapshot settings;
    public DataRepository(Context context) { this.context=context.getApplicationContext(); settings=ConnectionSettings.load(context); }
    public boolean cloud() { return settings.cloud(); }
    public String label() { return cloud()?"Cloud data":"On-device data"; }
    public ApiClient.Summary summary(YearMonth month) throws IOException { return cloud()?api().summary(month.toString()):null; }
    private ApiClient api() throws IOException {
        if(settings.invalid) throw new IOException("Saved connection unavailable. Reset it in Cloud connection.");
        return new ApiClient(settings.url,settings.token,BuildConfig.DEBUG);
    }
    public List<Transaction> list(YearMonth month) throws IOException {
        if(!cloud()) return AppDatabase.getInstance(context).transactionDao().getAllTransactions();
        List<Transaction> result=new ArrayList<>();
        for(ApiClient.Entry entry:api().list(month.toString())) result.add(from(entry));
        return result;
    }
    public Transaction get(long localId,String remoteId) throws IOException {
        if(cloud()) return from(api().get(remoteId));
        return AppDatabase.getInstance(context).transactionDao().getById(localId);
    }
    public void save(Transaction transaction,boolean editing,String key) throws IOException {
        if(cloud()) {
            ApiClient.Entry result=api().save(new ApiClient.Entry(editing?transaction.remoteId:null,money(transaction.amountMinor),
                    transaction.type,transaction.category,transaction.date,transaction.note,transaction.remoteVersion),key);
            transaction.remoteId=result.id; transaction.remoteVersion=result.version;
        } else {
            TransactionDao dao=AppDatabase.getInstance(context).transactionDao();
            if(editing) { if(dao.update(transaction)!=1) throw new IOException("Transaction no longer exists"); }
            else transaction.id=dao.insert(transaction);
        }
    }
    public int delete(Transaction transaction) throws IOException {
        if(!cloud()) return AppDatabase.getInstance(context).transactionDao().deleteById(transaction.id);
        api().delete(transaction.remoteId,transaction.remoteVersion); return 1;
    }
    public BudgetEntity budget(String month) throws IOException {
        if(!cloud()) return AppDatabase.getInstance(context).budgetDao().getBudgetForMonth(month);
        ApiClient.Budget value=api().budget(month);
        if(value==null) return null;
        BudgetEntity result=new BudgetEntity(month,AmountParser.parsePositiveMinor(value.amount)); result.remoteVersion=value.version;
        return result;
    }
    public void saveBudget(BudgetEntity budget) throws IOException {
        if(!cloud()) { AppDatabase.getInstance(context).budgetDao().saveBudget(budget); return; }
        ApiClient.Budget result=api().saveBudget(budget.month,money(budget.amountMinor),budget.remoteVersion);
        budget.remoteVersion=result.version;
    }
    private static Transaction from(ApiClient.Entry entry) {
        Transaction result=new Transaction(AmountParser.parsePositiveMinor(entry.amount),entry.type,entry.category,entry.date,entry.note);
        result.remoteId=entry.id; result.remoteVersion=entry.version; return result;
    }
    private static String money(long minor) { return BigDecimal.valueOf(minor,2).toPlainString(); }
    public static String message(Exception exception) {
        if(exception instanceof ApiException) {
            int status=((ApiException)exception).status;
            if(status==401) return "Access token rejected. Update Cloud connection.";
            if(status==409) return "Data changed on the server. Reload before saving again.";
            if(status==404) return "This item no longer exists or belongs to another account.";
            if(status==400) return "The server rejected the input. Check the amount, date and category.";
        }
        return "Request failed. Check the connection and retry. No local data was replaced.";
    }
}
