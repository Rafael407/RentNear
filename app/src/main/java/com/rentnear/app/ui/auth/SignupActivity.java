package com.rentnear.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.rentnear.app.R;
import com.rentnear.app.databinding.ActivitySignupBinding;
import com.rentnear.app.viewmodel.AuthViewModel;

public class SignupActivity extends AppCompatActivity {
    private ActivitySignupBinding b;
    private AuthViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        b.btnSignup.setOnClickListener(v -> {
            String name = text(b.etName.getText());
            String email = text(b.etEmail.getText());
            String pass = text(b.etPassword.getText());
            String confirm = text(b.etConfirm.getText());
            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                toast(R.string.err_fields);
            } else if (pass.length() < 6) {
                toast(R.string.err_password_short);
            } else if (!pass.equals(confirm)) {
                toast(R.string.err_password_match);
            } else {
                vm.signUp(name, email, pass);
            }
        });
        b.btnGoLogin.setOnClickListener(v -> finish());

        vm.loading().observe(this, l -> b.progress.setVisibility(l ? View.VISIBLE : View.INVISIBLE));
        vm.authResult().observe(this, r -> {
            if (r == null) return;
            if (!r.isOk()) {
                Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
                vm.clearAuthResult();
                return;
            }
            // New email accounts always need verification.
            Intent i = new Intent(this, VerifyEmailActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private static String text(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }
}
