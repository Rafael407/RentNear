package com.rentnear.app.data.geoapify;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeoapifyApi {
    @GET("v1/geocode/autocomplete")
    Call<GeoapifyModels.AutocompleteResponse> autocomplete(
            @Query("text") String text,
            @Query("bias") String bias,   // "proximity:lon,lat"
            @Query("limit") int limit,
            @Query("apiKey") String apiKey);

    @GET("v1/routing")
    Call<GeoapifyModels.RoutingResponse> route(
            @Query("waypoints") String waypoints, // "lat,lon|lat,lon"
            @Query("mode") String mode,
            @Query("apiKey") String apiKey);
}
