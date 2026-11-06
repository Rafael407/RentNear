package com.rentnear.app.ui.chat;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.rentnear.app.databinding.ItemChatBinding;
import com.rentnear.app.model.Chat;

import java.util.Objects;

/** RecyclerView adapter for the chat list: other person, item, last message, unread badge. */
public class ChatListAdapter extends ListAdapter<Chat, ChatListAdapter.Holder> {
    public interface OnClick {
        void onClick(Chat chat);
    }

    private final OnClick onClick;

    public ChatListAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<Chat>() {
            @Override
            public boolean areItemsTheSame(@NonNull Chat x, @NonNull Chat y) {
                return x.id.equals(y.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Chat x, @NonNull Chat y) {
                return Objects.equals(x.lastMessage, y.lastMessage)
                        && x.lastMessageAtMs == y.lastMessageAtMs
                        && x.unreadOwner == y.unreadOwner
                        && x.unreadRenter == y.unreadRenter;
            }
        });
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemChatBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Chat c = getItem(position);
        String me = FirebaseAuth.getInstance().getUid();
        if (me == null) me = "";

        h.b.tvName.setText(c.otherName(me));
        h.b.tvItem.setText(c.itemTitle);
        h.b.tvLast.setText((me.equals(c.lastSenderId) ? "You: " : "") + c.lastMessage);
        h.b.tvTime.setText(DateUtils.getRelativeTimeSpanString(c.lastMessageAtMs,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE));
        Glide.with(h.b.ivItem).load(c.itemPhoto).centerCrop().into(h.b.ivItem);

        long unread = c.unreadFor(me);
        if (unread > 0) {
            h.b.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
            h.b.tvUnread.setVisibility(View.VISIBLE);
        } else {
            h.b.tvUnread.setVisibility(View.GONE);
        }
        h.b.getRoot().setOnClickListener(v -> onClick.onClick(c));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemChatBinding b;

        Holder(ItemChatBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
