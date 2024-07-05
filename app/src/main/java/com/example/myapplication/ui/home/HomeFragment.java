package com.example.myapplication.ui.home;

import android.location.Location;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.myapplication.CustomImageView;
import com.example.myapplication.ImageAdapter;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.example.myapplication.helpers.GeocoderHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private GridView gridView;
    private CustomImageView imageView;
    private TextView textViewOverlay;
    private ArrayList<String> imagePaths;
    private ImageAdapter imageAdapter;
    private GestureDetector gestureDetector;
    private int currentPosition;
    private float historicX;
    private float historicY;
    private FrameLayout imageContainer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        gridView = root.findViewById(R.id.gridView);
        imageView = root.findViewById(R.id.imageView);
        textViewOverlay = root.findViewById(R.id.textViewOverlay);
        imageContainer = root.findViewById(R.id.imageContainer);
        imagePaths = new ArrayList<>();

        // Load images from app's private directory
        loadImages();

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            currentPosition = position;
            showImage(position);
        });

        imageView.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case (MotionEvent.ACTION_DOWN) :
                        historicY = event.getY();
                        historicX = event.getX();
                        return true;
                    case (MotionEvent.ACTION_UP) :
                        float yVal = event.getY();
                        float xVal = event.getX();
                        if (Math.abs(yVal - historicY) > 100) {
                            showGridView();
                            return true;
                        }
                        if (xVal - historicX > 100 && currentPosition > 0) {
                            showPreviousImage();
                            currentPosition--;
                            return true;
                        } else if ( historicX - xVal > 100 && currentPosition+1 < imagePaths.size()) {
                            showNextImage();
                            currentPosition++;
                            return true;
                        }
                        return true;
                    default :
                        return false;
                }
            }
        });

        return root;
    }

    private void loadImages() {
        // Access app's private storage directory
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File imagesFolder = new File(picturesDir, "/ThirdEye");
        if (imagesFolder.exists()) {
            File[] images = imagesFolder.listFiles();
            if (images != null) {
                for (File image : images) {
                    if (image.isFile() && image.getName().toLowerCase().endsWith(".jpg")) {
                        imagePaths.add(image.getAbsolutePath());
                    }
                }
            }
            imageAdapter = new ImageAdapter(requireContext(), imagePaths);
            gridView.setAdapter(imageAdapter);
        } else {
            Toast.makeText(requireContext(), "Images folder does not exist", Toast.LENGTH_SHORT).show();
        }
    }

    private void showImage(int position) {
        gridView.setVisibility(View.GONE);
        imageContainer.setVisibility(View.VISIBLE);
        Glide.with(this).load(imagePaths.get(position)).into(imageView);

        // Display the timestamp and location
        File imageFile = new File(imagePaths.get(position));
        try {
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());

            // Retrieve attributes set during image saving
            String timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (timestamp == null) timestamp = "";
            String latitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String latitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String longitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String longitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

            // Format location if available
            String location;
            if (latitude != null && longitude != null) {
                double lat = convertToDegree(latitude);
                double lon = convertToDegree(longitude);
                if ("S".equals(latitudeRef)) {
                    lat = -lat;
                }
                if ("W".equals(longitudeRef)) {
                    lon = -lon;
                }
                location = String.format(Locale.getDefault(), "Lat: %f, Lon: %f", lat, lon);

                // Retrieve city and country names
                String cityAndCountry = GeocoderHelper.getCityAndCountry(requireContext(), lat, lon);

                // Display timestamp, location, city, and country information
                textViewOverlay.setText(String.format("%s\n%s\n%s", timestamp, location, cityAndCountry));
            } else {
                location = "Location not available";
                textViewOverlay.setText(String.format("%s\n%s", timestamp, location));
            }

        } catch (IOException e) {
            e.printStackTrace();
            textViewOverlay.setText("Error reading image metadata");
        }
    }

    private double convertToDegree(String stringDMS) {
        String[] DMS = stringDMS.split(",", 3);

        String[] stringD = DMS[0].split("/", 2);
        double D0 = Double.valueOf(stringD[0]);
        double D1 = Double.valueOf(stringD[1]);
        double FloatD = D0 / D1;

        String[] stringM = DMS[1].split("/", 2);
        double M0 = Double.valueOf(stringM[0]);
        double M1 = Double.valueOf(stringM[1]);
        double FloatM = M0 / M1;

        String[] stringS = DMS[2].split("/", 2);
        double S0 = Double.valueOf(stringS[0]);
        double S1 = Double.valueOf(stringS[1]);
        double FloatS = S0 / S1;

        return FloatD + (FloatM / 60) + (FloatS / 3600);
    }


    private void showGridView() {
        imageContainer.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        gridView.smoothScrollToPosition(currentPosition);
    }

    private void showNextImage() {
        if (currentPosition < imagePaths.size() - 1) {
            currentPosition++;
            showImage(currentPosition);
        }
    }

    private void showPreviousImage() {
        if (currentPosition > 0) {
            currentPosition--;
            showImage(currentPosition);
        }
    }
}
