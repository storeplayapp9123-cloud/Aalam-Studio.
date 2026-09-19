package com.aalam.studio;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnCreateApp = findViewById(R.id.btnCreateApp);
        Button btnCreateGame = findViewById(R.id.btnCreateGame);

        btnCreateApp.setOnClickListener(v ->
                Toast.makeText(this, "Create App - coming soon", Toast.LENGTH_SHORT).show());

        btnCreateGame.setOnClickListener(v ->
                Toast.makeText(this, "Create Game - coming soon", Toast.LENGTH_SHORT).show());
    }
}
