package trikita.slide;

import android.content.Context;
import android.graphics.pdf.PdfDocument;

import java.io.IOException;
import java.io.OutputStream;

import trikita.slide.ui.Style;

public final class PdfExporter {

    private static final int PAGE_WIDTH = 640;

    private PdfExporter() {}

    // Blocks while background images load, so call it off the main thread.
    public static void write(Context c, State state, OutputStream out) throws IOException {
        int pageHeight = PAGE_WIDTH * 9 / 16;
        int[] colors = Style.COLOR_SCHEMES[state.colorScheme()];
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(PAGE_WIDTH, pageHeight, 1).create();
            for (Slide slide : state.slides()) {
                PdfDocument.Page page = document.startPage(pageInfo);
                SlideRenderer.render(c, slide, page.getCanvas(), PAGE_WIDTH, pageHeight,
                        Style.SLIDE_FONT, colors[0], colors[1], true, null);
                document.finishPage(page);
            }
            document.writeTo(out);
        } finally {
            document.close();
        }
    }
}
