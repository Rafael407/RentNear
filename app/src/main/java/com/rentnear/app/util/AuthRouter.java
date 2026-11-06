package com.rentnear.app.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.MainActivity;
import com.rentnear.app.R;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.ui.auth.VerifyEmailActivity;

/** Decides where a signed-in user goes: banned -> stay out, unverified -> verify screen, else main. */
public final class AuthRouter {
    private AuthRouter() {}

    /** @return true if the user was routed onward, false if they must stay on the login screen. */
    public static boolean route(Activity from, FirebaseUser user, UserProfile profile,
                                Runnable onBanned) {
        if (profile != null && profile.banned) {
            Toast.makeText(from, R.string.err_banned, Toast.LENGTH_LONG).show();
            if (onBanned != null) onBanned.run();
            return false;
        }
        Class<?> target = user.isEmailVerified() ? MainActivity.class : VerifyEmailActivity.class;
        Intent i = new Intent(from, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(i);
        from.finish();
        return true;
    }
}
