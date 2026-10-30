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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import trikita.anvil.Anvil;

public class Slide {

    private static class Background {
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
        private final String url;
        private final float scale;
        private final int gravity;
        public Background(String bg) {
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
                } else if (part.contains("://")){
                    url = part;
                }
            }
            this.url = url;
            this.scale = scale;
            this.gravity = g;
        }
    }

    private final List<Background> backgrounds = new ArrayList<>();
    private final SpannableStringBuilder text = new SpannableStringBuilder();

    private Slide(String s) {
        int emSpanStart = -1;
        for (String line : s.split("\n")) {
            if (line.startsWith("@")) {
                backgrounds.add(new Background(line.substring(1)));
            } else if (line.startsWith("#")) {
                int start = text.length();
                text.append(line.substring(1)).append('\n');
                text.setSpan(new RelativeSizeSpan(1.6f), start, text.length(), 0);
                text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), 0);
            } else if (line.startsWith("  ")) {
                int start = text.length();
                text.append(line.substring(2)).append('\n');
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    text.setSpan(new TypefaceSpan(Typeface.MONOSPACE), start, text.length(), 0);
                } else {
                    text.setSpan(new TypefaceSpan("monospace"), start, text.length(), 0);
                }
            } else {
                if (line.startsWith(".")) {
                    line = line.substring(1);
                }
                // Handle emphasis
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '*') {
                        if (emSpanStart == -1) {
                            emSpanStart = text.length();
                        } else {
                            if (emSpanStart != text.length()) {
                                text.setSpan(new StyleSpan(Typeface.BOLD), emSpanStart, text.length(), 0);
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
            text.delete(text.length() - 1, text.length());
        }
    }

    // Slides are re-parsed on every state change, so loaded bitmaps are cached
    // statically by URL and size to avoid starting a new request on each redraw.
    private static class CacheTarget extends CustomTarget<Bitmap> {
        private static final int MAX_ENTRIES = 16;
        private static final Map<String, CacheTarget> CACHE =
                new LinkedHashMap<String, CacheTarget>(MAX_ENTRIES, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheTarget> eldest) {
                        if (size() > MAX_ENTRIES) {
                            Glide.with(eldest.getValue().context).clear(eldest.getValue());
                            return true;
                        }
                        return false;
                    }
                };

        private final Context context;
        private final String key;
        private Bitmap bitmap;
        private boolean loadingSynchronously;

        private CacheTarget(Context context, String key) {
            this.context = context;
            this.key = key;
        }

        static Bitmap get(Context context, RequestBuilder<Bitmap> request, String key) {
            CacheTarget target = CACHE.get(key);
            if (target == null) {
                target = new CacheTarget(context, key);
                CACHE.put(key, target);
                // Glide delivers memory-cache hits synchronously from into().
                target.loadingSynchronously = true;
                request.into(target);
                target.loadingSynchronously = false;
            }
            return target.bitmap;
        }

        @Override
        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
            bitmap = resource;
            if (!loadingSynchronously) {
                Anvil.render();
            }
        }

        @Override
        public void onLoadFailed(Drawable errorDrawable) {
            CACHE.remove(key);
        }

        @Override
        public void onLoadCleared(Drawable placeholder) {
            bitmap = null;
        }
    }

    public void render(Context c, Canvas canvas, int width, int height, String typeface, int fg, int bg, boolean blocking) {
        TextPaint textPaint = new TextPaint();
        canvas.drawColor(bg);
        textPaint.setColor(fg);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(typeface, 0));

        Context app = c.getApplicationContext();
        for (Background img: backgrounds) {
            if (img.url == null) {
                continue;
            }
            float scale = img.scale > 0 ? img.scale : 1f;
            int targetWidth = (int) (width * scale);
            int targetHeight = (int) (height * scale);
            RequestBuilder<Bitmap> request = Glide.with(app)
                    .asBitmap()
                    .load(img.url)
                    .override(targetWidth, targetHeight)
                    .centerInside();
            if (blocking) {
                FutureTarget<Bitmap> future = request.submit();
                try {
                    drawBackground(canvas, future.get(), img.gravity, width, height, textPaint);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    Glide.with(app).clear(future);
                }
            } else {
                String key = img.url + "@" + targetWidth + "x" + targetHeight;
                drawBackground(canvas, CacheTarget.get(app, request, key), img.gravity, width, height, textPaint);
            }
        }

        float margin = 0.1f;

        int w = (int) (width * (1 - margin * 2));
        int h = (int) (height * (1 - margin * 2));


        for (int textSize = height; textSize > 1 ; textSize--) {
            textPaint.setTextSize(textSize);
            if (StaticLayout.getDesiredWidth(text, textPaint) <= w) {
                StaticLayout layout = createStaticLayout(text, textPaint, w);
                if (layout.getHeight() >= h) {
                    continue;
                }
                int l = 0;
                for (int i = 0; i < layout.getLineCount(); i++) {
                    int m = (int) (width - layout.getLineWidth(i))/2;
                    if (i == 0 || m < l) {
                        l = m;
                    }
                }
                canvas.translate(l, (height - layout.getHeight())/2);

                textPaint.setColor(bg);
                textPaint.setStyle(Paint.Style.STROKE);
                textPaint.setStrokeWidth(8);
                layout = createStaticLayout(text, textPaint, w);
                layout.draw(canvas);
                textPaint.setColor(fg);
                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setStrokeWidth(0);
                layout = createStaticLayout(text, textPaint, w);
                layout.draw(canvas);
                return;
            }
        }
    }

    private static void drawBackground(Canvas canvas, Bitmap b, int gravity, int width, int height, Paint paint) {
        if (b == null) {
            return;
        }
        Rect r = new Rect();
        Gravity.apply(gravity, b.getWidth(), b.getHeight(), new Rect(0, 0, width, height), r);
        canvas.drawBitmap(b, r.left, r.top, paint);
    }

    private static StaticLayout createStaticLayout(CharSequence text, TextPaint paint, int width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, 1)
                    .setIncludePad(false)
                    .build();
        } else {
            return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        }
    }

    public static List<Slide> parse(String s) {
        List<Slide> slides = new ArrayList<>();
        String[] paragraphs = s.split("(\n\\s*){2,}");
        for (String par : paragraphs) {
            slides.add(new Slide(par));
        }
        return slides;
    }
}
