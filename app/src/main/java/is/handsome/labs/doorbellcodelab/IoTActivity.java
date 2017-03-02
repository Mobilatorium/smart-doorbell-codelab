package is.handsome.labs.doorbellcodelab;

import android.app.Activity;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.nio.ByteBuffer;

import timber.log.Timber;

public class IoTActivity extends Activity {

    private static final String GPIO_PIN_BUTTON_NAME = "BCM21";
    private static final String GPIO_PIN_LED_NAME = "BCM6";
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private Button button;

    private DoorbellCamera doorbellCamera;

    private Gpio ledGpio;
    private Handler handler = new Handler();
    private Runnable blinkRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit if the GPIO is already closed
            if (ledGpio == null) {
                return;
            }

            try {
                // Step 2.3. Toggle the LED state
                ledGpio.setValue(!ledGpio.getValue());

                // Step 2.4. Schedule another event after delay.
                handler.postDelayed(blinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);
            } catch (IOException e) {
                Timber.e("Error on PeripheralIO API", e);
            }
        }
    };

    private Handler backgroundIoHandler;
    private HandlerThread backgroundTaskHandlerThread;

    // Callback to receive captured camera image data
    private ImageReader.OnImageAvailableListener onImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Get the raw image bytes
                    Image image = reader.acquireLatestImage();
                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    onPictureTaken(imageBytes);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot);

        Timber.plant(new Timber.DebugTree());

        PeripheralManagerService service = new PeripheralManagerService();
        Timber.d("Available GPIO: " + service.getGpioList());

        try {
            // Init button
            button = new Button(
                    GPIO_PIN_BUTTON_NAME,
                    Button.LogicState.PRESSED_WHEN_LOW);
            button.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        Timber.d(GPIO_PIN_BUTTON_NAME + ": pressed. Take a picture.");
                        doorbellCamera.takePicture();
                    } else {
                        Timber.d(GPIO_PIN_BUTTON_NAME + ": unpressed");
                    }
                }
            });

            // Camera
            doorbellCamera = DoorbellCamera.getInstance();
            doorbellCamera.initializeCamera(this, backgroundIoHandler, onImageAvailableListener);

            // Led
            // Step 2.1. Create GPIO connection.
            ledGpio = service.openGpio(GPIO_PIN_LED_NAME);
            // Step 2.2. Configure as an output.
            ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            // Step 2.3. Repeat using a handler.
            handler.post(blinkRunnable);
        } catch (IOException e) {
            Timber.e(e);
        }

        startBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Button
        try {
            button.close();
        } catch (IOException e) {
            Timber.e("Error while close " + GPIO_PIN_BUTTON_NAME + "button", e);
        }

        // Led
        // Step 2.4. Remove handler events on close.
        handler.removeCallbacks(blinkRunnable);

        // Step 2.5. Close the resource.
        if (ledGpio != null) {
            try {
                ledGpio.close();
            } catch (IOException e) {
                Timber.e("Error on PeripheralIO API", e);
            }
        }
    }

    private void startBackgroundThread() {
        backgroundTaskHandlerThread = new HandlerThread("InputThread");
        backgroundTaskHandlerThread.start();
        backgroundIoHandler = new Handler(backgroundTaskHandlerThread.getLooper());
    }

    private void onPictureTaken(byte[] imageBytes) {
        if (imageBytes != null) {
            // ...process the captured image...
        }
    }

}
