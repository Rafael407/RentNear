package com.rentnear.app.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Categories {
    public static final String ALL = "All";

    private static final String[] NAMES = {
            "Tools", "Electronics", "Cameras", "Sports & Outdoors", "Party & Events",
            "Bikes & Vehicles", "Musical Instruments", "Books & Study", "Home & Garden", "Other"
    };

    private Categories() {}

    public static List<String> forListing() {
        return new ArrayList<>(Arrays.asList(NAMES));
    }

    public static List<String> forFilter() {
        List<String> l = new ArrayList<>();
        l.add(ALL);
        l.addAll(Arrays.asList(NAMES));
        return l;
    }
}
