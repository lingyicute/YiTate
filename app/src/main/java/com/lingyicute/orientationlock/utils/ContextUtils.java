package com.lingyicute.orientationlock.utils;

import android.content.Context;
import android.content.res.TypedArray;

public class ContextUtils {

    /** Resolves a theme attribute such as {@code android.R.attr.colorAccent} to its color. */
    public static int getColorFromAttr(Context context, int attrColorRes) {
        TypedArray a = context.obtainStyledAttributes(new int[] { attrColorRes });
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    private ContextUtils() {
        throw new IllegalStateException();
    }
}
