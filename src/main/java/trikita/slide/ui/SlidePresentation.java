package trikita.slide.ui;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;

import trikita.anvil.Anvil;
import trikita.anvil.RenderableView;
import trikita.slide.App;

import static trikita.anvil.DSL.*;

// Shows the current slide full screen on an external display (HDMI, Cast, etc).
public class SlidePresentation extends Presentation {

    public SlidePresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new RenderableView(getContext()) {
            @Override
            public void view() {
                frameLayout(() -> {
                    size(FILL, FILL);
                    Style.Preview.background(App.getState().colorScheme());

                    v(Preview.class, () -> {
                        size(FILL, WRAP);
                        layoutGravity(CENTER);
                        Anvil.currentView().invalidate();
                    });
                });
            }
        });
    }
}
