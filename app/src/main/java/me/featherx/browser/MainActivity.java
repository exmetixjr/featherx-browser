package me.featherx.browser;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TabManager tabManager;
    private WebView webView;
    private EditText urlBar;
    private ProgressBar progressBar;
    private TextView tabCount;
    private FrameLayout overlayContainer;
    private LinearLayout tabStrip;
    private HorizontalScrollView tabStripScroll;
    private ImageView urlLock;
    private ImageButton btnBookmark;
    private ImageButton btnBack, btnForward;
    private boolean devToolsActive = false;
    private boolean desktopMode = false;
    private boolean incognitoMode = false;
    private int currentSearchEngine = 0;
    private final Handler progressGuard = new Handler(Looper.getMainLooper());

    private static final String[] SEARCH_ENGINES = {
        "https://www.google.com/search?q=",
        "https://www.bing.com/search?q=",
        "https://duckduckgo.com/?q=",
        "https://search.yahoo.com/search?p="
    };
    private static final String[] SEARCH_NAMES = {"Google", "Bing", "DuckDuckGo", "Yahoo"};
    private static final int[] SEARCH_ICONS = {
        R.drawable.ic_search_engine,
        R.drawable.ic_bing,
        R.drawable.ic_dg,
        R.drawable.ic_yahoo
    };
    private static final String HOME = "https://www.google.com";

    private BookmarkManager bookmarkManager;
    private HistoryManager historyManager;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            doOnCreate(savedInstanceState);
        } catch (Throwable t) {
            showCrashScreen(t);
        }
    }

    private void showCrashScreen(Throwable t) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            final String trace = "FeatherX startup crash:\n\n"
                + t.getClass().getName() + ": " + t.getMessage() + "\n\n"
                + "Android " + android.os.Build.VERSION.RELEASE
                + " (SDK " + android.os.Build.VERSION.SDK_INT + ")\n"
                + "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n\n"
                + sw.toString();

            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(0xFF101010);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            int pad = px(16);
            col.setPadding(pad, pad, pad, pad);

            TextView title = new TextView(this);
            title.setText("FeatherX crashed on launch");
            title.setTextColor(0xFFFF6B6B);
            title.setTextSize(18f);
            col.addView(title);

            TextView body = new TextView(this);
            body.setText(trace);
            body.setTextColor(0xFFE0E0E0);
            body.setTextSize(11f);
            body.setTypeface(android.graphics.Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            int top = px(12);
            body.setPadding(0, top, 0, top);
            col.addView(body);

            Button copyBtn = new Button(this);
            copyBtn.setText("Copy crash details");
            copyBtn.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("FeatherX crash", trace));
                Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });
            col.addView(copyBtn);

            scroll.addView(col);
            setContentView(scroll);
        } catch (Throwable inner) {
            Toast.makeText(this, "Crash: " + t.getClass().getSimpleName() + " " + t.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void doOnCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        tabCount = findViewById(R.id.tab_count);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        findBar = findViewById(R.id.find_bar);
        findInput = findViewById(R.id.find_input);
        findCount = findViewById(R.id.find_count);
        overlayContainer = findViewById(R.id.overlay_container);
        tabStrip = findViewById(R.id.tab_strip);
        tabStripScroll = findViewById(R.id.tab_strip_scroll);
        urlLock = findViewById(R.id.url_lock);
        btnBookmark = findViewById(R.id.btn_bookmark);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);

        bookmarkManager = new BookmarkManager(this);
        historyManager = new HistoryManager(this);

        swipeRefresh.setColorSchemeResources(R.color.accent, R.color.accent_2);
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_surface);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());

        setupFindBar();
        setupDownloads(webView);

        tabManager = new TabManager(this);
        tabManager.setListener((tab, newWebView) -> {
            ViewGroup parent = (ViewGroup) webView.getParent();
            int index = parent.indexOfChild(webView);
            parent.removeView(webView);
            webView = newWebView;
            parent.addView(webView, index);
            urlBar.setText(tab.url == null ? "" : tab.url);
            updateTabCount();
            renderTabStrip();
            updateUrlChrome();
            updateNavButtons();
        });

        // Register the initial webview as the first tab so TabManager knows about it.
        TabManager.Tab firstTab = tabManager.createTab(false);
        tabManager.initWebViewForTab(firstTab, webView);

        setupWebViewForCurrent(webView);
        setupUrlBar();
        setupNavButtons();
        updateTabCount();
        renderTabStrip();
        webView.loadUrl(HOME);
    }

    @Override
    public void onBackPressed() {
        if (overlayContainer != null && overlayContainer.getVisibility() == View.VISIBLE) {
            hideOverlay();
            return;
        }
        if (findBar != null && findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
            return;
        }
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    // ---------- WebView setup (shared) ----------
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebViewForCurrent(final WebView wv) {
        WebSettings s = wv.getSettings();
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
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        wv.addJavascriptInterface(new WebAppInterface(this, wv), "FeatherX");
        attachClients(wv, false);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupBackgroundWebView(WebView wv, boolean incognito) {
        WebSettings s = wv.getSettings();
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
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);

        if (incognito) {
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        }
        CookieManager.getInstance().setAcceptCookie(!incognito);
        if (!incognito) CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        wv.addJavascriptInterface(new WebAppInterface(this, wv), "FeatherX");
        attachClients(wv, incognito);
    }

    private void attachClients(final WebView wv, final boolean incognito) {
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                if (v == webView) {
                    urlBar.setText(url);
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(0);
                    armProgressGuard();
                    updateUrlChrome();
                    updateNavButtons();
                }
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (v == webView) {
                    progressBar.setVisibility(View.GONE);
                    progressGuard.removeCallbacksAndMessages(null);
                    urlBar.setText(url);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    updateUrlChrome();
                    updateNavButtons();
                }
                CookieManager.getInstance().flush();
                TabManager.Tab tab = findTabFor(v);
                if (tab != null) {
                    tab.url = url;
                    tab.title = v.getTitle();
                }
                if (!incognito && historyManager != null && v == webView) {
                    historyManager.add(v.getTitle(), url);
                }
                if (v == webView && devToolsActive) {
                    injectDevTools(null);
                }
                renderTabStrip();
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                if (v != webView) return;
                progressBar.setProgress(p);
                if (p >= 100) {
                    progressBar.setVisibility(View.GONE);
                    progressGuard.removeCallbacksAndMessages(null);
                } else if (p >= 80) {
                    // Safety: hide soon if the page never quite finishes.
                    progressGuard.removeCallbacksAndMessages(null);
                    progressGuard.postDelayed(() -> progressBar.setVisibility(View.GONE), 1500);
                }
            }
            @Override
            public void onReceivedTitle(WebView v, String title) {
                TabManager.Tab tab = findTabFor(v);
                if (tab != null) tab.title = title;
                renderTabStrip();
            }
            @Override
            public void onReceivedIcon(WebView v, Bitmap icon) {
                TabManager.Tab tab = findTabFor(v);
                if (tab != null) tab.favicon = icon;
                renderTabStrip();
            }
            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Open links that try to open in a new window as a real new tab.
                final WebView newView = new WebView(MainActivity.this);
                TabManager.Tab newTab = tabManager.createTab(incognito);
                setupBackgroundWebView(newView, incognito);
                tabManager.initWebViewForTab(newTab, newView);

                // Swap visible WebView to the new tab
                ViewGroup parent = (ViewGroup) webView.getParent();
                int idx = parent.indexOfChild(webView);
                parent.removeView(webView);
                webView = newView;
                parent.addView(webView, idx);
                updateTabCount();

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newView);
                resultMsg.sendToTarget();
                renderTabStrip();
                return true;
            }
        });
        setupDownloads(wv);
    }

    private TabManager.Tab findTabFor(WebView v) {
        for (TabManager.Tab t : tabManager.getAllTabs()) {
            if (t.webView == v) return t;
        }
        return null;
    }

    private void armProgressGuard() {
        progressGuard.removeCallbacksAndMessages(null);
        progressGuard.postDelayed(() -> progressBar.setVisibility(View.GONE), 25_000);
    }

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
            url = SEARCH_ENGINES[currentSearchEngine] + Uri.encode(input);
        }
        webView.loadUrl(url);
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void setupNavButtons() {
        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        findViewById(R.id.btn_reload).setOnClickListener(v -> {
            if (progressBar.getVisibility() == View.VISIBLE) webView.stopLoading();
            else webView.reload();
        });
        findViewById(R.id.btn_home).setOnClickListener(v -> webView.loadUrl(HOME));
        btnBookmark.setOnClickListener(v -> { toggleBookmark(); updateUrlChrome(); });
        urlLock.setOnClickListener(v -> showSiteSettingsOverlay());
        findViewById(R.id.btn_new_tab_strip).setOnClickListener(v -> createNewTab(incognitoMode));
        findViewById(R.id.btn_tabs).setOnClickListener(v -> showTabsOverlay());
        findViewById(R.id.btn_overflow).setOnClickListener(this::showOverflowSheet);
        updateNavButtons();
        updateUrlChrome();
    }

    private void updateNavButtons() {
        if (btnBack == null || webView == null) return;
        boolean back = webView.canGoBack();
        boolean fwd = webView.canGoForward();
        btnBack.setEnabled(back);
        btnBack.setAlpha(back ? 1f : 0.35f);
        btnForward.setEnabled(fwd);
        btnForward.setAlpha(fwd ? 1f : 0.35f);
    }

    private void updateUrlChrome() {
        if (urlLock == null || btnBookmark == null || webView == null) return;
        String url = webView.getUrl();
        if (url != null && url.startsWith("https://")) {
            urlLock.setImageResource(R.drawable.ic_lock);
        } else {
            urlLock.setImageResource(R.drawable.ic_globe_small);
        }
        boolean isBookmarked = url != null && bookmarkManager != null && bookmarkManager.exists(url);
        btnBookmark.setImageResource(isBookmarked
            ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
    }

    private void updateTabCount() {
        int count = tabManager.getTabCount();
        tabCount.setText(String.valueOf(count));
    }

    // ---------- Tab strip (desktop-style) ----------
    private void renderTabStrip() {
        if (tabStrip == null || tabManager == null) return;
        tabStrip.removeAllViews();
        List<TabManager.Tab> all = tabManager.getAllTabs();
        TabManager.Tab current = tabManager.getCurrentTab();
        View activeView = null;
        for (int i = 0; i < all.size(); i++) {
            final int idx = i;
            final TabManager.Tab tab = all.get(i);
            View item = LayoutInflater.from(this)
                .inflate(R.layout.item_tab_strip, tabStrip, false);
            ImageView fav = item.findViewById(R.id.strip_favicon);
            TextView title = item.findViewById(R.id.strip_title);
            ImageButton close = item.findViewById(R.id.strip_close);

            String t = (tab.title == null || tab.title.isEmpty()) ? "New Tab" : tab.title;
            if (tab.isIncognito) t = "🕶 " + t;
            title.setText(t);
            if (tab.favicon != null) fav.setImageBitmap(tab.favicon);
            else fav.setImageResource(tab.isIncognito ? R.drawable.ic_incognito : R.drawable.ic_globe);

            boolean isActive = tab == current;
            if (isActive) {
                item.setBackgroundResource(R.drawable.bg_tab_pill_active);
                title.setTextColor(0xFFF2F1F8);
                activeView = item;
            } else {
                item.setBackgroundResource(R.drawable.bg_tab_pill);
                title.setTextColor(0xFFB8B6C8);
            }
            item.setOnClickListener(v -> tabManager.switchToTab(idx));
            close.setOnClickListener(v -> {
                if (tabManager.getTabCount() <= 1) {
                    Toast.makeText(MainActivity.this, "Can't close the last tab",
                        Toast.LENGTH_SHORT).show();
                    return;
                }
                tabManager.closeTab(idx);
                updateTabCount();
                renderTabStrip();
            });
            tabStrip.addView(item);
        }
        // Auto-scroll active tab into view
        if (activeView != null && tabStripScroll != null) {
            final View target = activeView;
            tabStripScroll.post(() -> {
                int x = target.getLeft();
                int w = target.getWidth();
                int sw = tabStripScroll.getWidth();
                int scrollX = Math.max(0, x - (sw - w) / 2);
                tabStripScroll.smoothScrollTo(scrollX, 0);
            });
        }
    }

    private void showSearchEngineDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Search engine");
        builder.setItems(SEARCH_NAMES, (dialog, which) -> {
            currentSearchEngine = which;
            Toast.makeText(MainActivity.this, "Search: " + SEARCH_NAMES[which], Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void createNewTab(boolean incognito) {
        TabManager.Tab newTab = tabManager.createTab(incognito);
        WebView newWebView = new WebView(this);
        setupBackgroundWebView(newWebView, incognito);
        tabManager.initWebViewForTab(newTab, newWebView);

        // Swap to the new tab
        ViewGroup parent = (ViewGroup) webView.getParent();
        int idx = parent.indexOfChild(webView);
        parent.removeView(webView);
        webView = newWebView;
        parent.addView(webView, idx);
        webView.loadUrl(HOME);
        updateTabCount();
        renderTabStrip();
        updateUrlChrome();
        updateNavButtons();
    }

    // ---------- Tabs overlay ----------
    private boolean tabsFilterIncognito = false;

    private void showTabsOverlay() {
        captureCurrentThumbnail();
        View root = LayoutInflater.from(this).inflate(R.layout.overlay_tabs, overlayContainer, false);
        overlayContainer.removeAllViews();
        overlayContainer.addView(root);
        overlayContainer.setVisibility(View.VISIBLE);

        ImageButton close = root.findViewById(R.id.tabs_close);
        ImageButton clearAll = root.findViewById(R.id.tabs_close_all);
        ImageButton add = root.findViewById(R.id.tabs_add);
        Button done = root.findViewById(R.id.tabs_done);
        ImageButton incoToggle = root.findViewById(R.id.tabs_incognito_toggle);
        TextView incoLabel = root.findViewById(R.id.tabs_incognito_label);
        LinearLayout chipRow = root.findViewById(R.id.tabs_chip_row);
        LinearLayout grid = root.findViewById(R.id.tabs_grid);

        close.setOnClickListener(v -> hideOverlay());
        done.setOnClickListener(v -> hideOverlay());
        add.setOnClickListener(v -> { hideOverlay(); createNewTab(incognitoMode); });
        clearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Close all tabs?")
                .setMessage("This will close every tab and start fresh.")
                .setPositiveButton("Close all", (d, w) -> {
                    tabManager.closeAll(false);
                    hideOverlay();
                    createNewTab(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        incoToggle.setOnClickListener(v -> {
            incognitoMode = !incognitoMode;
            incoLabel.setText("New tabs: " + (incognitoMode ? "Incognito" : "Normal"));
        });
        incoLabel.setText("New tabs: " + (incognitoMode ? "Incognito" : "Normal"));

        // Chips (categories)
        chipRow.removeAllViews();
        addChip(chipRow, "All", !tabsFilterIncognito && !showingIncognitoOnly());
        addChip(chipRow, "Normal", !tabsFilterIncognito);
        addChip(chipRow, "Incognito", tabsFilterIncognito);
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            final int idx = i;
            chipRow.getChildAt(i).setOnClickListener(v -> {
                if (idx == 2) tabsFilterIncognito = true;
                else tabsFilterIncognito = false;
                showTabsOverlay();
            });
        }

        renderTabsGrid(grid);
    }

    private boolean showingIncognitoOnly() { return tabsFilterIncognito; }

    private void addChip(LinearLayout row, String label, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextColor(selected ? 0xFFF2F1F8 : 0xFFB8B6C8);
        chip.setTextSize(13f);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setSelected(selected);
        chip.setPadding(px(16), px(8), px(16), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, px(8), 0);
        chip.setLayoutParams(lp);
        row.addView(chip);
    }

    private void renderTabsGrid(LinearLayout grid) {
        grid.removeAllViews();
        List<TabManager.Tab> all = tabManager.getAllTabs();
        TabManager.Tab current = tabManager.getCurrentTab();

        int shown = 0;
        for (int i = 0; i < all.size(); i++) {
            final TabManager.Tab tab = all.get(i);
            if (tabsFilterIncognito && !tab.isIncognito) continue;
            if (!tabsFilterIncognito && tab.isIncognito) continue;
            shown++;
            final int idx = i;

            View card = LayoutInflater.from(this).inflate(R.layout.item_tab_card, grid, false);
            TextView title = card.findViewById(R.id.tab_title);
            TextView url = card.findViewById(R.id.tab_url);
            ImageView fav = card.findViewById(R.id.tab_favicon);
            ImageView thumb = card.findViewById(R.id.tab_thumb);
            TextView placeholder = card.findViewById(R.id.tab_thumb_placeholder);
            ImageButton closeBtn = card.findViewById(R.id.tab_close);

            String t = (tab.title == null || tab.title.isEmpty()) ? "New Tab" : tab.title;
            title.setText((tab.isIncognito ? "🕶  " : "") + t);
            url.setText(tab.url == null || tab.url.isEmpty() ? "about:blank" : tab.url);
            if (tab.favicon != null) fav.setImageBitmap(tab.favicon);
            if (tab.thumbnail != null) {
                thumb.setImageBitmap(tab.thumbnail);
                placeholder.setVisibility(View.GONE);
            } else {
                thumb.setImageDrawable(null);
                placeholder.setVisibility(View.VISIBLE);
            }

            // Highlight current tab
            if (tab == current) card.setBackgroundResource(R.drawable.bg_card_active);

            card.setOnClickListener(v -> {
                tabManager.switchToTab(idx);
                hideOverlay();
            });
            closeBtn.setOnClickListener(v -> {
                if (tabManager.getTabCount() <= 1) {
                    Toast.makeText(this, "Can't close the last tab", Toast.LENGTH_SHORT).show();
                    return;
                }
                tabManager.closeTab(idx);
                updateTabCount();
                showTabsOverlay();
            });
            grid.addView(card);
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(tabsFilterIncognito ? "No incognito tabs" : "No tabs");
            empty.setTextColor(0xFF6B6982);
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, px(60), 0, 0);
            grid.addView(empty);
        }
    }

    private void captureCurrentThumbnail() {
        try {
            TabManager.Tab cur = tabManager.getCurrentTab();
            if (cur == null || webView == null) return;
            int w = webView.getWidth();
            int h = webView.getHeight();
            if (w <= 0 || h <= 0) return;
            int tw = Math.max(1, w / 3);
            int th = Math.max(1, h / 3);
            Bitmap bm = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(bm);
            c.scale(1f / 3f, 1f / 3f);
            webView.draw(c);
            cur.thumbnail = bm;
        } catch (Throwable ignored) {}
    }

    private void hideOverlay() {
        if (overlayContainer == null) return;
        overlayContainer.removeAllViews();
        overlayContainer.setVisibility(View.GONE);
    }

    // ---------- Overflow sheet (custom) ----------
    private void showOverflowSheet(View anchor) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.sheet_overflow, null);
        sheet.setContentView(root);

        ImageButton back = root.findViewById(R.id.of_back);
        ImageButton fwd = root.findViewById(R.id.of_forward);
        ImageButton reload = root.findViewById(R.id.of_reload);
        ImageButton bm = root.findViewById(R.id.of_bookmark);
        ImageButton share = root.findViewById(R.id.of_share);

        back.setEnabled(webView.canGoBack());
        back.setAlpha(webView.canGoBack() ? 1f : 0.4f);
        fwd.setEnabled(webView.canGoForward());
        fwd.setAlpha(webView.canGoForward() ? 1f : 0.4f);

        boolean isBookmarked = bookmarkManager.exists(webView.getUrl());
        bm.setImageResource(isBookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);

        back.setOnClickListener(v -> { sheet.dismiss(); if (webView.canGoBack()) webView.goBack(); });
        fwd.setOnClickListener(v -> { sheet.dismiss(); if (webView.canGoForward()) webView.goForward(); });
        reload.setOnClickListener(v -> { sheet.dismiss(); webView.reload(); });
        bm.setOnClickListener(v -> { sheet.dismiss(); toggleBookmark(); });
        share.setOnClickListener(v -> { sheet.dismiss(); shareCurrentUrl(); });

        GridLayout grid = root.findViewById(R.id.of_grid);
        grid.removeAllViews();
        addOverflowItem(grid, "Bookmarks", R.drawable.ic_bookmark, () -> showLinkOverlay(true));
        addOverflowItem(grid, "History", R.drawable.ic_history, () -> showLinkOverlay(false));
        addOverflowItem(grid, "Find in page", R.drawable.ic_find, this::showFindBar);
        addOverflowItem(grid, "Save page", R.drawable.ic_download, this::savePage);
        addOverflowItem(grid, devToolsActive ? "Hide DevTools" : "DevTools", R.drawable.ic_lightning, this::toggleDevTools);
        addOverflowItem(grid, desktopMode ? "Mobile site" : "Desktop site", R.drawable.ic_desktop, this::toggleDesktopMode);
        addOverflowItem(grid, "JS inject", R.drawable.ic_lightning, () -> openToolsSheet(2));
        addOverflowItem(grid, "Cookies", R.drawable.ic_cookie, () -> openToolsSheet(3));
        addOverflowItem(grid, "User agent", R.drawable.ic_globe, () -> openToolsSheet(4));
        addOverflowItem(grid, "Site settings", R.drawable.ic_settings, this::showSiteSettingsOverlay);
        addOverflowItem(grid, "Clear data", R.drawable.ic_trash, this::showClearDataDialog);
        addOverflowItem(grid, "New tab", R.drawable.ic_plus, () -> createNewTab(incognitoMode));

        // Wrap each overflow item callback with sheet.dismiss()
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            View.OnClickListener original = (View.OnClickListener) child.getTag(R.id.of_grid);
            if (original != null) {
                child.setOnClickListener(v -> { sheet.dismiss(); original.onClick(v); });
            }
        }
        sheet.show();
    }

    private void addOverflowItem(GridLayout grid, String label, int iconRes, final Runnable action) {
        View item = LayoutInflater.from(this).inflate(R.layout.item_overflow_grid, grid, false);
        ImageView icon = item.findViewById(R.id.ofg_icon);
        TextView lbl = item.findViewById(R.id.ofg_label);
        icon.setImageResource(iconRes);
        lbl.setText(label);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(px(4), px(4), px(4), px(4));
        item.setLayoutParams(lp);
        View.OnClickListener click = v -> action.run();
        item.setTag(R.id.of_grid, click);
        item.setOnClickListener(click);
        grid.addView(item);
    }

    // ---------- Bookmarks ----------
    private void toggleBookmark() {
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) return;
        if (bookmarkManager.exists(url)) {
            bookmarkManager.remove(url);
            Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show();
        } else {
            bookmarkManager.add(webView.getTitle(), url);
            Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Bookmarks / History overlay ----------
    private void showLinkOverlay(final boolean bookmarks) {
        View root = LayoutInflater.from(this).inflate(R.layout.overlay_list, overlayContainer, false);
        overlayContainer.removeAllViews();
        overlayContainer.addView(root);
        overlayContainer.setVisibility(View.VISIBLE);

        TextView titleView = root.findViewById(R.id.list_title);
        titleView.setText(bookmarks ? "Bookmarks" : "History");
        ImageButton close = root.findViewById(R.id.list_close);
        ImageButton clear = root.findViewById(R.id.list_clear);
        EditText search = root.findViewById(R.id.list_search);
        final LinearLayout container = root.findViewById(R.id.list_items);
        final TextView empty = root.findViewById(R.id.list_empty);

        close.setOnClickListener(v -> hideOverlay());
        clear.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Clear all?")
                .setMessage(bookmarks ? "Remove every bookmark." : "Remove all history.")
                .setPositiveButton("Clear", (d, w) -> {
                    if (bookmarks) bookmarkManager.clear();
                    else historyManager.clear();
                    renderLinks(container, empty, bookmarks, search.getText().toString());
                })
                .setNegativeButton("Cancel", null)
                .show()
        );
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                renderLinks(container, empty, bookmarks, s.toString());
            }
        });
        renderLinks(container, empty, bookmarks, "");
    }

    private void renderLinks(LinearLayout container, TextView empty, boolean bookmarks, String filter) {
        container.removeAllViews();
        List<Object[]> items = new ArrayList<>(); // [title, url]
        if (bookmarks) {
            for (BookmarkManager.Bookmark b : bookmarkManager.getAll()) {
                items.add(new Object[]{b.title, b.url});
            }
        } else {
            for (HistoryManager.Entry e : historyManager.getAll()) {
                items.add(new Object[]{e.title, e.url});
            }
        }
        String f = filter == null ? "" : filter.trim().toLowerCase();
        int shown = 0;
        for (Object[] o : items) {
            String t = (String) o[0]; String u = (String) o[1];
            if (!f.isEmpty()) {
                String hay = ((t == null ? "" : t) + " " + (u == null ? "" : u)).toLowerCase();
                if (!hay.contains(f)) continue;
            }
            shown++;
            final String url = u;
            View card = LayoutInflater.from(this).inflate(R.layout.item_link_card, container, false);
            TextView tv = card.findViewById(R.id.link_title);
            TextView uv = card.findViewById(R.id.link_url);
            ImageButton rm = card.findViewById(R.id.link_remove);
            tv.setText((t == null || t.isEmpty()) ? u : t);
            uv.setText(u);
            card.setOnClickListener(v -> { hideOverlay(); webView.loadUrl(url); });
            if (bookmarks) {
                rm.setOnClickListener(v -> {
                    bookmarkManager.remove(url);
                    renderLinks(container, empty, true, "");
                });
            } else {
                rm.setVisibility(View.GONE);
            }
            container.addView(card);
        }
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        empty.setText(bookmarks ? "No bookmarks yet" : "No history yet");
    }

    // ---------- Site Settings overlay ----------
    private void showSiteSettingsOverlay() {
        View root = LayoutInflater.from(this).inflate(R.layout.overlay_settings, overlayContainer, false);
        overlayContainer.removeAllViews();
        overlayContainer.addView(root);
        overlayContainer.setVisibility(View.VISIBLE);

        root.findViewById(R.id.settings_close).setOnClickListener(v -> hideOverlay());
        LinearLayout body = root.findViewById(R.id.settings_content);
        body.removeAllViews();

        final WebSettings s = webView.getSettings();
        String host;
        try { host = Uri.parse(webView.getUrl() == null ? "" : webView.getUrl()).getHost(); }
        catch (Exception e) { host = null; }
        if (host == null) host = "this page";

        body.addView(sectionLabel("Site"));
        body.addView(infoLine(host));

        body.addView(sectionLabel("Permissions & content"));
        body.addView(switchRow("JavaScript", s.getJavaScriptEnabled(), s::setJavaScriptEnabled));
        body.addView(switchRow("DOM storage", s.getDomStorageEnabled(), s::setDomStorageEnabled));
        body.addView(switchRow("Allow images",
            s.getLoadsImagesAutomatically(),
            s::setLoadsImagesAutomatically));
        boolean cookieOn = CookieManager.getInstance().acceptCookie();
        body.addView(switchRow("Accept cookies", cookieOn, val -> {
            CookieManager.getInstance().setAcceptCookie(val);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, val);
        }));
        body.addView(switchRow("Desktop site", desktopMode, val -> {
            desktopMode = val;
            applyDesktopMode(desktopMode);
        }));

        body.addView(sectionLabel("Zoom"));
        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        zoomRow.addView(actionButton("A−", v -> webView.zoomOut()));
        zoomRow.addView(actionButton("100%", v -> {
            // Reset by clearing user-set scale: reload usually does it
            webView.evaluateJavascript("document.body.style.zoom='1'", null);
        }));
        zoomRow.addView(actionButton("A+", v -> webView.zoomIn()));
        body.addView(zoomRow);

        body.addView(sectionLabel("Storage for this site"));
        body.addView(actionButton("Clear cookies for site", v -> {
            String url = webView.getUrl();
            if (url == null) return;
            String prefix = Uri.parse(url).getScheme() + "://" + Uri.parse(url).getHost();
            String existing = CookieManager.getInstance().getCookie(prefix);
            if (existing != null) {
                for (String pair : existing.split(";")) {
                    String name = pair.split("=")[0].trim();
                    CookieManager.getInstance().setCookie(prefix, name + "=; Max-Age=0; Path=/");
                }
                CookieManager.getInstance().flush();
            }
            Toast.makeText(this, "Cookies cleared for " + prefix, Toast.LENGTH_SHORT).show();
        }));
        body.addView(actionButton("Clear cache", v -> {
            webView.clearCache(true);
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
        }));
        body.addView(actionButton("Clear local storage", v -> {
            android.webkit.WebStorage.getInstance().deleteAllData();
            Toast.makeText(this, "Local storage cleared", Toast.LENGTH_SHORT).show();
        }));
    }

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextColor(0xFF8B7CFF);
        tv.setTextSize(11f);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(16), 0, px(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView infoLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFEFEFEF);
        tv.setTextSize(14f);
        tv.setBackgroundResource(R.drawable.bg_card);
        tv.setPadding(px(14), px(12), px(14), px(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, px(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    public interface BoolConsumer { void accept(boolean v); }

    private View switchRow(String label, boolean initial, final BoolConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(px(14), px(8), px(14), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, px(8));
        row.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFEFEFEF);
        tv.setTextSize(14f);
        LinearLayout.LayoutParams tvlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvlp);
        row.addView(tv);

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((b, v) -> onChange.accept(v));
        row.addView(sw);
        return row;
    }

    private Button actionButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFEFEFEF);
        b.setBackgroundResource(R.drawable.bg_action_pill);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, px(44), 1f);
        lp.setMargins(px(4), px(6), px(4), px(6));
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    // ---------- Find in page ----------
    private void setupFindBar() {
        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString();
                if (q.isEmpty()) {
                    webView.clearMatches();
                    findCount.setText("");
                } else {
                    webView.findAllAsync(q);
                }
            }
        });
        webView.setFindListener((active, total, isDoneCounting) -> {
            if (isDoneCounting) {
                findCount.setText(total == 0 ? "0/0" : (active + 1) + "/" + total);
            }
        });
        findViewById(R.id.find_prev).setOnClickListener(v -> webView.findNext(false));
        findViewById(R.id.find_next).setOnClickListener(v -> webView.findNext(true));
        findViewById(R.id.find_close).setOnClickListener(v -> hideFindBar());
    }

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(findInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideFindBar() {
        findBar.setVisibility(View.GONE);
        findInput.setText("");
        webView.clearMatches();
        android.view.inputmethod.InputMethodManager imm =
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findInput.getWindowToken(), 0);
    }

    // ---------- Downloads ----------
    private void setupDownloads(WebView wv) {
        wv.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setMimeType(mimetype);
                req.addRequestHeader("User-Agent", userAgent);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                req.allowScanningByMediaScanner();
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(req);
                Toast.makeText(MainActivity.this, "Downloading: " + fileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Save page ----------
    private void savePage() {
        try {
            String fileName = (webView.getTitle() != null
                ? webView.getTitle().replaceAll("[^a-zA-Z0-9-_.]", "_") : "page");
            if (fileName.length() > 60) fileName = fileName.substring(0, 60);
            java.io.File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            java.io.File out = new java.io.File(dir, fileName + ".mht");
            webView.saveWebArchive(out.getAbsolutePath());
            Toast.makeText(this, "Saved: " + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Clear browsing data ----------
    private void showClearDataDialog() {
        final boolean[] checks = {true, true, true, false};
        final String[] labels = {"Cache", "Cookies", "History", "Bookmarks"};
        new AlertDialog.Builder(this)
            .setTitle("Clear browsing data")
            .setMultiChoiceItems(labels, checks, (d, which, isChecked) -> checks[which] = isChecked)
            .setPositiveButton("Clear", (d, w) -> {
                if (checks[0]) webView.clearCache(true);
                if (checks[1]) {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                }
                if (checks[2]) { historyManager.clear(); webView.clearHistory(); }
                if (checks[3]) bookmarkManager.clear();
                Toast.makeText(MainActivity.this, "Cleared", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void shareCurrentUrl() {
        String url = webView.getUrl();
        if (url == null || url.isEmpty()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void toggleDevTools() {
        if (!devToolsActive) {
            injectDevTools(() -> {
                devToolsActive = true;
                Toast.makeText(MainActivity.this, "DevTools shown — tap the floating icon", Toast.LENGTH_SHORT).show();
            });
        } else {
            webView.evaluateJavascript("if(window.eruda){eruda.hide();eruda.destroy && eruda.destroy();}", null);
            devToolsActive = false;
            Toast.makeText(MainActivity.this, "DevTools deactivated", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleDesktopMode() {
        desktopMode = !desktopMode;
        applyDesktopMode(desktopMode);
        Toast.makeText(this, desktopMode ? "Desktop mode ON" : "Mobile mode ON", Toast.LENGTH_SHORT).show();
    }

    private void applyDesktopMode(boolean desktop) {
        WebSettings s = webView.getSettings();
        if (desktop) {
            s.setUserAgentString(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/122.0.0.0 Safari/537.36");
        } else {
            s.setUserAgentString(null);
        }
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        webView.reload();
    }

    // ---------- Tools sheet (JS Inject / Cookies / UA / etc.) ----------
    private void openToolsSheet(int tabIndex) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_tools, null);
        sheet.setContentView(root);
        sheet.getBehavior().setPeekHeight(560);

        TabLayout tabs = root.findViewById(R.id.tool_tabs);
        FrameLayout frame = root.findViewById(R.id.tool_content);

        String[] tabNames = {"DevTools", "Search", "UA", "JS Inject", "Cookies", "Storage", "Snippets", "Runner"};
        for (String name : tabNames) tabs.addTab(tabs.newTab().setText(name));

        tabs.getTabAt(tabIndex).select();
        renderTab(frame, tabIndex);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { renderTab(frame, tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        sheet.show();
    }

    private void renderTab(FrameLayout frame, int idx) {
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

    private View tabDevTools() {
        LinearLayout l = column();
        l.addView(label("DEVTOOLS"));
        Button devToolsBtn = accentBtn(devToolsActive ? "Hide DevTools" : "Show DevTools",
            v -> toggleDevTools());
        l.addView(devToolsBtn);

        l.addView(label("DISPLAY MODE"));
        Button desktopBtn = btn(desktopMode ? "Switch to Mobile Mode" : "Switch to Desktop Mode",
            v -> toggleDesktopMode());
        l.addView(desktopBtn);
        return l;
    }

    private View tabSearch() {
        LinearLayout l = column();
        l.addView(label("SEARCH ENGINE"));
        for (int i = 0; i < SEARCH_ENGINES.length; i++) {
            final int idx = i;
            Button b = btn(SEARCH_NAMES[i] + (i == currentSearchEngine ? " ✓" : ""),
                v -> { currentSearchEngine = idx; });
            if (i == currentSearchEngine) b.setBackgroundColor(0xFF3D3665);
            l.addView(b);
        }
        return l;
    }

    private View tabUA() {
        LinearLayout l = column();
        l.addView(output("Current UA:\n" + webView.getSettings().getUserAgentString()));
        l.addView(btn("Copy current UA", v -> copy(webView.getSettings().getUserAgentString(), "UA")));
        l.addView(label("PRESETS"));
        final String[] uaNames = UAProfiles.getNames();
        final String[] uaValues = UAProfiles.getValues();
        for (int i = 0; i < uaNames.length; i++) {
            final String value = uaValues[i];
            l.addView(btn(uaNames[i], v -> { webView.getSettings().setUserAgentString(value); webView.reload(); }));
        }
        return l;
    }

    private View tabJSInject() {
        LinearLayout l = column();
        l.addView(label("JS INJECT"));
        final EditText code = multiInput("// your JS here…", 7);
        l.addView(code);
        final TextView result = output("Result: (none)");
        l.addView(result);
        l.addView(accentBtn("Run", v -> {
            String js = code.getText().toString().trim();
            if (js.isEmpty()) return;
            webView.evaluateJavascript(
                "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'Error: '+e.message;}})()",
                val -> runOnUiThread(() -> result.setText("Result: " + val))
            );
        }));
        return l;
    }

    private View tabCookies() {
        LinearLayout l = column();
        String url = webView.getUrl() != null ? webView.getUrl() : "";
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies == null) cookies = "(no cookies for this domain)";
        final TextView cookieView = output(cookies);
        l.addView(cookieView);
        l.addView(btn("Clear all cookies", v -> {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
            cookieView.setText("(all cookies cleared)");
        }));
        return l;
    }

    private View tabStorage() {
        LinearLayout l = column();
        l.addView(label("LOCAL STORAGE"));
        final TextView lsView = output("Loading…");
        l.addView(lsView);
        webView.evaluateJavascript(
            "(function(){try{var o={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);o[k]=localStorage.getItem(k);}return JSON.stringify(o,null,2);}catch(e){return'Error: '+e.message;}})()",
            val -> runOnUiThread(() -> lsView.setText(val))
        );
        l.addView(btn("Clear local storage", v -> {
            android.webkit.WebStorage.getInstance().deleteAllData();
            lsView.setText("(cleared)");
        }));
        return l;
    }

    private View tabSnippets() {
        LinearLayout l = column();
        final SnippetManager sm = new SnippetManager(this);
        l.addView(label("SAVED SNIPPETS"));
        try {
            org.json.JSONArray all = sm.getAll();
            for (int i = 0; i < all.length(); i++) {
                org.json.JSONObject o = all.getJSONObject(i);
                final String name = o.optString("name", "snippet");
                final String code = o.optString("code", "");
                l.addView(btn(name, v -> webView.evaluateJavascript(code, null)));
            }
        } catch (Exception ignored) {}
        l.addView(label("NEW SNIPPET"));
        final EditText nameInput = input("snippet name");
        final EditText codeInput = multiInput("// js …", 5);
        l.addView(nameInput);
        l.addView(codeInput);
        l.addView(accentBtn("Save", v -> {
            sm.add(nameInput.getText().toString(), codeInput.getText().toString());
            Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
        }));
        return l;
    }

    private View tabRunner() {
        LinearLayout l = column();
        l.addView(label("JS REPL"));
        final EditText code = multiInput("expression or statement…", 5);
        l.addView(code);
        final TextView out = output("// output appears here");
        l.addView(out);
        l.addView(accentBtn("▶ Run", v -> {
            String js = code.getText().toString().trim();
            if (js.isEmpty()) return;
            String wrapped = "(function(){try{return String(eval(" + JSONObject.quote(js) + "));}catch(e){return'⚠ '+e.message;}}())";
            webView.evaluateJavascript(wrapped, result -> runOnUiThread(() -> {
                String prev = out.getText().toString();
                String cleaned = result.replaceAll("^\"|\"$", "").replace("\\n", "\n").replace("\\\"", "\"");
                out.setText(prev + "\n> " + cleaned);
            }));
        }));
        return l;
    }

    // ---------- DevTools (Eruda) ----------
    private void injectDevTools(final Runnable onSuccess) {
        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("eruda.min.js")));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) { sb.append(line); sb.append('\n'); }
                reader.close();
                final String js = sb.toString();
                runOnUiThread(() -> webView.evaluateJavascript(js, v1 -> {
                    // Inject mobile-friendly CSS so eruda panels are full-width and scroll
                    // properly on narrow screens (no need to split-screen the device).
                    String mobileCss =
                        "var s=document.createElement('style');" +
                        "s.id='__featherx_eruda_css';" +
                        "s.textContent='" +
                        "._eruda-content,._eruda-tab-content{max-width:100vw !important;overflow-x:auto !important;}" +
                        "._eruda-bottom-bar,._eruda-tools{flex-wrap:wrap !important;}" +
                        "._eruda-resources-table,._eruda-network-table{font-size:11px !important;min-width:0 !important;width:100% !important;}" +
                        "._eruda-network-table td,._eruda-network-table th{word-break:break-all !important;}" +
                        "._eruda-network-detail,._eruda-resources-detail{position:fixed !important;left:0 !important;right:0 !important;top:0 !important;bottom:0 !important;width:100vw !important;height:100vh !important;}" +
                        "';" +
                        "document.head.appendChild(s);";
                    webView.evaluateJavascript(
                        "eruda.init({tool:['console','elements','network','resources','sources','info','snippets']});" +
                        mobileCss + "eruda.show();",
                        v2 -> { if (onSuccess != null) runOnUiThread(onSuccess); }
                    );
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "DevTools load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ---------- Helpers ----------
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
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(4), 0, px(4));
        b.setLayoutParams(lp);
        b.setOnClickListener(click);
        return b;
    }

    private Button accentBtn(String label, View.OnClickListener click) {
        Button b = btn(label, click);
        b.setBackgroundColor(0xFF3D3665);
        b.setTextColor(0xFFB6ACFF);
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
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, px(4), 0, px(4));
        et.setLayoutParams(lp);
        return et;
    }

    private EditText multiInput(String hint, int minLines) {
        EditText et = input(hint);
        et.setMinLines(minLines);
        et.setGravity(Gravity.TOP | Gravity.START);
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
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
}
