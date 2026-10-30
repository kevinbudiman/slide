package trikita.slide;

import android.view.Gravity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A parsed slide. Parsing is plain Java so it can be unit tested; rendering
// lives in SlideRenderer.
public final class Slide {

    // Blank lines (possibly containing whitespace) separate slides.
    private static final Pattern PARAGRAPH_SEPARATOR = Pattern.compile("(\n\\s*){2,}");

    public static final int SPAN_HEADER = 0;
    public static final int SPAN_CODE = 1;
    public static final int SPAN_BOLD = 2;

    public static final class Span {
        public final int type;
        public final int start;
        public final int end;

        Span(int type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }

    public static final class Background {
        private static final Map<String, Integer> GRAVITY = new HashMap<>();
        static {
            GRAVITY.put("left", Gravity.LEFT);
            GRAVITY.put("top", Gravity.TOP);
            GRAVITY.put("right", Gravity.RIGHT);
            GRAVITY.put("bottom", Gravity.BOTTOM);
            GRAVITY.put("center", Gravity.CENTER);
            GRAVITY.put("w", Gravity.LEFT);
            GRAVITY.put("n", Gravity.TOP);
            GRAVITY.put("e", Gravity.RIGHT);
            GRAVITY.put("s", Gravity.BOTTOM);
            GRAVITY.put("nw", Gravity.LEFT | Gravity.TOP);
            GRAVITY.put("sw", Gravity.LEFT | Gravity.BOTTOM);
            GRAVITY.put("ne", Gravity.RIGHT | Gravity.TOP);
            GRAVITY.put("se", Gravity.RIGHT | Gravity.BOTTOM);
        }

        public final String url;
        public final float scale;
        public final int gravity;

        Background(String bg) {
            int g = Gravity.CENTER;
            float scale = 1f;
            String url = null;
            for (String part : bg.split("\\s+")) {
                if (part.endsWith("%")) {
                    try {
                        scale = Float.parseFloat(part.substring(0, part.length() - 1)) / 100;
                    } catch (NumberFormatException ignored) {}
                } else if (GRAVITY.containsKey(part)) {
                    g = GRAVITY.get(part);
                } else if (part.contains("://")) {
                    url = part;
                }
            }
            this.url = url;
            this.scale = scale;
            this.gravity = g;
        }
    }

    public final List<Background> backgrounds;
    public final String text;
    public final List<Span> spans;

    private Slide(List<Background> backgrounds, String text, List<Span> spans) {
        this.backgrounds = Collections.unmodifiableList(backgrounds);
        this.text = text;
        this.spans = Collections.unmodifiableList(spans);
    }

    static Slide parseOne(String s) {
        List<Background> backgrounds = new ArrayList<>();
        List<Span> spans = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int emSpanStart = -1;
        for (String line : s.split("\n")) {
            if (line.startsWith("@")) {
                backgrounds.add(new Background(line.substring(1)));
            } else if (line.startsWith("#")) {
                int start = text.length();
                text.append(line.substring(1)).append('\n');
                spans.add(new Span(SPAN_HEADER, start, text.length()));
            } else if (line.startsWith("  ")) {
                int start = text.length();
                text.append(line.substring(2)).append('\n');
                spans.add(new Span(SPAN_CODE, start, text.length()));
            } else {
                if (line.startsWith(".")) {
                    line = line.substring(1);
                }
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '*') {
                        if (emSpanStart == -1) {
                            emSpanStart = text.length();
                        } else {
                            if (emSpanStart != text.length()) {
                                spans.add(new Span(SPAN_BOLD, emSpanStart, text.length()));
                            } else {
                                text.append('*');
                            }
                            emSpanStart = -1;
                        }
                    } else {
                        text.append(c);
                    }
                }
                text.append('\n');
            }
        }
        if (text.length() > 0 && text.charAt(text.length() - 1) == '\n') {
            text.setLength(text.length() - 1);
        }
        int length = text.length();
        List<Span> clamped = new ArrayList<>();
        for (Span span : spans) {
            int end = Math.min(span.end, length);
            if (span.start < end) {
                clamped.add(new Span(span.type, span.start, end));
            }
        }
        return new Slide(backgrounds, text.toString(), clamped);
    }

    public static List<Slide> parse(String s) {
        List<Slide> slides = new ArrayList<>();
        for (String par : PARAGRAPH_SEPARATOR.split(s)) {
            slides.add(parseOne(par));
        }
        return slides;
    }

    public static int count(String s) {
        return Math.max(1, PARAGRAPH_SEPARATOR.split(s).length);
    }

    // Index of the slide that contains the given cursor position. A separator at
    // the very end of the text does not start a slide, matching parse().
    public static int pageAt(String text, int cursor) {
        int c = Math.max(0, Math.min(cursor, text.length()));
        int page = 0;
        Matcher m = PARAGRAPH_SEPARATOR.matcher(text);
        while (m.find() && m.end() <= c) {
            if (m.end() < text.length()) {
                page++;
            }
        }
        return page;
    }

    // Offset of the first character of the given slide. Slide N starts right
    // after the N-th separator, which also holds for a leading separator
    // because parse() turns it into an empty first slide.
    public static int offsetOf(String text, int page) {
        if (page <= 0) {
            return 0;
        }
        Matcher m = PARAGRAPH_SEPARATOR.matcher(text);
        int found = 0;
        while (m.find()) {
            found++;
            if (found == page) {
                return m.end();
            }
        }
        return text.length();
    }
}
