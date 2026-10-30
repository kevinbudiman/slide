package trikita.slide.middleware;

import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import trikita.jedux.Action;
import trikita.jedux.Store;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.State;
import trikita.slide.ui.Style;

public class WindowController implements Store.Middleware<Action<ActionType, ?>, State> {
    private Window mWindow;

    public void setWindow(Window w) {
        mWindow = w;
        if (w != null) {
            State state = App.getState();
            setStatusBarColor(state);
            if (state.presentationMode()) {
                enterPresentation();
            } else {
                exitPresentation();
            }
        }
    }

    @Override
    public void dispatch(Store<Action<ActionType, ?>, State> store, Action<ActionType, ?> action,
                         Store.NextDispatcher<Action<ActionType, ?>> next) {
        next.dispatch(action);
        if (mWindow == null) {
            return;
        }
        switch (action.type) {
            case SET_COLOR_SCHEME:
                setStatusBarColor(store.getState());
                break;
            case OPEN_PRESENTATION:
                enterPresentation();
                break;
            case CLOSE_PRESENTATION:
                exitPresentation();
                break;
        }
    }

    private void setStatusBarColor(State state) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            mWindow.setStatusBarColor(Style.COLOR_SCHEMES[state.colorScheme()][1]);
        }
    }

    private void enterPresentation() {
        mWindow.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWindow.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = mWindow.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            mWindow.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void exitPresentation() {
        mWindow.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWindow.setDecorFitsSystemWindows(true);
            WindowInsetsController controller = mWindow.getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    // Edge-to-edge: the bars sit over the editor background, so their
                    // icons must contrast with it.
                    int mode = mWindow.getContext().getResources().getConfiguration().uiMode
                            & Configuration.UI_MODE_NIGHT_MASK;
                    int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(
                            mode == Configuration.UI_MODE_NIGHT_YES ? 0 : light, light);
                }
            }
        } else {
            mWindow.getDecorView().setSystemUiVisibility(0);
        }
    }
}
