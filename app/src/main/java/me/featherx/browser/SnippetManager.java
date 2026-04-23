package me.featherx.browser;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public class SnippetManager {
    private static final String PREFS_NAME = "featherx_snippets";
    private static final String KEY_DATA   = "snippets_v1";
    private final SharedPreferences prefs;

    public SnippetManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public JSONArray getAll() {
        try {
            return new JSONArray(prefs.getString(KEY_DATA, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public void add(String name, String code) {
        try {
            JSONArray arr = getAll();
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("code", code);
            arr.put(obj);
            save(arr);
        } catch (Exception ignored) {}
    }

    public void remove(int index) {
        try {
            JSONArray arr = getAll();
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                if (i != index) next.put(arr.get(i));
            }
            save(next);
        } catch (Exception ignored) {}
    }

    public void update(int index, String name, String code) {
        try {
            JSONArray arr = getAll();
            if (index >= 0 && index < arr.length()) {
                JSONObject obj = new JSONObject();
                obj.put("name", name);
                obj.put("code", code);
                arr.put(index, obj);
                save(arr);
            }
        } catch (Exception ignored) {}
    }

    private void save(JSONArray arr) {
        prefs.edit().putString(KEY_DATA, arr.toString()).apply();
    }
}
