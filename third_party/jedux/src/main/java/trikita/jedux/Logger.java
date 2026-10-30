package trikita.jedux;

import android.util.Log;

/**
 * Sample middleware which log all actions and state changes in store.
 */
public class Logger<A, S> implements Store.Middleware<A, S> {

    private final String tag;

    public Logger(String tag) {
        this.tag = tag;
    }

    @Override
    public void dispatch(Store<A, S> store, A action, Store.NextDispatcher<A> next) {
        Log.d(tag, "--> " + action.toString());
        next.dispatch(action);
        Log.d(tag, "<-- " + store.getState().toString());
    }
}
