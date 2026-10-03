package com.example.spendi;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long amountMinor;

    @NonNull
    public String type;

    @NonNull
    public String category;

    @NonNull
    public String date;

    @NonNull
    public String note;

    public Transaction(long amountMinor,
                       @NonNull String type,
                       @NonNull String category,
                       @NonNull String date,
                       @NonNull String note) {
        this.amountMinor = amountMinor;
        this.type = type;
        this.category = category;
        this.date = date;
        this.note = note;
    }
}
