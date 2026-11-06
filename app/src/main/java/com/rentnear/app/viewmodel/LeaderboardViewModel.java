package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.rentnear.app.model.UserProfile;
import com.rentnear.app.repository.ReviewRepository;
import com.rentnear.app.util.Result;

import java.util.List;

/** Top users, ranked by points or by trust score. */
public class LeaderboardViewModel extends ViewModel {
    private final ReviewRepository repo = new ReviewRepository();
    private final MutableLiveData<Result<List<UserProfile>>> users = new MutableLiveData<>();
    private boolean byPoints = true;

    public LiveData<Result<List<UserProfile>>> users() { return users; }
    public boolean byPoints() { return byPoints; }

    public void refresh() {
        repo.leaderboard(byPoints, users);
    }

    public void setByPoints(boolean value) {
        if (byPoints == value && users.getValue() != null) return;
        byPoints = value;
        refresh();
    }
}
