package com.example.spendi;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveBudget(BudgetEntity budget);

    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    BudgetEntity getBudgetForMonth(String month);
}
