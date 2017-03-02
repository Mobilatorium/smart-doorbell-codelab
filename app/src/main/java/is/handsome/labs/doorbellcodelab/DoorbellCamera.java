package is.handsome.labs.doorbellcodelab;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.Collections;

import timber.log.Timber;

import static android.content.Context.CAMERA_SERVICE;

class DoorbellCamera {
    // Camera image parameters (device-specific)
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 240;
    private static final int MAX_IMAGES = 1;
    // Image result processor
    private ImageReader imageReader;
    // Active camera device connection
    private CameraDevice cameraDevice;
    // Callback handling devices state changes
    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {

                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    DoorbellCamera.this.cameraDevice = cameraDevice;
                }

                @Override
                public void onClosed(@NonNull CameraDevice cameraDevice) {
                    Timber.d("Closed camera, releasing");
                    cameraDevice = null;
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    Timber.d("Camera disconnected, closing.");
                    cameraDevice.close();
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    Timber.e("Camera device error: " + error + ", closing.");
                    cameraDevice.close();
                }
            };
    // Active camera capture session
    private CameraCaptureSession captureSession;
    // Callback handling capture progress events
    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull CaptureResult partialResult) {
                    Timber.d("Partial result");
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                        @NonNull CaptureRequest request,
                        @NonNull TotalCaptureResult result) {
                    session.close();
                    captureSession = null;
                    Timber.d("CaptureSession closed");
                }
            };
    // Callback handling session state changes
    private final CameraCaptureSession.StateCallback sessionCallback =
            new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    // When the session is ready, we start capture.
                    captureSession = cameraCaptureSession;
                    triggerImageCapture();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Timber.w("Failed to configure camera");
                }
            };

    static DoorbellCamera getInstance() {
        return InstanceHolder.mCamera;
    }

    private void triggerImageCapture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            captureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException cae) {
            Timber.e("camera capture exception", cae);
        }
    }

    private static class InstanceHolder {
        private static DoorbellCamera mCamera = new DoorbellCamera();
    }

    // Initialize a new camera device connection
    public void initializeCamera(Context context,
            Handler backgroundHandler,
            ImageReader.OnImageAvailableListener imageListener) {
        Timber.d("!DoorbellCamera.initializeCamera");

        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        String[] camIds = {};
        try {
            camIds = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            Timber.d("Cam access exception getting IDs", e);
        }
        if (camIds.length < 1) {
            Timber.d("No cameras found");
            return;
        }
        String id = camIds[0];

        // Initialize image processor
        imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT,
                ImageFormat.JPEG, MAX_IMAGES);
        imageReader.setOnImageAvailableListener(imageListener, backgroundHandler);

        // Open the camera resource
        try {
            manager.openCamera(id, stateCallback, backgroundHandler);
        } catch (CameraAccessException cae) {
            Timber.d("Camera access exception", cae);
        }
    }

    // Close the camera resources
    public void shutDown() {
        Timber.d("!DoorbellCamera.shutDown");
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }

    public void takePicture() {
        if (cameraDevice == null) {
            Timber.w("Cannot capture image. Camera not initialized.");
            return;
        }

        // Here, we create a CameraCaptureSession for capturing still images.
        try {
            cameraDevice.createCaptureSession(
                    Collections.singletonList(imageReader.getSurface()),
                    sessionCallback,
                    null);
        } catch (CameraAccessException cae) {
            Timber.e("access exception while preparing pic", cae);
        }
    }
}
