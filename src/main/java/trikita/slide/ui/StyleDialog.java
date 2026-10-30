package trikita.slide.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import trikita.jedux.Action;
import trikita.slide.ActionType;
import trikita.slide.App;
import trikita.slide.R;
import trikita.slide.State;

public final class StyleDialog {

    private static final int COLUMNS = 4;

    private StyleDialog() {}

    public static void show(Context c, State state) {
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Style.dip(c, 16);
        content.setPadding(padding, padding, padding, padding);
        addColorGrid(c, content, state.colorScheme());

        content.addView(sectionTitle(c, R.string.style_font));
        RadioGroup fonts = new RadioGroup(c);
        for (int i = 0; i < Style.FONTS.length; i++) {
            RadioButton option = new RadioButton(c);
            option.setId(View.generateViewId());
            option.setText(Style.FONT_LABELS[i]);
            option.setTypeface(Typeface.create(Style.FONTS[i], Typeface.NORMAL));
            int index = i;
            option.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.SET_FONT, index)));
            fonts.addView(option);
            option.setChecked(i == state.font());
        }
        content.addView(fonts);

        content.addView(sectionTitle(c, R.string.style_aspect));
        RadioGroup aspects = new RadioGroup(c);
        aspects.setOrientation(RadioGroup.HORIZONTAL);
        String[] labels = {"16:9", "4:3"};
        int[] values = {State.ASPECT_16_9, State.ASPECT_4_3};
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(c);
            option.setId(View.generateViewId());
            option.setText(labels[i]);
            int value = values[i];
            option.setOnClickListener(v -> App.dispatch(new Action<>(ActionType.SET_ASPECT, value)));
            aspects.addView(option);
            option.setChecked(value == state.aspect());
        }
        content.addView(aspects);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(c)
                .setTitle(R.string.dlg_style_picker_title)
                .setView(scroll)
                .setPositiveButton(R.string.btn_ok, null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private static void addColorGrid(Context c, LinearLayout parent, int colorScheme) {
        List<ImageView> checks = new ArrayList<>();
        LinearLayout row = null;
        for (int i = 0; i < Style.COLOR_SCHEMES.length; i++) {
            if (i % COLUMNS == 0) {
                row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = Style.dip(c, 12);
                parent.addView(row, rowParams);
            }
            int index = i;
            int[] scheme = Style.COLOR_SCHEMES[i];

            FrameLayout item = new FrameLayout(c);
            ShapeDrawable circle = new ShapeDrawable(new OvalShape());
            circle.getPaint().setColor(scheme[1]);
            item.setBackground(circle);
            item.setContentDescription(c.getString(R.string.cd_color_scheme, i + 1));

            TextView sample = new TextView(c);
            sample.setText("A");
            sample.setTextSize(20);
            sample.setTextColor(scheme[0]);
            sample.setTypeface(Typeface.create(Style.SLIDE_FONT, Typeface.NORMAL));
            sample.setGravity(Gravity.CENTER);
            item.addView(sample, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

            ImageView check = new ImageView(c);
            check.setImageResource(R.drawable.ic_check);
            Style.tint(check, scheme[0]);
            check.setScaleType(ImageView.ScaleType.CENTER);
            item.addView(check, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            checks.add(check);

            item.setOnClickListener(v -> {
                App.dispatch(new Action<>(ActionType.SET_COLOR_SCHEME, index));
                updateSelection(checks, index);
            });

            int size = Style.dip(c, 48);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, size, 1f);
            itemParams.gravity = Gravity.CENTER;
            FrameLayout cell = new FrameLayout(c);
            cell.addView(item, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            row.addView(cell, itemParams);
        }
        updateSelection(checks, colorScheme);
    }

    private static TextView sectionTitle(Context c, int text) {
        TextView title = new TextView(c);
        title.setText(text);
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, Style.dip(c, 16), 0, Style.dip(c, 4));
        return title;
    }

    private static void updateSelection(List<ImageView> checks, int selected) {
        for (int i = 0; i < checks.size(); i++) {
            View check = checks.get(i);
            check.setVisibility(i == selected ? View.VISIBLE : View.GONE);
            View sample = ((FrameLayout) check.getParent()).getChildAt(0);
            sample.setVisibility(i == selected ? View.GONE : View.VISIBLE);
        }
    }
}
