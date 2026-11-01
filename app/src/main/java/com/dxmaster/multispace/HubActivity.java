package com.dxmaster.multispace;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class HubActivity extends Activity {
    private static final String UA_DESKTOP = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    private static final String LOGIN = "https://m.facebook.com/login/";
    private static final String HOME = "https://www.facebook.com/messages/";

    private final WebView[] webs = new WebView[6];
    private final TextView[] tabs = new TextView[6];
    private final boolean[] switched = new boolean[6];
    private FrameLayout holder;
    private int count;
    private SharedPreferences sp;
    private String js = "";
    private boolean boostOn;
    private AudioManager am;
    private BroadcastReceiver rx;
    private AudioPanel panel;

    private int dp(int v) { return AudioPanel.dp(this, v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("settings", 0);
        count = sp.getInt("count", 2);
        boostOn = sp.getBoolean("on", true);
        js = readAsset("dx.js") + "\n" + readAsset("auto.js");
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout tabRow = new LinearLayout(this);
        for (int i = 1; i <= count; i++) {
            final int slot = i;
            TextView t = AudioPanel.button(this, "" + i, 0xFF37474F);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            t.setOnClickListener(v -> open(slot));
            tabs[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabRow.addView(t, lp);
        }
        top.addView(tabRow, new LinearLayout.LayoutParams(0, -2, 1));

        TextView bSound = AudioPanel.button(this, "🔊", 0xFF1565C0);
        TextView bReload = AudioPanel.button(this, "🔄", 0xFF37474F);
        TextView bClose = AudioPanel.button(this, "✖", 0xFFE0314B);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.leftMargin = dp(4);
        top.addView(bSound, bl);
        top.addView(bReload, new LinearLayout.LayoutParams(bl));
        top.addView(bClose, new LinearLayout.LayoutParams(bl));
        root.addView(top);

        holder = new FrameLayout(this);
        root.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1));

        int[] init = new int[5];
        for (int i = 0; i < 5; i++) init[i] = sp.getInt(AudioPanel.KEYS[i], 50);
        panel = new AudioPanel(this, init, (k, v) -> {
            sp.edit().putInt(k, v).apply();
            for (WebView w : webs) {
                if (w != null) w.evaluateJavascript("window.__dx&&__dx.set('" + k + "'," + v + ")", null);
            }
        });
        panel.root.setVisibility(View.GONE);
        root.addView(panel.root, new LinearLayout.LayoutParams(-1, -2));

        bSound.setOnClickListener(v -> panel.root.setVisibility(
                panel.root.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
        bReload.setOnClickListener(v -> {
            for (WebView w : webs) if (w != null && w.getAlpha() > 0.5f) w.reload();
        });
        bClose.setOnClickListener(v -> stopAll());

        setContentView(root);

        ArrayList<String> need = new ArrayList<>();
        need.add(Manifest.permission.RECORD_AUDIO);
        need.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(need.toArray(new String[0]), 1);
        startKeepAlive();

        rx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) { stopAll(); }
        };
        IntentFilter f = new IntentFilter(MainActivity.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(rx, f);

        open(getIntent().getIntExtra("slot", 0) + 1);
    }

    private void startKeepAlive() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Intent s = new Intent(this, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);
        } catch (Exception ignored) {}
    }

    private WebView makeWeb(final int slot) {
        WebView w = new WebView(this);
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                ProfileStore.getInstance().getOrCreateProfile("dx" + slot);
                WebViewCompat.setProfile(w, "dx" + slot);
            } else {
                Toast.makeText(this, "WebView আপডেট করো, নইলে সব ট্যাবে একই লগইন থাকবে", Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {}

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(UA_MOBILE);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        } catch (Exception ignored) {}

        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) { inject(v); }

            @Override
            public void onPageFinished(WebView v, String url) {
                inject(v);
                checkLogin(v, slot);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    Toast.makeText(HubActivity.this, "লোড হয়নি: " + e.getDescription(), Toast.LENGTH_LONG).show();
                }
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest r) {
                runOnUiThread(() -> r.grant(r.getResources()));
            }
        });
        holder.addView(w, new FrameLayout.LayoutParams(-1, -1));
        w.loadUrl(LOGIN);
        return w;
    }

    private void checkLogin(final WebView v, final int slot) {
        if (switched[slot]) return;
        v.evaluateJavascript("(document.cookie.indexOf('c_user=')>-1)", val -> {
            if ("true".equals(val) && !switched[slot]) {
                switched[slot] = true;
                v.getSettings().setUserAgentString(UA_DESKTOP);
                v.loadUrl(HOME);
            }
        });
    }

    private void open(int slot) {
        if (slot < 1 || slot > count) slot = 1;
        if (webs[slot] == null) webs[slot] = makeWeb(slot);
        for (int i = 1; i <= 5; i++) {
            if (webs[i] != null) webs[i].setAlpha(i == slot ? 1f : 0f);
            if (tabs[i] != null) tabs[i].setAlpha(i == slot ? 1f : 0.5f);
        }
        webs[slot].bringToFront();
        webs[slot].requestFocus();
    }

    private void inject(WebView w) {
        if (!boostOn) return;
        w.evaluateJavascript(js, null);
        StringBuilder sb = new StringBuilder("window.__dx&&__dx.setAll({");
        for (String k : AudioPanel.KEYS) sb.append(k).append(':').append(sp.getInt(k, 50)).append(',');
        sb.append("});");
        w.evaluateJavascript(sb.toString(), null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            return o.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void stopAll() {
        try { stopService(new Intent(this, KeepAliveService.class)); } catch (Exception ignored) {}
        try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
        finishAndRemoveTask();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        open(i.getIntExtra("slot", 0) + 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        startKeepAlive();
        for (WebView w : webs) if (w != null) w.reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (boostOn) {
            try {
                am.setSpeakerphoneOn(true);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        for (WebView w : webs) {
            if (w != null && w.getAlpha() > 0.5f && w.canGoBack()) {
                w.goBack();
                return;
            }
        }
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) {}
        try { am.setSpeakerphoneOn(false); } catch (Exception ignored) {}
        for (int i = 1; i <= 5; i++) {
            if (webs[i] != null) {
                holder.removeView(webs[i]);
                webs[i].destroy();
                webs[i] = null;
            }
        }
        super.onDestroy();
    }
}
