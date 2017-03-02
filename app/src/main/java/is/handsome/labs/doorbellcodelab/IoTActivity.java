package is.handsome.labs.doorbellcodelab;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

import timber.log.Timber;

public class IoTActivity extends Activity {

    private static final String GPIO_PIN_BUTTON_NAME = "BCM21";
    private static final String GPIO_PIN_LED_NAME = "BCM6";
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    private Button button;

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
            button = new Button(
                    GPIO_PIN_BUTTON_NAME,
                    Button.LogicState.PRESSED_WHEN_LOW);
            button.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    String pressedString = pressed ? "pressed" : "unpressed";
                    Timber.d(GPIO_PIN_BUTTON_NAME + ": " + pressedString);
                }
            });

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
}
