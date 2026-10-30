package trikita.slide;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UndoHistoryTest {

    @Test
    public void typingBurstIsOneStep() {
        UndoHistory h = new UndoHistory();
        h.reset("", 0);
        h.record("a", 1, 0);
        h.record("ab", 2, 100);
        h.record("abc", 3, 200);
        assertEquals("", h.undo().text);
        assertFalse(h.canUndo());
    }

    @Test
    public void pauseStartsANewStep() {
        UndoHistory h = new UndoHistory();
        h.reset("", 0);
        h.record("a", 1, 0);
        h.record("ab", 2, UndoHistory.MERGE_WINDOW_MS + 1);
        UndoHistory.Entry e = h.undo();
        assertEquals("a", e.text);
        assertEquals(1, e.cursor);
        assertEquals("", h.undo().text);
    }

    @Test
    public void redoRestoresUndoneText() {
        UndoHistory h = new UndoHistory();
        h.reset("x", 1);
        h.record("xy", 2, 0);
        h.undo();
        assertTrue(h.canRedo());
        assertEquals("xy", h.redo().text);
        assertNull(h.redo());
    }

    @Test
    public void newEditClearsRedo() {
        UndoHistory h = new UndoHistory();
        h.reset("x", 1);
        h.record("xy", 2, 0);
        h.undo();
        h.record("xz", 2, 5000);
        assertFalse(h.canRedo());
    }

    @Test
    public void cursorMovesDoNotCreateSteps() {
        UndoHistory h = new UndoHistory();
        h.reset("abc", 0);
        h.record("abc", 2, 0);
        assertFalse(h.canUndo());
    }

    @Test
    public void editAfterUndoIsItsOwnStep() {
        UndoHistory h = new UndoHistory();
        h.reset("", 0);
        h.record("a", 1, 0);
        h.undo();
        h.record("b", 1, 10);
        assertEquals("", h.undo().text);
    }
}
