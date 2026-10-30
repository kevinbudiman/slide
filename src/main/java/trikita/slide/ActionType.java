package trikita.slide;

public enum ActionType {
    SET_TEXT,
    SET_CURSOR,

    OPEN_PRESENTATION,
    CLOSE_PRESENTATION,
    TOGGLE_TOOLBAR,
    NEXT_PAGE,
    PREV_PAGE,

    SET_COLOR_SCHEME,
    SET_FONT,
    SET_ASPECT,

    // Document actions carry the document URI as a String.
    NEW_DOCUMENT,
    OPEN_DOCUMENT,
    SAVE_DOCUMENT_AS,
    DOCUMENT_LOADED,    // value: State.LoadedDocument
    INSERT_IMAGE,       // value: image URI as a String
    EXPORT_PDF,
}
