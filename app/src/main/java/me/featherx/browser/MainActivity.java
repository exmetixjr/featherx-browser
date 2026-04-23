package me.featherx.browser;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    // ── State ──────────────────────────────────────────────────────────────
    private WebView  webView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private boolean erudaActive  = false;
    private boolean desktopMode  = false;
    private String  savedCustomUA = "";

    private static final String HOME = "https://www.google.com";

    // ── Lifecycle ──────────────────────────────────────────────────────────
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView     = findViewById(R.id.webview);
        urlBar      = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);

        setupWebView();
        setupUrlBar();
        setupNavButtons();

        webView.loadUrl(HOME);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    // ── WebView Setup ──────────────────────────────────────────────────────
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebAppInterface(this, webView), "FeatherX");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                urlBar.setText(url);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                erudaActive = false; // eruda needs re-inject on new page
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                urlBar.setText(url);
                CookieManager.getInstance().flush();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progressBar.setProgress(p);
                if (p == 100) progressBar.setVisibility(View.GONE);
            }
            @Override
            public void onReceivedTitle(WebView v, String title) {
                // Could set toolbar title here
            }
        });
    }

    // ── URL Bar ────────────────────────────────────────────────────────────
    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                navigate(urlBar.getText().toString().trim());
                return true;
            }
            return false;
        });
    }

    private void navigate(String input) {
        if (input.isEmpty()) return;
        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.matches("^[\\w.-]+\\.[a-z]{2,}.*")) {
            url = "https://" + input;
        } else {
            url = "https://www.google.com/search?q=" + android.net.Uri.encode(input);
        }
        webView.loadUrl(url);
        // hide keyboard
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    // ── Navigation Buttons ─────────────────────────────────────────────────
    private void setupNavButtons() {
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        findViewById(R.id.btn_refresh).setOnClickListener(v -> webView.reload());
        findViewById(R.id.btn_home).setOnClickListener(v -> webView.loadUrl(HOME));
        findViewById(R.id.btn_tabs).setOnClickListener(v ->
            Toast.makeText(this, "Multi-tab coming in v2", Toast.LENGTH_SHORT).show()
        );
        findViewById(R.id.btn_tools).setOnClickListener(v -> openToolsSheet());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TOOLS BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════════════════
    private void openToolsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_tools, null);
        sheet.setContentView(root);
        sheet.getBehavior().setPeekHeight(560);

        TabLayout tabs    = root.findViewById(R.id.tool_tabs);
        FrameLayout frame = root.findViewById(R.id.tool_content);

        String[] tabNames = {"DevTools", "UA", "JS Inject", "Cookies", "Storage", "Snippets", "Runner"};
        for (String name : tabNames) tabs.addTab(tabs.newTab().setText(name));

        renderTab(frame, 0, sheet);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                renderTab(frame, tab.getPosition(), sheet);
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        sheet.show();
    }

    private void renderTab(FrameLayout frame, int idx, BottomSheetDialog sheet) {
        frame.removeAllViews();
        switch (idx) {
            case 0: frame.addView(tabDevTools()); break;
            case 1: frame.addView(tabUA());       break;
            case 2: frame.addView(tabJSInject()); break;
            case 3: frame.addView(tabCookies());  break;
            case 4: frame.addView(tabStorage());  break;
            case 5: frame.addView(tabSnippets()); break;
            case 6: frame.addView(tabRunner());   break;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(16), px(12), px(16), px(24));
        return l;
    }

    private Button btn(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFEFEFEF);
        b.setBackgroundColor(0xFF252525);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(4), 0, px(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    private Button dangerBtn(String label, View.OnClickListener click) {
        Button b = btn(label, click);
        b.setBackgroundColor(0xFF3D1A20);
        b.setTextColor(0xFFCF6679);
        return b;
    }

    private Button accentBtn(String label, View.OnClickListener click) {
        Button b = btn(label, click);
        b.setBackgroundColor(0xFF3D3665);
        b.setTextColor(0xFF7C6AF7);
        return b;
    }

    private EditText input(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(0xFF666666);
        et.setTextColor(0xFFEFEFEF);
        et.setBackgroundColor(0xFF252525);
        et.setPadding(px(12), px(10), px(12), px(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(4), 0, px(4));
        et.setLayoutParams(lp);
        return et;
    }

    private EditText multiInput(String hint, int minLines) {
        EditText et = input(hint);
        et.setMinLines(minLines);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setTypeface(android.graphics.Typeface.MONOSPACE);
        et.setTextSize(12f);
        return et;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF888888);
        tv.setTextSize(11f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(8), 0, px(2));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView output(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF00E676);
        tv.setTextSize(11f);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setBackgroundColor(0xFF0D0D0D);
        tv.setPadding(px(12), px(10), px(12), px(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(8), 0, px(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private int px(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }

    private void copy(String text, String label) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: DevTools
    // ═══════════════════════════════════════════════════════════════════════
    private View tabDevTools() {
        LinearLayout l = column();

        l.addView(label("ERUDA DEVTOOLS"));
        Button erudaBtn = accentBtn(
            erudaActive ? "Hide Eruda Panel" : "Inject & Show Eruda",
            null
        );
        erudaBtn.setOnClickListener(v -> {
            if (!erudaActive) {
                injectEruda(() -> {
                    erudaActive = true;
                    erudaBtn.setText("Hide Eruda Panel");
                });
            } else {
                webView.evaluateJavascript("eruda.hide();", null);
                erudaActive = false;
                erudaBtn.setText("Inject & Show Eruda");
            }
        });
        l.addView(erudaBtn);

        l.addView(label("DISPLAY MODE"));
        Button desktopBtn = btn(
            desktopMode ? "Switch to Mobile Mode" : "Switch to Desktop Mode",
            null
        );
        desktopBtn.setOnClickListener(v -> {
            desktopMode = !desktopMode;
            applyDesktopMode(desktopMode);
            desktopBtn.setText(desktopMode ? "Switch to Mobile Mode" : "Switch to Desktop Mode");
        });
        l.addView(desktopBtn);

        l.addView(label("PAGE SOURCE"));
        l.addView(btn("View Page Source", v ->
            webView.evaluateJavascript(
                "(function(){return document.documentElement.outerHTML;})()",
                src -> runOnUiThread(() -> {
                    if (src != null) {
                        // Open source in a new page
                        String encoded = android.util.Base64.encodeToString(
                            src.getBytes(), android.util.Base64.DEFAULT);
                        webView.loadUrl("data:text/plain;base64," + encoded);
                    }
                })
            )
        ));

        l.addView(label("PAGE INFO"));
        TextView infoView = output("Tap to load page info...");
        l.addView(infoView);
        l.addView(btn("Load Page Info", v ->
            webView.evaluateJavascript(
                "(function(){return JSON.stringify({" +
                "url:location.href," +
                "title:document.title," +
                "referrer:document.referrer," +
                "charset:document.characterSet," +
                "cookies:document.cookie.split(';').length," +
                "scripts:document.scripts.length," +
                "images:document.images.length," +
                "links:document.links.length" +
                "});})()",
                result -> runOnUiThread(() -> {
                    try {
                        JSONObject o = new JSONObject(result.replace("\\\"", "\"").replaceAll("^\"|\"$", ""));
                        StringBuilder sb = new StringBuilder();
                        sb.append("URL: ").append(o.optString("url")).append("\n");
                        sb.append("Title: ").append(o.optString("title")).append("\n");
                        sb.append("Charset: ").append(o.optString("charset")).append("\n");
                        sb.append("Cookies: ").append(o.optInt("cookies")).append("\n");
                        sb.append("Scripts: ").append(o.optInt("scripts")).append("\n");
                        sb.append("Images: ").append(o.optInt("images")).append("\n");
                        sb.append("Links: ").append(o.optInt("links"));
                        infoView.setText(sb.toString());
                    } catch (Exception e) {
                        infoView.setText(result);
                    }
                })
            )
        ));

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: User Agent
    // ═══════════════════════════════════════════════════════════════════════
    private View tabUA() {
        LinearLayout l = column();

        TextView current = output("Current UA:\n" + webView.getSettings().getUserAgentString());
        l.addView(current);
        l.addView(btn("Copy Current UA", v -> copy(webView.getSettings().getUserAgentString(), "UA")));

        l.addView(label("PRESETS"));
        String[] names  = UAProfiles.getNames();
        String[] values = UAProfiles.getValues();
        for (int i = 0; i < names.length; i++) {
            final String ua   = values[i];
            final String name = names[i];
            l.addView(btn(name, v -> {
                webView.getSettings().setUserAgentString(ua); // null resets to default
                webView.reload();
                current.setText("Current UA:\n" + webView.getSettings().getUserAgentString());
                Toast.makeText(this, "Applied: " + name, Toast.LENGTH_SHORT).show();
            }));
        }

        l.addView(label("CUSTOM UA"));
        EditText customInput = input("Paste custom user agent…");
        customInput.setText(savedCustomUA);
        l.addView(customInput);
        l.addView(accentBtn("Apply Custom UA", v -> {
            String ua = customInput.getText().toString().trim();
            if (ua.isEmpty()) { Toast.makeText(this, "UA empty", Toast.LENGTH_SHORT).show(); return; }
            savedCustomUA = ua;
            webView.getSettings().setUserAgentString(ua);
            webView.reload();
            current.setText("Current UA:\n" + webView.getSettings().getUserAgentString());
        }));

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: JS Inject
    // ═══════════════════════════════════════════════════════════════════════
    private View tabJSInject() {
        LinearLayout l = column();

        l.addView(label("INJECT JAVASCRIPT"));
        EditText code = multiInput("// your JS here…", 7);
        l.addView(code);

        TextView result = output("Result: (none)");
        l.addView(result);

        l.addView(accentBtn("Run", v -> {
            String js = code.getText().toString().trim();
            if (js.isEmpty()) return;
            webView.evaluateJavascript(
                "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'Error: '+e.message;}})()",
                val -> runOnUiThread(() -> result.setText("Result: " + val))
            );
        }));
        l.addView(btn("Clear", v -> {
            code.setText("");
            result.setText("Result: (none)");
        }));
        l.addView(btn("Copy Code", v -> copy(code.getText().toString(), "JS")));

        l.addView(label("QUICK INJECT"));
        String[][] quick = {
            {"Remove Ads (basic)",  "document.querySelectorAll('iframe,[class*=ad],[id*=ad]').forEach(e=>e.remove())"},
            {"Dark Mode",           "document.body.style.filter='invert(1) hue-rotate(180deg)'"},
            {"Scroll to Top",       "window.scrollTo(0,0)"},
            {"Scroll to Bottom",    "window.scrollTo(0,document.body.scrollHeight)"},
            {"Copy All Text",       "navigator.clipboard.writeText(document.body.innerText)"},
            {"Count Links",         "document.links.length"},
            {"Disable Images",      "document.querySelectorAll('img').forEach(e=>e.style.display='none')"},
        };
        for (String[] pair : quick) {
            l.addView(btn("▶ " + pair[0], v -> {
                code.setText(pair[1]);
                webView.evaluateJavascript(
                    "(function(){try{return String(eval(" + JSONObject.quote(pair[1]) + "));}catch(e){return'Error: '+e.message;}})()",
                    val -> runOnUiThread(() -> result.setText("Result: " + val))
                );
            }));
        }

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: Cookies
    // ═══════════════════════════════════════════════════════════════════════
    private View tabCookies() {
        LinearLayout l = column();

        String url     = webView.getUrl() != null ? webView.getUrl() : "";
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies == null) cookies = "(no cookies for this domain)";

        TextView cookieView = output(cookies);
        l.addView(cookieView);

        final String finalCookies = cookies;
        l.addView(btn("Copy All Cookies", v -> copy(finalCookies, "Cookies")));

        l.addView(label("SET COOKIE"));
        EditText nameIn  = input("name");
        EditText valueIn = input("value");
        EditText domIn   = input("domain (leave blank = current)");
        l.addView(nameIn);
        l.addView(valueIn);
        l.addView(domIn);
        l.addView(accentBtn("Set Cookie", v -> {
            String n = nameIn.getText().toString().trim();
            String val = valueIn.getText().toString().trim();
            String dom = domIn.getText().toString().trim();
            if (dom.isEmpty()) dom = android.net.Uri.parse(url).getHost();
            String cookieStr = n + "=" + val + "; domain=" + dom + "; path=/";
            CookieManager.getInstance().setCookie(url, cookieStr);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Cookie set", Toast.LENGTH_SHORT).show();
        }));

        l.addView(label("CLEAR"));
        l.addView(dangerBtn("Clear All Cookies", v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            cookieView.setText("(all cookies cleared)");
            Toast.makeText(this, "All cookies cleared", Toast.LENGTH_SHORT).show();
        }));
        l.addView(dangerBtn("Clear Session Cookies", v -> {
            CookieManager.getInstance().removeSessionCookies(null);
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Session cookies cleared", Toast.LENGTH_SHORT).show();
        }));

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: Storage (localStorage + sessionStorage)
    // ═══════════════════════════════════════════════════════════════════════
    private View tabStorage() {
        LinearLayout l = column();

        l.addView(label("LOCAL STORAGE"));
        TextView lsView = output("Loading…");
        l.addView(lsView);

        Runnable reloadLS = () -> webView.evaluateJavascript(
            "(function(){var r={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);r[k]=localStorage.getItem(k);}return JSON.stringify(r);})()",
            val -> runOnUiThread(() -> lsView.setText(val != null ? val : "(empty)"))
        );
        reloadLS.run();

        l.addView(btn("Refresh", v -> reloadLS.run()));

        l.addView(label("SET ITEM"));
        EditText keyIn = input("key");
        EditText valIn = input("value");
        l.addView(keyIn);
        l.addView(valIn);
        l.addView(accentBtn("Set localStorage Item", v -> {
            String k = keyIn.getText().toString().trim();
            String val = valIn.getText().toString().trim();
            if (k.isEmpty()) return;
            webView.evaluateJavascript(
                "localStorage.setItem(" + JSONObject.quote(k) + "," + JSONObject.quote(val) + ")",
                r -> runOnUiThread(reloadLS)
            );
        }));

        l.addView(label("REMOVE ITEM"));
        EditText delKey = input("key to remove");
        l.addView(delKey);
        l.addView(dangerBtn("Remove Item", v -> {
            String k = delKey.getText().toString().trim();
            if (k.isEmpty()) return;
            webView.evaluateJavascript("localStorage.removeItem(" + JSONObject.quote(k) + ")", r ->
                runOnUiThread(reloadLS)
            );
        }));

        l.addView(label("CLEAR"));
        l.addView(dangerBtn("Clear localStorage", v -> {
            webView.evaluateJavascript("localStorage.clear()", r -> runOnUiThread(reloadLS));
        }));
        l.addView(dangerBtn("Clear sessionStorage", v -> {
            webView.evaluateJavascript("sessionStorage.clear()", null);
            Toast.makeText(this, "sessionStorage cleared", Toast.LENGTH_SHORT).show();
        }));

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: Snippets
    // ═══════════════════════════════════════════════════════════════════════
    private View tabSnippets() {
        LinearLayout l = column();
        SnippetManager sm = new SnippetManager(this);

        l.addView(label("SAVE NEW SNIPPET"));
        EditText nameIn = input("Snippet name");
        EditText codeIn = multiInput("// JS code…", 4);
        l.addView(nameIn);
        l.addView(codeIn);
        l.addView(accentBtn("Save Snippet", v -> {
            String n = nameIn.getText().toString().trim();
            String c = codeIn.getText().toString().trim();
            if (n.isEmpty() || c.isEmpty()) {
                Toast.makeText(this, "Name and code required", Toast.LENGTH_SHORT).show();
                return;
            }
            sm.add(n, c);
            nameIn.setText("");
            codeIn.setText("");
            // Re-render snippets list
            refreshSnippetList(l, sm, 3);
            Toast.makeText(this, "Snippet saved!", Toast.LENGTH_SHORT).show();
        }));

        l.addView(label("SAVED SNIPPETS"));
        refreshSnippetList(l, sm, 3);

        return l;
    }

    private void refreshSnippetList(LinearLayout parent, SnippetManager sm, int listStartIdx) {
        // Remove all views after the static header area
        while (parent.getChildCount() > listStartIdx) {
            parent.removeViewAt(parent.getChildCount() - 1);
        }
        JSONArray all = sm.getAll();
        if (all.length() == 0) {
            parent.addView(label("(no snippets saved yet)"));
            return;
        }
        for (int i = 0; i < all.length(); i++) {
            try {
                JSONObject obj = all.getJSONObject(i);
                String name = obj.getString("name");
                String code = obj.getString("code");
                final int idx = i;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBackgroundColor(0xFF1A1A1A);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, px(3), 0, px(3));
                row.setLayoutParams(rowLp);
                row.setPadding(px(8), 0, px(8), 0);

                Button runBtn = new Button(this);
                runBtn.setText("▶  " + name);
                runBtn.setAllCaps(false);
                runBtn.setTextColor(0xFFEFEFEF);
                runBtn.setBackgroundColor(0x00000000);
                LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                runBtn.setLayoutParams(runLp);
                runBtn.setOnClickListener(v ->
                    webView.evaluateJavascript(
                        "(function(){try{return String(eval(" + JSONObject.quote(code) + "));}catch(e){return'Error: '+e.message;}})()",
                        result -> runOnUiThread(() -> Toast.makeText(this, result, Toast.LENGTH_LONG).show())
                    )
                );

                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFCF6679);
                delBtn.setBackgroundColor(0x00000000);
                delBtn.setOnClickListener(v -> {
                    sm.remove(idx);
                    refreshSnippetList(parent, sm, listStartIdx);
                });

                row.addView(runBtn);
                row.addView(delBtn);
                parent.addView(row);
            } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TAB: Code Runner (REPL)
    // ═══════════════════════════════════════════════════════════════════════
    private View tabRunner() {
        LinearLayout l = column();

        l.addView(label("JS REPL — runs in page context"));
        EditText code = multiInput("expression or statement…", 5);
        l.addView(code);

        TextView out = output("// output appears here");
        l.addView(out);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button runBtn = accentBtn("▶ Run", v -> {
            String js = code.getText().toString().trim();
            if (js.isEmpty()) return;
            String wrapped = "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'⚠ '+e.message;}}())";
            webView.evaluateJavascript(wrapped, result ->
                runOnUiThread(() -> {
                    String prev = out.getText().toString();
                    String cleaned = result.replaceAll("^\"|\"$", "").replace("\\n", "\n").replace("\\\"", "\"");
                    out.setText(prev + "\n> " + cleaned);
                })
            );
        });
        runBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clearBtn = btn("Clear", v -> out.setText("// output appears here"));
        clearBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(runBtn);
        row.addView(clearBtn);
        l.addView(row);
        l.addView(btn("Copy Output", v -> copy(out.getText().toString(), "Output")));

        l.addView(label("QUICK EXPRESSIONS"));
        String[][] exprs = {
            {"navigator.userAgent",     "navigator.userAgent"},
            {"window.innerWidth/Height","window.innerWidth + 'x' + window.innerHeight"},
            {"performance.now()",       "performance.now()"},
            {"document.readyState",     "document.readyState"},
            {"localStorage length",     "localStorage.length"},
        };
        for (String[] pair : exprs) {
            l.addView(btn(pair[0], v -> code.setText(pair[1])));
        }

        return l;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  ERUDA INJECT
    // ═══════════════════════════════════════════════════════════════════════
    private void injectEruda(Runnable onSuccess) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("eruda.min.js")));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) { sb.append(line); sb.append('\n'); }
                reader.close();
                String js = sb.toString();
                runOnUiThread(() ->
                    webView.evaluateJavascript(js, v1 ->
                        webView.evaluateJavascript("eruda.init();", v2 -> {
                            if (onSuccess != null) runOnUiThread(onSuccess);
                        })
                    )
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Eruda load failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DESKTOP MODE
    // ═══════════════════════════════════════════════════════════════════════
    private void applyDesktopMode(boolean desktop) {
        WebSettings s = webView.getSettings();
        if (desktop) {
            s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Safari/537.36");
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
        } else {
            s.setUserAgentString(null);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
        }
        webView.reload();
        Toast.makeText(this, desktop ? "Desktop mode ON" : "Mobile mode ON", Toast.LENGTH_SHORT).show();
    }
}
