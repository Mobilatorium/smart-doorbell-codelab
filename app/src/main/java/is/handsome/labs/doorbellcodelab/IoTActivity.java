package is.handsome.labs.doorbellcodelab;

import android.app.Activity;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.nio.ByteBuffer;

import timber.log.Timber;

public class IoTActivity extends Activity {

    private static final String GPIO_PIN_BUTTON_NAME = "BCM21";

    private Button button;

    private DoorbellCamera doorbellCamera;

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

            // Init camera
            doorbellCamera = DoorbellCamera.getInstance();
            doorbellCamera.initializeCamera(this, backgroundIoHandler, onImageAvailableListener);

        } catch (IOException e) {
            Timber.e(e);
        }

        startBackgroundThread();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doorbellCamera.shutDown();
        try {
            button.close();
        } catch (IOException e) {
            Timber.e(e, "Error while close " + GPIO_PIN_BUTTON_NAME + "button");
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
