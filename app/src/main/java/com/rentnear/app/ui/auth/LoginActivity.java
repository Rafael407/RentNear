package com.rentnear.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.rentnear.app.R;
import com.rentnear.app.databinding.ActivityLoginBinding;
import com.rentnear.app.util.AuthRouter;
import com.rentnear.app.viewmodel.AuthViewModel;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding b;
    private AuthViewModel vm;
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount acct = task.getResult(ApiException.class);
                    vm.signInWithGoogle(acct.getIdToken());
                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-in cancelled or failed (" + e.getStatusCode() + ")",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        // default_web_client_id is generated from google-services.json by the Google Services plugin
        int webClientRes = getResources().getIdentifier(
                "default_web_client_id", "string", getPackageName());
        if (webClientRes != 0) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(webClientRes))
                    .requestEmail()
                    .build();
            googleClient = GoogleSignIn.getClient(this, gso);
        }

        b.btnLogin.setOnClickListener(v -> {
            String email = text(b.etEmail.getText());
            String pass = text(b.etPassword.getText());
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, R.string.err_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            vm.signIn(email, pass);
        });
        b.btnGoogle.setOnClickListener(v -> {
            if (googleClient == null) {
                Toast.makeText(this, "Google sign-in is not configured. Enable it in Firebase and "
                        + "re-download google-services.json (see README).", Toast.LENGTH_LONG).show();
                return;
            }
            googleClient.signOut().addOnCompleteListener(t ->
                    googleLauncher.launch(googleClient.getSignInIntent()));
        });
        b.btnGoSignup.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));

        vm.loading().observe(this, l -> b.progress.setVisibility(l ? View.VISIBLE : View.INVISIBLE));
        vm.authResult().observe(this, r -> {
            if (r == null) return;
            if (!r.isOk()) {
                Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
                vm.clearAuthResult();
                return;
            }
            AuthRouter.route(this, vm.currentUser(), r.data, () -> {
                vm.signOut();
                vm.clearAuthResult();
            });
        });

        // Already signed in from a previous run: load profile and route.
        if (vm.currentUser() != null && vm.authResult().getValue() == null) {
            vm.restoreSession();
        }
    }

    private static String text(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }
}
