package com.example.myapplication.ui.services;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.EventDrivenCapture;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentServicesBinding;

public class ServicesFragment extends Fragment {

    private FragmentServicesBinding binding;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    public ServicesFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentServicesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Switch serviceSwitch = root.findViewById(R.id.vibrationDetection);
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (checkLocationPermission()) {
                        startCaptureService();
                    } else {
                        requestLocationPermission();
                    }
                } else {
                    stopCaptureService();
                }
            }
        });

        return root;
    }

    private boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(requireContext(), "Location permission is required to start service", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start service if switch is checked
                Switch serviceSwitch = binding.vibrationDetection;
                if (serviceSwitch.isChecked()) {
                    startCaptureService();
                }
            } else {
                // Permission denied, handle this scenario (optional)
                Toast.makeText(requireContext(), "Location permission denied, cannot start service", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCaptureService() {
        EventDrivenCapture.startService(requireContext());
        Toast.makeText(requireContext(), "Service started", Toast.LENGTH_SHORT).show();
    }

    private void stopCaptureService() {
        EventDrivenCapture.stopService(requireContext());
        Toast.makeText(requireContext(), "Service stopped", Toast.LENGTH_SHORT).show();
    }
}
