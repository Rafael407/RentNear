package com.rentnear.app.ui.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentChatListBinding;
import com.rentnear.app.viewmodel.ChatListViewModel;

/** Chats tab. Data comes from the activity-scoped ChatListViewModel (one shared live listener). */
public class ChatListFragment extends Fragment {
    private FragmentChatListBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentChatListBinding.inflate(inflater, container, false);
        ChatListViewModel vm = new ViewModelProvider(requireActivity()).get(ChatListViewModel.class);

        ChatListAdapter adapter = new ChatListAdapter(chat -> {
            Bundle args = new Bundle();
            args.putString("chatId", chat.id);
            NavHostFragment.findNavController(this).navigate(R.id.chatFragment, args);
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        vm.chats().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            b.tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
        });
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
