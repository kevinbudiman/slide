package trikita.slide;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

// Most recently used document URIs, newest first.
public final class RecentDocuments {

    private static final int MAX = 10;
    private static final String KEY = "uris";

    private final SharedPreferences mPreferences;

    public RecentDocuments(Context c) {
        mPreferences = c.getSharedPreferences("recent", Context.MODE_PRIVATE);
    }

    public List<String> list() {
        String stored = mPreferences.getString(KEY, "");
        List<String> uris = new ArrayList<>();
        for (String uri : stored.split("\n")) {
            if (!uri.isEmpty()) {
                uris.add(uri);
            }
        }
        return uris;
    }

    public void add(String uri) {
        if (uri == null) {
            return;
        }
        List<String> uris = list();
        uris.remove(uri);
        uris.add(0, uri);
        while (uris.size() > MAX) {
            uris.remove(uris.size() - 1);
        }
        store(uris);
    }

    public void remove(String uri) {
        List<String> uris = list();
        if (uris.remove(uri)) {
            store(uris);
        }
    }

    private void store(List<String> uris) {
        mPreferences.edit().putString(KEY, TextUtils.join("\n", uris)).apply();
    }
}
