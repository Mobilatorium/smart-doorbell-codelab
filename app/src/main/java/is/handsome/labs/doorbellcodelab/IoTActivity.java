package is.handsome.labs.doorbellcodelab;

import android.app.Activity;
import android.os.Bundle;

import timber.log.Timber;

public class IoTActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_iot);

        Timber.plant(new Timber.DebugTree());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }
}
