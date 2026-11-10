package com.rentnear.app.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Reads the phone's contacts through the Contacts content provider (ContentResolver) and lets the
 * user pick an email address. Needs the READ_CONTACTS permission, which the caller requests first.
 */
public final class ContactsPicker {
    private ContactsPicker() {}

    /** Each entry is {display name, email address}. */
    static List<String[]> load(ContentResolver resolver) {
        List<String[]> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS};
        try (Cursor c = resolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, projection,
                null, null, ContactsContract.CommonDataKinds.Email.DISPLAY_NAME + " COLLATE NOCASE ASC")) {
            if (c == null) return out;
            int nameCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME);
            int mailCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS);
            while (c.moveToNext() && out.size() < 300) {
                String mail = c.getString(mailCol);
                if (mail == null || !seen.add(mail.toLowerCase())) continue;
                String name = c.getString(nameCol);
                out.add(new String[]{name == null ? mail : name, mail});
            }
        }
        return out;
    }

    public static void show(Context context, Consumer<String> onEmail) {
        List<String[]> contacts = load(context.getContentResolver());
        if (contacts.isEmpty()) {
            Toast.makeText(context, R.string.contacts_none, Toast.LENGTH_LONG).show();
            return;
        }
        CharSequence[] labels = new CharSequence[contacts.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = contacts.get(i)[0] + "\n" + contacts.get(i)[1];
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.contacts_title)
                .setItems(labels, (d, which) -> onEmail.accept(contacts.get(which)[1]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
