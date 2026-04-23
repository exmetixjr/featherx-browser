package me.featherx.browser;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TabManager tabManager;
    private WebView webView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private boolean devToolsActive = false;
    private boolean desktopMode = false;
    private String savedCustomUA = "";
    private boolean incognitoMode = false;
    private int currentSearchEngine = 0;
    private static final String[] SEARCH_ENGINES = {
        "https://www.google.com/search?q=",
        "https://www.bing.com/search?q=",
        "https://duckduckgo.com/?q=",
        "https://search.yahoo.com/search?p="
    };
    private static final String[] SEARCH_NAMES = {"Google", "Bing", "DuckDuckGo", "Yahoo"};
    private static final String HOME = "https://www.google.com";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);

        tabManager = new TabManager(this);
        tabManager.setListener(new TabManager.TabManagerListener() {
            @Override
            public void onTabChanged(TabManager.Tab tab, WebView newWebView) {
                ViewGroup parent = (ViewGroup) webView.getParent();
                int index = parent.indexOfChild(webView);
                parent.removeView(webView);
                webView = newWebView;
                parent.addView(webView, index);
                urlBar.setText(tab.url);
                devToolsActive = false;
            }
        });

        setupWebViewForMain();
        setupUrlBar();
        setupNavButtons();
        webView.loadUrl(HOME);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebViewForMain() {
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
                devToolsActive = false;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                progressBar.setVisibility(View.GONE);
                urlBar.setText(url);
                CookieManager.getInstance().flush();
                TabManager.Tab tab = tabManager.getCurrentTab();
                if (tab != null) {
                    tab.url = url;
                    tab.title = v.getTitle();
                }
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
                TabManager.Tab tab = tabManager.getCurrentTab();
                if (tab != null) tab.title = title;
            }
        });
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean go = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN);
                if (go) {
                    navigate(urlBar.getText().toString().trim());
                    return true;
                }
                return false;
            }
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
            url = SEARCH_ENGINES[currentSearchEngine] + android.net.Uri.encode(input);
        }
        webView.loadUrl(url);
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void setupNavButtons() {
        findViewById(R.id.btn_reload_top).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.reload();
            }
        });
        findViewById(R.id.btn_tabs_top).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabSwitcher();
            }
        });
        findViewById(R.id.btn_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl(HOME);
            }
        });
        findViewById(R.id.btn_tools).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openToolsSheet();
            }
        });
    }

    private void showTabSwitcher() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tabs (" + tabManager.getTabCount() + ")");

        final List<TabManager.Tab> tabs = tabManager.getAllTabs();
        String[] items = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            TabManager.Tab tab = tabs.get(i);
            String title = (tab.title != null && !tab.title.isEmpty()) ? tab.title : "New Tab";
            String url = (tab.url != null && !tab.url.isEmpty()) ? tab.url : "about:blank";
            items[i] = (tab.isIncognito ? "[Incognito] " : "") + title + "\n" + url;
        }

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                tabManager.switchToTab(which);
            }
        });
        builder.setPositiveButton("New Tab", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                TabManager.Tab newTab = tabManager.createTab(incognitoMode);
                WebView newWebView = new WebView(MainActivity.this);
                WebSettings ns = newWebView.getSettings();
                ns.setJavaScriptEnabled(true);
                ns.setDomStorageEnabled(true);
                ns.setDatabaseEnabled(true);
                ns.setAllowFileAccess(true);
                ns.setSupportZoom(true);
                ns.setBuiltInZoomControls(true);
                ns.setDisplayZoomControls(false);
                ns.setUseWideViewPort(true);
                ns.setLoadWithOverviewMode(true);
                ns.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                ns.setMediaPlaybackRequiresUserGesture(false);
                if (incognitoMode) {
                    ns.setCacheMode(WebSettings.LOAD_NO_CACHE);
                    CookieManager.getInstance().setAcceptCookie(false);
                } else {
                    CookieManager.getInstance().setAcceptCookie(true);
                    CookieManager.getInstance().setAcceptThirdPartyCookies(newWebView, true);
                }
                newWebView.addJavascriptInterface(new WebAppInterface(MainActivity.this, newWebView), "FeatherX");
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView v, String url, android.graphics.Bitmap fav) {
                        TabManager.Tab t = tabManager.getCurrentTab();
                        if (t != null) {
                            t.url = url;
                        }
                    }
                    @Override
                    public void onPageFinished(WebView v, String url) {
                        TabManager.Tab t = tabManager.getCurrentTab();
                        if (t != null) {
                            t.url = url;
                            t.title = v.getTitle();
                        }
                    }
                });
                tabManager.initWebViewForTab(newTab, newWebView);
                newWebView.loadUrl(HOME);
            }
        });
        builder.setNegativeButton("Close Tab", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                tabManager.closeTab(tabManager.getCurrentIndex());
            }
        });
        builder.setNeutralButton("Incognito: " + (incognitoMode ? "ON" : "OFF"), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                incognitoMode = !incognitoMode;
                Toast.makeText(MainActivity.this, "New tabs will be " + (incognitoMode ? "incognito" : "normal"), Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }

    private void openToolsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_tools, null);
        sheet.setContentView(root);
        sheet.getBehavior().setPeekHeight(560);

        TabLayout tabs = root.findViewById(R.id.tool_tabs);
        final FrameLayout frame = root.findViewById(R.id.tool_content);

        String[] tabNames = {"DevTools", "Search", "UA", "JS Inject", "Cookies", "Storage", "Snippets", "Runner"};
        for (String name : tabNames) tabs.addTab(tabs.newTab().setText(name));

        renderTab(frame, 0, sheet);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                renderTab(frame, tab.getPosition(), sheet);
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
        sheet.show();
    }

    private void renderTab(FrameLayout frame, int idx, BottomSheetDialog sheet) {
        frame.removeAllViews();
        switch (idx) {
            case 0: frame.addView(tabDevTools()); break;
            case 1: frame.addView(tabSearch()); break;
            case 2: frame.addView(tabUA()); break;
            case 3: frame.addView(tabJSInject()); break;
            case 4: frame.addView(tabCookies()); break;
            case 5: frame.addView(tabStorage()); break;
            case 6: frame.addView(tabSnippets()); break;
            case 7: frame.addView(tabRunner()); break;
        }
    }

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

    private View tabDevTools() {
        LinearLayout l = column();
        l.addView(label("DEVTOOLS (Eruda)"));
        Button devToolsBtn = accentBtn(
            devToolsActive ? "Hide DevTools" : "Show DevTools",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!devToolsActive) {
                        injectDevTools(new Runnable() {
                            @Override
                            public void run() {
                                devToolsActive = true;
                                ((Button)v).setText("Hide DevTools");
                            }
                        });
                    } else {
                        webView.evaluateJavascript("if(window.eruda) eruda.hide();", null);
                        devToolsActive = false;
                        ((Button)v).setText("Show DevTools");
                    }
                }
            }
        );
        l.addView(devToolsBtn);

        l.addView(label("DISPLAY MODE"));
        Button desktopBtn = btn(
            desktopMode ? "Switch to Mobile Mode" : "Switch to Desktop Mode",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    desktopMode = !desktopMode;
                    applyDesktopMode(desktopMode);
                    ((Button)v).setText(desktopMode ? "Switch to Mobile Mode" : "Switch to Desktop Mode");
                }
            }
        );
        l.addView(desktopBtn);

        l.addView(label("PAGE SOURCE"));
        l.addView(btn("View Page Source", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.evaluateJavascript(
                    "(function(){return document.documentElement.outerHTML;})()",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String src) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (src != null) {
                                        String encoded = android.util.Base64.encodeToString(
                                            src.getBytes(), android.util.Base64.DEFAULT);
                                        webView.loadUrl("data:text/plain;base64," + encoded);
                                    }
                                }
                            });
                        }
                    }
                );
            }
        }));

        l.addView(label("PAGE INFO"));
        TextView infoView = output("Tap to load page info...");
        l.addView(infoView);
        l.addView(btn("Load Page Info", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
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
                                }
                            });
                        }
                    }
                );
            }
        }));

        return l;
    }

    private View tabSearch() {
        LinearLayout l = column();
        l.addView(label("SEARCH ENGINE"));
        for (int i = 0; i < SEARCH_NAMES.length; i++) {
            final int idx = i;
            Button b = btn(SEARCH_NAMES[i] + (i == currentSearchEngine ? " ✓" : ""),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        currentSearchEngine = idx;
                        Toast.makeText(MainActivity.this, "Search engine: " + SEARCH_NAMES[idx], Toast.LENGTH_SHORT).show();
                        renderTab((FrameLayout)((View)v).getParent().getParent(), 1, null);
                    }
                }
            );
            if (i == currentSearchEngine) {
                b.setBackgroundColor(0xFF3D3665);
            }
            l.addView(b);
        }
        return l;
    }

    private View tabUA() {
        LinearLayout l = column();
        TextView current = output("Current UA:\n" + webView.getSettings().getUserAgentString());
        l.addView(current);
        l.addView(btn("Copy Current UA", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(webView.getSettings().getUserAgentString(), "UA");
            }
        }));
        l.addView(label("PRESETS"));
        String[] names = UAProfiles.getNames();
        String[] values = UAProfiles.getValues();
        for (int i = 0; i < names.length; i++) {
            final String ua = values[i];
            final String name = names[i];
            l.addView(btn(name, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    webView.getSettings().setUserAgentString(ua);
                    webView.reload();
                    current.setText("Current UA:\n" + webView.getSettings().getUserAgentString());
                    Toast.makeText(MainActivity.this, "Applied: " + name, Toast.LENGTH_SHORT).show();
                }
            }));
        }
        l.addView(label("CUSTOM UA"));
        final EditText customInput = input("Paste custom user agent…");
        customInput.setText(savedCustomUA);
        l.addView(customInput);
        l.addView(accentBtn("Apply Custom UA", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ua = customInput.getText().toString().trim();
                if (ua.isEmpty()) { Toast.makeText(MainActivity.this, "UA empty", Toast.LENGTH_SHORT).show(); return; }
                savedCustomUA = ua;
                webView.getSettings().setUserAgentString(ua);
                webView.reload();
                current.setText("Current UA:\n" + webView.getSettings().getUserAgentString());
            }
        }));
        return l;
    }

    private View tabJSInject() {
        LinearLayout l = column();
        l.addView(label("INJECT JAVASCRIPT"));
        final EditText code = multiInput("// your JS here…", 7);
        l.addView(code);
        final TextView result = output("Result: (none)");
        l.addView(result);
        l.addView(accentBtn("Run", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String js = code.getText().toString().trim();
                if (js.isEmpty()) return;
                webView.evaluateJavascript(
                    "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'Error: '+e.message;}})()",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String val) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    result.setText("Result: " + val);
                                }
                            });
                        }
                    }
                );
            }
        }));
        l.addView(btn("Clear", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                code.setText("");
                result.setText("Result: (none)");
            }
        }));
        l.addView(btn("Copy Code", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(code.getText().toString(), "JS");
            }
        }));
        l.addView(label("QUICK INJECT"));
        String[][] quick = {
            {"Remove Ads (basic)", "document.querySelectorAll('iframe,[class*=ad],[id*=ad]').forEach(e=>e.remove())"},
            {"Dark Mode", "document.body.style.filter='invert(1) hue-rotate(180deg)'"},
            {"Scroll to Top", "window.scrollTo(0,0)"},
            {"Scroll to Bottom", "window.scrollTo(0,document.body.scrollHeight)"},
            {"Copy All Text", "navigator.clipboard.writeText(document.body.innerText)"},
            {"Count Links", "document.links.length"},
            {"Disable Images", "document.querySelectorAll('img').forEach(e=>e.style.display='none')"},
        };
        for (final String[] pair : quick) {
            l.addView(btn("▶ " + pair[0], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    code.setText(pair[1]);
                    webView.evaluateJavascript(
                        "(function(){try{return String(eval(" + JSONObject.quote(pair[1]) + "));}catch(e){return'Error: '+e.message;}})()",
                        new android.webkit.ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String val) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        result.setText("Result: " + val);
                                    }
                                });
                            }
                        }
                    );
                }
            }));
        }
        return l;
    }

    private View tabCookies() {
        LinearLayout l = column();
        String url = webView.getUrl() != null ? webView.getUrl() : "";
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies == null) cookies = "(no cookies for this domain)";
        final TextView cookieView = output(cookies);
        l.addView(cookieView);
        final String finalCookies = cookies;
        l.addView(btn("Copy All Cookies", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(finalCookies, "Cookies");
            }
        }));
        l.addView(label("SET COOKIE"));
        final EditText nameIn = input("name");
        final EditText valueIn = input("value");
        final EditText domIn = input("domain (leave blank = current)");
        l.addView(nameIn);
        l.addView(valueIn);
        l.addView(domIn);
        l.addView(accentBtn("Set Cookie", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = nameIn.getText().toString().trim();
                String val = valueIn.getText().toString().trim();
                String dom = domIn.getText().toString().trim();
                if (dom.isEmpty()) dom = android.net.Uri.parse(url).getHost();
                String cookieStr = n + "=" + val + "; domain=" + dom + "; path=/";
                CookieManager.getInstance().setCookie(url, cookieStr);
                CookieManager.getInstance().flush();
                Toast.makeText(MainActivity.this, "Cookie set", Toast.LENGTH_SHORT).show();
            }
        }));
        l.addView(label("CLEAR"));
        l.addView(dangerBtn("Clear All Cookies", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CookieManager.getInstance().removeAllCookies(null);
                CookieManager.getInstance().flush();
                cookieView.setText("(all cookies cleared)");
                Toast.makeText(MainActivity.this, "All cookies cleared", Toast.LENGTH_SHORT).show();
            }
        }));
        l.addView(dangerBtn("Clear Session Cookies", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CookieManager.getInstance().removeSessionCookies(null);
                CookieManager.getInstance().flush();
                Toast.makeText(MainActivity.this, "Session cookies cleared", Toast.LENGTH_SHORT).show();
            }
        }));
        return l;
    }

    private View tabStorage() {
        LinearLayout l = column();
        l.addView(label("LOCAL STORAGE"));
        final TextView lsView = output("Loading…");
        l.addView(lsView);
        final Runnable reloadLS = new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(
                    "(function(){var r={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);r[k]=localStorage.getItem(k);}return JSON.stringify(r);})()",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String val) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    lsView.setText(val != null ? val : "(empty)");
                                }
                            });
                        }
                    }
                );
            }
        };
        reloadLS.run();
        l.addView(btn("Refresh", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reloadLS.run();
            }
        }));
        l.addView(label("SET ITEM"));
        final EditText keyIn = input("key");
        final EditText valIn = input("value");
        l.addView(keyIn);
        l.addView(valIn);
        l.addView(accentBtn("Set localStorage Item", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String k = keyIn.getText().toString().trim();
                String val = valIn.getText().toString().trim();
                if (k.isEmpty()) return;
                webView.evaluateJavascript(
                    "localStorage.setItem(" + JSONObject.quote(k) + "," + JSONObject.quote(val) + ")",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String r) {
                            runOnUiThread(reloadLS);
                        }
                    }
                );
            }
        }));
        l.addView(label("REMOVE ITEM"));
        final EditText delKey = input("key to remove");
        l.addView(delKey);
        l.addView(dangerBtn("Remove Item", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String k = delKey.getText().toString().trim();
                if (k.isEmpty()) return;
                webView.evaluateJavascript("localStorage.removeItem(" + JSONObject.quote(k) + ")",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String r) {
                            runOnUiThread(reloadLS);
                        }
                    }
                );
            }
        }));
        l.addView(label("CLEAR"));
        l.addView(dangerBtn("Clear localStorage", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.evaluateJavascript("localStorage.clear()",
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String r) {
                            runOnUiThread(reloadLS);
                        }
                    }
                );
            }
        }));
        l.addView(dangerBtn("Clear sessionStorage", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.evaluateJavascript("sessionStorage.clear()", null);
                Toast.makeText(MainActivity.this, "sessionStorage cleared", Toast.LENGTH_SHORT).show();
            }
        }));
        return l;
    }

    private View tabSnippets() {
        LinearLayout l = column();
        final SnippetManager sm = new SnippetManager(this);
        l.addView(label("SAVE NEW SNIPPET"));
        final EditText nameIn = input("Snippet name");
        final EditText codeIn = multiInput("// JS code…", 4);
        l.addView(nameIn);
        l.addView(codeIn);
        l.addView(accentBtn("Save Snippet", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = nameIn.getText().toString().trim();
                String c = codeIn.getText().toString().trim();
                if (n.isEmpty() || c.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Name and code required", Toast.LENGTH_SHORT).show();
                    return;
                }
                sm.add(n, c);
                nameIn.setText("");
                codeIn.setText("");
                refreshSnippetList(l, sm, 3);
                Toast.makeText(MainActivity.this, "Snippet saved!", Toast.LENGTH_SHORT).show();
            }
        }));
        l.addView(label("SAVED SNIPPETS"));
        refreshSnippetList(l, sm, 3);
        return l;
    }

    private void refreshSnippetList(LinearLayout parent, SnippetManager sm, int listStartIdx) {
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
                final JSONObject obj = all.getJSONObject(i);
                String name = obj.getString("name");
                final String code = obj.getString("code");
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
                runBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            webView.evaluateJavascript(
                                "(function(){try{return String(eval(" + JSONObject.quote(code) + "));}catch(e){return'Error: '+e.message;}})()",
                                new android.webkit.ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String result) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                                            }
                                        });
                                    }
                                }
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                Button delBtn = new Button(this);
                delBtn.setText("✕");
                delBtn.setTextColor(0xFFCF6679);
                delBtn.setBackgroundColor(0x00000000);
                delBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        sm.remove(idx);
                        refreshSnippetList(parent, sm, listStartIdx);
                    }
                });
                row.addView(runBtn);
                row.addView(delBtn);
                parent.addView(row);
            } catch (Exception ignored) {}
        }
    }

    private View tabRunner() {
        LinearLayout l = column();
        l.addView(label("JS REPL — runs in page context"));
        final EditText code = multiInput("expression or statement…", 5);
        l.addView(code);
        final TextView out = output("// output appears here");
        l.addView(out);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button runBtn = accentBtn("▶ Run", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String js = code.getText().toString().trim();
                if (js.isEmpty()) return;
                String wrapped = "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'⚠ '+e.message;}}())";
                webView.evaluateJavascript(wrapped,
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String prev = out.getText().toString();
                                    String cleaned = result.replaceAll("^\"|\"$", "").replace("\\n", "\n").replace("\\\"", "\"");
                                    out.setText(prev + "\n> " + cleaned);
                                }
                            });
                        }
                    }
                );
            }
        });
        runBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button clearBtn = btn("Clear", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                out.setText("// output appears here");
            }
        });
        clearBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(runBtn);
        row.addView(clearBtn);
        l.addView(row);
        l.addView(btn("Copy Output", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy(out.getText().toString(), "Output");
            }
        }));
        l.addView(label("QUICK EXPRESSIONS"));
        String[][] exprs = {
            {"navigator.userAgent", "navigator.userAgent"},
            {"window.innerWidth/Height", "window.innerWidth + 'x' + window.innerHeight"},
            {"performance.now()", "performance.now()"},
            {"document.readyState", "document.readyState"},
            {"localStorage length", "localStorage.length"},
        };
        for (final String[] pair : exprs) {
            l.addView(btn(pair[0], new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    code.setText(pair[1]);
                }
            }));
        }
        return l;
    }

    private void injectDevTools(final Runnable onSuccess) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(getAssets().open("eruda.min.js")));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) { sb.append(line); sb.append('\n'); }
                    reader.close();
                    final String js = sb.toString();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String v1) {
                                    webView.evaluateJavascript("eruda.init();", new android.webkit.ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String v2) {
                                            if (onSuccess != null) runOnUiThread(onSuccess);
                                        }
                                    });
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "DevTools load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

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
