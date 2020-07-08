package tv.paddi.android;

import android.app.Application;
import com.google.firebase.FirebaseApp;

//import co.paystack.android.PaystackSdk;
import co.paystack.android.PaystackSdk;
import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        FirebaseApp.initializeApp(this);
        PaystackSdk.initialize(this);

        CalligraphyConfig.initDefault(new CalligraphyConfig.Builder()
                .setDefaultFontPath("fonts/ubuntu.regular.ttf")
                .setFontAttrId(R.attr.fontPath)
                .build()
        );
    }
}

