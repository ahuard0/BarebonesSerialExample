package com.huard.barebonesserialexample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private SerialWriter _serialWriter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Handler terminalHandler = new Handler(Looper.getMainLooper(), msg -> {
            Log.d("USB", "Received Msg: " + msg.obj);
            return true;
        });

        Handler connectionHandler = new Handler(Looper.getMainLooper(), msg -> {
            Log.d("USB", "Connection Msg: " + msg.obj);
            return true;
        });

        _serialWriter = new SerialWriter(this, terminalHandler, connectionHandler);

        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(view -> _serialWriter.write("Hello this is the demo."));
    }
}