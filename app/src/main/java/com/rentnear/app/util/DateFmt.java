package com.rentnear.app.util;

import android.content.Context;
import android.text.format.DateUtils;

public final class DateFmt {
    private DateFmt() {}

    public static String range(Context c, long startMs, long endMs) {
        return DateUtils.formatDateRange(c, startMs, endMs,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL);
    }

    public static String one(Context c, long ms) {
        return DateUtils.formatDateTime(c, ms,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL);
    }
}
