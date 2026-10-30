package trikita.slide.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import java.util.List;

import trikita.slide.Slide;
import trikita.slide.SlideRenderer;
import trikita.slide.State;

public class Preview extends View {

    private State mState;
    private int mPageOffset = 0;
    private int mFixedPage = -1;
    private final Runnable mInvalidate = this::invalidate;

    public Preview(Context context) {
        super(context);
    }

    public void setState(State state) {
        boolean resize = mState == null || mState.aspect() != state.aspect();
        mState = state;
        if (resize) {
            requestLayout();
        }
        invalidate();
    }

    // Shows the slide this many pages after the current one.
    public void setPageOffset(int offset) {
        mPageOffset = offset;
        invalidate();
    }

    // Shows a specific slide regardless of the current page.
    public void setFixedPage(int page) {
        mFixedPage = page;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float ratio = mState != null ? mState.aspectRatio() : 9f / 16f;
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || w * ratio <= h) {
            h = (int) (w * ratio);
        } else {
            w = (int) (h / ratio);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mState == null) {
            return;
        }
        int[] colors = Style.scheme(mState.colorScheme());
        List<Slide> slides = mState.slides();
        int page = mFixedPage >= 0 ? mFixedPage : mState.page() + mPageOffset;
        if (page >= 0 && page < slides.size()) {
            SlideRenderer.render(getContext(), slides.get(page), canvas, getWidth(), getHeight(),
                    Style.font(mState.font()), colors[0], colors[1], false, mInvalidate);
        } else {
            canvas.drawColor(colors[1]);
        }
    }
}
