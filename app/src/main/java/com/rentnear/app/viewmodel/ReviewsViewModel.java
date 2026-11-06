package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.rentnear.app.model.Review;
import com.rentnear.app.repository.ReviewRepository;
import com.rentnear.app.util.Result;

import java.util.List;

/** Reviews for one item, or reviews written about one user. */
public class ReviewsViewModel extends ViewModel {
    private final ReviewRepository repo = new ReviewRepository();
    private final MutableLiveData<Result<List<Review>>> reviews = new MutableLiveData<>();
    private boolean loaded;

    public LiveData<Result<List<Review>>> reviews() { return reviews; }

    /** @param kind "item" or "user" */
    public void load(String kind, String id) {
        if (loaded) return;
        loaded = true;
        if ("item".equals(kind)) repo.forItem(id, reviews);
        else repo.forUser(id, reviews);
    }
}
