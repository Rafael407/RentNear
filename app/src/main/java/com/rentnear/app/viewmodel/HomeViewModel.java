package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.firebase.geofire.GeoLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.model.Item;
import com.rentnear.app.repository.ItemRepository;
import com.rentnear.app.util.Categories;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared by the Map and Browse tabs: one nearby search, filtered and sorted in memory. */
public class HomeViewModel extends ViewModel {
    public enum Sort { DISTANCE, PRICE, RATING }

    public static final double RADIUS_M = 10_000;

    private final ItemRepository repo = new ItemRepository();

    private final MutableLiveData<GeoLocation> location = new MutableLiveData<>();
    private final MutableLiveData<Result<List<Item>>> raw = new MutableLiveData<>();
    private final MutableLiveData<List<Item>> shown = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> category = new MutableLiveData<>(Categories.ALL);
    private final MutableLiveData<Sort> sort = new MutableLiveData<>(Sort.DISTANCE);

    public HomeViewModel() {
        raw.observeForever(rawObserver);
    }

    private final androidx.lifecycle.Observer<Result<List<Item>>> rawObserver = r -> {
        loading.setValue(false);
        if (r == null) return;
        if (!r.isOk()) {
            error.setValue(r.error);
            return;
        }
        apply();
    };

    @Override
    protected void onCleared() {
        raw.removeObserver(rawObserver);
    }

    public LiveData<GeoLocation> location() { return location; }
    public LiveData<List<Item>> shown() { return shown; }
    public LiveData<Boolean> loading() { return loading; }
    public LiveData<String> error() { return error; }
    public LiveData<String> category() { return category; }
    public LiveData<Sort> sort() { return sort; }

    public void setLocation(GeoLocation l) {
        location.setValue(l);
        refresh();
    }

    public void refresh() {
        GeoLocation l = location.getValue();
        if (l == null) return;
        loading.setValue(true);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        repo.nearby(l, RADIUS_M, u == null ? null : u.getUid(), raw);
    }

    public void setCategory(String c) {
        category.setValue(c);
        apply();
    }

    public void setSort(Sort s) {
        sort.setValue(s);
        apply();
    }

    private void apply() {
        Result<List<Item>> r = raw.getValue();
        if (r == null || !r.isOk()) return;
        String cat = category.getValue();
        List<Item> out = new ArrayList<>();
        for (Item i : r.data) {
            if (Categories.ALL.equals(cat) || (cat != null && cat.equals(i.category))) out.add(i);
        }
        Sort s = sort.getValue();
        Comparator<Item> cmp;
        if (s == Sort.PRICE) cmp = Comparator.comparingDouble(Item::pricePerHour);
        else if (s == Sort.RATING) cmp = (a, b) -> Double.compare(b.ratingAvg, a.ratingAvg);
        else cmp = Comparator.comparingDouble(i -> i.distanceM);
        out.sort(cmp);
        shown.setValue(out);
    }
}
