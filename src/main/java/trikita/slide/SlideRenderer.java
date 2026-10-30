package trikita.slide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.FutureTarget;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;

public final class SlideRenderer {

    private static final float MARGIN = 0.1f;
    private static final int OUTLINE_WIDTH = 8;

    // Per-slide cache of the styled text and the last fitted text size. Slides are
    // immutable and re-created when the text changes, so weak keys are enough.
    private static final class Cached {
        CharSequence styled;
        String fitKey;
        float fitSize;
    }
    private static final Map<Slide, Cached> CACHE = new WeakHashMap<>();

    private SlideRenderer() {}

    // Renders a slide. With blocking=false, missing background images load in the
    // background and onImageLoaded runs (on the main thread) when one is ready.
    public static void render(Context c, Slide slide, Canvas canvas, int width, int height,
                              String typeface, int fg, int bg, boolean blocking,
                              Runnable onImageLoaded) {
        TextPaint paint = new TextPaint();
        canvas.drawColor(bg);
        paint.setColor(fg);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.create(typeface, Typeface.NORMAL));

        drawBackgrounds(c, slide, canvas, width, height, paint, blocking, onImageLoaded);

        CharSequence text = styledText(slide);
        int w = (int) (width * (1 - MARGIN * 2));
        int h = (int) (height * (1 - MARGIN * 2));
        if (w <= 0 || h <= 0 || text.length() == 0) {
            return;
        }

        paint.setTextSize(fittedTextSize(slide, text, paint, typeface, w, h, height));
        StaticLayout layout = createLayout(text, paint, w);

