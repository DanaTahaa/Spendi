package com.example.spendi;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "budgets")
public class BudgetEntity {

    @PrimaryKey
    @NonNull
    public String month;

    public long amountMinor;

    public BudgetEntity(@NonNull String month, long amountMinor) {
        this.month = month;
        this.amountMinor = amountMinor;
    }
}