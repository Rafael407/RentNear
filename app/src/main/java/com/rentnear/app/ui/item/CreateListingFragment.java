package com.rentnear.app.ui.item;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.rentnear.app.R;
import com.rentnear.app.data.geoapify.GeoapifyClient;
import com.rentnear.app.data.geoapify.GeoapifyModels;
import com.rentnear.app.databinding.FragmentCreateListingBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.ui.common.CategoryPicker;
import com.rentnear.app.util.Categories;
import com.rentnear.app.util.LocationProvider;
import com.rentnear.app.viewmodel.CreateListingViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Create a listing: details, price, audience, pickup location, up to 4 photos. */
public class CreateListingFragment extends Fragment {
    private static final int MAX_PHOTOS = 4;

    private FragmentCreateListingBinding b;
    private CreateListingViewModel vm;

    private String category;
    private Double lat;
    private Double lng;
    private String address;
    private final List<Uri> photos = new ArrayList<>();
    private final List<GeoapifyModels.AutocompleteResponse.Feature> suggestions = new ArrayList<>();
    private SuggestionAdapter suggestionAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private boolean ignoreAddressChange = false;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS), uris -> {
                photos.clear();
                if (uris != null) photos.addAll(uris);
                if (b == null) return;
                b.tvPhotoCount.setText(photos.isEmpty() ? getString(R.string.no_photos)
                        : getString(R.string.photos_selected, photos.size()));
            });

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> useMyLocation());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCreateListingBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(CreateListingViewModel.class);

        suggestionAdapter = new SuggestionAdapter();
        b.etAddress.setAdapter(suggestionAdapter);
        b.etAddress.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (ignoreAddressChange) return;
                // Typing again invalidates a previously chosen address.
                lat = null;
                lng = null;
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                String text = s.toString().trim();
                if (text.length() < 3) return;
                pendingSearch = () -> vm.searchAddress(text,
                        LocationProvider.DEFAULT.latitude, LocationProvider.DEFAULT.longitude);
                handler.postDelayed(pendingSearch, 450);
            }
        });
        b.etAddress.setOnItemClickListener((parent, view, position, id) -> {
            GeoapifyModels.AutocompleteResponse.Feature f = suggestions.get(position);
            lat = f.properties.lat;
            lng = f.properties.lon;
            address = f.properties.formatted;
            setAddressText(address);
            b.tvLocationStatus.setText(getString(R.string.location_set, address));
        });

        vm.suggestions().observe(getViewLifecycleOwner(), list -> {
            suggestions.clear();
            if (list != null) suggestions.addAll(list);
            suggestionAdapter.notifyDataSetChanged();
            if (!suggestions.isEmpty() && b.etAddress.hasFocus()) b.etAddress.showDropDown();
        });

        b.btnCategory.setOnClickListener(v -> CategoryPicker.show(requireContext(),
                Categories.forListing(), category, c -> {
                    category = c;
                    b.btnCategory.setText(c);
                }));
        b.btnPhotos.setOnClickListener(v -> photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.btnUseLocation.setOnClickListener(v -> {
            if (LocationProvider.hasPermission(requireContext())) {
                useMyLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        b.btnSubmit.setOnClickListener(v -> submit());

        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            boolean on = Boolean.TRUE.equals(busy);
            b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            b.btnSubmit.setEnabled(!on);
        });
        vm.created().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            vm.consumeResult();
            if (r.isOk()) {
                Toast.makeText(requireContext(), R.string.listing_published, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
            } else {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
            }
        });

        if (!GeoapifyClient.isConfigured()) {
            b.etAddress.setHint(R.string.use_my_location);
        }
        return b.getRoot();
    }

    private void useMyLocation() {
        if (!isAdded()) return;
        LocationProvider.current(requireContext(), (loc, real) -> {
            if (b == null) return;
            if (!real) {
                Toast.makeText(requireContext(), R.string.default_location_notice,
                        Toast.LENGTH_LONG).show();
                return;
            }
            lat = loc.latitude;
            lng = loc.longitude;
            address = String.format(Locale.US, "Current location (%.5f, %.5f)", lat, lng);
            setAddressText(address);
            b.tvLocationStatus.setText(getString(R.string.location_set, address));
        });
    }

    private void setAddressText(String text) {
        ignoreAddressChange = true;
        b.etAddress.setText(text);
        b.etAddress.dismissDropDown();
        ignoreAddressChange = false;
    }

    private void submit() {
        String title = text(b.etTitle.getText());
        String description = text(b.etDescription.getText());
        double price = parse(text(b.etPrice.getText()));
        double declared = parse(text(b.etDeclared.getText()));

        if (title.isEmpty()) { toast(R.string.err_title); return; }
        if (category == null) { toast(R.string.err_category); return; }
        if (price <= 0) { toast(R.string.err_price); return; }
        if (declared <= 0) { toast(R.string.err_declared); return; }
        if (lat == null || lng == null) { toast(R.string.err_location); return; }
        if (photos.isEmpty()) { toast(R.string.err_photos); return; }

        String unit = b.toggleUnit.getCheckedButtonId() == R.id.btnPerHour
                ? Item.UNIT_HOUR : Item.UNIT_DAY;
        String audience = b.toggleAudience.getCheckedButtonId() == R.id.btnFriends
                ? Item.AUDIENCE_FRIENDS : Item.AUDIENCE_EVERYONE;
        vm.submit(requireContext(), new ArrayList<>(photos), title, description, category, price,
                unit, declared, address == null ? "" : address, lat, lng, audience);
    }

    private void toast(int res) {
        Toast.makeText(requireContext(), res, Toast.LENGTH_SHORT).show();
    }

    private static String text(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        super.onDestroyView();
        b = null;
    }

    /**
     * Dropdown adapter that never filters: the suggestions already match what was typed, and a
     * normal ArrayAdapter would hide results that do not start with the typed text.
     */
    private class SuggestionAdapter extends BaseAdapter implements Filterable {
        @Override public int getCount() { return suggestions.size(); }
        @Override public Object getItem(int i) { return suggestions.get(i).properties.formatted; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) (convertView != null ? convertView
                    : LayoutInflater.from(parent.getContext()).inflate(
                    android.R.layout.simple_dropdown_item_1line, parent, false));
            tv.setText(suggestions.get(position).properties.formatted);
            return tv;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults r = new FilterResults();
                    r.values = suggestions;
                    r.count = suggestions.size();
                    return r;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }
}
