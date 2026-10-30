package trikita.slide.middleware;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import trikita.jedux.Action;
import trikita.jedux.Store;
import trikita.slide.ActionType;
import trikita.slide.State;

// Persists the state as JSON. The keys match what the earlier Gson/Immutables
// version wrote, so existing installs keep their presentation.
public class PersistanceController implements Store.Middleware<Action<ActionType, ?>, State> {

    private static final String KEY = "data";
    private static final long WRITE_DELAY_MS = 500;

    private final SharedPreferences mPreferences;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private State mPending;
    private final Runnable mWriter = this::flush;

    public PersistanceController(Context c) {
        mPreferences = c.getSharedPreferences("data", Context.MODE_PRIVATE);
    }

    public State getSavedState() {
        String json = mPreferences.getString(KEY, null);
        if (json == null) {
            return null;
        }
        try {
            JSONObject o = new JSONObject(json);
            String uri = o.isNull("uri") ? null : o.optString("uri", null);
            return new State(uri,
                    o.getString("text"),
                    o.optInt("page", 0),
                    o.optInt("cursor", 0),
                    o.optBoolean("presentationMode", false),
                    o.optBoolean("toolbarShown", true),
                    o.optInt("colorScheme", 0));
        } catch (JSONException e) {
            return null;
        }
    }

    @Override
    public void dispatch(Store<Action<ActionType, ?>, State> store, Action<ActionType, ?> action,
            Store.NextDispatcher<Action<ActionType, ?>> next) {
        State before = store.getState();
        next.dispatch(action);
        State after = store.getState();
        if (after != before) {
            mPending = after;
            mHandler.removeCallbacks(mWriter);
            mHandler.postDelayed(mWriter, WRITE_DELAY_MS);
        }
    }

    public void flush() {
        mHandler.removeCallbacks(mWriter);
        State s = mPending;
        mPending = null;
        if (s == null) {
            return;
        }
        try {
            JSONObject o = new JSONObject();
            if (s.uri() != null) {
                o.put("uri", s.uri());
            }
            o.put("text", s.text());
            o.put("page", s.page());
            o.put("cursor", s.cursor());
            o.put("presentationMode", s.presentationMode());
            o.put("toolbarShown", s.toolbarShown());
            o.put("colorScheme", s.colorScheme());
            mPreferences.edit().putString(KEY, o.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
