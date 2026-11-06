package com.rentnear.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;
import com.rentnear.app.databinding.DialogCategoryBinding;

import java.util.List;

/** Category chooser: a dialog around a single-choice ListView. */
public final class CategoryPicker {
    public interface OnPicked {
        void onPicked(String category);
    }

    private CategoryPicker() {}

    public static void show(Context context, List<String> categories, String current, OnPicked cb) {
        DialogCategoryBinding b = DialogCategoryBinding.inflate(LayoutInflater.from(context));
        b.listCategories.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_single_choice, categories));
        int idx = current == null ? -1 : categories.indexOf(current);
        if (idx >= 0) b.listCategories.setItemChecked(idx, true);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pick_category)
                .setView(b.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        b.listCategories.setOnItemClickListener((parent, view, position, id) -> {
            cb.onPicked(categories.get(position));
            dialog.dismiss();
        });
        dialog.show();
    }
}
