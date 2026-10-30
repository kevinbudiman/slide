package trikita.slide.ui;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.EditText;

public class Editor extends EditText {

    interface OnSelectionChangedListener {
        void onSelectionChanged(int position);
    }

    interface OnUndoRedoListener {
        void onUndoRedo(boolean redo);
    }

    private OnSelectionChangedListener mOnSelectionChangedListener;
    private OnUndoRedoListener mOnUndoRedoListener;

    public Editor(Context context) {
        super(context);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener l) {
        mOnSelectionChangedListener = l;
    }

    public void setOnUndoRedoListener(OnUndoRedoListener l) {
        mOnUndoRedoListener = l;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (mOnSelectionChangedListener != null) {
            mOnSelectionChangedListener.onSelectionChanged(selStart);
        }
    }

    // Ctrl+Z undoes, Ctrl+Shift+Z and Ctrl+Y redo, using the app's history
    // instead of the platform one (which is lost when the text is set).
    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (mOnUndoRedoListener != null && event.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEYCODE_Z) {
                mOnUndoRedoListener.onUndoRedo(event.isShiftPressed());
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_Y) {
                mOnUndoRedoListener.onUndoRedo(true);
                return true;
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }
}
