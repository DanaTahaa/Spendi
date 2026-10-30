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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.spendi.core.AmountParser;
import com.example.spendi.core.MonthlyTotals;

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
    private int transactionRequest = 0;
    private boolean loadingTransactions;
    private boolean dataUnavailable;
    private DataRepository repository;
    private com.example.spendi.client.ApiClient.Summary cloudSummary;

    private AlertDialog budgetDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dashboard);
        repository=new DataRepository(this);
        findViewById(R.id.btnCloudConnection).setOnClickListener(view -> startActivity(new Intent(this,CloudConnectionActivity.class)));
        findViewById(R.id.tvDataSource).setOnClickListener(view -> loadTransactions());

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
            loadTransactions();
        });

        findViewById(R.id.btnNextMonth).setOnClickListener(view -> {
            selectedMonth = selectedMonth.plusMonths(1);
            loadTransactions();
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

        findViewById(R.id.btnSetBudget).setEnabled(false);
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
        repository=new DataRepository(this);
        DataRepository source=repository;
        YearMonth month=selectedMonth;
        int request=++transactionRequest;
        ++budgetRequest;
        loadingTransactions=true;
        dataUnavailable=false;
        allTransactions.clear(); displayedTransactions.clear(); currentBudget=null; cloudSummary=null;
        selectedMonthView.setText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.getDefault())));
        ((TextView)findViewById(R.id.tvDataSource)).setText(source.label()+" • tap to refresh");
        transactionsView.setEnabled(false); transactionsView.setText("Loading transactions…");
        balanceView.setText("…"); incomeView.setText("…"); expensesView.setText("…");
        budgetAmountView.setText("…"); budgetStatusView.setText("");
        findViewById(R.id.btnSetBudget).setEnabled(false);

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                List<Transaction> transactions = source.list(month);
                var summary=source.summary(month);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || request!=transactionRequest || !month.equals(selectedMonth)) {
                        return;
                    }

                    allTransactions = new ArrayList<>(transactions);
                    cloudSummary=summary;
                    loadingTransactions=false;
                    transactionsView.setEnabled(true);
                    refreshDashboard();
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not load transactions", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || request!=transactionRequest || !month.equals(selectedMonth)) {
                        return;
                    }

                    loadingTransactions=false;
                    transactionsView.setText(DataRepository.message(exception));
                    dataUnavailable=true;
                    balanceView.setText("Unavailable"); incomeView.setText("Unavailable"); expensesView.setText("Unavailable");
                    budgetAmountView.setText("Unavailable");
                    Toast.makeText(
                            appContext,
                            DataRepository.message(exception),
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
        return MonthlyTotals.belongsToMonth(transaction.date, selectedMonth);
    }

    private MonthlyTotals monthlyTotals() {
        MonthlyTotals totals = new MonthlyTotals(selectedMonth);
        for (Transaction transaction : allTransactions) {
            if (transaction.amountMinor > 0
                    && ("INCOME".equals(transaction.type)
                    || "EXPENSE".equals(transaction.type))) {
                totals.add(transaction.amountMinor, transaction.type, transaction.date);
            }
        }
        return totals;
    }

    private void updateTotals() {
        if(cloudSummary!=null) {
            incomeView.setText(formatMoney(new BigDecimal(cloudSummary.income)));
            expensesView.setText(formatMoney(new BigDecimal(cloudSummary.expenses)));
            balanceView.setText(formatMoney(new BigDecimal(cloudSummary.balance))); return;
        }
        MonthlyTotals totals = monthlyTotals();
        incomeView.setText(formatMoney(totals.income()));
        expensesView.setText(formatMoney(totals.expenses()));
        balanceView.setText(formatMoney(totals.balance()));
    }

    private void displayFilteredTransactions() {
        if(loadingTransactions || dataUnavailable) return;
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
            String message = monthHasTransactions
                    ? "No transactions match your search or filter."
                    : "No transactions for this month.";
            transactionsView.setText(message + getCategorySummary());
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

        transactionsView.setText(recent.toString() + getCategorySummary());
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
                        intent.putExtra("remote_transaction_id",transaction.remoteId);

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
        DataRepository source=repository;

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                int deleted = source.delete(transaction);

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
                            DataRepository.message(exception),
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
        DataRepository source=repository;
        findViewById(R.id.btnSetBudget).setEnabled(false);

        currentBudget = null;
        budgetAmountView.setText("Loading budget...");
        budgetStatusView.setText("");

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                BudgetEntity budget = source.budget(month);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()
                            || request != budgetRequest
                            || !month.equals(selectedMonth.toString())) {
                        return;
                    }

                    currentBudget = budget;
                    findViewById(R.id.btnSetBudget).setEnabled(true);
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
        if(cloudSummary!=null) {
            budgetAmountView.setText(cloudSummary.budget==null?getString(R.string.budget_not_set):formatMoney(new BigDecimal(cloudSummary.budget)));
            if(cloudSummary.remaining==null) budgetStatusView.setText("");
            else {
                BigDecimal remaining=new BigDecimal(cloudSummary.remaining);
                budgetStatusView.setText((remaining.signum()>=0?"Remaining: ":"Over budget: ")+formatMoney(remaining.abs()));
                budgetStatusView.setTextColor(Color.parseColor(remaining.signum()>=0?"#166534":"#991B1B"));
            }
            return;
        }
        if (currentBudget == null) {
            budgetAmountView.setText(R.string.budget_not_set);
            budgetStatusView.setText("");
            return;
        }

        BigDecimal budgetAmount =
                BigDecimal.valueOf(currentBudget.amountMinor, 2);

        BigDecimal remaining = monthlyTotals().remainingBudget(currentBudget.amountMinor);

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
        DataRepository source=repository;
        long versionToSave=currentBudget==null?0:currentBudget.remoteVersion;

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
                long amountMinor;
                try {
                    amountMinor = AmountParser.parsePositiveMinor(text);
                } catch (AmountParser.InvalidAmountException exception) {
                    int message = switch (exception.error()) {
                        case REQUIRED -> R.string.error_amount_required;
                        case INVALID -> R.string.error_amount_invalid;
                        case NOT_POSITIVE -> R.string.error_amount_positive;
                        case PRECISION -> R.string.error_amount_precision;
                        case TOO_LARGE -> R.string.error_amount_too_large;
                    };
                    input.setError(getString(message));
                    return;
                }

                BudgetEntity budget = new BudgetEntity(
                        monthToSave,
                        amountMinor
                );
                budget.remoteVersion=versionToSave;

                saveButton.setEnabled(false);
                input.setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                        .setEnabled(false);
                dialog.setCancelable(false);

                Context appContext = getApplicationContext();

                AppDatabase.databaseExecutor.execute(() -> {
                    try {
                        source.saveBudget(budget);

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

                            loadTransactions();
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
                                    DataRepository.message(exception)
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

    private String getCategorySummary() {

        List<Transaction> monthlyTransactions = new ArrayList<>();

        for (Transaction transaction : allTransactions) {
            if (belongsToSelectedMonth(transaction)) {
                monthlyTransactions.add(transaction);
            }
        }

        Map<String, BigDecimal> totals = new java.util.TreeMap<>();
        for(Transaction transaction:monthlyTransactions) {
            if("EXPENSE".equals(transaction.type)) totals.merge(transaction.category,BigDecimal.valueOf(transaction.amountMinor,2),BigDecimal::add);
        }

        if (totals.isEmpty()) {
            return "\n\nNo expenses by category.";
        }

        StringBuilder summary =
                new StringBuilder("\n\nExpenses by Category:");

        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {

            summary.append("\n")
                    .append(entry.getKey())
                    .append(": ")
                    .append(formatMoney(
                            entry.getValue()
                    ));
        }

        return summary.toString();
    }

}
