package trikita.slide;

import android.view.Gravity;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlideTest {

    @Test
    public void blankLinesSeparateSlides() {
        List<Slide> slides = Slide.parse("A\n\nB\n  \n\nC");
        assertEquals(3, slides.size());
        assertEquals("A", slides.get(0).text);
        assertEquals("B", slides.get(1).text);
        assertEquals("C", slides.get(2).text);
    }

    @Test
    public void headerLineIsStyled() {
        Slide s = Slide.parse("#Title\nbody").get(0);
        assertEquals("Title\nbody", s.text);
        assertEquals(1, s.spans.size());
        assertSpan(s.spans.get(0), Slide.SPAN_HEADER, 0, 6);
    }

    @Test
    public void indentedLineIsCode() {
        Slide s = Slide.parse("  x = 1").get(0);
        assertEquals("x = 1", s.text);
        assertSpan(s.spans.get(0), Slide.SPAN_CODE, 0, 5);
    }

    @Test
    public void starsMarkBoldText() {
        Slide s = Slide.parse("a *b* c").get(0);
        assertEquals("a b c", s.text);
        assertSpan(s.spans.get(0), Slide.SPAN_BOLD, 2, 3);
    }

    @Test
    public void doubleStarIsLiteral() {
        assertEquals("a*b", Slide.parse("a**b").get(0).text);
    }

    @Test
    public void leadingDotEscapesMarkup() {
        Slide s = Slide.parse(".#not a header").get(0);
        assertEquals("#not a header", s.text);
        assertTrue(s.spans.isEmpty());
    }

    @Test
    public void backgroundLineIsParsed() {
        Slide s = Slide.parse("@https://example.com/a.png 50% nw\ntext").get(0);
        assertEquals("text", s.text);
        Slide.Background bg = s.backgrounds.get(0);
        assertEquals("https://example.com/a.png", bg.url);
        assertEquals(0.5f, bg.scale, 0.0001f);
        assertEquals(Gravity.LEFT | Gravity.TOP, bg.gravity);
    }

    @Test
    public void semicolonLinesAreSpeakerNotes() {
        Slide s = Slide.parse("Title\n; first note\n;second\n.;shown").get(0);
        assertEquals("Title\n;shown", s.text);
        assertEquals("first note\nsecond", s.notes);
    }

    @Test
    public void pageAtFollowsCursor() {
        String text = "A\n\nB";
        assertEquals(0, Slide.pageAt(text, 0));
        assertEquals(0, Slide.pageAt(text, 1));
        assertEquals(1, Slide.pageAt(text, 3));
        assertEquals(1, Slide.pageAt(text, 4));
        assertEquals(1, Slide.pageAt(text, 100));
    }

    @Test
    public void trailingSeparatorDoesNotStartASlide() {
        assertEquals(0, Slide.pageAt("A\n\n", 3));
        assertEquals(1, Slide.count("A\n\n"));
    }

    @Test
    public void leadingSeparatorMakesEmptyFirstSlide() {
        assertEquals(2, Slide.parse("\n\nA").size());
        assertEquals(1, Slide.pageAt("\n\nA", 2));
        assertEquals(2, Slide.offsetOf("\n\nA", 1));
    }

    @Test
    public void offsetOfFindsSlideStart() {
        String text = "A\n\nB\n\nC";
        assertEquals(0, Slide.offsetOf(text, 0));
        assertEquals(3, Slide.offsetOf(text, 1));
        assertEquals(6, Slide.offsetOf(text, 2));
        assertEquals(text.length(), Slide.offsetOf(text, 5));
    }

    private static void assertSpan(Slide.Span span, int type, int start, int end) {
        assertEquals(type, span.type);
        assertEquals(start, span.start);
        assertEquals(end, span.end);
    }
}
