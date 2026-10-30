package trikita.slide;

import java.util.ArrayDeque;
import java.util.Deque;

// Undo/redo for the editor text. Edits that follow each other within
// MERGE_WINDOW_MS are merged into one step, so undo removes a burst of typing
// rather than single characters.
public final class UndoHistory {

    static final long MERGE_WINDOW_MS = 1000;
    private static final int MAX_STEPS = 100;

    public static final class Entry {
        public final String text;
        public final int cursor;

        Entry(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    private final Deque<Entry> mUndo = new ArrayDeque<>();
    private final Deque<Entry> mRedo = new ArrayDeque<>();
    private Entry mCurrent;
    private long mLastEditAt = Long.MIN_VALUE;

    public void reset(String text, int cursor) {
        mUndo.clear();
        mRedo.clear();
        mCurrent = new Entry(text, cursor);
        mLastEditAt = Long.MIN_VALUE;
    }

    // Keeps the history if it already describes this text (e.g. after the
    // activity was recreated), otherwise starts over.
    public void sync(String text, int cursor) {
        if (mCurrent == null || !mCurrent.text.equals(text)) {
            reset(text, cursor);
        }
    }

    public void record(String text, int cursor, long now) {
        if (mCurrent == null) {
            reset(text, cursor);
            return;
        }
        if (text.equals(mCurrent.text)) {
            mCurrent = new Entry(text, cursor);
            return;
        }
        if (mLastEditAt == Long.MIN_VALUE || now - mLastEditAt > MERGE_WINDOW_MS) {
            mUndo.push(mCurrent);
            if (mUndo.size() > MAX_STEPS) {
                mUndo.removeLast();
            }
        }
        mCurrent = new Entry(text, cursor);
        mLastEditAt = now;
        mRedo.clear();
    }

    public boolean canUndo() {
        return !mUndo.isEmpty();
    }

    public boolean canRedo() {
        return !mRedo.isEmpty();
    }

    public Entry undo() {
        if (mUndo.isEmpty()) {
            return null;
        }
        mRedo.push(mCurrent);
        mCurrent = mUndo.pop();
        mLastEditAt = Long.MIN_VALUE;
        return mCurrent;
    }

    public Entry redo() {
        if (mRedo.isEmpty()) {
            return null;
        }
        mUndo.push(mCurrent);
        mCurrent = mRedo.pop();
        mLastEditAt = Long.MIN_VALUE;
        return mCurrent;
    }
}
