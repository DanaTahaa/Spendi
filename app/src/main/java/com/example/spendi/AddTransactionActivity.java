package com.example.spendi;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    private TextInputLayout amountLayout;
    private TextInputEditText amountInput;
    private EditText noteInput;
    private RadioGroup typeGroup;
    private Spinner categorySpinner;
    private MaterialButton dateButton;
    private MaterialButton saveButton;

    private LocalDate selectedDate = LocalDate.now();
    private long transactionId = -1;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_transaction);

        amountLayout = findViewById(R.id.amountInputLayout);
        amountInput = findViewById(R.id.etAmount);
        noteInput = findViewById(R.id.etNote);
        typeGroup = findViewById(R.id.rgTransactionType);
        categorySpinner = findViewById(R.id.spCategory);
        dateButton = findViewById(R.id.btnTransactionDate);
        saveButton = findViewById(R.id.btnSaveTransaction);

        transactionId = getIntent().getLongExtra(
                "transaction_id", -1
        );

        updateCategories();
        updateDateButton();

        typeGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updateCategories()
        );

        dateButton.setOnClickListener(view -> {
            new DatePickerDialog(
                    this,
                    (picker, year, month, day) -> {
                        selectedDate = LocalDate.of(
                                year, month + 1, day
                        );
                        updateDateButton();
                    },
                    selectedDate.getYear(),
                    selectedDate.getMonthValue() - 1,
                    selectedDate.getDayOfMonth()
            ).show();
        });

        saveButton.setOnClickListener(view -> saveTransaction());

        if (transactionId > 0) {
            saveButton.setText("Save changes");
        }

        if (savedInstanceState != null
                && savedInstanceState.getBoolean("draft_ready")) {
            restoreDraft(savedInstanceState);
        } else if (transactionId > 0) {
            loadTransaction();
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );
                    view.setPadding(
                            bars.left, bars.top,
                            bars.right, bars.bottom
                    );
                    return insets;
                }
        );
    }

    private void updateCategories() {
        int arrayId =
                typeGroup.getCheckedRadioButtonId() == R.id.rbIncome
                        ? R.array.income_categories
                        : R.array.expense_categories;

        ArrayAdapter<CharSequence> adapter =
                ArrayAdapter.createFromResource(
                        this,
                        arrayId,
                        android.R.layout.simple_spinner_item
                );

        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        categorySpinner.setAdapter(adapter);
    }

    private void selectCategory(String category) {
        for (int i = 0; i < categorySpinner.getCount(); i++) {
            if (categorySpinner.getItemAtPosition(i)
                    .toString().equals(category)) {
                categorySpinner.setSelection(i);
                return;
            }
        }
    }

    private void updateDateButton() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(Locale.getDefault());

        dateButton.setText(selectedDate.format(formatter));
    }

    private void setBusy(boolean value) {
        busy = value;
        saveButton.setEnabled(!value);
        amountInput.setEnabled(!value);
        noteInput.setEnabled(!value);
        categorySpinner.setEnabled(!value);
        dateButton.setEnabled(!value);

        for (int i = 0; i < typeGroup.getChildCount(); i++) {
            typeGroup.getChildAt(i).setEnabled(!value);
        }
    }

    private void loadTransaction() {
        setBusy(true);
        Context appContext = getApplicationContext();
        long requestedId = transactionId;

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                Transaction transaction =
                        AppDatabase.getInstance(appContext)
                                .transactionDao()
                                .getById(requestedId);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (transaction == null) {
                        Toast.makeText(
                                this,
                                "Transaction no longer exists",
                                Toast.LENGTH_LONG
                        ).show();
                        finish();
                        return;
                    }

                    amountInput.setText(
                            BigDecimal.valueOf(
                                    transaction.amountMinor, 2
                            ).toPlainString()
                    );

                    typeGroup.check(
                            "INCOME".equals(transaction.type)
                                    ? R.id.rbIncome
                                    : R.id.rbExpense
                    );

                    selectCategory(transaction.category);
                    noteInput.setText(transaction.note);
                    selectedDate = LocalDate.parse(transaction.date);
                    updateDateButton();
                    setBusy(false);
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not load transaction", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            "Could not load transaction",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            }
        });
    }

    private void saveTransaction() {
        if (busy) {
            return;
        }

        amountLayout.setError(null);

        String text = amountInput.getText() == null
                ? ""
                : amountInput.getText().toString().trim();

        if (text.isEmpty()) {
            amountLayout.setError(
                    getString(R.string.error_amount_required)
            );
            return;
        }

        StringBuilder normalized = new StringBuilder();

        for (char character : text.toCharArray()) {
            int digit = Character.digit(character, 10);

            if (digit >= 0) {
                normalized.append(digit);
            } else if (character == '\u066B' || character == ',') {
                normalized.append('.');
            } else {
                normalized.append(character);
            }
        }

        String value = normalized.toString();

        if (!value.matches("[0-9]+(\\.[0-9]+)?")) {
            amountLayout.setError(
                    getString(R.string.error_amount_invalid)
            );
            return;
        }

        BigDecimal amount = new BigDecimal(value);

        if (amount.signum() <= 0) {
            amountLayout.setError(
                    getString(R.string.error_amount_positive)
            );
            return;
        }

        if (amount.scale() > 2) {
            amountLayout.setError(
                    getString(R.string.error_amount_precision)
            );
            return;
        }

        long amountMinor;

        try {
            amountMinor = amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            amountLayout.setError("Amount is too large");
            return;
        }

        if (categorySpinner.getSelectedItemPosition() <= 0) {
            Toast.makeText(
                    this,
                    R.string.error_category_required,
                    Toast.LENGTH_SHORT
            ).show();
            categorySpinner.performClick();
            return;
        }

        String type =
                typeGroup.getCheckedRadioButtonId() == R.id.rbIncome
                        ? "INCOME"
                        : "EXPENSE";

        Transaction transaction = new Transaction(
                amountMinor,
                type,
                categorySpinner.getSelectedItem().toString(),
                selectedDate.toString(),
                noteInput.getText().toString().trim()
        );

        boolean editing = transactionId > 0;

        if (editing) {
            transaction.id = transactionId;
        }

        setBusy(true);
        Context appContext = getApplicationContext();

        AppDatabase.databaseExecutor.execute(() -> {
            try {
                TransactionDao dao =
                        AppDatabase.getInstance(appContext)
                                .transactionDao();

                if (editing) {
                    if (dao.update(transaction) != 1) {
                        throw new IllegalStateException(
                                "Transaction no longer exists"
                        );
                    }
                } else {
                    dao.insert(transaction);
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    Toast.makeText(
                            this,
                            editing
                                    ? "Transaction updated"
                                    : "Transaction saved",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();
                });
            } catch (Exception exception) {
                Log.e("Spendi", "Could not save transaction", exception);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    setBusy(false);

                    Toast.makeText(
                            this,
                            "Could not save. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("draft_ready", !busy);

        if (!busy) {
            outState.putString(
                    "draft_amount",
                    amountInput.getText() == null
                            ? ""
                            : amountInput.getText().toString()
            );
            outState.putString(
                    "draft_note", noteInput.getText().toString()
            );
            outState.putInt(
                    "draft_type", typeGroup.getCheckedRadioButtonId()
            );
            outState.putString(
                    "draft_category",
                    categorySpinner.getSelectedItem().toString()
            );
            outState.putString("draft_date", selectedDate.toString());
        }
    }

    private void restoreDraft(Bundle state) {
        amountInput.setText(state.getString("draft_amount", ""));
        noteInput.setText(state.getString("draft_note", ""));
        typeGroup.check(
                state.getInt("draft_type", R.id.rbExpense)
        );
        selectCategory(state.getString("draft_category", ""));
        selectedDate = LocalDate.parse(
                state.getString("draft_date", LocalDate.now().toString())
        );
        updateDateButton();
    }
}