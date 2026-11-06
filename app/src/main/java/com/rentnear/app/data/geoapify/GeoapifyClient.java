package com.rentnear.app.data.geoapify;

import com.rentnear.app.BuildConfig;

import java.util.Locale;

import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Geoapify free plan: address search and walking directions. Key comes from local.properties. */
public final class GeoapifyClient {
    private static GeoapifyApi api;

    private GeoapifyClient() {}

    public static boolean isConfigured() {
        return !BuildConfig.GEOAPIFY_KEY.isEmpty();
    }

    private static GeoapifyApi api() {
        if (api == null) {
            api = new Retrofit.Builder()
                    .baseUrl("https://api.geoapify.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GeoapifyApi.class);
        }
        return api;
    }

    public static void autocomplete(String text, double biasLat, double biasLng,
                                    Callback<GeoapifyModels.AutocompleteResponse> cb) {
        String bias = String.format(Locale.US, "proximity:%f,%f", biasLng, biasLat);
        api().autocomplete(text, bias, 5, BuildConfig.GEOAPIFY_KEY).enqueue(cb);
    }

    public static void walkingRoute(double fromLat, double fromLng, double toLat, double toLng,
                                    Callback<GeoapifyModels.RoutingResponse> cb) {
        String wp = String.format(Locale.US, "%f,%f|%f,%f", fromLat, fromLng, toLat, toLng);
        api().route(wp, "walk", BuildConfig.GEOAPIFY_KEY).enqueue(cb);
    }
}
