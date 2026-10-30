package trikita.slide;

import java.util.List;

import trikita.jedux.Action;
import trikita.jedux.Store;

public final class State {

    public static final int ASPECT_16_9 = 0;
    public static final int ASPECT_4_3 = 1;

    public static final class LoadedDocument {
        public final String uri;
        public final String text;

        public LoadedDocument(String uri, String text) {
            this.uri = uri;
            this.text = text;
        }
    }

    private final String uri;
    private final String text;
    private final int page;
    private final int cursor;
    private final boolean presentationMode;
    private final boolean toolbarShown;
    private final int colorScheme;
    private final int font;
    private final int aspect;

    private List<Slide> slides;

    public State(String uri, String text, int page, int cursor,
                 boolean presentationMode, boolean toolbarShown, int colorScheme, int font, int aspect) {
        this.uri = uri;
        this.text = text;
        this.page = page;
        this.cursor = cursor;
        this.presentationMode = presentationMode;
        this.toolbarShown = toolbarShown;
        this.colorScheme = colorScheme;
        this.font = font;
        this.aspect = aspect;
    }

    public static State initial(String text) {
        return new State(null, text, 0, 0, false, true, 0, 0, ASPECT_16_9);
    }

    public String uri() { return uri; }
    public String text() { return text; }
    public int page() { return page; }
    public int cursor() { return cursor; }
    public boolean presentationMode() { return presentationMode; }
    public boolean toolbarShown() { return toolbarShown; }
    public int colorScheme() { return colorScheme; }
    public int font() { return font; }
    public int aspect() { return aspect; }

    // Height divided by width.
    public float aspectRatio() {
        return aspect == ASPECT_4_3 ? 3f / 4f : 9f / 16f;
    }

    public synchronized List<Slide> slides() {
        if (slides == null) {
            slides = Slide.parse(text);
        }
        return slides;
    }

    State withUri(String v) { return new State(v, text, page, cursor, presentationMode, toolbarShown, colorScheme, font, aspect); }
    State withText(String v) { return new State(uri, v, page, cursor, presentationMode, toolbarShown, colorScheme, font, aspect); }
    State withPage(int v) { return new State(uri, text, v, cursor, presentationMode, toolbarShown, colorScheme, font, aspect); }
    State withPresentationMode(boolean v) { return new State(uri, text, page, cursor, v, toolbarShown, colorScheme, font, aspect); }
    State withToolbarShown(boolean v) { return new State(uri, text, page, cursor, presentationMode, v, colorScheme, font, aspect); }
    State withColorScheme(int v) { return new State(uri, text, page, cursor, presentationMode, toolbarShown, v, font, aspect); }

    State withFont(int v) { return new State(uri, text, page, cursor, presentationMode, toolbarShown, colorScheme, v, aspect); }
    State withAspect(int v) { return new State(uri, text, page, cursor, presentationMode, toolbarShown, colorScheme, font, v); }

    State withCursor(int v) {
        int c = Math.max(0, Math.min(v, text.length()));
        return new State(uri, text, Slide.pageAt(text, c), c, presentationMode, toolbarShown, colorScheme, font, aspect);
    }

    // Inserts "@<image>" as a new line above the line that holds the cursor.
    static State insertImage(State s, String image) {
        String text = s.text;
        int c = Math.max(0, Math.min(s.cursor, text.length()));
        int startOfLine = text.lastIndexOf('\n', c - 1) + 1;
        String updated = text.substring(0, startOfLine) + "@" + image + "\n" + text.substring(startOfLine);
        return s.withText(updated).withCursor(startOfLine);
    }

    public static class Reducer implements Store.Reducer<Action<ActionType, ?>, State> {
        public State reduce(Action<ActionType, ?> a, State s) {
            switch (a.type) {
                case SET_TEXT:
                    return s.withText((String) a.value).withCursor(s.cursor);
                case SET_CURSOR:
                    return s.withCursor((Integer) a.value);
                case NEXT_PAGE:
                    return s.withPage(Math.min(s.page + 1, Slide.count(s.text) - 1));
                case PREV_PAGE:
                    return s.withPage(Math.max(s.page - 1, 0));
                case OPEN_PRESENTATION:
                    return s.withPresentationMode(true);
                case CLOSE_PRESENTATION:
                    return s.withPresentationMode(false);
                case TOGGLE_TOOLBAR:
                    return s.withToolbarShown(!s.toolbarShown);
                case SET_COLOR_SCHEME:
                    return s.withColorScheme((Integer) a.value);
                case SET_FONT:
                    return s.withFont((Integer) a.value);
                case SET_ASPECT:
                    return s.withAspect((Integer) a.value);
                case NEW_DOCUMENT:
                    return s.withUri((String) a.value).withText("").withCursor(0);
                case SAVE_DOCUMENT_AS:
                    return s.withUri((String) a.value);
                case DOCUMENT_LOADED: {
                    LoadedDocument doc = (LoadedDocument) a.value;
                    return s.withUri(doc.uri).withText(doc.text).withCursor(0);
                }
                case INSERT_IMAGE:
                    return insertImage(s, (String) a.value);
                default:
                    return s;
            }
        }
    }
}
