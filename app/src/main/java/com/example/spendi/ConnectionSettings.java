package com.example.spendi;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import com.example.spendi.client.ApiClient;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials are encrypted by an app-scoped Android Keystore key; no token is embedded in the APK. */
public final class ConnectionSettings {
    private static final String ALIAS="spendi.cloud.connection.v1";
    public static final class Snapshot {
        public final String url, token;
        public final boolean invalid;
        private Snapshot(String url,String token,boolean invalid) { this.url=url; this.token=token; this.invalid=invalid; }
        public boolean cloud() { return url!=null || invalid; }
    }
    private ConnectionSettings() { }
    public static Snapshot load(Context context) {
        String stored=context.getSharedPreferences("cloud_connection",Context.MODE_PRIVATE).getString("encrypted",null);
        if(stored==null) return new Snapshot(null,null,false);
        try {
            String[] fields=stored.split(":",2);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(fields[0],Base64.NO_WRAP)));
            JSONObject json=new JSONObject(new String(cipher.doFinal(Base64.decode(fields[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
            return new Snapshot(json.getString("url"),json.getString("token"),false);
        } catch(Exception exception) { return new Snapshot(null,null,true); }
    }
    public static void save(Context context,String url,String token) throws Exception {
        new ApiClient(url,token,BuildConfig.DEBUG);
        JSONObject json=new JSONObject().put("url",url.trim()).put("token",token.trim());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        String stored=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"
                +Base64.encodeToString(cipher.doFinal(json.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences("cloud_connection",Context.MODE_PRIVATE).edit().putString("encrypted",stored).commit()) {
            throw new IllegalStateException("Could not save the connection");
        }
    }
    public static void local(Context context) {
        if(!context.getSharedPreferences("cloud_connection",Context.MODE_PRIVATE).edit().clear().commit()) {
            throw new IllegalStateException("Could not reset the connection");
        }
    }
    private static synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
