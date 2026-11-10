package com.rentnear.app.ui.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.R;
import com.rentnear.app.databinding.ItemFriendBinding;
import com.rentnear.app.databinding.ItemSectionBinding;
import com.rentnear.app.model.FriendRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sections: requests for me, requests I sent, and my friends. */
public class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface Actions {
        void accept(FriendRequest r);
        void decline(FriendRequest r);
        void cancel(FriendRequest r);
        void remove(String friendUid, String name);
    }

    private static final int HEADER = 0;
    private static final int ROW = 1;

    private static class Entry {
        int type;
        String header;
        String name;
        String sub;
        FriendRequest request;
        boolean incoming;
        String friendUid;
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Actions actions;

    public FriendsAdapter(Actions actions) {
        this.actions = actions;
    }

    public void setData(List<FriendRequest> incoming, List<FriendRequest> outgoing, Map<String, String> friends) {
        entries.clear();
        if (!incoming.isEmpty()) {
            header("Requests for you");
            for (FriendRequest r : incoming) {
                Entry e = row(r.fromName, "wants to be friends");
                e.request = r;
                e.incoming = true;
            }
        }
        if (!outgoing.isEmpty()) {
            header("Requests you sent");
            for (FriendRequest r : outgoing) {
                Entry e = row(r.toName, "waiting for an answer");
                e.request = r;
            }
        }
        header("Friends (" + friends.size() + ")");
        for (Map.Entry<String, String> f : friends.entrySet()) {
            Entry e = row(f.getValue(), "can see your Friends-only listings");
            e.friendUid = f.getKey();
        }
        notifyDataSetChanged();
    }

    private void header(String text) {
        Entry e = new Entry();
        e.type = HEADER;
        e.header = text;
        entries.add(e);
    }

    private Entry row(String name, String sub) {
        Entry e = new Entry();
        e.type = ROW;
        e.name = name == null || name.isEmpty() ? "User" : name;
        e.sub = sub;
        entries.add(e);
        return e;
    }

    @Override
    public int getItemViewType(int position) {
        return entries.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == HEADER) return new HeaderHolder(ItemSectionBinding.inflate(inf, parent, false));
        return new RowHolder(ItemFriendBinding.inflate(inf, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Entry e = entries.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).v.tvSection.setText(e.header);
            return;
        }
        ItemFriendBinding v = ((RowHolder) holder).v;
        v.tvName.setText(e.name);
        v.tvSub.setText(e.sub);
        v.btnPrimary.setVisibility(View.GONE);
        v.btnSecondary.setVisibility(View.GONE);
        if (e.request != null && e.incoming) {
            v.btnPrimary.setText(R.string.friend_accept);
            v.btnPrimary.setOnClickListener(x -> actions.accept(e.request));
            v.btnPrimary.setVisibility(View.VISIBLE);
            v.btnSecondary.setText(R.string.friend_decline);
            v.btnSecondary.setOnClickListener(x -> actions.decline(e.request));
            v.btnSecondary.setVisibility(View.VISIBLE);
        } else if (e.request != null) {
            v.btnSecondary.setText(R.string.friend_cancel);
            v.btnSecondary.setOnClickListener(x -> actions.cancel(e.request));
            v.btnSecondary.setVisibility(View.VISIBLE);
        } else if (e.friendUid != null) {
            v.btnSecondary.setText(R.string.friend_remove);
            v.btnSecondary.setOnClickListener(x -> actions.remove(e.friendUid, e.name));
            v.btnSecondary.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemSectionBinding v;

        HeaderHolder(ItemSectionBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final ItemFriendBinding v;

        RowHolder(ItemFriendBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }
}
