package com.rentnear.app.ui.chat;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentChatBinding;
import com.rentnear.app.model.Chat;
import com.rentnear.app.model.ChatMessage;
import com.rentnear.app.util.Format;
import com.rentnear.app.viewmodel.ChatViewModel;

/** One conversation: bubbles, photos, price offers, block and report. */
public class ChatFragment extends Fragment {
    private static final int MENU_VIEW_ITEM = 1;
    private static final int MENU_BLOCK = 2;
    private static final int MENU_REPORT = 3;

    private FragmentChatBinding b;
    private ChatViewModel vm;
    private MessageAdapter adapter;

    private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null && vm != null) vm.sendImage(requireContext(), uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentChatBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(ChatViewModel.class);
        String chatId = getArguments() == null ? null : getArguments().getString("chatId");
        if (chatId != null) vm.init(chatId);

        adapter = new MessageAdapter(vm.me(), new MessageAdapter.OfferActions() {
            @Override public void onAccept(ChatMessage offer) { vm.acceptOffer(offer); }
            @Override public void onDecline(ChatMessage offer) { vm.declineOffer(offer); }

            @Override
            public void onCounter(ChatMessage offer) {
                showAmountDialog(R.string.counter_dialog_title, offer.offerUnit,
                        amount -> vm.counterOffer(offer, amount));
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        b.rvMessages.setLayoutManager(lm);
        b.rvMessages.setAdapter(adapter);
        b.rvMessages.setItemAnimator(null);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.headerTitle.setOnClickListener(v -> openItem());
        b.btnMore.setOnClickListener(this::showMenu);
        b.btnImage.setOnClickListener(v -> photoPicker.launch(
                new androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        b.btnOffer.setOnClickListener(v -> {
            Chat c = vm.chat().getValue();
            if (c == null) return;
            showAmountDialog(R.string.offer_dialog_title, c.itemUnit, amount -> vm.sendOffer(amount, c.itemUnit));
        });
        b.btnSend.setOnClickListener(v -> {
            String text = b.etMessage.getText() == null ? "" : b.etMessage.getText().toString().trim();
            if (text.isEmpty()) return;
            b.etMessage.setText("");
            vm.sendText(text);
        });

        vm.chat().observe(getViewLifecycleOwner(), this::showChat);
        vm.messages().observe(getViewLifecycleOwner(), list -> adapter.submitList(list, () -> {
            if (b != null && !list.isEmpty()) b.rvMessages.scrollToPosition(list.size() - 1);
        }));
        vm.blockedByMe().observe(getViewLifecycleOwner(), v -> updateBlockedUi());
        vm.blockedMe().observe(getViewLifecycleOwner(), v -> updateBlockedUi());
        vm.sending().observe(getViewLifecycleOwner(), s ->
                b.progress.setVisibility(Boolean.TRUE.equals(s) ? View.VISIBLE : View.INVISIBLE));
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
        });
        vm.info().observe(getViewLifecycleOwner(), i -> {
            if (i == null) return;
            Toast.makeText(requireContext(), i, Toast.LENGTH_LONG).show();
            vm.clearInfo();
        });
        return b.getRoot();
    }

    private final com.rentnear.app.repository.PresenceRepository presence =
            new com.rentnear.app.repository.PresenceRepository();
    private String watchedUid;

    private void showChat(@Nullable Chat c) {
        if (c == null || b == null) return;
        String other = c.otherUid(vm.me());
        if (other != null && !other.equals(watchedUid)) {
            watchedUid = other;
            presence.watch(other, (online, lastSeen) -> {
                if (b == null) return;
                if (online) {
                    b.tvPresence.setText(R.string.presence_online);
                } else if (lastSeen > 0) {
                    b.tvPresence.setText(getString(R.string.presence_last_seen,
                            android.text.format.DateUtils.getRelativeTimeSpanString(lastSeen,
                                    System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)));
                } else {
                    b.tvPresence.setText("");
                }
                b.tvPresence.setVisibility(online || lastSeen > 0 ? View.VISIBLE : View.GONE);
            });
        }
        b.tvChatName.setText(c.otherName(vm.me()));
        b.tvChatItem.setText(c.itemTitle);
        if (c.acceptedAmount > 0) {
            b.tvAgreed.setText(getString(R.string.agreed_price, Format.money(c.acceptedAmount),
                    c.acceptedUnit == null ? c.itemUnit : c.acceptedUnit));
            b.tvAgreed.setVisibility(View.VISIBLE);
        } else {
            b.tvAgreed.setVisibility(View.GONE);
        }

        // Only the renter books; the booking is pre-filled with the agreed price.
        boolean canBook = c.acceptedAmount > 0 && vm.me().equals(c.renterId);
        b.btnBook.setVisibility(canBook ? View.VISIBLE : View.GONE);
        b.btnBook.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("itemId", c.itemId);
            args.putDouble("rate", c.acceptedAmount);
            NavHostFragment.findNavController(this).navigate(R.id.bookingRequestFragment, args);
        });
    }

    /** Hides the composer and explains why when either side has blocked the other. */
    private void updateBlockedUi() {
        if (b == null) return;
        boolean byMe = Boolean.TRUE.equals(vm.blockedByMe().getValue());
        boolean byThem = Boolean.TRUE.equals(vm.blockedMe().getValue());
        if (byMe || byThem) {
            b.composer.setVisibility(View.GONE);
            b.tvBlocked.setVisibility(View.VISIBLE);
            b.tvBlocked.setText(byMe ? R.string.blocked_by_me : R.string.blocked_me);
        } else {
            b.composer.setVisibility(View.VISIBLE);
            b.tvBlocked.setVisibility(View.GONE);
        }
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        boolean byMe = Boolean.TRUE.equals(vm.blockedByMe().getValue());
        popup.getMenu().add(0, MENU_VIEW_ITEM, 0, R.string.menu_view_item);
        popup.getMenu().add(0, MENU_BLOCK, 1, byMe ? R.string.menu_unblock : R.string.menu_block);
        popup.getMenu().add(0, MENU_REPORT, 2, R.string.menu_report);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_VIEW_ITEM:
                    openItem();
                    return true;
                case MENU_BLOCK:
                    if (byMe) vm.unblock();
                    else confirmBlock();
                    return true;
                case MENU_REPORT:
                    showReportDialog();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void openItem() {
        Chat c = vm.chat().getValue();
        if (c == null) return;
        Bundle args = new Bundle();
        args.putString("itemId", c.itemId);
        NavHostFragment.findNavController(this).navigate(R.id.itemDetailFragment, args);
    }

    private void confirmBlock() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.block_confirm_title)
                .setMessage(R.string.block_confirm_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.block, (d, w) -> vm.block())
                .show();
    }

    private void showReportDialog() {
        String[] labels = {
                getString(R.string.report_spam), getString(R.string.report_harassment),
                getString(R.string.report_item), getString(R.string.report_other)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.report_title)
                .setItems(labels, (d, which) -> vm.report(labels[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface AmountCallback {
        void onAmount(double amount);
    }

    private void showAmountDialog(int titleRes, String unit, AmountCallback cb) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint(R.string.offer_hint);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(requireContext());
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(titleRes, unit))
                .setView(frame)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.offer_send, (d, w) -> {
                    double amount;
                    try {
                        amount = Double.parseDouble(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        amount = 0;
                    }
                    if (amount <= 0) {
                        Toast.makeText(requireContext(), R.string.err_offer_amount,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        cb.onAmount(amount);
                    }
                })
                .show();
    }

    @Override
    public void onStart() {
        super.onStart();
        vm.start();
    }

    @Override
    public void onStop() {
        vm.stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        presence.stopWatching();
        watchedUid = null;
        super.onDestroyView();
        b = null;
    }
}
