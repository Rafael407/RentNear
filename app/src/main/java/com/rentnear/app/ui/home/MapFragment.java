package com.rentnear.app.ui.home;

import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoLocation;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentMapBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.MapConfig;
import com.rentnear.app.viewmodel.HomeViewModel;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Map tab: pins for nearby items; tapping a pin shows a preview card. */
public class MapFragment extends Fragment {
    private static final String ITEMS_SRC = "items-src";
    private static final String ITEMS_LAYER = "items-layer";
    private static final String ME_SRC = "me-src";
    private static final String ME_LAYER = "me-layer";

    private FragmentMapBinding b;
    private HomeViewModel vm;
    private MapLibreMap map;
    private Style style;
    private boolean cameraPlaced = false;
    private final Map<String, Item> byId = new HashMap<>();
    private Item selected;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMapBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(requireParentFragment()).get(HomeViewModel.class);

        b.mapView.onCreate(savedInstanceState);
        b.mapView.getMapAsync(m -> {
            map = m;
            // Start near the user instead of the whole-world view: the real location if the search
            // already has it, otherwise the fallback centre. The real position recentres the map later.
            GeoLocation start = vm.location().getValue();
            if (start == null) start = com.rentnear.app.util.LocationProvider.DEFAULT;
            m.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(start.latitude, start.longitude))
                    .zoom(13.0)
                    .build());
            m.setMinZoomPreference(3.0);
            m.setStyle(new Style.Builder().fromUri(MapConfig.STYLE_URL), s -> {
                style = s;
                s.addSource(new GeoJsonSource(ME_SRC));
                s.addLayer(new CircleLayer(ME_LAYER, ME_SRC).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(Color.parseColor("#2563EB")),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)));
                s.addSource(new GeoJsonSource(ITEMS_SRC));
                s.addLayer(new CircleLayer(ITEMS_LAYER, ITEMS_SRC).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor(Color.parseColor("#16A34A")),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)));
                applyLocation(vm.location().getValue());
                applyItems(vm.shown().getValue());
            });
            m.addOnMapClickListener(latLng -> {
                PointF px = m.getProjection().toScreenLocation(latLng);
                List<Feature> hits = m.queryRenderedFeatures(px, ITEMS_LAYER);
                if (hits.isEmpty()) {
                    b.previewCard.setVisibility(View.GONE);
                    return false;
                }
                Item i = byId.get(hits.get(0).getStringProperty("id"));
                if (i != null) showPreview(i);
                return true;
            });
        });

        b.btnPreviewView.setOnClickListener(v -> {
            if (selected == null) return;
            Bundle args = new Bundle();
            args.putString("itemId", selected.id);
            NavHostFragment.findNavController(requireParentFragment())
                    .navigate(R.id.itemDetailFragment, args);
        });

        vm.location().observe(getViewLifecycleOwner(), this::applyLocation);
        vm.shown().observe(getViewLifecycleOwner(), this::applyItems);
        vm.loading().observe(getViewLifecycleOwner(), l ->
                b.progress.setVisibility(Boolean.TRUE.equals(l) ? View.VISIBLE : View.INVISIBLE));
        return b.getRoot();
    }

    private void applyLocation(@Nullable GeoLocation loc) {
        if (loc == null || style == null || map == null) return;
        GeoJsonSource src = style.getSourceAs(ME_SRC);
        if (src != null) {
            src.setGeoJson(Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude)));
        }
        if (!cameraPlaced) {
            cameraPlaced = true;
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(loc.latitude, loc.longitude))
                    .zoom(13.0)
                    .build());
        }
    }

    private void applyItems(@Nullable List<Item> items) {
        if (style == null) return;
        byId.clear();
        List<Feature> features = new ArrayList<>();
        if (items != null) {
            for (Item i : items) {
                byId.put(i.id, i);
                Feature f = Feature.fromGeometry(Point.fromLngLat(i.lng, i.lat));
                f.addStringProperty("id", i.id);
                features.add(f);
            }
        }
        GeoJsonSource src = style.getSourceAs(ITEMS_SRC);
        if (src != null) src.setGeoJson(FeatureCollection.fromFeatures(features));
        if (selected != null && !byId.containsKey(selected.id)) {
            b.previewCard.setVisibility(View.GONE);
        }
    }

    private void showPreview(Item i) {
        selected = i;
        b.tvPreviewTitle.setText(i.title);
        b.tvPreviewPrice.setText(Format.price(i));
        b.tvPreviewMeta.setText(Format.distance(i.distanceM)
                + (i.category == null ? "" : "  ·  " + i.category));
        Glide.with(this).load(i.firstPhoto()).centerCrop().into(b.ivPreview);
        b.previewCard.setVisibility(View.VISIBLE);
    }

    // MapView needs its lifecycle forwarded.
    @Override public void onStart() { super.onStart(); b.mapView.onStart(); }
    @Override public void onResume() { super.onResume(); b.mapView.onResume(); }
    @Override public void onPause() { b.mapView.onPause(); super.onPause(); }
    @Override public void onStop() { b.mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (b != null) b.mapView.onLowMemory(); }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (b != null) b.mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        b.mapView.onDestroy();
        map = null;
        style = null;
        b = null;
        super.onDestroyView();
    }
}