package com.rentnear.app.data.geoapify;

import java.util.List;

/** Minimal Gson models for the two Geoapify endpoints the app uses. */
public final class GeoapifyModels {
    private GeoapifyModels() {}

    public static class AutocompleteResponse {
        public List<Feature> features;

        public static class Feature {
            public Properties properties;
        }

        public static class Properties {
            public String formatted;
            public double lat;
            public double lon;
        }
    }

    public static class RoutingResponse {
        public List<Feature> features;

        public static class Feature {
            public Properties properties;
            public Geometry geometry;
        }

        public static class Properties {
            public double distance; // metres
            public double time;     // seconds
        }

        /** Routing returns a MultiLineString: segments of [lon, lat] pairs. */
        public static class Geometry {
            public String type;
            public List<List<List<Double>>> coordinates;
        }
    }
}
