package trikita.slide.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import trikita.jedux.Action;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.R;
import trikita.slide.Slide;
import trikita.slide.State;
import trikita.slide.UndoHistory;

public class MainLayout extends FrameLayout {

    private static final int MATCH = LayoutParams.MATCH_PARENT;
    private static final int WRAP = LayoutParams.WRAP_CONTENT;

    private final UndoHistory mHistory;

    private final View mEditorScreen;
    private Editor mEditor;
    private Preview mEditorPreview;
    private boolean mUpdatingEditor;

    private final View mPresentationScreen;
    private Preview mPresentationPreview;
    private ImageButton mPresentationClose;

    private final View mPresenterScreen;
    private TextView mPresenterCounter;
    private TextView mPresenterTimer;
    private Preview mPresenterCurrent;
    private Preview mPresenterNext;
    private TextView mPresenterNotes;
    private long mPresentationStartedAt;
    private final Runnable mTimerTick = this::tickTimer;

    private boolean mExternalDisplay = false;
    private State mState;

    public MainLayout(Context c, UndoHistory history, View.OnClickListener onMenu) {
        super(c);
        mHistory = history;
        mEditorScreen = buildEditor(c, onMenu);
        mPresentationScreen = buildPresentation(c);
        mPresenterScreen = buildPresenter(c);
        addView(mEditorScreen, MATCH, MATCH);
        addView(mPresentationScreen, MATCH, MATCH);
        addView(mPresenterScreen, MATCH, MATCH);
    }

    public void setExternalDisplay(boolean connected) {
        if (mExternalDisplay != connected) {
            mExternalDisplay = connected;
            if (mState != null) {
                render(mState);
            }
        }
    }

    public void render(State s) {
        State previous = mState;
        mState = s;
        boolean presenting = s.presentationMode();
        boolean presenter = presenting && mExternalDisplay;
        mEditorScreen.setVisibility(presenting ? GONE : VISIBLE);
        mPresentationScreen.setVisibility(presenting && !mExternalDisplay ? VISIBLE : GONE);
        mPresenterScreen.setVisibility(presenter ? VISIBLE : GONE);

        renderEditor(previous, s);

        int[] colors = Style.scheme(s.colorScheme());
        mPresentationScreen.setBackgroundColor(colors[1]);
        mPresentationPreview.setState(s);
        mPresentationClose.setVisibility(s.toolbarShown() ? VISIBLE : GONE);
        mPresentationClose.setBackgroundResource(colors[2]);
        Style.tint(mPresentationClose, colors[3]);

        if (presenting && (previous == null || !previous.presentationMode())) {
            mPresentationStartedAt = SystemClock.elapsedRealtime();
        }
        mPresenterCounter.setText((s.page() + 1) + " / " + Slide.count(s.text()));
        mPresenterCurrent.setState(s);
        mPresenterNext.setState(s);
        List<Slide> slides = s.slides();
        String notes = s.page() < slides.size() ? slides.get(s.page()).notes : "";
        mPresenterNotes.setText(notes);
        mPresenterNotes.setVisibility(notes.isEmpty() ? GONE : VISIBLE);
        mPresenterTimer.removeCallbacks(mTimerTick);
        if (presenter) {
            tickTimer();
        }
    }

    private void renderEditor(State previous, State s) {
        if (previous == null) {
            mHistory.sync(s.text(), s.cursor());
        } else if (!TextUtils.equals(previous.uri(), s.uri())) {
            mHistory.reset(s.text(), s.cursor());
        }
        if (!TextUtils.equals(mEditor.getText(), s.text())) {
            // Changed outside the editor (inserted image, opened file): keep it undoable
            // unless a different document was loaded.
            if (previous != null && TextUtils.equals(previous.uri(), s.uri())) {
                mHistory.record(s.text(), s.cursor(), SystemClock.uptimeMillis());
            }
            setEditorText(s.text(), s.cursor());
        }
        mEditorPreview.setState(s);
    }

    private void setEditorText(String text, int cursor) {
        mUpdatingEditor = true;
        mEditor.setText(text);
        mEditor.setSelection(Math.max(0, Math.min(cursor, text.length())));
        mUpdatingEditor = false;
    }

    public boolean canUndo() {
        return mHistory.canUndo();
    }

    public boolean canRedo() {
        return mHistory.canRedo();
    }

    public void undo() {
        apply(mHistory.undo());
    }

    public void redo() {
        apply(mHistory.redo());
    }

    private void apply(UndoHistory.Entry entry) {
        if (entry == null) {
            return;
        }
        setEditorText(entry.text, entry.cursor);
        App.dispatch(new Action<>(ActionType.SET_TEXT, entry.text));
        App.dispatch(new Action<>(ActionType.SET_CURSOR, entry.cursor));
    }

