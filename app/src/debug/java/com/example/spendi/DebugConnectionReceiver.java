package com.example.spendi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only CLI setup. The manifest requires the system/shell DUMP permission. */
public final class DebugConnectionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        try {
            if(intent.getBooleanExtra("local",false)) ConnectionSettings.local(context);
            else ConnectionSettings.save(context,intent.getStringExtra("base_url"),intent.getStringExtra("access_token"));
            setResultCode(1);
        } catch(Exception exception) { setResultCode(0); }
    }
}
