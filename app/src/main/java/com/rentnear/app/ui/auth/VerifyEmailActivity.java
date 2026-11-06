package com.rentnear.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.MainActivity;
import com.rentnear.app.R;
import com.rentnear.app.databinding.ActivityVerifyEmailBinding;
import com.rentnear.app.viewmodel.AuthViewModel;

public class VerifyEmailActivity extends AppCompatActivity {
    private ActivityVerifyEmailBinding b;
    private AuthViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityVerifyEmailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        FirebaseUser u = vm.currentUser();
        if (u == null) {
            goLogin();
            return;
        }
        b.tvVerifyBody.setText(getString(R.string.verify_body, u.getEmail()));

        b.btnDone.setOnClickListener(v -> vm.checkVerified());
        b.btnResend.setOnClickListener(v -> vm.resendVerification());
        b.btnLogout.setOnClickListener(v -> {
            vm.signOut();
            goLogin();
        });

        vm.verifyResult().observe(this, r -> {
            if (r == null) return;
            if (!r.isOk()) {
                Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
            } else if (r.data) {
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } else {
                Toast.makeText(this, R.string.not_verified_yet, Toast.LENGTH_SHORT).show();
            }
        });
        vm.resendResult().observe(this, r -> {
            if (r == null) return;
            Toast.makeText(this, r.isOk() ? getString(R.string.verification_sent) : r.error,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
