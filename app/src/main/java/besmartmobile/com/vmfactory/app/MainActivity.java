package besmartmobile.com.vmfactory.app;

import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FirstFragment fragment = new FirstFragment();
        fragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, fragment, null)
                .commitAllowingStateLoss();
    }
}
