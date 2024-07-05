package com.example.myapplication.helpers;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class GeocoderHelper {

    private static final String TAG = "GeocoderHelper";

    public static String getCityAndCountry(Context context, double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        String result = "";

        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String city = address.getLocality();
                String country = address.getCountryName();
                result = String.format("%s, %s", city, country);
            } else {
                result = "Location not found";
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from coordinates: " + e.getMessage());
            result = "Error getting location";
        }

        return result;
    }
}
