package com.example.myapplication.helpers;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Semaphore;

public class CameraHelper {

    private static final String TAG = "CameraHelper";

    private final Context context;
    private SurfaceView surfaceView;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    private ImageReader imageReader;
    private Runnable onCameraOpenedCallback;

    private LocationManager locationManager;
    private Location currentLocation;

    public CameraHelper(Context context, SurfaceView surfaceView, Runnable onCameraOpenedCallback) {
        this.context = context;
        this.surfaceView = surfaceView;
        this.onCameraOpenedCallback = onCameraOpenedCallback;
        initLocationManager();
    }

    private void initLocationManager() {
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing: " + e.getMessage());
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            currentLocation = location;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}
    };

    public void startCamera() {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            openCamera();
        } else {
            Toast.makeText(context, "No camera detected on device", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopCamera() {
        closeCamera();
    }

    public void release() {
        closeCamera();
        locationManager.removeUpdates(locationListener);
    }

    @SuppressLint("MissingPermission")
    private void openCamera() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIDs = cameraManager.getCameraIdList();
            cameraManager.openCamera(String.valueOf(cameraIDs[0]), new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    cameraDevice = camera;
                    if (surfaceView != null) {
                        createCameraPreviewSession();
                    } else {
                        createImageReader();
                        createCaptureSession();
                    }
                    if (onCameraOpenedCallback != null) {
                        onCameraOpenedCallback.run();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    cameraDevice = null;
                    Log.e(TAG, "Camera device error: " + error);
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to access camera: " + e.getMessage());
        }
    }

    private void createCameraPreviewSession() {
        SurfaceHolder holder = surfaceView.getHolder();
        try {
            Surface surface = holder.getSurface();
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Camera access exception while starting preview: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera capture session.");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to access camera: " + e.getMessage());
        }
    }

    private void createImageReader() {
        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
    }

    private void createCaptureSession() {
        try {
            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            try {
                                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                captureRequestBuilder.addTarget(imageReader.getSurface());
                                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Camera access exception while configuring capture request: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure camera capture session.");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to create capture session: " + e.getMessage());
        }
    }

    public void captureImage() {
        if (cameraDevice == null) {
            Log.e(TAG, "Camera device is null.");
            return;
        }

        try {
            cameraCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    Log.d(TAG, "Image captured!");
                    Toast.makeText(context, "Image Saved", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera access exception while capturing image: " + e.getMessage());
        }
    }

    private final ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    saveImage(bytes);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while processing image: " + e.getMessage());
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    };

    public void saveImage(byte[] bytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Bitmap processedBitmap = processBitmap(bitmap);
        saveBitmapToFile(processedBitmap);
    }

    private void saveBitmapToFile(Bitmap bitmap) {
        ContentValues values = new ContentValues();
        String fileName = "IMG_" + (System.currentTimeMillis() / 1000L) + ".jpg";
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        String filePath;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ThirdEye");
            filePath = Environment.DIRECTORY_PICTURES + "/ThirdEye/" + fileName;
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), "/ThirdEye");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            filePath = new File(directory, fileName).getAbsolutePath();
            values.put(MediaStore.MediaColumns.DATA, filePath);
        }

        ContentResolver resolver = context.getContentResolver();
        OutputStream outputStream = null;

        try {
            outputStream = resolver.openOutputStream(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values));
            if (outputStream != null) {
                // Rotate the bitmap by 90 degrees clockwise
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                // Compress and save the rotated bitmap
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                outputStream.close();

                if (currentLocation != null) {
                    saveGeoTag(new File(new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES), "/ThirdEye"), fileName), currentLocation);
                }

                Log.d(TAG, "Image saved to " + Environment.DIRECTORY_PICTURES + "/ThirdEye");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save image: " + e.getMessage());
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveGeoTag(File file, Location location) {
        try {
            ExifInterface exif = new ExifInterface(file);
            setGpsInfo(exif, location);
            exif.saveAttributes();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save geolocation data: " + e.getMessage());
        }
    }

    // Helper method to set GPS info
    private void setGpsInfo(ExifInterface exif, Location location) {
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convert(location.getLatitude()));
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, location.getLatitude() > 0 ? "N" : "S");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convert(location.getLongitude()));
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, location.getLongitude() > 0 ? "E" : "W");
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, String.valueOf(location.getAltitude()));
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, location.getAltitude() > 0 ? "0" : "1");
        exif.setAttribute(ExifInterface.TAG_DATETIME, convertTime(location.getTime()));
    }

    private String convert(double coordinate) {
        coordinate = Math.abs(coordinate);
        int degree = (int) coordinate;
        coordinate *= 60;
        coordinate -= (degree * 60.0d);
        int minute = (int) coordinate;
        coordinate *= 60;
        coordinate -= (minute * 60.0d);
        int second = (int) (coordinate * 10000.0d);

        return String.format("%d/1,%d/1,%d/10000", degree, minute, second);
    }

    private String convertTime(long time) {
        // Convert timestamp to EXIF format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
        return sdf.format(new Date(time));
    }


    private Bitmap processBitmap(Bitmap bitmap) {
        int newWidth = 800;
        int newHeight = (bitmap.getHeight() * newWidth) / bitmap.getWidth();
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }

    public void closeSession() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }
}
