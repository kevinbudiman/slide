package trikita.slide.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import trikita.jedux.Action;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.PdfExporter;
import trikita.slide.PdfShareProvider;
import trikita.slide.R;
import trikita.slide.State;

public class MainActivity extends Activity {

    private static final int NEW_DOCUMENT_REQUEST_CODE = 41;
    private static final int SAVE_DOCUMENT_AS_REQUEST_CODE = 42;
    private static final int OPEN_DOCUMENT_REQUEST_CODE = 43;
    private static final int PICK_IMAGE_REQUEST_CODE = 44;
    private static final int EXPORT_PDF_REQUEST_CODE = 46;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private MainLayout mLayout;
    private DisplayManager mDisplayManager;
    private SlidePresentation mPresentation;
    private Runnable mUnsubscribe;
    private Object mBackCallback;
    private boolean mBackCallbackRegistered;

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { updatePresentation(); }
        @Override public void onDisplayRemoved(int displayId) { updatePresentation(); }
        @Override public void onDisplayChanged(int displayId) { updatePresentation(); }
    };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        mLayout = new MainLayout(this, App.getUndoHistory(), this::showMenu);
        setContentView(mLayout);
        App.getWindowController().setWindow(getWindow());
        mUnsubscribe = App.subscribe(this::render);
        render();
    }

    @Override
    public void onResume() {
        super.onResume();
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
        updatePresentation();
        if (App.getState().presentationMode()) {
            App.dispatch(new Action<>(ActionType.OPEN_PRESENTATION));
        }
    }

    @Override
    protected void onPause() {
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        dismissPresentation();
        App.flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mUnsubscribe.run();
        App.getWindowController().setWindow(null);
        super.onDestroy();
    }

    private void render() {
        State state = App.getState();
        mLayout.render(state);
        if (mPresentation != null) {
            mPresentation.render(state);
        }
        updateBackCallback(state);
    }

    // With predictive back (targetSdk 36 on Android 16+) onBackPressed() is no longer
    // called, so leaving presentation mode needs an OnBackInvokedCallback. It is only
    // registered while presenting, so the editor keeps the system back-to-home animation.
    private void updateBackCallback(State state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        boolean wanted = state.presentationMode();
        if (wanted == mBackCallbackRegistered) {
            return;
        }
        if (mBackCallback == null) {
            mBackCallback = (OnBackInvokedCallback) () ->
                    App.dispatch(new Action<>(ActionType.CLOSE_PRESENTATION));
        }
        OnBackInvokedCallback callback = (OnBackInvokedCallback) mBackCallback;
        if (wanted) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        } else {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);
        }
        mBackCallbackRegistered = wanted;
    }

    @Override
    public void onBackPressed() {
        if (App.getState().presentationMode()) {
            App.dispatch(new Action<>(ActionType.CLOSE_PRESENTATION));
        } else {
            super.onBackPressed();
        }
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.overflow_popup, menu.getMenu());
        menu.getMenu().findItem(R.id.menu_recent).setVisible(!App.getRecentDocuments().list().isEmpty());
        menu.getMenu().findItem(R.id.menu_undo).setEnabled(mLayout.canUndo());
        menu.getMenu().findItem(R.id.menu_redo).setEnabled(mLayout.canRedo());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_undo) {
                mLayout.undo();
            } else if (id == R.id.menu_redo) {
                mLayout.redo();
            } else if (id == R.id.menu_overview) {
                OverviewDialog.show(this, App.getState(), mLayout::jumpToPage);
            } else if (id == R.id.menu_share_pdf) {
                sharePdf();
            } else if (id == R.id.menu_new) {
                confirmDiscard(() -> createDocument(NEW_DOCUMENT_REQUEST_CODE, "text/plain",
                        getString(R.string.default_new_doc_name)));
            } else if (id == R.id.menu_open) {
                confirmDiscard(() -> openDocument("text/*", OPEN_DOCUMENT_REQUEST_CODE));
            } else if (id == R.id.menu_recent) {
                confirmDiscard(this::showRecentDocuments);
            } else if (id == R.id.menu_save_as) {
                createDocument(SAVE_DOCUMENT_AS_REQUEST_CODE, "text/plain",
                        getString(R.string.default_new_doc_name));
            } else if (id == R.id.menu_insert_image) {
                openDocument("image/*", PICK_IMAGE_REQUEST_CODE);
            } else if (id == R.id.menu_style) {
                StyleDialog.show(this, App.getState());
            } else if (id == R.id.menu_export_pdf) {
                createDocument(EXPORT_PDF_REQUEST_CODE, "application/pdf", pdfName());
            }
            return true;
        });
        menu.show();
    }

    // Opening or creating a document replaces the editor text. That is only lossy
    // when the text is not attached to a file.
    private void confirmDiscard(Runnable action) {
        State state = App.getState();
        boolean unsaved = state.uri() == null
                && !state.text().trim().isEmpty()
                && !state.text().equals(getString(R.string.tutorial_text));
        if (!unsaved) {
            action.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.dlg_discard_message)
                .setPositiveButton(R.string.btn_discard, (d, w) -> action.run())
                .setNeutralButton(R.string.om_save_as, (d, w) -> createDocument(
                        SAVE_DOCUMENT_AS_REQUEST_CODE, "text/plain", getString(R.string.default_new_doc_name)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createDocument(int requestCode, String mimeType, String title) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, title);
        startPicker(intent, requestCode);
    }

    private void openDocument(String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        startPicker(intent, requestCode);
    }

    private void startPicker(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    // Renders the PDF into the cache and hands it to the share sheet.
    private void sharePdf() {
        State state = App.getState();
        String name = pdfName() + ".pdf";
        Toast.makeText(this, R.string.preparing_pdf, Toast.LENGTH_SHORT).show();
        App.io().execute(() -> {
            File dir = PdfShareProvider.directory(this);
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    f.delete();
                }
            }
            dir.mkdirs();
            File file = new File(dir, name);
            boolean ok;
            try (OutputStream out = new FileOutputStream(file)) {
                PdfExporter.write(this, state, out);
                ok = true;
            } catch (IOException e) {
                e.printStackTrace();
                ok = false;
            }
            boolean written = ok;
            mMain.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!written) {
                    Toast.makeText(this, R.string.failed_export_pdf, Toast.LENGTH_LONG).show();
                    return;
                }
                Uri uri = PdfShareProvider.uriFor(this, file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/pdf");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(send, getString(R.string.om_share_pdf)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.failed_export_pdf, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // Presentation clickers send page/arrow keys; volume keys work as a fallback.
    // Handled before the views so focus navigation does not swallow arrow keys.
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (App.getState().presentationMode()) {
            ActionType action = presentationKeyAction(event.getKeyCode());
            if (action != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    App.dispatch(new Action<>(action));
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private static ActionType presentationKeyAction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_N:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return ActionType.NEXT_PAGE;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DEL:
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_VOLUME_UP:
                return ActionType.PREV_PAGE;
            case KeyEvent.KEYCODE_ESCAPE:
                return ActionType.CLOSE_PRESENTATION;
            default:
                return null;
        }
    }

    private String pdfName() {
        return getString(R.string.pdf_name_prefix)
                + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date());
    }

    private void showRecentDocuments() {
        List<String> uris = App.getRecentDocuments().list();
        App.io().execute(() -> {
            List<String> available = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (String uri : uris) {
                String name = displayName(Uri.parse(uri));
                if (name == null) {
                    App.getRecentDocuments().remove(uri);
                } else {
                    available.add(uri);
                    names.add(name);
                }
            }
            mMain.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (available.isEmpty()) {
                    Toast.makeText(this, R.string.no_recent_documents, Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(this)
                        .setTitle(R.string.om_recent)
                        .setItems(names.toArray(new String[0]), (d, which) ->
                                App.dispatch(new Action<>(ActionType.OPEN_DOCUMENT, available.get(which))))
                        .show();
            });
        });
    }

    // Returns null when the document is gone or no longer accessible.
    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                return name != null ? name : uri.getLastPathSegment();
            }
        } catch (SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updatePresentation() {
        Display[] displays = mDisplayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Display display = displays.length > 0 ? displays[0] : null;

        if (mPresentation != null
                && (display == null || mPresentation.getDisplay().getDisplayId() != display.getDisplayId())) {
            dismissPresentation();
        }

        if (mPresentation == null && display != null) {
            SlidePresentation presentation = new SlidePresentation(this, display);
            presentation.setOnDismissListener(dialog -> {
                if (mPresentation == dialog) {
                    mPresentation = null;
                    mLayout.setExternalDisplay(false);
                }
            });
            try {
                presentation.show();
                presentation.render(App.getState());
                mPresentation = presentation;
            } catch (WindowManager.InvalidDisplayException e) {
                e.printStackTrace();
            }
        }

        mLayout.setExternalDisplay(mPresentation != null);
    }

    private void dismissPresentation() {
        if (mPresentation != null) {
            SlidePresentation presentation = mPresentation;
            mPresentation = null;
            presentation.dismiss();
            mLayout.setExternalDisplay(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        String uri = data.getData().toString();
        switch (requestCode) {
            case NEW_DOCUMENT_REQUEST_CODE:
                App.dispatch(new Action<>(ActionType.NEW_DOCUMENT, uri));
                break;
            case SAVE_DOCUMENT_AS_REQUEST_CODE:
                App.dispatch(new Action<>(ActionType.SAVE_DOCUMENT_AS, uri));
                break;
            case OPEN_DOCUMENT_REQUEST_CODE:
                App.dispatch(new Action<>(ActionType.OPEN_DOCUMENT, uri));
                break;
            case PICK_IMAGE_REQUEST_CODE:
                App.dispatch(new Action<>(ActionType.INSERT_IMAGE, uri));
                break;
            case EXPORT_PDF_REQUEST_CODE:
                App.dispatch(new Action<>(ActionType.EXPORT_PDF, uri));
                break;
        }
    }
}
