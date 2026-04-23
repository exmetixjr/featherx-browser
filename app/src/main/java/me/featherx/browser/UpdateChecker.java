package me.featherx.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String RELEASES_URL =
        "https://api.github.com/repos/exmetixjr/featherx-browser/releases/latest";
    private static final String DOWNLOAD_PAGE =
        "https://github.com/exmetixjr/featherx-browser/releases/latest";
    private static final String PREFS = "featherx_updater";
    private static final String KEY_LAST_TAG = "last_checked_tag";

    public static void checkForUpdates(Context context, boolean showNoUpdateMsg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lastTag = prefs.getString(KEY_LAST_TAG, "");

        new AsyncTask<Void, Void, UpdateResult>() {
            @Override
            protected UpdateResult doInBackground(Void... voids) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);

                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject release = new JSONObject(sb.toString());
                    String tagName = release.getString("tag_name");
                    String body = release.optString("body", "New version available");

                    if (!tagName.equals(lastTag)) {
                        return new UpdateResult(true, tagName, body);
                    }
                    return new UpdateResult(false, tagName, null);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(UpdateResult result) {
                if (result == null) {
                    if (showNoUpdateMsg)
                        Toast.makeText(context, "Update check failed", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.hasUpdate) {
                    showUpdateDialog(context, result);
                } else if (showNoUpdateMsg) {
                    Toast.makeText(context, "Already up to date!", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    private static void showUpdateDialog(Context context, UpdateResult result) {
        new android.app.AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage(result.body)
            .setPositiveButton("Download", (dialog, which) -> {
                context.startActivity(new android.content.Intent(
                    android.content.Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE)));
                // Save that we notified user about this release
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_LAST_TAG, result.tagName).apply();
            })
            .setNegativeButton("Later", (dialog, which) -> {
                // Save that we notified user, so we don't prompt again for this version
                SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                prefs.edit().putString(KEY_LAST_TAG, result.tagName).apply();
            })
            .show();
    }

    private static class UpdateResult {
        boolean hasUpdate;
        String tagName;
        String body;

        UpdateResult(boolean hasUpdate, String tagName, String body) {
            this.hasUpdate = hasUpdate;
            this.tagName = tagName;
            this.body = body;
        }
    }
}
