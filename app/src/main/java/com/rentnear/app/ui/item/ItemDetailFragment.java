package com.rentnear.app.ui.item;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentItemDetailBinding;
import com.rentnear.app.databinding.ItemPhotoBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.LocationProvider;
import com.rentnear.app.util.MapConfig;
import com.rentnear.app.viewmodel.ItemDetailViewModel;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.List;

/** Item detail: photos, info, pickup pin and a Geoapify walking route from the viewer. */
public class ItemDetailFragment extends Fragment {
    private static final String PIN_SRC = "pin-src";
    private static final String ROUTE_SRC = "route-src";
    private static final String ME_SRC = "me-src";

    private FragmentItemDetailBinding b;
    private ItemDetailViewModel vm;
    private MapLibreMap map;
    private Style style;
    private Item item;
    private GeoLocation me;
    private boolean routeRequested = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentItemDetailBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(ItemDetailViewModel.class);

        b.rvPhotos.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));

        b.mapView.onCreate(savedInstanceState);
        b.mapView.getMapAsync(m -> {
            map = m;
            m.setStyle(new Style.Builder().fromUri(MapConfig.STYLE_URL), s -> {
                style = s;
                s.addSource(new GeoJsonSource(ROUTE_SRC));
                s.addLayer(new LineLayer("route-layer", ROUTE_SRC).withProperties(
                        PropertyFactory.lineColor(Color.parseColor("#2563EB")),
                        PropertyFactory.lineWidth(5f)));
                s.addSource(new GeoJsonSource(ME_SRC));
                s.addLayer(new CircleLayer("me-layer", ME_SRC).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(Color.parseColor("#2563EB")),
                        PropertyFactory.circleStrokeWidth(3f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)));
                s.addSource(new GeoJsonSource(PIN_SRC));
                s.addLayer(new CircleLayer("pin-layer", PIN_SRC).withProperties(
                        PropertyFactory.circleRadius(10f),
                        PropertyFactory.circleColor(Color.parseColor("#0F766E")),
                        PropertyFactory.circleStrokeWidth(2.5f),
                        PropertyFactory.circleStrokeColor(Color.WHITE)));
                drawPins();
            });
        });

        vm.item().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            if (!r.isOk()) {
                Toast.makeText(requireContext(), getString(R.string.item_load_failed) + ": " + r.error,
                        Toast.LENGTH_LONG).show();
                return;
            }
            item = r.data;
            showItem();
            drawPins();
            maybeRequestRoute();
        });
        vm.route().observe(getViewLifecycleOwner(), r -> {
            if (r == null || b == null) return;
            if (!r.isOk()) {
                b.tvRoute.setText(r.error);
                return;
            }
            b.tvRoute.setText(getString(R.string.route_summary,
                    Format.distance(r.data.distanceM), Format.duration(r.data.timeS)));
            drawRoute(r.data);
        });

        b.btnReviews.setOnClickListener(v -> {
            if (item == null) return;
            Bundle args = new Bundle();
            args.putString("kind", "item");
            args.putString("id", item.id);
            args.putString("title", getString(R.string.reviews_item_title));
            NavHostFragment.findNavController(this).navigate(R.id.reviewsFragment, args);
        });
        b.btnBook.setOnClickListener(v -> {
            if (item == null) return;
            Bundle args = new Bundle();
            args.putString("itemId", item.id);
            NavHostFragment.findNavController(this).navigate(R.id.bookingRequestFragment, args);
        });
        b.btnMessage.setOnClickListener(v -> {
            FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
            if (item == null || me == null) return;
            b.btnMessage.setEnabled(false);
            vm.openChat(item, me);
        });
        vm.chatId().observe(getViewLifecycleOwner(), r -> {
            if (r == null || b == null) return;
            vm.clearChatId();
            b.btnMessage.setEnabled(true);
            if (!r.isOk()) {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
                return;
            }
            Bundle args = new Bundle();
            args.putString("chatId", r.data);
            NavHostFragment.findNavController(this).navigate(R.id.chatFragment, args);
        });

        String id = getArguments() == null ? null : getArguments().getString("itemId");
        if (id != null) vm.load(id);

        LocationProvider.current(requireContext(), (loc, real) -> {
            if (b == null) return;
            if (real) {
                me = loc;
                drawPins();
                maybeRequestRoute();
            } else {
                b.tvRoute.setText(R.string.default_location_notice);
            }
        });
        return b.getRoot();
    }

    private void showItem() {
        b.tvTitle.setText(item.title);
        b.tvPrice.setText(Format.price(item));
        String rating = item.ratingCount == 0 ? "New" : String.format(java.util.Locale.US, "%.1f★ (%d)",
                item.ratingAvg, item.ratingCount);
        b.tvMeta.setText((item.category == null ? "" : item.category) + "  ·  " + rating);
        b.tvDescription.setText(item.description);
        b.tvValue.setText(getString(R.string.value_line, Format.money(item.declaredValue)));
        b.tvOwner.setText(getString(R.string.owner_line, item.ownerName));
        b.tvAddress.setText(item.address);
        b.rvPhotos.setAdapter(new PhotoAdapter(item.photos));

        // Owners cannot message themselves; everyone else can start a chat about this item.
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        boolean isOwner = me != null && me.getUid().equals(item.ownerId);
        b.btnMessage.setEnabled(!isOwner);
        b.btnMessage.setText(isOwner ? R.string.your_listing : R.string.message_owner);
        b.btnBook.setEnabled(!isOwner && item.active);
    }

    private void maybeRequestRoute() {
        if (routeRequested || item == null || me == null) return;
        routeRequested = true;
        vm.loadRoute(me.latitude, me.longitude, item.lat, item.lng);
    }

    private void drawPins() {
        if (style == null || item == null) return;
        GeoJsonSource pin = style.getSourceAs(PIN_SRC);
        if (pin != null) pin.setGeoJson(Feature.fromGeometry(Point.fromLngLat(item.lng, item.lat)));
        if (me != null) {
            GeoJsonSource meSrc = style.getSourceAs(ME_SRC);
            if (meSrc != null) {
                meSrc.setGeoJson(Feature.fromGeometry(Point.fromLngLat(me.longitude, me.latitude)));
            }
        }
        if (me == null || GeoFireUtils.getDistanceBetween(me, new GeoLocation(item.lat, item.lng)) < 5) {
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(item.lat, item.lng)).zoom(15.0).build());
        } else {
            LatLngBounds bounds = new LatLngBounds.Builder()
                    .include(new LatLng(item.lat, item.lng))
                    .include(new LatLng(me.latitude, me.longitude))
                    .build();
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80));
        }
    }

    private void drawRoute(ItemDetailViewModel.Route r) {
        if (style == null || r.points.size() < 2) return;
        List<Point> pts = new ArrayList<>();
        for (double[] p : r.points) pts.add(Point.fromLngLat(p[0], p[1]));
        GeoJsonSource src = style.getSourceAs(ROUTE_SRC);
        if (src != null) src.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts)));
    }

    /** Horizontal photo strip. */
    private static class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.Holder> {
        private final List<String> urls;

        PhotoAdapter(List<String> urls) {
            this.urls = urls;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemPhotoBinding.inflate(LayoutInflater.from(parent.getContext()),
                    parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Glide.with(h.b.ivPhoto).load(urls.get(position)).fitCenter().into(h.b.ivPhoto);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ItemPhotoBinding b;

            Holder(ItemPhotoBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
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
