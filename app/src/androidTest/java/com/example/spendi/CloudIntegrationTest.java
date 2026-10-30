package com.example.spendi;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.spendi.client.ApiClient;
import com.example.spendi.client.ApiException;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import static org.junit.Assert.*;

/** Explicit CLI-only integration test; requires the locally running API and its private token. */
@RunWith(AndroidJUnit4.class)
public class CloudIntegrationTest {
    @Test public void androidUsesRealBackendAndPreservesRoomData() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var arguments=InstrumentationRegistry.getArguments();
        String url=arguments.getString("baseUrl"), token=arguments.getString("accessToken");
        assertNotNull("Pass baseUrl and accessToken through the CLI test script",url);
        assertNotNull(token);
        var prefs=context.getSharedPreferences("cloud_connection",Context.MODE_PRIVATE);
        String previous=prefs.getString("encrypted",null);
        int originalCount=AppDatabase.getInstance(context).transactionDao().getAllTransactions().size();
        Transaction saved=null;
        try {
            ConnectionSettings.save(context,url,token);
            assertFalse(prefs.getString("encrypted","").contains(token));
            assertTrue("Encrypted credential round-trip failed",token.equals(ConnectionSettings.load(context).token));
            DataRepository repository=new DataRepository(context); assertTrue(repository.cloud());
            String key="android-cli-"+UUID.randomUUID();
            saved=new Transaction(125,"EXPENSE","Food",LocalDate.now().toString(),key);
            repository.save(saved,false,key); assertNotNull(saved.remoteId); assertEquals(1,saved.remoteVersion);
            Transaction replay=new Transaction(125,"EXPENSE","Food",saved.date,key);
            repository.save(replay,false,key); assertEquals(saved.remoteId,replay.remoteId);
            assertTrue(repository.list(YearMonth.now()).stream().anyMatch(entry -> key.equals(entry.note)));
            Transaction edit=repository.get(0,saved.remoteId); edit.amountMinor=250;
            repository.save(edit,true,null); assertEquals(2,edit.remoteVersion);
            assertEquals(250,repository.get(0,saved.remoteId).amountMinor);
            Transaction stale=replay; stale.amountMinor=999;
            try {repository.save(stale,true,null); fail("A stale edit must be rejected");}
            catch(ApiException expected) {assertEquals(409,expected.status);}
            assertNotNull(repository.summary(YearMonth.now()).balance);
            repository.delete(edit); saved=null;
            assertEquals(originalCount,AppDatabase.getInstance(context).transactionDao().getAllTransactions().size());
            ApiClient unavailable=new ApiClient("http://127.0.0.1:9",token,true);
            assertThrows(java.io.IOException.class,()->unavailable.list(YearMonth.now().toString()));
            assertEquals(originalCount,AppDatabase.getInstance(context).transactionDao().getAllTransactions().size());
        } finally {
            if(saved!=null && saved.remoteId!=null) {
                DataRepository repository=new DataRepository(context);
                try {repository.delete(repository.get(0,saved.remoteId));} catch(Exception ignored) { }
            }
            var editor=prefs.edit(); if(previous==null) editor.remove("encrypted"); else editor.putString("encrypted",previous); editor.commit();
        }
    }
}
