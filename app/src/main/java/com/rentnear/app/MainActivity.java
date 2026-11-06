package com.rentnear.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.rentnear.app.databinding.ActivityMainBinding;
import com.rentnear.app.repository.WalletRepository;
import com.rentnear.app.ui.auth.LoginActivity;
import com.rentnear.app.viewmodel.ChatListViewModel;
import com.rentnear.app.workers.ReminderWorker;

/** Hosts the four bottom-nav tabs and keeps the chat unread badge live while the app is open. */
public class MainActivity extends AppCompatActivity {
    private ChatListViewModel chats;
    private NavController nav;
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }

        ActivityMainBinding b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        Fragment host = getSupportFragmentManager().findFragmentById(R.id.navHost);
        nav = ((NavHostFragment) host).getNavController();
        NavigationUI.setupWithNavController(b.bottomNav, nav);

        // Demo wallet is created (with starting credit) the first time the user gets here.
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null && savedInstanceState == null) new WalletRepository().ensure(uid);

        chats = new ViewModelProvider(this).get(ChatListViewModel.class);
        chats.totalUnread().observe(this, count -> {
            BadgeDrawable badge = b.bottomNav.getOrCreateBadge(R.id.chatsFragment);
            badge.setVisible(count != null && count > 0);
            if (count != null && count > 0) badge.setNumber(count);
        });

        // Reminders are notifications; Android 13+ needs the user's permission to show them.
        if (Build.VERSION.SDK_INT >= 33 && savedInstanceState == null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (savedInstanceState == null) openFromIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromIntent(intent);
    }

    /** Tapping a reminder notification opens that booking. */
    private void openFromIntent(Intent intent) {
        if (intent == null || nav == null) return;
        String id = intent.getStringExtra(ReminderWorker.KEY_BOOKING);
        if (id == null) return;
        intent.removeExtra(ReminderWorker.KEY_BOOKING);
        Bundle args = new Bundle();
        args.putString("bookingId", id);
        nav.navigate(R.id.bookingDetailFragment, args);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chats != null) chats.start();
    }

    @Override
    protected void onStop() {
        if (chats != null) chats.stop();
        super.onStop();
    }
}
