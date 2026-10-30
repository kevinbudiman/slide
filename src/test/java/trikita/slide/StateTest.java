package trikita.slide;

import org.junit.Test;

import trikita.jedux.Action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StateTest {

    private final State.Reducer reducer = new State.Reducer();

    private State reduce(State s, ActionType type, Object value) {
        return reducer.reduce(new Action<>(type, value), s);
    }

    @Test
    public void cursorIsClampedToText() {
        State s = reduce(State.initial("A\n\nB"), ActionType.SET_CURSOR, 99);
        assertEquals(4, s.cursor());
        assertEquals(1, s.page());
    }

    @Test
    public void shorterTextClampsCursor() {
        State s = reduce(State.initial("A\n\nBBBB"), ActionType.SET_CURSOR, 7);
        s = reduce(s, ActionType.SET_TEXT, "A");
        assertEquals(1, s.cursor());
        assertEquals(0, s.page());
    }

    @Test
    public void insertImageGoesAboveCurrentLine() {
        State s = reduce(State.initial("A\n\nB"), ActionType.SET_CURSOR, 4);
        s = reduce(s, ActionType.INSERT_IMAGE, "content://img");
        assertEquals("A\n\n@content://img\nB", s.text());
        assertEquals(3, s.cursor());
        assertEquals(1, s.page());
    }

    @Test
    public void insertImageOnFirstLine() {
        State s = reduce(State.initial("A"), ActionType.INSERT_IMAGE, "content://img");
        assertEquals("@content://img\nA", s.text());
        assertEquals(0, s.cursor());
    }

    @Test
    public void pagingStaysInRange() {
        State s = State.initial("A\n\nB");
        s = reduce(s, ActionType.PREV_PAGE, null);
        assertEquals(0, s.page());
        s = reduce(s, ActionType.NEXT_PAGE, null);
        s = reduce(s, ActionType.NEXT_PAGE, null);
        assertEquals(1, s.page());
    }

    @Test
    public void loadingADocumentResetsPosition() {
        State s = reduce(State.initial("A\n\nB"), ActionType.SET_CURSOR, 4);
        s = reduce(s, ActionType.DOCUMENT_LOADED, new State.LoadedDocument("content://doc", "X"));
        assertEquals("content://doc", s.uri());
        assertEquals("X", s.text());
        assertEquals(0, s.cursor());
        assertEquals(0, s.page());
    }

    @Test
    public void newDocumentStartsEmpty() {
        State s = reduce(State.initial("A"), ActionType.NEW_DOCUMENT, "content://new");
        assertEquals("content://new", s.uri());
        assertEquals("", s.text());
    }

    @Test
    public void saveAsKeepsText() {
        State s = reduce(State.initial("A"), ActionType.SAVE_DOCUMENT_AS, "content://copy");
        assertEquals("content://copy", s.uri());
        assertEquals("A", s.text());
        assertNull(State.initial("A").uri());
    }
}
