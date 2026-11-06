package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.model.Booking;
import com.rentnear.app.model.Item;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.repository.BookingRepository;
import com.rentnear.app.repository.ChatRepository;
import com.rentnear.app.repository.ItemRepository;
import com.rentnear.app.repository.UserRepository;
import com.rentnear.app.util.Pricing;
import com.rentnear.app.util.Result;

import java.util.Map;

/** Booking request form: loads the item, holds the chosen dates, and submits the request. */
public class BookingRequestViewModel extends ViewModel {
    private final ItemRepository items = new ItemRepository();
    private final UserRepository users = new UserRepository();
    private final BookingRepository bookings = new BookingRepository();
    private final ChatRepository chats = new ChatRepository();

    private final MutableLiveData<Result<Item>> item = new MutableLiveData<>();
    private final MutableLiveData<Result<UserProfile>> profile = new MutableLiveData<>();
    private final MutableLiveData<Long> start = new MutableLiveData<>();
    private final MutableLiveData<Long> end = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Result<String>> created = new MutableLiveData<>();
    private final MutableLiveData<Result<String>> chatOpened = new MutableLiveData<>();

    private double rate;
    private boolean started;

    public LiveData<Result<Item>> item() { return item; }
    public LiveData<Result<UserProfile>> profile() { return profile; }
    public LiveData<Long> start() { return start; }
    public LiveData<Long> end() { return end; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<String> error() { return error; }
    public LiveData<Result<String>> created() { return created; }
    public double rate() { return rate; }

    /** @param rateArg price agreed in chat, or 0 to use the item's listed price */
    public void init(String itemId, double rateArg) {
        if (started) return;
        started = true;
        rate = rateArg;
        items.get(itemId, item);
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) users.load(u.getUid(), profile);
    }

    /** Called once the item has loaded: settle the rate and default the dates (next hour, 1 unit). */
    public void onItemLoaded(Item i) {
        if (rate <= 0) rate = i.price;
        if (start.getValue() == null) {
            long now = System.currentTimeMillis();
            long s = ((now / 3600_000L) + 1) * 3600_000L;
            start.setValue(s);
            end.setValue(s + Pricing.unitMs(i.priceUnit));
        }
    }

    public void setStart(long ms) {
        start.setValue(ms);
        Long e = end.getValue();
        Result<Item> r = item.getValue();
        if (r != null && r.isOk() && (e == null || e <= ms)) {
            end.setValue(ms + Pricing.unitMs(r.data.priceUnit));
        }
    }

    public void setEnd(long ms) {
        end.setValue(ms);
    }

    /** True if this item needs a Trusted renter and the current user is not one. */
    public boolean needsTrust() {
        Result<Item> i = item.getValue();
        Result<UserProfile> p = profile.getValue();
        return i != null && i.isOk() && i.data.declaredValue > Pricing.TRUSTED_VALUE_LIMIT
                && (p == null || !p.isOk() || !p.data.trusted);
    }

    public void submit(String note) {
        Result<Item> ir = item.getValue();
        Long s = start.getValue();
        Long e = end.getValue();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (ir == null || !ir.isOk() || s == null || e == null || u == null) {
            error.setValue("Still loading, try again in a moment");
            return;
        }
        Item i = ir.data;
        if (u.getUid().equals(i.ownerId)) {
            error.setValue("You cannot book your own item");
            return;
        }
        if (s < System.currentTimeMillis() - 5 * 60_000L) {
            error.setValue("The start time is in the past");
            return;
        }
        if (e <= s) {
            error.setValue("The end must be after the start");
            return;
        }
        if (e - s > Pricing.MAX_DURATION_MS) {
            error.setValue("Bookings can be at most 30 days");
            return;
        }
        if (needsTrust()) {
            error.setValue("This item needs a Trusted account. Complete more rentals first.");
            return;
        }

        busy.setValue(true);
        int units = Pricing.units(s, e, i.priceUnit);
        String renterName = u.getDisplayName() == null || u.getDisplayName().isEmpty()
                ? "User" : u.getDisplayName();

        // Make sure the chat exists first, so both sides can message about this booking.
        Observer<Result<String>> obs = new Observer<Result<String>>() {
            @Override
            public void onChanged(Result<String> r) {
                if (r == null) return;
                chatOpened.removeObserver(this);
                chatOpened.setValue(null);
                if (!r.isOk()) {
                    busy.setValue(false);
                    created.setValue(Result.fail(r.error));
                    return;
                }
                Map<String, Object> m = Booking.newMap(i, u.getUid(), renterName, s, e, rate, units,
                        note);
                bookings.create(m, created);
            }
        };
        chatOpened.observeForever(obs);
        chats.openChat(i, u, chatOpened);
    }

    public void consumeResult() {
        busy.setValue(false);
        created.setValue(null);
    }

    public void clearError() {
        error.setValue(null);
    }
}
