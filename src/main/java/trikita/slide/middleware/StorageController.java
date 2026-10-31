package trikita.slide.middleware;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import trikita.jedux.Action;
import trikita.jedux.Store;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.PdfExporter;
import trikita.slide.R;
import trikita.slide.State;

// Keeps the attached text document in sync with the editor and performs all
// document I/O on a background executor.
public class StorageController implements Store.Middleware<Action<ActionType, ?>, State> {

    private static final long SAVE_DELAY_MS = 3000;

    private final Context mContext;
    private final Executor mIo;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private Store<Action<ActionType, ?>, State> mStore;
    private boolean mSavePending;
    private final Runnable mSaver = this::flush;

    public StorageController(Context c, Executor io) {
        mContext = c.getApplicationContext();
        mIo = io;
    }

    @Override
    public void dispatch(Store<Action<ActionType, ?>, State> store, Action<ActionType, ?> action,
                         Store.NextDispatcher<Action<ActionType, ?>> next) {
        mStore = store;
        State before = store.getState();
        switch (action.type) {
            case OPEN_DOCUMENT:
                flush();
                takePermission((String) action.value, true);
                load((String) action.value);
                return;
            case EXPORT_PDF:
                exportPdf(before, Uri.parse((String) action.value));
                return;
            case NEW_DOCUMENT:
                flush();
                break;
            case SAVE_DOCUMENT_AS:
                flush();
                takePermission((String) action.value, true);
                break;
            case DOCUMENT_LOADED:
                flush();
                break;
            case INSERT_IMAGE:
                takePermission((String) action.value, false);
                break;
            default:
                break;
        }

        next.dispatch(action);
        State after = store.getState();

        switch (action.type) {
            case NEW_DOCUMENT:
                break;
            case SAVE_DOCUMENT_AS:
                App.getRecentDocuments().add(after.uri());
                save(after);
                break;
            case DOCUMENT_LOADED:
                App.getRecentDocuments().add(after.uri());
                break;
            default:
                if (after.uri() != null && !after.text().equals(before.text())) {
                    mSavePending = true;
                    mMain.removeCallbacks(mSaver);
                    mMain.postDelayed(mSaver, SAVE_DELAY_MS);
                }
        }
    }

    // Writes any pending change to the attached document right away.
    public void flush() {
        mMain.removeCallbacks(mSaver);
        if (mSavePending && mStore != null) {
            mSavePending = false;
            save(mStore.getState());
        }
    }

    // Persisting lets the app reopen documents and images after a restart.
    private void takePermission(String uri, boolean write) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (write) {
            flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        try {
            mContext.getContentResolver().takePersistableUriPermission(Uri.parse(uri), flags);
        } catch (SecurityException e) {
            // Not every provider offers persistable grants; the current grant still works.
            e.printStackTrace();
        }
    }

    private void save(State state) {
        if (state.uri() == null) {
            return;
        }
        Uri uri = Uri.parse(state.uri());
        String text = state.text();
        byte[] data = (text.endsWith("\n") ? text : text + "\n").getBytes(StandardCharsets.UTF_8);
        mIo.execute(() -> {
            try (OutputStream out = openForWriting(mContext.getContentResolver(), uri)) {
                out.write(data);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                e.printStackTrace();
                toast(R.string.failed_save_doc);
            }
        });
    }

    private void load(String uriString) {
        Uri uri = Uri.parse(uriString);
        mIo.execute(() -> {
            String text = null;
            try (InputStream in = mContext.getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    text = readText(in);
                }
            } catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
            String loaded = text;
            mMain.post(() -> {
                if (loaded != null) {
                    App.dispatch(new Action<>(ActionType.DOCUMENT_LOADED,
                            new State.LoadedDocument(uriString, loaded)));
                } else {
                    Toast.makeText(mContext, R.string.failed_open_doc, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void exportPdf(State state, Uri uri) {
        mIo.execute(() -> {
            try (OutputStream out = openForWriting(mContext.getContentResolver(), uri)) {
                PdfExporter.write(mContext, state, out);
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                e.printStackTrace();
                toast(R.string.failed_export_pdf);
            }
        });
    }

    // "wt" truncates existing content; some providers only support "w".
    public static OutputStream openForWriting(ContentResolver resolver, Uri uri) throws IOException {
        OutputStream out;
        try {
            out = resolver.openOutputStream(uri, "wt");
        } catch (FileNotFoundException | IllegalArgumentException | UnsupportedOperationException e) {
            out = resolver.openOutputStream(uri, "w");
        }
        if (out == null) {
            throw new FileNotFoundException(uri.toString());
        }
        return out;
    }

    // Line endings are normalized and the single trailing newline added on save is
    // dropped, so saving and reopening a document leaves it unchanged.
    static String readText(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }

    private void toast(int message) {
        mMain.post(() -> Toast.makeText(mContext, message, Toast.LENGTH_LONG).show());
    }
}
