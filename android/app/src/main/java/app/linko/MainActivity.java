package app.linko;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.content.ContentValues;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.view.WindowManager;
import java.util.Collections;
import java.util.Locale;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ValueCallback;
import java.io.OutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private WebView wv;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;
    private PermissionRequest pending;
    private ValueCallback<Uri[]> fileCb;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#EEF2F6"));
        wv = new WebView(this);
        root.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        wv.addJavascriptInterface(new Bridge(), "LinkoNative");
        publishShortcut();
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(Intent.createChooser(i, "Linko"), 41);
                } catch (Exception e) { fileCb = null; cb.onReceiveValue(null); return false; }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        boolean wantsMic = false;
                        for (String r : req.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                        if (!wantsMic) { req.deny(); return; }
                        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            pending = req;
                            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
                        } else {
                            req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                        }
                    }
                });
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customCb = cb;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                wv.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                wv.setVisibility(View.VISIBLE);
                if (customCb != null) customCb.onCustomViewHidden();
            }
        });

        if (b != null) wv.restoreState(b); else wv.loadUrl(urlFor(getIntent()));
    }


    /** Lit une page web (sans les blocages CORS du navigateur) et renvoie son code a la page Linko. */
    private class Bridge {
        @JavascriptInterface
        public void fetchPost(final String id, final String url, final String body, final String ua) {
            new Thread(new Runnable() {
                public void run() {
                    String out = "";
                    try {
                        if (url.startsWith("https://")) {
                            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                            c.setRequestMethod("POST");
                            c.setDoOutput(true);
                            c.setConnectTimeout(8000);
                            c.setReadTimeout(12000);
                            c.setRequestProperty("Content-Type", "application/json");
                            c.setRequestProperty("User-Agent", (ua != null && ua.length() > 0) ? ua : "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                            c.setRequestProperty("Accept-Language", "fr,en;q=0.8");
                            byte[] data = body.getBytes("UTF-8");
                            c.getOutputStream().write(data);
                            int code = c.getResponseCode();
                            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                            if (in != null) {
                                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                                byte[] buf = new byte[16384];
                                int n, total = 0;
                                while ((n = in.read(buf)) > 0 && total < 4000000) { bo.write(buf, 0, n); total += n; }
                                out = bo.toString("UTF-8");
                            }
                        }
                    } catch (Exception ignored) { }
                    final String js = "window.__nativeFetched(" + JSONObject.quote(id) + "," + JSONObject.quote(out) + ")";
                    runOnUiThread(new Runnable() { public void run() { wv.evaluateJavascript(js, null); } });
                }
            }).start();
        }

        /** Enregistre un fichier texte dans Telechargements. Renvoie le nom si c'est bon, sinon "". */
        @JavascriptInterface
        public String saveFile(String name, String mime, String text) {
            try {
                if (Build.VERSION.SDK_INT < 29) return "";
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u == null) return "";
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(text.getBytes("UTF-8"));
                o.close();
                return name;
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public void keepOn(final boolean on) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        @JavascriptInterface
        public void openExternal(final String url) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void fetchPage(final String id, final String url) {
            new Thread(new Runnable() {
                public void run() {
                    String out = "";
                    try {
                        String cur = url;
                        for (int hop = 0; hop < 6; hop++) {
                            if (!cur.startsWith("http")) break;
                            HttpURLConnection c = (HttpURLConnection) new URL(cur.startsWith("http://") ? "https://" + cur.substring(7) : cur).openConnection();
                            c.setInstanceFollowRedirects(false);
                            c.setConnectTimeout(8000);
                            c.setReadTimeout(10000);
                            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                            c.setRequestProperty("Accept-Language", "fr,en;q=0.8");
                            int code = c.getResponseCode();
                            if (code >= 300 && code < 400) {
                                String loc = c.getHeaderField("Location");
                                if (loc == null) break;
                                cur = new URL(new URL(cur), loc).toString();
                                continue;
                            }
                            InputStream in = c.getInputStream();
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[16384];
                            int n, total = 0;
                            while ((n = in.read(buf)) > 0 && total < 3000000) { bo.write(buf, 0, n); total += n; }
                            out = bo.toString("UTF-8");
                            break;
                        }
                    } catch (Exception ignored) { }
                    final String js = "window.__nativeFetched(" + JSONObject.quote(id) + "," + JSONObject.quote(out) + ")";
                    runOnUiThread(new Runnable() { public void run() { wv.evaluateJavascript(js, null); } });
                }
            }).start();
        }
    }

    /** Raccourci de partage direct : "Linko > Recettes" dans le menu Partager (Android 10+ le met en tete). */
    private void publishShortcut() {
        try {
            if (Build.VERSION.SDK_INT < 25) return;
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm == null) return;
            Intent in = new Intent(this, MainActivity.class);
            in.setAction(Intent.ACTION_VIEW);
            String label = "fr".equals(Locale.getDefault().getLanguage()) ? "Recettes" : "Recipes";
            ShortcutInfo si = new ShortcutInfo.Builder(this, "recipes")
                    .setShortLabel(label)
                    .setLongLabel("Linko : " + label)
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setCategories(Collections.singleton("app.linko.category.RECIPES"))
                    .setIntent(in)
                    .build();
            sm.setDynamicShortcuts(Collections.singletonList(si));
        } catch (Exception ignored) { }
    }

    private String urlFor(Intent i) {
        if (i != null && Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null && "app.linko".equals(i.getData().getScheme())) {
            String frag = i.getData().getEncodedFragment();
            return HOME + (frag != null ? "#" + frag : "");
        }
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            String title = i.getStringExtra(Intent.EXTRA_SUBJECT);
            Uri.Builder u = Uri.parse(HOME).buildUpon();
            if (text != null) u.appendQueryParameter("text", text);
            if (title != null) u.appendQueryParameter("title", title);
            if ("recipes".equals(i.getStringExtra(Intent.EXTRA_SHORTCUT_ID))) u.appendQueryParameter("rec", "1");
            return u.build().toString();
        }
        return HOME;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        wv.loadUrl(urlFor(i));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] g) {
        if (code == 7 && pending != null) {
            if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) pending.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pending.deny();
            pending = null;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 41) {
            if (fileCb != null) {
                Uri[] r = null;
                if (res == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
                fileCb.onReceiveValue(r);
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle o) {
        super.onSaveInstanceState(o);
        wv.saveState(o);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { wv.getWebChromeClient().onHideCustomView(); return; }
        if (wv.canGoBack()) wv.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onPause() { super.onPause(); wv.onPause(); }

    @Override
    protected void onResume() { super.onResume(); wv.onResume(); }
}
