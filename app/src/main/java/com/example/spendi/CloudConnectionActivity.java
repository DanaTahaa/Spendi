package com.example.spendi;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.spendi.client.ApiClient;
import java.time.YearMonth;

public final class CloudConnectionActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(24*getResources().getDisplayMetrics().density); layout.setPadding(padding,padding,padding,padding);
        TextView title=new TextView(this); title.setText(R.string.cloud_connection_title); title.setTextSize(24); layout.addView(title);
        TextView explanation=new TextView(this); explanation.setText(R.string.cloud_connection_explanation); layout.addView(explanation);
        ConnectionSettings.Snapshot saved=ConnectionSettings.load(this);
        EditText url=new EditText(this); url.setHint(R.string.cloud_url_hint); url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(saved.url!=null?saved.url:(BuildConfig.DEBUG?"http://10.0.2.2:8080":"")); layout.addView(url);
        EditText token=new EditText(this); token.setHint(R.string.cloud_token_hint); token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD); token.setText(saved.token); layout.addView(token);
        token.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        token.setSaveEnabled(false);
        TextView status=new TextView(this); layout.addView(status);
        Button connect=new Button(this); connect.setText(R.string.connect_cloud); layout.addView(connect);
        Button local=new Button(this); local.setText(R.string.use_local_data); layout.addView(local);
        setContentView(layout);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(layout,(view,insets) -> {
            var bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            view.setPadding(padding+bars.left,padding+bars.top,padding+bars.right,padding+bars.bottom); return insets;
        });
        local.setOnClickListener(view -> { ConnectionSettings.local(this); finish(); });
        connect.setOnClickListener(view -> {
            String endpoint=url.getText().toString().trim(), credential=token.getText().toString().trim();
            connect.setEnabled(false); local.setEnabled(false); status.setText(R.string.checking_cloud);
            AppDatabase.databaseExecutor.execute(() -> {
                try {
                    new ApiClient(endpoint,credential,BuildConfig.DEBUG).summary(YearMonth.now().toString());
                    runOnUiThread(() -> {
                        if(isFinishing()||isDestroyed()) return;
                        try { ConnectionSettings.save(this,endpoint,credential); finish(); }
                        catch(Exception exception) { status.setText(R.string.connection_save_failed); connect.setEnabled(true); local.setEnabled(true); }
                    });
                } catch(Exception exception) {
                    runOnUiThread(() -> { if(!isFinishing()&&!isDestroyed()) {
                        status.setText(DataRepository.message(exception)); connect.setEnabled(true); local.setEnabled(true);
                    }});
                }
            });
        });
    }
}
