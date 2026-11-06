package com.rentnear.app.ui.chat;

import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;
import com.rentnear.app.R;
import com.rentnear.app.databinding.ItemMessageBinding;
import com.rentnear.app.model.ChatMessage;
import com.rentnear.app.util.Format;

import java.util.Date;
import java.util.Objects;

/** Message bubbles: text, image, and offer cards with Accept / Counter / Decline. */
public class MessageAdapter extends ListAdapter<ChatMessage, MessageAdapter.Holder> {
    public interface OfferActions {
        void onAccept(ChatMessage offer);
        void onCounter(ChatMessage offer);
        void onDecline(ChatMessage offer);
    }

    private final String me;
    private final OfferActions actions;

    public MessageAdapter(String me, OfferActions actions) {
        super(new DiffUtil.ItemCallback<ChatMessage>() {
            @Override
            public boolean areItemsTheSame(@NonNull ChatMessage x, @NonNull ChatMessage y) {
                return x.id.equals(y.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull ChatMessage x, @NonNull ChatMessage y) {
                return Objects.equals(x.body, y.body)
                        && Objects.equals(x.offerStatus, y.offerStatus)
                        && x.createdAtMs == y.createdAtMs;
            }
        });
        this.me = me;
        this.actions = actions;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMessageBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ChatMessage m = getItem(position);
        ItemMessageBinding b = h.b;
        boolean mine = me.equals(m.senderId);

        // Alignment and colour: mine on the right, theirs on the left.
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.bubble.getLayoutParams();
        lp.gravity = mine ? Gravity.END : Gravity.START;
        b.bubble.setLayoutParams(lp);
        b.bubble.setCardBackgroundColor(MaterialColors.getColor(b.bubble, mine
                ? com.google.android.material.R.attr.colorPrimaryContainer
                : com.google.android.material.R.attr.colorSurfaceVariant));

        b.tvTime.setText(DateFormat.getTimeFormat(b.getRoot().getContext())
                .format(new Date(m.createdAtMs)));

        b.tvBody.setVisibility(View.GONE);
        b.ivImage.setVisibility(View.GONE);
        b.offerBlock.setVisibility(View.GONE);

        if (ChatMessage.TYPE_IMAGE.equals(m.type)) {
            b.ivImage.setVisibility(View.VISIBLE);
            Glide.with(b.ivImage).load(m.body).centerCrop().into(b.ivImage);
        } else if (m.isOffer()) {
            bindOffer(b, m, mine);
        } else {
            b.tvBody.setVisibility(View.VISIBLE);
            b.tvBody.setText(m.body);
        }
    }

    private void bindOffer(ItemMessageBinding b, ChatMessage m, boolean mine) {
        b.offerBlock.setVisibility(View.VISIBLE);
        android.content.Context c = b.getRoot().getContext();
        b.tvOfferTitle.setText(ChatMessage.KIND_COUNTER.equals(m.offerKind)
                ? R.string.counter_title : R.string.offer_title);
        b.tvOfferAmount.setText(c.getString(R.string.offer_amount,
                Format.money(m.offerAmount), m.offerUnit));

        b.offerActions.setVisibility(View.GONE);
        b.tvOfferStatus.setVisibility(View.VISIBLE);
        String status = m.offerStatus == null ? ChatMessage.STATUS_PENDING : m.offerStatus;
        switch (status) {
            case ChatMessage.STATUS_ACCEPTED:
                b.tvOfferStatus.setText(R.string.offer_accepted);
                break;
            case ChatMessage.STATUS_DECLINED:
                b.tvOfferStatus.setText(R.string.offer_declined);
                break;
            case ChatMessage.STATUS_COUNTERED:
                b.tvOfferStatus.setText(R.string.offer_countered);
                break;
            default:
                if (mine) {
                    b.tvOfferStatus.setText(R.string.offer_waiting);
                } else {
                    // Only the person who received a pending offer can answer it.
                    b.tvOfferStatus.setVisibility(View.GONE);
                    b.offerActions.setVisibility(View.VISIBLE);
                    b.btnAccept.setOnClickListener(v -> actions.onAccept(m));
                    b.btnCounter.setOnClickListener(v -> actions.onCounter(m));
                    b.btnDecline.setOnClickListener(v -> actions.onDecline(m));
                }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemMessageBinding b;

        Holder(ItemMessageBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