    // Moves the editor cursor to the start of the given slide.
    public void jumpToPage(int page) {
        mEditor.requestFocus();
        mEditor.setSelection(Slide.offsetOf(mEditor.getText().toString(), page));
    }

    private void tickTimer() {
        long seconds = (SystemClock.elapsedRealtime() - mPresentationStartedAt) / 1000;
        String text = seconds >= 3600
                ? String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
        mPresenterTimer.setText(text);
        mPresenterTimer.postDelayed(mTimerTick, 1000 - (SystemClock.elapsedRealtime() - mPresentationStartedAt) % 1000);
    }

    // Tablets in landscape get the editor and a large preview side by side.
    private static boolean isWide(Context c) {
        Configuration config = c.getResources().getConfiguration();
        return config.smallestScreenWidthDp >= 600 && config.screenWidthDp >= 840;
    }

    private View buildEditor(Context c, View.OnClickListener onMenu) {
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(Style.color(c, R.color.editor_background));
        padForInsets(root, Style.dip(c, 8), true);

        mEditor = new Editor(c);
        mEditor.setGravity(Gravity.TOP | Gravity.START);
        mEditor.setTextColor(Style.color(c, R.color.editor_text));
        mEditor.setTypeface(Typeface.create(Style.SLIDE_FONT, Typeface.NORMAL));
        mEditor.setBackground(null);
        mEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!mUpdatingEditor) {
                    String text = s.toString();
                    int cursor = mEditor.getSelectionStart();
                    mHistory.record(text, cursor, SystemClock.uptimeMillis());
                    App.dispatch(new Action<>(ActionType.SET_TEXT, text));
                    App.dispatch(new Action<>(ActionType.SET_CURSOR, cursor));
                }
            }
        });
        mEditor.setOnSelectionChangedListener(pos -> {
            if (!mUpdatingEditor) {
                App.dispatch(new Action<>(ActionType.SET_CURSOR, pos));
            }
        });
        mEditor.setOnUndoRedoListener(redo -> {
            if (redo) {
                redo();
            } else {
                undo();
            }
        });

        mEditorPreview = new Preview(c);
        mEditorPreview.setContentDescription(c.getString(R.string.cd_start_presentation));
        mEditorPreview.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.OPEN_PRESENTATION)));
        FrameLayout frame = previewFrame(c);

        if (isWide(c)) {
            LinearLayout row = new LinearLayout(c);
            row.addView(mEditor, new LinearLayout.LayoutParams(0, MATCH, 1f));
            FrameLayout pane = new FrameLayout(c);
            int padding = Style.dip(c, 24);
            pane.setPadding(padding, padding, padding, padding);
            frame.addView(mEditorPreview, new LayoutParams(MATCH, WRAP));
            pane.addView(frame, new LayoutParams(MATCH, WRAP, Gravity.CENTER));
            row.addView(pane, new LinearLayout.LayoutParams(0, MATCH, 1f));
            root.addView(row, MATCH, MATCH);
        } else {
            root.addView(mEditor, MATCH, MATCH);
            frame.addView(mEditorPreview, new LayoutParams(Style.dip(c, 144), WRAP));
            LayoutParams frameParams = new LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
            int margin = Style.dip(c, 12);
            frameParams.setMargins(margin, margin, margin, margin);
            root.addView(frame, frameParams);
        }

        ImageButton menu = new ImageButton(c);
        menu.setImageResource(R.drawable.ic_menu);
        Style.tint(menu, Style.color(c, R.color.editor_menu_button));
        Style.borderlessRipple(menu);
        menu.setContentDescription(c.getString(R.string.cd_menu));
        menu.setOnClickListener(onMenu);
        int size = Style.dip(c, 48);
        root.addView(menu, new LayoutParams(size, size, Gravity.TOP | Gravity.END));
        return root;
    }

    private View buildPresentation(Context c) {
        FrameLayout root = new FrameLayout(c);

        mPresentationPreview = new Preview(c);
        root.addView(mPresentationPreview, new LayoutParams(MATCH, WRAP, Gravity.CENTER));

        LinearLayout zones = new LinearLayout(c);
        zones.addView(touchZone(c, ActionType.PREV_PAGE), new LinearLayout.LayoutParams(0, MATCH, 1f));
        zones.addView(touchZone(c, ActionType.TOGGLE_TOOLBAR), new LinearLayout.LayoutParams(0, MATCH, 1f));
        zones.addView(touchZone(c, ActionType.NEXT_PAGE), new LinearLayout.LayoutParams(0, MATCH, 1f));
        root.addView(zones, MATCH, MATCH);

        mPresentationClose = closeButton(c);
        int size = Style.dip(c, 54);
        LayoutParams closeParams = new LayoutParams(size, size, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        closeParams.bottomMargin = Style.dip(c, 25);
        root.addView(mPresentationClose, closeParams);
        return root;
    }

    // Shown on the phone while the slide itself is on an external display.
    private View buildPresenter(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Style.PRESENTER_BACKGROUND);
        padForInsets(root, Style.dip(c, 16), false);

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        mPresenterCounter = presenterLabel(c, 18);
        header.addView(mPresenterCounter, new LinearLayout.LayoutParams(0, WRAP, 1f));
        mPresenterTimer = presenterLabel(c, 18);
        mPresenterTimer.setContentDescription(c.getString(R.string.cd_reset_timer));
        mPresenterTimer.setOnClickListener(v -> {
            mPresentationStartedAt = SystemClock.elapsedRealtime();
            mPresenterTimer.removeCallbacks(mTimerTick);
            tickTimer();
        });
        int timerPadding = Style.dip(c, 12);
        mPresenterTimer.setPadding(timerPadding, timerPadding, timerPadding, timerPadding);
        header.addView(mPresenterTimer, new LinearLayout.LayoutParams(WRAP, WRAP));
        ImageButton close = new ImageButton(c);
        close.setImageResource(R.drawable.ic_close);
        Style.tint(close, Style.PRESENTER_TEXT);
        Style.borderlessRipple(close);
        close.setContentDescription(c.getString(R.string.cd_close_presentation));
        close.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.CLOSE_PRESENTATION)));
        int size = Style.dip(c, 48);
        header.addView(close, new LinearLayout.LayoutParams(size, size));
        root.addView(header, MATCH, WRAP);

        FrameLayout current = new FrameLayout(c);
        mPresenterCurrent = new Preview(c);
        current.addView(mPresenterCurrent, new LayoutParams(MATCH, WRAP, Gravity.CENTER));
        LinearLayout zones = new LinearLayout(c);
        zones.addView(touchZone(c, ActionType.PREV_PAGE), new LinearLayout.LayoutParams(0, MATCH, 1f));
        zones.addView(touchZone(c, ActionType.NEXT_PAGE), new LinearLayout.LayoutParams(0, MATCH, 1f));
        current.addView(zones, MATCH, MATCH);
        LinearLayout.LayoutParams currentParams = new LinearLayout.LayoutParams(MATCH, 0, 2f);
        currentParams.topMargin = Style.dip(c, 8);
        root.addView(current, currentParams);

        TextView nextLabel = presenterLabel(c, 14);
        nextLabel.setText(R.string.next_slide);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(MATCH, WRAP);
        labelParams.topMargin = Style.dip(c, 16);
        root.addView(nextLabel, labelParams);

        FrameLayout next = new FrameLayout(c);
        mPresenterNext = new Preview(c);
        mPresenterNext.setPageOffset(1);
        mPresenterNext.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.NEXT_PAGE)));
        next.addView(mPresenterNext, new LayoutParams(MATCH, WRAP, Gravity.CENTER));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        nextParams.topMargin = Style.dip(c, 8);
        root.addView(next, nextParams);

        ScrollView notesScroll = new ScrollView(c);
        mPresenterNotes = presenterLabel(c, 18);
        mPresenterNotes.setTextColor(0xffffffff);
        notesScroll.addView(mPresenterNotes);
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        notesParams.topMargin = Style.dip(c, 16);
        root.addView(notesScroll, notesParams);
        return root;
    }

    private static TextView presenterLabel(Context c, int sp) {
        TextView label = new TextView(c);
        label.setTextColor(Style.PRESENTER_TEXT);
        label.setTextSize(sp);
        return label;
    }

    private static View touchZone(Context c, ActionType action) {
        View zone = new View(c);
        zone.setOnClickListener(v -> App.dispatch(new Action<>(action)));
        return zone;
    }

    private static ImageButton closeButton(Context c) {
        ImageButton close = new ImageButton(c);
        close.setImageResource(R.drawable.ic_close);
        int padding = Style.dip(c, 12);
        close.setPadding(padding, padding, padding, padding);
        close.setContentDescription(c.getString(R.string.cd_close_presentation));
        close.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.CLOSE_PRESENTATION)));
        return close;
    }

    private static FrameLayout previewFrame(Context c) {
        FrameLayout frame = new FrameLayout(c);
        frame.setBackgroundResource(R.drawable.preview_bg);
        frame.setPadding(Style.dip(c, 1), Style.dip(c, 1), Style.dip(c, 2), Style.dip(c, 2));
        return frame;
    }

    // Since Android 15 the window is always edge-to-edge and the keyboard no longer
    // resizes it, so system bars, cutouts and (when asked) the keyboard become padding.
    private static void padForInsets(View v, int extra, boolean includeIme) {
        v.setPadding(extra, extra, extra, extra);
        v.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int types = WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout();
                if (includeIme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    types |= WindowInsets.Type.ime();
                }
                Insets i = insets.getInsets(types);
                left = i.left;
                top = i.top;
                right = i.right;
                bottom = i.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left + extra, top + extra, right + extra, bottom + extra);
            return insets;
        });
    }
}
