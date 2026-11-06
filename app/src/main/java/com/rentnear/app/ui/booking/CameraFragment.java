package com.rentnear.app.ui.booking;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentCameraBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One camera screen with two modes. SCAN reads a QR code with ML Kit; PHOTO takes up to 4
 * condition photos with CameraX. The result goes back to the caller through the Fragment Result API.
 */
public class CameraFragment extends Fragment {
    public static final String ARG_MODE = "mode";
    public static final String ARG_KEY = "key";
    public static final String MODE_SCAN = "scan";
    public static final String MODE_PHOTO = "photo";
    public static final String RESULT_TEXT = "text";
    public static final String RESULT_URIS = "uris";

    private static final int MAX_PHOTOS = 4;

    private FragmentCameraBinding b;
    private ExecutorService executor;
    private ImageCapture imageCapture;
    private BarcodeScanner scanner;
    private boolean scanDelivered = false;
    private String mode;
    private String key;
    private final ArrayList<String> photoUris = new ArrayList<>();

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    Toast.makeText(requireContext(), R.string.camera_permission_needed,
                            Toast.LENGTH_LONG).show();
                    NavHostFragment.findNavController(this).navigateUp();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCameraBinding.inflate(inflater, container, false);
        executor = Executors.newSingleThreadExecutor();

        Bundle args = getArguments();
        mode = args == null ? MODE_SCAN : args.getString(ARG_MODE, MODE_SCAN);
        key = args == null ? "result" : args.getString(ARG_KEY, "result");
        boolean photo = MODE_PHOTO.equals(mode);

        b.tvHint.setText(photo ? R.string.camera_hint_photo : R.string.camera_hint_scan);
        b.photoControls.setVisibility(photo ? View.VISIBLE : View.GONE);
        b.btnClose.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        updateCount();

        b.btnShutter.setOnClickListener(v -> takePhoto());
        b.btnDone.setOnClickListener(v -> {
            Bundle r = new Bundle();
            r.putStringArrayList(RESULT_URIS, new ArrayList<>(photoUris));
            getParentFragmentManager().setFragmentResult(key, r);
            NavHostFragment.findNavController(this).navigateUp();
        });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
        return b.getRoot();
    }

    private void startCamera() {
        if (b == null) return;
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            if (b == null) return;
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(b.previewView.getSurfaceProvider());
                provider.unbindAll();

                if (MODE_PHOTO.equals(mode)) {
                    imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();
                    provider.bindToLifecycle(getViewLifecycleOwner(),
                            CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                } else {
                    scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
                    ImageAnalysis analysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    analysis.setAnalyzer(executor, this::analyze);
                    provider.bindToLifecycle(getViewLifecycleOwner(),
                            CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.camera_failed, Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @androidx.annotation.OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(@NonNull ImageProxy proxy) {
        if (scanDelivered || scanner == null || proxy.getImage() == null) {
            proxy.close();
            return;
        }
        InputImage image = InputImage.fromMediaImage(proxy.getImage(),
                proxy.getImageInfo().getRotationDegrees());
        scanner.process(image)
                .addOnSuccessListener(codes -> {
                    for (Barcode code : codes) {
                        String value = code.getRawValue();
                        if (value != null && !scanDelivered) {
                            scanDelivered = true;
                            deliverScan(value);
                            break;
                        }
                    }
                })
                .addOnCompleteListener(t -> proxy.close());
    }

    private void deliverScan(String text) {
        if (b == null) return;
        Bundle r = new Bundle();
        r.putString(RESULT_TEXT, text);
        getParentFragmentManager().setFragmentResult(key, r);
        NavHostFragment.findNavController(this).navigateUp();
    }

    private void takePhoto() {
        if (imageCapture == null || photoUris.size() >= MAX_PHOTOS) return;
        File file = new File(requireContext().getCacheDir(),
                "condition_" + System.currentTimeMillis() + ".jpg");
        b.btnShutter.setEnabled(false);
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (b == null) return;
                        photoUris.add(Uri.fromFile(file).toString());
                        updateCount();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        if (b == null) return;
                        b.btnShutter.setEnabled(photoUris.size() < MAX_PHOTOS);
                        Toast.makeText(requireContext(), R.string.camera_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void updateCount() {
        if (b == null) return;
        b.tvCount.setText(getString(R.string.photo_count, photoUris.size(), MAX_PHOTOS));
        b.btnShutter.setEnabled(photoUris.size() < MAX_PHOTOS);
        b.btnDone.setEnabled(!photoUris.isEmpty());
    }

    @Override
    public void onDestroyView() {
        if (scanner != null) scanner.close();
        if (executor != null) executor.shutdown();
        super.onDestroyView();
        b = null;
    }
}
