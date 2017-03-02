package is.handsome.labs.doorbellcodelab;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

import timber.log.Timber;

public class IoTActivity extends Activity {

    private static final String GPIO_PIN_BUTTON_NAME = "BCM21";
    private static final String GPIO_PIN_LED_NAME = "BCM6";
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private Gpio buttonGpio;
    private GpioCallback buttonGpioCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {

            Timber.d(gpio + ": pressed");
            // Step 1.5. Return true to keep callback active.
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Timber.e(gpio + ": error " + error);
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot);

        Timber.plant(new Timber.DebugTree());

        PeripheralManagerService service = new PeripheralManagerService();
        Timber.d("Available GPIO: " + service.getGpioList());

        try {
            // Button
            // Step 1.1. Create GPIO connection.
            buttonGpio = service.openGpio(GPIO_PIN_BUTTON_NAME);
            // Step 1.2. Configure as an input.
            buttonGpio.setDirection(Gpio.DIRECTION_IN);
            // Step 1.3. Enable edge trigger events.
            buttonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            // Step 1.4. Register an event callback.
            buttonGpio.registerGpioCallback(buttonGpioCallback);

            // Led
            // Step 2.1. Create GPIO connection.
            ledGpio = service.openGpio(GPIO_PIN_LED_NAME);
            // Step 2.2. Configure as an output.
            ledGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            // Step 2.3. Repeat using a handler.
            handler.post(blinkRunnable);
        } catch (IOException exception) {
            Timber.e(exception);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Button
        // Step 1.6. Close the resource
        if (buttonGpio != null) {
            buttonGpio.unregisterGpioCallback(buttonGpioCallback);
            try {
                buttonGpio.close();
            } catch (IOException exception) {
                Timber.e("Error on PeripheralIO API", exception);
            }
        }

        // Led
        // Step 2.4. Remove handler events on close.
        handler.removeCallbacks(blinkRunnable);

        // Step 2.5. Close the resource.
        if (ledGpio != null) {
            try {
                ledGpio.close();
            } catch (IOException exception) {
                Timber.e("Error on PeripheralIO API", exception);
            }
        }
    }
}
