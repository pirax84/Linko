package app.linko;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

    private String urlFor(Intent i) {
        if (i != null && Intent.ACTION_SEND.equals(i.getAction())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            String title = i.getStringExtra(Intent.EXTRA_SUBJECT);
            Uri.Builder u = Uri.parse(HOME).buildUpon();
            if (text != null) u.appendQueryParameter("text", text);
            if (title != null) u.appendQueryParameter("title", title);
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
