package com.rentnear.app.data.cloudinary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.exifinterface.media.ExifInterface;

import com.rentnear.app.BuildConfig;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Uploads images to Cloudinary's free plan using an unsigned upload preset, so no secret
 * ships in the app. Photos are downscaled and re-encoded first to stay inside the free quota.
 */
public final class CloudinaryUploader {
    private static final int MAX_DIMENSION = 1280;
    private static final int JPEG_QUALITY = 80;

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        /** Exactly one of urls / error is non-null. Always called on the main thread. */
        void onDone(List<String> urls, String error);
    }

    private CloudinaryUploader() {}

    public static boolean isConfigured() {
        return !BuildConfig.CLOUDINARY_CLOUD.isEmpty() && !BuildConfig.CLOUDINARY_PRESET.isEmpty();
    }

    public static void uploadAll(Context context, List<Uri> uris, Callback cb) {
        if (!isConfigured()) {
            cb.onDone(null, "Cloudinary is not configured (see README).");
            return;
        }
        Context app = context.getApplicationContext();
        POOL.execute(() -> {
            List<String> urls = new ArrayList<>();
            try {
                for (Uri u : uris) urls.add(uploadOne(app, u));
                MAIN.post(() -> cb.onDone(urls, null));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Upload failed" : e.getMessage();
                MAIN.post(() -> cb.onDone(null, msg));
            }
        });
    }

    private static String uploadOne(Context ctx, Uri uri) throws IOException {
        byte[] jpeg = compress(ctx, uri);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", BuildConfig.CLOUDINARY_PRESET)
                .addFormDataPart("folder", "rentnear/items")
                .addFormDataPart("file", "photo.jpg",
                        RequestBody.create(jpeg, MediaType.parse("image/jpeg")))
                .build();
        Request req = new Request.Builder()
                .url("https://api.cloudinary.com/v1_1/" + BuildConfig.CLOUDINARY_CLOUD + "/image/upload")
                .post(body)
                .build();
        try (Response resp = CLIENT.newCall(req).execute()) {
            String text = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                throw new IOException("Cloudinary error " + resp.code() + ": " + text);
            }
            String url = new JSONObject(text).optString("secure_url", "");
            if (url.isEmpty()) throw new IOException("Cloudinary returned no URL");
            return url;
        } catch (org.json.JSONException e) {
            throw new IOException("Bad Cloudinary response", e);
        }
    }

    private static byte[] compress(Context ctx, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("Could not read image");

        float scale = (float) MAX_DIMENSION / Math.max(bmp.getWidth(), bmp.getHeight());
        Matrix m = new Matrix();
        if (scale < 1f) m.postScale(scale, scale);
        m.postRotate(exifRotation(ctx, uri));
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos);
        return bos.toByteArray();
    }

    private static float exifRotation(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            int o = new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}
