package trikita.slide.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import trikita.jedux.Action;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.middleware.StorageController;

public class MainActivity extends Activity {

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
        mLayout = new MainLayout(this);
        setContentView(mLayout);
        App.getWindowController().setWindow(getWindow());
        mUnsubscribe = App.subscribe(this::updateBackCallback);
        updateBackCallback();
    }

    // With predictive back (targetSdk 36 on Android 16+) onBackPressed() is no longer
    // called, so leaving presentation mode needs an OnBackInvokedCallback. It is only
    // registered while presenting, so the editor keeps the system back-to-home animation.
    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        boolean wanted = App.getState().presentationMode();
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
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mUnsubscribe.run();
        App.getWindowController().setWindow(null);
        super.onDestroy();
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
    public void onBackPressed() {
        if (App.getState().presentationMode()) {
            App.dispatch(new Action<>(ActionType.CLOSE_PRESENTATION));
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == StorageController.EXPORT_PDF_REQUEST_CODE) {
            Uri uri = data.getData();
            App.dispatch(new Action<>(ActionType.EXPORT_PDF, uri));
        } else if (requestCode == StorageController.PICK_IMAGE_REQUEST_CODE) {
            Uri uri = data.getData();
            App.dispatch(new Action<>(ActionType.INSERT_IMAGE, uri));
        } else if (requestCode == StorageController.OPEN_DOCUMENT_REQUEST_CODE) {
            Uri uri = data.getData();
            App.dispatch(new Action<>(ActionType.LOAD_DOCUMENT, uri));
        }
    }
}
