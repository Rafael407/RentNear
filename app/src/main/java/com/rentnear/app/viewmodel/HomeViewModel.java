package com.rentnear.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.firebase.geofire.GeoLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.data.cache.ListingCache;
import com.rentnear.app.model.Item;
import com.rentnear.app.repository.FriendRepository;
import com.rentnear.app.repository.ItemRepository;
import com.rentnear.app.util.Categories;
import com.rentnear.app.util.Prefs;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared by the Map and Browse tabs: one nearby search, filtered and sorted in memory. The last
 * search is kept in Room and shown first, so the feed appears instantly and works offline. The
 * Browse tab shows it 20 at a time.
 */
public class HomeViewModel extends AndroidViewModel {
    public enum Sort { DISTANCE, PRICE, RATING }

    public static final int PAGE_SIZE = 20;

    private final ItemRepository repo = new ItemRepository();
    private final FriendRepository friends = new FriendRepository();

    private final MutableLiveData<GeoLocation> location = new MutableLiveData<>();
    private final MutableLiveData<Result<List<Item>>> fresh = new MutableLiveData<>();
    /** Everything that matches the filters (the map shows all of it). */
    private final MutableLiveData<List<Item>> shown = new MutableLiveData<>(new ArrayList<>());
    /** The first pages of {@link #shown} (the Browse list). */
    private final MutableLiveData<List<Item>> browse = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> fromCache = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> category = new MutableLiveData<>(Categories.ALL);
    private final MutableLiveData<Sort> sort = new MutableLiveData<>(Sort.DISTANCE);

    private List<Item> rawList = new ArrayList<>();
    private int pages = 1;
    private int searchId;

    private final Observer<Result<List<Item>>> freshObserver = r -> {
        loading.setValue(false);
        if (r == null) return;
        if (!r.isOk()) {
            error.setValue(r.error);
            return;
        }
        fromCache.setValue(false);
        rawList = r.data;
        GeoLocation l = location.getValue();
        if (l != null) ListingCache.save(getApplication(), l, Prefs.radiusMetres(), rawList);
        apply();
    };

    public HomeViewModel(@NonNull Application app) {
        super(app);
        fresh.observeForever(freshObserver);
    }

    @Override
    protected void onCleared() {
        fresh.removeObserver(freshObserver);
    }

    public LiveData<GeoLocation> location() { return location; }
    public LiveData<List<Item>> shown() { return shown; }
    public LiveData<List<Item>> browse() { return browse; }
    public LiveData<Boolean> hasMore() { return hasMore; }
    public LiveData<Boolean> loading() { return loading; }
    public LiveData<Boolean> fromCache() { return fromCache; }
    public LiveData<String> error() { return error; }
    public LiveData<String> category() { return category; }
    public LiveData<Sort> sort() { return sort; }

    public void setLocation(GeoLocation l) {
        location.setValue(l);
        // Show the saved search straight away if there is one for this place; then refresh.
        final int id = ++searchId;
        ListingCache.load(getApplication(), l, Prefs.radiusMetres(), items -> {
            if (items != null && id == searchId && rawList.isEmpty()) {
                rawList = items;
                fromCache.setValue(true);
                apply();
            }
        });
        refresh();
    }

    public void refresh() {
        GeoLocation l = location.getValue();
        if (l == null) return;
        loading.setValue(true);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String uid = u == null ? null : u.getUid();
        double radius = Prefs.radiusMetres();
        // Friends-only listings are shown to friends, so read the friend list first.
        friends.friendIds(uid, ids -> repo.nearby(l, radius, uid, ids, fresh));
    }

    public void setCategory(String c) {
        category.setValue(c);
        pages = 1;
        apply();
    }

    public void setSort(Sort s) {
        sort.setValue(s);
        pages = 1;
        apply();
    }

    public void loadMore() {
        pages++;
        publishPage();
    }

    private void apply() {
        String cat = category.getValue();
        List<Item> out = new ArrayList<>();
        for (Item i : rawList) {
            if (Categories.ALL.equals(cat) || (cat != null && cat.equals(i.category))) out.add(i);
        }
        Sort s = sort.getValue();
        Comparator<Item> cmp;
        if (s == Sort.PRICE) cmp = Comparator.comparingDouble(Item::pricePerHour);
        else if (s == Sort.RATING) cmp = (a, b) -> Double.compare(b.ratingAvg, a.ratingAvg);
        else cmp = Comparator.comparingDouble(i -> i.distanceM);
        out.sort(cmp);
        shown.setValue(out);
        publishPage();
    }

    private void publishPage() {
        List<Item> all = shown.getValue();
        if (all == null) all = new ArrayList<>();
        int end = Math.min(all.size(), pages * PAGE_SIZE);
        browse.setValue(new ArrayList<>(all.subList(0, end)));
        hasMore.setValue(end < all.size());
    }
}