        int left = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            int m = (int) (width - layout.getLineWidth(i)) / 2;
            if (i == 0 || m < left) {
                left = m;
            }
        }

        int save = canvas.save();
        canvas.translate(left, (height - layout.getHeight()) / 2f);
        // The layout reads the paint at draw time, so it can be drawn twice:
        // first an outline in the background color, then the text itself.
        paint.setColor(bg);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(OUTLINE_WIDTH);
        layout.draw(canvas);
        paint.setColor(fg);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private static Cached cached(Slide slide) {
        synchronized (CACHE) {
            Cached cached = CACHE.get(slide);
            if (cached == null) {
                cached = new Cached();
                CACHE.put(slide, cached);
            }
            return cached;
        }
    }

    private static CharSequence styledText(Slide slide) {
        Cached cached = cached(slide);
        synchronized (cached) {
            if (cached.styled == null) {
                cached.styled = buildStyledText(slide);
            }
            return cached.styled;
        }
    }

    private static CharSequence buildStyledText(Slide slide) {
        SpannableStringBuilder text = new SpannableStringBuilder(slide.text);
        for (Slide.Span span : slide.spans) {
            int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
            switch (span.type) {
                case Slide.SPAN_HEADER:
                    text.setSpan(new RelativeSizeSpan(1.6f), span.start, span.end, flags);
                    text.setSpan(new StyleSpan(Typeface.BOLD), span.start, span.end, flags);
                    break;
                case Slide.SPAN_CODE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        text.setSpan(new TypefaceSpan(Typeface.MONOSPACE), span.start, span.end, flags);
                    } else {
                        text.setSpan(new TypefaceSpan("monospace"), span.start, span.end, flags);
                    }
                    break;
                case Slide.SPAN_BOLD:
                    text.setSpan(new StyleSpan(Typeface.BOLD), span.start, span.end, flags);
                    break;
            }
        }
        return text;
    }

    // Largest text size at which the slide fits into w x h. Fitting is monotonic in
    // the text size, so a binary search replaces trying every size from the top.
    private static float fittedTextSize(Slide slide, CharSequence text, TextPaint paint,
                                        String typeface, int w, int h, int maxSize) {
        Cached cached = cached(slide);
        String key = typeface + ":" + w + "x" + h;
        synchronized (cached) {
            if (key.equals(cached.fitKey)) {
                return cached.fitSize;
            }
        }
        int lo = 1;
        int hi = Math.max(1, maxSize);
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fits(text, paint, mid, w, h)) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        synchronized (cached) {
            cached.fitKey = key;
            cached.fitSize = lo;
        }
        return lo;
    }

    private static boolean fits(CharSequence text, TextPaint paint, int size, int w, int h) {
        paint.setTextSize(size);
        if (StaticLayout.getDesiredWidth(text, paint) > w) {
            return false;
        }
        return createLayout(text, paint, w).getHeight() < h;
    }

    private static StaticLayout createLayout(CharSequence text, TextPaint paint, int width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, 1)
                    .setIncludePad(false)
                    .build();
        }
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
    }

    private static void drawBackgrounds(Context c, Slide slide, Canvas canvas, int width, int height,
                                        Paint paint, boolean blocking, Runnable onImageLoaded) {
        Context app = c.getApplicationContext();
        for (Slide.Background img : slide.backgrounds) {
            if (img.url == null) {
                continue;
            }
            float scale = img.scale > 0 ? img.scale : 1f;
            int targetWidth = Math.max(1, (int) (width * scale));
            int targetHeight = Math.max(1, (int) (height * scale));
            RequestBuilder<Bitmap> request = Glide.with(app)
                    .asBitmap()
                    .load(img.url)
                    .override(targetWidth, targetHeight)
                    .centerInside();
            if (blocking) {
                FutureTarget<Bitmap> future = request.submit();
                try {
                    drawBitmap(canvas, future.get(), img.gravity, width, height, paint);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    Glide.with(app).clear(future);
                }
            } else {
                String key = img.url + "@" + targetWidth + "x" + targetHeight;
                drawBitmap(canvas, ImageCache.get(app, request, key, onImageLoaded),
                        img.gravity, width, height, paint);
            }
        }
    }

    private static void drawBitmap(Canvas canvas, Bitmap b, int gravity, int width, int height, Paint paint) {
        if (b == null) {
            return;
        }
        Rect r = new Rect();
        Gravity.apply(gravity, b.getWidth(), b.getHeight(), new Rect(0, 0, width, height), r);
        canvas.drawBitmap(b, r.left, r.top, paint);
    }

    // Keeps loaded bitmaps by URL and size, since previews redraw on every edit.
    // Only used from the main thread.
    private static final class ImageCache extends CustomTarget<Bitmap> {
        private static final int MAX_ENTRIES = 16;
        private static final Map<String, ImageCache> ENTRIES =
                new LinkedHashMap<String, ImageCache>(MAX_ENTRIES, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, ImageCache> eldest) {
                        if (size() > MAX_ENTRIES) {
                            Glide.with(eldest.getValue().context).clear(eldest.getValue());
                            return true;
                        }
                        return false;
                    }
                };

        private final Context context;
        private final String key;
        private final List<Runnable> waiting = new ArrayList<>();
        private Bitmap bitmap;

        private ImageCache(Context context, String key) {
            this.context = context;
            this.key = key;
        }

        static Bitmap get(Context context, RequestBuilder<Bitmap> request, String key, Runnable onLoaded) {
            ImageCache entry = ENTRIES.get(key);
            if (entry == null) {
                entry = new ImageCache(context, key);
                ENTRIES.put(key, entry);
                request.into(entry);
            }
            if (entry.bitmap == null && onLoaded != null && !entry.waiting.contains(onLoaded)) {
                entry.waiting.add(onLoaded);
            }
            return entry.bitmap;
        }

        @Override
        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
            bitmap = resource;
            List<Runnable> callbacks = new ArrayList<>(waiting);
            waiting.clear();
            for (Runnable r : callbacks) {
                r.run();
            }
        }

        @Override
        public void onLoadFailed(Drawable errorDrawable) {
            waiting.clear();
            ENTRIES.remove(key);
        }

        @Override
        public void onLoadCleared(Drawable placeholder) {
            bitmap = null;
        }
    }
}
