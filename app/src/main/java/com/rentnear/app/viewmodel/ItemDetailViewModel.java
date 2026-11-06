package com.rentnear.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.data.geoapify.GeoapifyClient;
import com.rentnear.app.data.geoapify.GeoapifyModels;
import com.rentnear.app.model.Item;
import com.rentnear.app.repository.ChatRepository;
import com.rentnear.app.repository.ItemRepository;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ItemDetailViewModel extends ViewModel {
    /** Walking route: polyline as [lng, lat] pairs plus distance and time. */
    public static class Route {
        public final List<double[]> points = new ArrayList<>();
        public double distanceM;
        public double timeS;
    }

    private final ItemRepository repo = new ItemRepository();
    private final MutableLiveData<Result<Item>> item = new MutableLiveData<>();
    private final MutableLiveData<Result<Route>> route = new MutableLiveData<>();

    private final ChatRepository chatRepo = new ChatRepository();
    private final MutableLiveData<Result<String>> chatId = new MutableLiveData<>();

    public LiveData<Result<Item>> item() { return item; }
    public LiveData<Result<Route>> route() { return route; }
    public LiveData<Result<String>> chatId() { return chatId; }

    /** Finds or creates the 1-to-1 chat about this item for the signed-in renter. */
    public void openChat(Item i, FirebaseUser me) {
        chatRepo.openChat(i, me, chatId);
    }

    public void clearChatId() {
        chatId.setValue(null);
    }

    public void load(String id) {
        if (item.getValue() == null) repo.get(id, item);
    }

    public void loadRoute(double fromLat, double fromLng, double toLat, double toLng) {
        if (!GeoapifyClient.isConfigured()) {
            route.setValue(Result.fail("Add GEOAPIFY_KEY to local.properties to see walking directions."));
            return;
        }
        GeoapifyClient.walkingRoute(fromLat, fromLng, toLat, toLng,
                new Callback<GeoapifyModels.RoutingResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeoapifyModels.RoutingResponse> call,
                                           @NonNull Response<GeoapifyModels.RoutingResponse> resp) {
                        GeoapifyModels.RoutingResponse body = resp.body();
                        if (!resp.isSuccessful() || body == null || body.features == null
                                || body.features.isEmpty()) {
                            route.setValue(Result.fail("No walking route found"));
                            return;
                        }
                        GeoapifyModels.RoutingResponse.Feature f = body.features.get(0);
                        Route r = new Route();
                        if (f.properties != null) {
                            r.distanceM = f.properties.distance;
                            r.timeS = f.properties.time;
                        }
                        if (f.geometry != null && f.geometry.coordinates != null) {
                            for (List<List<Double>> segment : f.geometry.coordinates) {
                                for (List<Double> p : segment) {
                                    if (p.size() >= 2) r.points.add(new double[]{p.get(0), p.get(1)});
                                }
                            }
                        }
                        route.setValue(Result.ok(r));
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeoapifyModels.RoutingResponse> call,
                                          @NonNull Throwable t) {
                        route.setValue(Result.fail("Could not load directions"));
                    }
                });
    }
}
