package trikita.slide.ui;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.widget.FrameLayout;

import trikita.slide.State;

// Shows the current slide full screen on an external display (HDMI, Cast, etc).
public class SlidePresentation extends Presentation {

    private FrameLayout mRoot;
    private Preview mPreview;
    private State mPendingState;

    public SlidePresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRoot = new FrameLayout(getContext());
        mPreview = new Preview(getContext());
        mRoot.addView(mPreview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(mRoot);
        if (mPendingState != null) {
            render(mPendingState);
        }
    }

    public void render(State s) {
        if (mRoot == null) {
            mPendingState = s;
            return;
        }
        mRoot.setBackgroundColor(Style.scheme(s.colorScheme())[1]);
        mPreview.setState(s);
    }
}
