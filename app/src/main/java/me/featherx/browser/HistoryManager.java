package me.featherx.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class HistoryManager {
    private static final String PREFS = "featherx_history";
    private static final String KEY = "history_v1";
    private static final int MAX_ENTRIES = 500;
    private final SharedPreferences prefs;

    public static class Entry {
        public String title;
        public String url;
        public long ts;
        public Entry(String title, String url, long ts) {
            this.title = title;
            this.url = url;
            this.ts = ts;
        }
    }

    public HistoryManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Entry> getAll() {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Entry(o.optString("title"), o.optString("url"), o.optLong("ts", 0)));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public void add(String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:") || url.startsWith("data:")) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            // De-duplicate consecutive same URL
            if (arr.length() > 0) {
                JSONObject last = arr.getJSONObject(arr.length() - 1);
                if (url.equals(last.optString("url"))) {
                    last.put("title", title == null ? url : title);
                    last.put("ts", System.currentTimeMillis());
                    prefs.edit().putString(KEY, arr.toString()).apply();
                    return;
                }
            }
            JSONObject o = new JSONObject();
            o.put("title", title == null ? url : title);
            o.put("url", url);
            o.put("ts", System.currentTimeMillis());
            arr.put(o);
            // Trim
            if (arr.length() > MAX_ENTRIES) {
                JSONArray trimmed = new JSONArray();
                for (int i = arr.length() - MAX_ENTRIES; i < arr.length(); i++) trimmed.put(arr.get(i));
                arr = trimmed;
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
