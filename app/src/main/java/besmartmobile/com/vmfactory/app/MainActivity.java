package besmartmobile.com.vmfactory.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;


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
