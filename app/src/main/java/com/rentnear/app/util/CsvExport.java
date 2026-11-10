package com.rentnear.app.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.rentnear.app.model.WalletTx;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Writes the wallet history to a CSV file in the app cache and shares it through a FileProvider. */
public final class CsvExport {
    private CsvExport() {}

    public static void shareWallet(Context context, List<WalletTx> txs) throws IOException {
        File dir = new File(context.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create export folder");
        File file = new File(dir, "rentnear_wallet.csv");
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        try (FileWriter w = new FileWriter(file, false)) {
            w.write("date,type,description,amount\n");
            for (WalletTx t : txs) {
                w.write(fmt.format(new Date(t.createdAtMs)) + "," + quote(t.type) + "," + quote(t.label) + ","
                        + String.format(Locale.US, "%.2f", t.amount) + "\n");
            }
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "RentNear wallet history")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(send, "Share wallet history")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }

    private static String quote(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
