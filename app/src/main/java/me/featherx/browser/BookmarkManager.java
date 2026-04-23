package me.featherx.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BookmarkManager {
    private static final String PREFS = "featherx_bookmarks";
    private static final String KEY = "bookmarks_v1";
    private final SharedPreferences prefs;

    public static class Bookmark {
        public String title;
        public String url;
        public Bookmark(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    public BookmarkManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Bookmark> getAll() {
        List<Bookmark> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Bookmark(o.optString("title"), o.optString("url")));
            }
        } catch (Exception ignored) {}
        return list;
    }

    public boolean exists(String url) {
        if (url == null) return false;
        for (Bookmark b : getAll()) if (url.equals(b.url)) return true;
        return false;
    }

    public void add(String title, String url) {
        if (url == null || url.isEmpty()) return;
        if (exists(url)) return;
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("title", title == null ? url : title);
            o.put("url", url);
            arr.put(o);
            prefs.edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void remove(String url) {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!url.equals(o.optString("url"))) next.put(o);
            }
            prefs.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public void clear() {
        prefs.edit().remove(KEY).apply();
    }
}
