package com.rentnear.app.util;

import androidx.annotation.Nullable;

/** Completion callback: error is null on success. */
public interface Done {
    void onDone(@Nullable String error);
}
