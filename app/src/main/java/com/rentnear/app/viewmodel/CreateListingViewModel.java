package com.rentnear.app.viewmodel;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.data.cloudinary.CloudinaryUploader;
import com.rentnear.app.data.geoapify.GeoapifyClient;
import com.rentnear.app.data.geoapify.GeoapifyModels;
import com.rentnear.app.model.Item;
import com.rentnear.app.repository.ItemRepository;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateListingViewModel extends ViewModel {
    private final ItemRepository repo = new ItemRepository();

    private final MutableLiveData<Result<String>> created = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<List<GeoapifyModels.AutocompleteResponse.Feature>> suggestions =
            new MutableLiveData<>(new ArrayList<>());

    public LiveData<Result<String>> created() { return created; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<List<GeoapifyModels.AutocompleteResponse.Feature>> suggestions() { return suggestions; }

    public void searchAddress(String text, double biasLat, double biasLng) {
        if (!GeoapifyClient.isConfigured()) return;
        GeoapifyClient.autocomplete(text, biasLat, biasLng,
                new Callback<GeoapifyModels.AutocompleteResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<GeoapifyModels.AutocompleteResponse> call,
                                           @NonNull Response<GeoapifyModels.AutocompleteResponse> resp) {
                        GeoapifyModels.AutocompleteResponse b = resp.body();
                        List<GeoapifyModels.AutocompleteResponse.Feature> out = new ArrayList<>();
                        if (resp.isSuccessful() && b != null && b.features != null) {
                            for (GeoapifyModels.AutocompleteResponse.Feature f : b.features) {
                                if (f.properties != null && f.properties.formatted != null) out.add(f);
                            }
                        }
                        suggestions.setValue(out);
                    }

                    @Override
                    public void onFailure(@NonNull Call<GeoapifyModels.AutocompleteResponse> call,
                                          @NonNull Throwable t) {
                        suggestions.setValue(new ArrayList<>());
                    }
                });
    }

    /** Uploads photos to Cloudinary, then writes the item document. */
    public void submit(Context context, List<Uri> photos, String title, String description,
                       String category, double price, String unit, double declaredValue,
                       String address, double lat, double lng, String audience) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            created.setValue(Result.fail("Not signed in"));
            return;
        }
        busy.setValue(true);
        CloudinaryUploader.uploadAll(context, photos, (urls, error) -> {
            if (error != null) {
                busy.setValue(false);
                created.setValue(Result.fail(error));
                return;
            }
            String owner = u.getDisplayName() == null || u.getDisplayName().isEmpty()
                    ? "User" : u.getDisplayName();
            repo.create(Item.newMap(u.getUid(), owner, title, description, category, price, unit,
                    declaredValue, address, lat, lng, audience, urls), created);
        });
    }

    /** Re-enables the form after a result has been shown. */
    public void consumeResult() {
        busy.setValue(false);
        created.setValue(null);
    }
}
