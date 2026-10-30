package trikita.slide.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.TypedValue;
import android.widget.ImageView;

import trikita.slide.R;

public final class Style {
    public static final String SLIDE_FONT = "sans-serif-light";

    // {text color, background color, close button drawable, close icon color}
    public static final int[][] COLOR_SCHEMES = {
        {0xff333333, 0xffeeeeee, R.drawable.dark_round_button, 0xffeeeeee},    // dark gray on white
        {0xfffefefe, 0xff333333, R.drawable.light_round_button, 0xff333333},    // white on dark gray
        {0xffffffff, 0xffe51c23, R.drawable.light_round_button, 0xff333333},    // white on red
        {0xffffffff, 0xffff5722, R.drawable.light_round_button, 0xff333333},    // white on deep orange
        {0xff222222, 0xffffeb3b, R.drawable.dark_round_button, 0xffeeeeee},    // black on yellow
        {0xff333333, 0xffcddc39, R.drawable.dark_round_button, 0xffeeeeee},    // black on lime
        {0xff222222, 0xff8bc34a, R.drawable.dark_round_button, 0xffeeeeee},    // black on light green
        {0xffffffff, 0xff009688, R.drawable.light_round_button, 0xff333333},    // white on teal
        {0xff333333, 0xff00bcd4, R.drawable.dark_round_button, 0xffeeeeee},    // black on cyan
        {0xffffffff, 0xff3f51b5, R.drawable.light_round_button, 0xff333333},    // white on indigo
        {0xffffffff, 0xff75507b, R.drawable.light_round_button, 0xff333333},    // white on plum
        {0xffffffff, 0xffe91e63, R.drawable.light_round_button, 0xff333333},    // white on pink
    };

    public static final int PRESENTER_BACKGROUND = 0xff111111;
    public static final int PRESENTER_TEXT = 0xffcccccc;

    private Style() {}

    public static int[] scheme(int index) {
        return COLOR_SCHEMES[Math.max(0, Math.min(index, COLOR_SCHEMES.length - 1))];
    }

    public static int color(Context c, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return c.getColor(id);
        }
        return c.getResources().getColor(id);
    }

    public static int dip(Context c, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics());
    }

    public static void tint(ImageView v, int color) {
        v.setImageTintList(ColorStateList.valueOf(color));
    }

    public static void borderlessRipple(android.view.View v) {
        TypedArray a = v.getContext().obtainStyledAttributes(
                new int[]{android.R.attr.selectableItemBackgroundBorderless});
        v.setBackground(a.getDrawable(0));
        a.recycle();
    }
}
