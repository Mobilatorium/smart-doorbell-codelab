package is.handsome.labs.doorbellcodelab;

import android.util.Base64;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.Map;

class FirebaseEventService {
    private FirebaseDatabase firebaseDatabase;

    public FirebaseEventService() {
        firebaseDatabase = FirebaseDatabase.getInstance();
    }

    void pushEvent(byte[] imageBytes, Map<String, Float> annotations) {
        DatabaseReference logReference = firebaseDatabase.getReference("logs").push();
        logReference.child("timestamp").setValue(ServerValue.TIMESTAMP);

        String encoded = Base64.encodeToString(imageBytes,
                Base64.NO_WRAP | Base64.URL_SAFE);
        logReference.child("image").setValue(encoded);

        if (annotations != null) {
            logReference.child("annotations").setValue(annotations);
        }
    }
}
