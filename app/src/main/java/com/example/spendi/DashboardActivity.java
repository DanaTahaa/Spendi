package com.example.spendi;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private TextView balanceView;
    private TextView incomeView;
    private TextView expensesView;
    private TextView transactionsView;
    private TextView selectedMonthView;
    private TextView budgetAmountView;
    private TextView budgetStatusView;

    private Spinner filterSpinner;
    private EditText searchInput;

    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> displayedTransactions = new ArrayList<>();

    private YearMonth selectedMonth = YearMonth.now();
    private BudgetEntity currentBudget;

    private boolean deleting = false;
    private int budgetRequest = 0;

    private AlertDialog budgetDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);

        balanceView = findViewById(R.id.tvBalance);
        incomeView = findViewById(R.id.tvIncome);
        expensesView = findViewById(R.id.tvExpenses);
        transactionsView = findViewById(R.id.tvEmptyState);
        selectedMonthView = findViewById(R.id.tvSelectedMonth);
        budgetAmountView = findViewById(R.id.tvBudgetAmount);
        budgetStatusView = findViewById(R.id.tvBudgetStatus);

        filterSpinner = findViewById(R.id.spTransactionFilter);
        searchInput = findViewById(R.id.etSearchTransactions);

        if (savedInstanceState != null) {
            selectedMonth = YearMonth.parse(
                    savedInstanceState.getString(
                            "selected_month",
                            YearMonth.now().toString()
                    )
            );

            filterSpinner.setSelection(
                    savedInstanceState.getInt("selected_filter", 0)
            );

            searchInput.setText(
                    savedInstanceState.getString("search_query", "")
            );
        }

        findViewById(R.id.btnPreviousMonth).setOnClickListener(view -> {
            selectedMonth = selectedMonth.minusMonths(1);
            refreshDashboard();
        });

        findViewById(R.id.btnNextMonth).setOnClickListener(view -> {
            selectedMonth = selectedMonth.plusMonths(1);
            refreshDashboard();
        });

        findViewById(R.id.btnSetBudget).setOnClickListener(
                view -> showBudgetDialog()
        );

        filterSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id
                    ) {
                        displayFilteredTransactions();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                }
        );

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                displayFilteredTransactions();
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });

        transactionsView.setOnClickListener(
                view -> showTransactionMenu()
        );

        findViewById(R.id.btnAddTransaction).setOnClickListener(view -> {
            startActivity(new Intent(
                    DashboardActivity.this,
                    AddTransactionActivity.class
            ));
        });

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        refreshDashboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTransactions();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(
                "selected_month",
                selectedMonth.toString()
        );

        outState.putInt(
                "selected_filter",
                filterSpinner.getSelectedItemPosition()
        );

        outState.putString(
                "search_query",
                searchInput.getText().toString()
        );
    }

    @Override
    protected void onDestroy() {
        if (budgetDialog != null) {
            budgetDialog.dismiss();
            budgetDialog = null;
        }

        super.onDestroy();
    }

    private void loadTransactions() {
        Context appContext = getApplicationContext();

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                List<Transaction> transactions =
                        AppDatabase.getInstance(appContext)
                                .transactionDao()
                                .getAllTransactions();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    allTransactions = new ArrayList<>(transactions);
                    refreshDashboard();
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not load transactions", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            appContext,
                            "Could not load transactions",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void refreshDashboard() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "MMMM yyyy",
                        Locale.getDefault()
                );

        selectedMonthView.setText(selectedMonth.format(formatter));

        updateTotals();
        displayFilteredTransactions();
        loadBudget();
    }

    private boolean belongsToSelectedMonth(Transaction transaction) {
        if (transaction.date == null) {
            return false;
        }

        try {
            LocalDate date = LocalDate.parse(transaction.date);
            return YearMonth.from(date).equals(selectedMonth);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private void updateTotals() {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;

        for (Transaction transaction : allTransactions) {
            if (!belongsToSelectedMonth(transaction)) {
                continue;
            }

            BigDecimal amount = BigDecimal.valueOf(
                    transaction.amountMinor,
                    2
            );

            if ("INCOME".equals(transaction.type)) {
                income = income.add(amount);
            } else if ("EXPENSE".equals(transaction.type)) {
                expenses = expenses.add(amount);
            }
        }

        incomeView.setText(formatMoney(income));
        expensesView.setText(formatMoney(expenses));
        balanceView.setText(formatMoney(income.subtract(expenses)));
    }

    private void displayFilteredTransactions() {
        int selectedFilter = filterSpinner.getSelectedItemPosition();

        String query = searchInput.getText().toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        displayedTransactions = new ArrayList<>();
        boolean monthHasTransactions = false;

        for (Transaction transaction : allTransactions) {
            if (!belongsToSelectedMonth(transaction)) {
                continue;
            }

            monthHasTransactions = true;

            boolean matchesType =
                    selectedFilter == 0
                            || (selectedFilter == 1
                            && "INCOME".equals(transaction.type))
                            || (selectedFilter == 2
                            && "EXPENSE".equals(transaction.type));

            String category = transaction.category == null
                    ? ""
                    : transaction.category.toLowerCase(Locale.ROOT);

            String note = transaction.note == null
                    ? ""
                    : transaction.note.toLowerCase(Locale.ROOT);

            boolean matchesSearch =
                    query.isEmpty()
                            || category.contains(query)
                            || note.contains(query);

            if (matchesType && matchesSearch) {
                displayedTransactions.add(transaction);
            }
        }

        if (displayedTransactions.isEmpty()) {
            transactionsView.setText(
                    monthHasTransactions
                            ? "No transactions match your search or filter."
                            : "No transactions for this month."
            );
            return;
        }

        StringBuilder recent = new StringBuilder();
        int count = Math.min(5, displayedTransactions.size());

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                recent.append("\n\n");
            }

            recent.append(
                    transactionDescription(displayedTransactions.get(i))
            );
        }

        if (displayedTransactions.size() > count) {
            recent.append("\n\nShowing ")
                    .append(count)
                    .append(" of ")
                    .append(displayedTransactions.size())
                    .append(" matching transactions.");
        }

        recent.append("\n\nTap here to edit or delete a transaction.");

        transactionsView.setText(recent.toString());
    }

    private String transactionDescription(Transaction transaction) {
        String sign = "INCOME".equals(transaction.type) ? "+" : "-";

        String description = transaction.category
                + " • " + sign
                + formatMoney(
                BigDecimal.valueOf(transaction.amountMinor, 2)
        )
                + "\n" + transaction.date;

        if (transaction.note != null && !transaction.note.isEmpty()) {
            description += "\n" + transaction.note;
        }

        return description;
    }

    private void showTransactionMenu() {
        if (deleting || displayedTransactions.isEmpty()) {
            return;
        }

        List<Transaction> choices =
                new ArrayList<>(displayedTransactions);

        String[] labels = new String[choices.size()];

        for (int i = 0; i < choices.size(); i++) {
            labels[i] = transactionDescription(choices.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle("Select a transaction")
                .setItems(labels, (dialog, position) -> {
                    showTransactionActions(choices.get(position));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTransactionActions(Transaction transaction) {
        String[] actions = {"Edit", "Delete"};

        new AlertDialog.Builder(this)
                .setTitle("Transaction options")
                .setItems(actions, (dialog, position) -> {
                    if (position == 0) {
                        Intent intent = new Intent(
                                DashboardActivity.this,
                                AddTransactionActivity.class
                        );

                        intent.putExtra(
                                "transaction_id",
                                transaction.id
                        );

                        startActivity(intent);
                    } else {
                        confirmDelete(transaction);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Transaction transaction) {
        new AlertDialog.Builder(this)
                .setTitle("Delete transaction?")
                .setMessage(
                        transactionDescription(transaction)
                                + "\n\nThis cannot be undone."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteTransaction(transaction);
                })
                .show();
    }

    private void deleteTransaction(Transaction transaction) {
        if (deleting) {
            return;
        }

        deleting = true;
        transactionsView.setEnabled(false);

        Context appContext = getApplicationContext();

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                int deleted = AppDatabase.getInstance(appContext)
                        .transactionDao()
                        .deleteById(transaction.id);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    deleting = false;
                    transactionsView.setEnabled(true);

                    Toast.makeText(
                            appContext,
                            deleted > 0
                                    ? "Transaction deleted"
                                    : "Transaction no longer exists",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadTransactions();
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not delete transaction", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    deleting = false;
                    transactionsView.setEnabled(true);

                    Toast.makeText(
                            appContext,
                            "Could not delete. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    private void loadBudget() {
        String month = selectedMonth.toString();
        int request = ++budgetRequest;
        Context appContext = getApplicationContext();

        currentBudget = null;
        budgetAmountView.setText("Loading budget...");
        budgetStatusView.setText("");

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                BudgetEntity budget =
                        AppDatabase.getInstance(appContext)
                                .budgetDao()
                                .getBudgetForMonth(month);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || request != budgetRequest
                            || !month.equals(selectedMonth.toString())) {
                        return;
                    }

                    currentBudget = budget;
                    displayBudget();
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not load budget", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || request != budgetRequest) {
                        return;
                    }

                    budgetAmountView.setText("Could not load budget");
                    budgetStatusView.setText("");
                });
            }
        });
    }

    private void displayBudget() {
        if (currentBudget == null) {
            budgetAmountView.setText(R.string.budget_not_set);
            budgetStatusView.setText("");
            return;
        }

        BigDecimal budgetAmount =
                BigDecimal.valueOf(currentBudget.amountMinor, 2);

        BigDecimal expenses = BigDecimal.ZERO;

        for (Transaction transaction : allTransactions) {
            if (belongsToSelectedMonth(transaction)
                    && "EXPENSE".equals(transaction.type)) {
                expenses = expenses.add(
                        BigDecimal.valueOf(transaction.amountMinor, 2)
                );
            }
        }

        BigDecimal remaining = budgetAmount.subtract(expenses);

        budgetAmountView.setText(formatMoney(budgetAmount));

        if (remaining.signum() >= 0) {
            budgetStatusView.setText(
                    "Remaining: " + formatMoney(remaining)
            );
            budgetStatusView.setTextColor(
                    Color.parseColor("#166534")
            );
        } else {
            budgetStatusView.setText(
                    "Over budget: " + formatMoney(remaining.abs())
            );
            budgetStatusView.setTextColor(
                    Color.parseColor("#991B1B")
            );
        }
    }

    private void showBudgetDialog() {
        if (budgetDialog != null && budgetDialog.isShowing()) {
            return;
        }

        String monthToSave = selectedMonth.toString();

        EditText input = new EditText(this);
        input.setHint(R.string.budget_amount_hint);
        input.setSingleLine(true);
        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        if (currentBudget != null) {
            input.setText(
                    BigDecimal.valueOf(currentBudget.amountMinor, 2)
                            .toPlainString()
            );
            input.setSelection(input.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Budget for " + monthToSave)
                .setView(input)
                .setNegativeButton(R.string.cancel_budget, null)
                .setPositiveButton(R.string.save_budget, null)
                .create();

        budgetDialog = dialog;

        dialog.setOnDismissListener(ignored -> {
            if (budgetDialog == dialog) {
                budgetDialog = null;
            }
        });

        dialog.setOnShowListener(ignored -> {
            Button saveButton =
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            saveButton.setOnClickListener(view -> {
                String text = input.getText().toString().trim();
                StringBuilder normalized = new StringBuilder();

                for (char character : text.toCharArray()) {
                    int digit = Character.digit(character, 10);

                    if (digit >= 0) {
                        normalized.append(digit);
                    } else if (character == '\u066B'
                            || character == ',') {
                        normalized.append('.');
                    } else {
                        normalized.append(character);
                    }
                }

                String value = normalized.toString();

                if (!value.matches("[0-9]+(\\.[0-9]{1,2})?")) {
                    input.setError(
                            "Enter an amount such as 200 or 200.50"
                    );
                    return;
                }

                long amountMinor;

                try {
                    amountMinor = new BigDecimal(value)
                            .movePointRight(2)
                            .longValueExact();
                } catch (ArithmeticException exception) {
                    input.setError("Amount is too large");
                    return;
                }

                if (amountMinor <= 0) {
                    input.setError("Budget must be greater than zero");
                    return;
                }

                BudgetEntity budget = new BudgetEntity(
                        monthToSave,
                        amountMinor
                );

                saveButton.setEnabled(false);
                input.setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setEnabled(false);
                dialog.setCancelable(false);

                Context appContext = getApplicationContext();

                AppDatabase.databaseExecutor.execute(() -> {
                    try {
                        AppDatabase.getInstance(appContext)
                                .budgetDao()
                                .saveBudget(budget);

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            dialog.dismiss();

                            Toast.makeText(
                                    appContext,
                                    "Budget saved",
                                    Toast.LENGTH_SHORT
                            ).show();

                            loadBudget();
                        });
                    } catch (Exception exception) {
                        Log.e("Spendi", "Could not save budget", exception);

                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }

                            saveButton.setEnabled(true);
                            input.setEnabled(true);
                            dialog.getButton(
                                    AlertDialog.BUTTON_NEGATIVE
                            ).setEnabled(true);
                            dialog.setCancelable(true);

                            input.setError(
                                    "Could not save. Please try again."
                            );
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2).toPlainString() + " USD";
    }
}