package trikita.slide;

import android.app.Application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import trikita.jedux.Action;
import trikita.jedux.Store;
import trikita.slide.middleware.PersistanceController;
import trikita.slide.middleware.StorageController;
import trikita.slide.middleware.WindowController;

public class App extends Application {
    private static App instance;

    private Store<Action<ActionType, ?>, State> store;
    private WindowController windowController;
    private PersistanceController persistanceController;
    private StorageController storageController;
    private RecentDocuments recentDocuments;
    // Lives here so it survives activity recreation (rotation, dark mode).
    private final UndoHistory undoHistory = new UndoHistory();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        App.instance = this;

        this.windowController = new WindowController();
        this.recentDocuments = new RecentDocuments(this);
        this.persistanceController = new PersistanceController(this);
        this.storageController = new StorageController(this, io);

        State initialState = persistanceController.getSavedState();
        if (initialState == null) {
            initialState = State.initial(getString(R.string.tutorial_text));
        }

        this.store = new Store<>(new State.Reducer(),
                initialState,
                persistanceController,
                windowController,
                storageController);
    }

    public static WindowController getWindowController() {
        return instance.windowController;
    }

    public static UndoHistory getUndoHistory() {
        return instance.undoHistory;
    }

    public static RecentDocuments getRecentDocuments() {
        return instance.recentDocuments;
    }

    // Single background thread for file I/O, so writes keep their order.
    public static ExecutorService io() {
        return instance.io;
    }

    public static State getState() {
        return instance.store.getState();
    }

    public static State dispatch(Action<ActionType, ?> action) {
        return instance.store.dispatch(action);
    }

    // Returns a Runnable that removes the subscription.
    public static Runnable subscribe(Runnable listener) {
        return instance.store.subscribe(listener);
    }

    // Writes pending state and document changes immediately.
    public static void flush() {
        instance.persistanceController.flush();
        instance.storageController.flush();
    }
}
