package com.example.spendi;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insert(Transaction transaction);

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    List<Transaction> getAllTransactions();

    @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
    Transaction getById(long transactionId);

    @Update
    int update(Transaction transaction);

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    int deleteById(long transactionId);
}
