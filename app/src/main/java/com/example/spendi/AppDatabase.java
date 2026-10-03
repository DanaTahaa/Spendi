package com.example.spendi;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = {
                Transaction.class,
                BudgetEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    public abstract TransactionDao transactionDao();

    public abstract BudgetDao budgetDao();

    private static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {
                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {
                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS budgets ("
                                    + "month TEXT NOT NULL, "
                                    + "amountMinor INTEGER NOT NULL, "
                                    + "PRIMARY KEY(month)"
                                    + ")"
                    );
                }
            };

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "spendi_transactions.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }

        return instance;
    }
}
