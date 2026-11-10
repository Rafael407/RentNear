package com.rentnear.app.ui.claim;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.R;
import com.rentnear.app.databinding.ItemDebtBinding;
import com.rentnear.app.model.Debt;
import com.rentnear.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Rows for the Debts screen: what I owe (with a Pay button) and what I am owed. */
public class DebtAdapter extends RecyclerView.Adapter<DebtAdapter.Holder> {
    public interface Actions {
        void pay(Debt d);
        void collect(Debt d);
        void ban(Debt d);
    }

    private final List<Debt> items = new ArrayList<>();
    private final String me;
    private final Actions actions;

    public DebtAdapter(String me, Actions actions) {
        this.me = me == null ? "" : me;
        this.actions = actions;
    }

    public void setItems(List<Debt> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemDebtBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Debt d = items.get(position);
        Context c = h.itemView.getContext();
        boolean debtor = me.equals(d.debtorId);
        long now = System.currentTimeMillis();
        String other = debtor ? d.creditorName : d.debtorName;
        if (other == null || other.isEmpty()) other = "User";

        h.v.tvTitle.setText(c.getString(debtor ? R.string.debt_you_owe : R.string.debt_owed_to_you,
                debtor ? Format.money(d.amount) : other));
        h.v.tvSub.setText(d.itemTitle + (debtor ? "  ·  to " + other : "  ·  " + Format.money(d.amount)));

        String status;
        int color = Color.parseColor("#0F766E");
        h.v.btnAction.setVisibility(View.GONE);
        if (d.paid) {
            status = c.getString(d.collected ? R.string.debt_collected : R.string.debt_paid);
            if (!debtor && !d.collected) {
                h.v.btnAction.setText(c.getString(R.string.btn_collect_debt, Format.money(d.amount)));
                h.v.btnAction.setOnClickListener(v -> actions.collect(d));
                h.v.btnAction.setVisibility(View.VISIBLE);
            }
        } else if (d.isOverdue(now)) {
            status = c.getString(R.string.debt_overdue);
            color = Color.parseColor("#B3261E");
            if (debtor) {
                h.v.btnAction.setText(c.getString(R.string.btn_pay_debt, Format.money(d.amount)));
                h.v.btnAction.setOnClickListener(v -> actions.pay(d));
            } else {
                h.v.btnAction.setText(R.string.btn_ban_debtor);
                h.v.btnAction.setOnClickListener(v -> actions.ban(d));
            }
            h.v.btnAction.setVisibility(View.VISIBLE);
        } else {
            long days = d.daysLeft(now);
            status = days <= 1 ? c.getString(R.string.debt_due_today) : c.getString(R.string.debt_due_days, (int) days);
            color = days <= 3 ? Color.parseColor("#B3261E") : Color.parseColor("#B26A00");
            if (debtor) {
                h.v.btnAction.setText(c.getString(R.string.btn_pay_debt, Format.money(d.amount)));
                h.v.btnAction.setOnClickListener(v -> actions.pay(d));
                h.v.btnAction.setVisibility(View.VISIBLE);
            }
        }
        h.v.tvStatus.setText(status);
        h.v.tvStatus.setTextColor(color);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemDebtBinding v;

        Holder(ItemDebtBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }
}
