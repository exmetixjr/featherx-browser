package me.featherx.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;

public class TabManager {
    public static class Tab {
        public WebView webView;
        public String url;
        public String title;
        public boolean isIncognito;
        public Bitmap favicon;
        public Bitmap thumbnail;

        Tab(WebView wv, String u, String t, boolean incognito) {
            webView = wv;
            url = u;
            title = t;
            isIncognito = incognito;
        }
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int currentTabIndex = -1;
    private final Context context;
    private TabManagerListener listener;

    public interface TabManagerListener {
        void onTabChanged(Tab newTab, WebView newWebView);
    }

    public TabManager(Context ctx) {
        context = ctx;
    }

    public void setListener(TabManagerListener l) {
        listener = l;
    }

    public Tab createTab(boolean incognito) {
        Tab tab = new Tab(null, "", "New Tab", incognito);
        tabs.add(tab);
        switchToTab(tabs.size() - 1);
        return tab;
    }

    public void initWebViewForTab(Tab tab, WebView wv) {
        tab.webView = wv;
        setupWebView(tab);
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (tabs.size() <= 1) return;
        Tab tab = tabs.get(index);
        if (tab.webView != null) {
            tab.webView.destroy();
        }
        tabs.remove(index);
        if (currentTabIndex >= tabs.size()) {
            currentTabIndex = tabs.size() - 1;
        } else if (currentTabIndex > index) {
            currentTabIndex--;
        }
        switchToTab(currentTabIndex);
    }

    public void closeAll(boolean keepOne) {
        for (Tab t : tabs) {
            if (t.webView != null) t.webView.destroy();
        }
        tabs.clear();
        currentTabIndex = -1;
        if (keepOne) {
            // caller should create a new fresh tab afterwards
        }
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentTabIndex = index;
        Tab tab = tabs.get(currentTabIndex);
        if (listener != null && tab.webView != null) {
            listener.onTabChanged(tab, tab.webView);
        }
    }

    public Tab getCurrentTab() {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null;
        return tabs.get(currentTabIndex);
    }

    public List<Tab> getAllTabs() {
        return tabs;
    }

    public int getCurrentIndex() {
        return currentTabIndex;
    }

    public int getTabCount() {
        return tabs.size();
    }

    private void setupWebView(Tab tab) {
        if (tab.webView == null) return;

        WebSettings s = tab.webView.getSettings();
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

        if (tab.isIncognito) {
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            CookieManager.getInstance().setAcceptCookie(false);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(tab.webView, true);
        }
    }
}
