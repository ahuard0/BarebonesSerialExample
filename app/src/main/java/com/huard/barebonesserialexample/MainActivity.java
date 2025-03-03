package com.huard.barebonesserialexample;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements StatusConnectedListener, StatusTerminalListener {

    private TextView lblTerminal;
    private TextView lblConnected;
    private ConnectionManager connectionManager;

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

        connectionManager = new ConnectionManager(this, statusTerminalHandler, statusConnectionHandler);

        initialize();
        connectionManager.connect();
    }

    private final Handler statusConnectionHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String message = (String) msg.obj;
            updateConnectionStatus(message);
        }
    };

    private final Handler statusTerminalHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String message = (String) msg.obj;
            updateTerminalStatus(message);
        }
    };

    private void initialize() {
        lblTerminal = findViewById(R.id.lblTerminal);
        lblConnected = findViewById(R.id.lblConnected);

        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnDisconnect = findViewById(R.id.btnDisconnect);
        Button btnOnD7 = findViewById(R.id.btnOnD7);

        btnConnect.setOnClickListener(v -> onPressConnect());
        btnDisconnect.setOnClickListener(v -> onPressDisconnect());
        btnOnD7.setOnClickListener(v -> onPressOnD7());
    }

    @Override
    public void updateTerminalStatus(String msg) {
        lblTerminal.setText(msg);
    }

    @Override
    public void updateConnectionStatus(String msg) {
        lblConnected.setText(msg);
    }

    private void onPressConnect() {
        connectionManager.connect();
    }

    private void onPressDisconnect() {
        connectionManager.disconnect();
    }

    private void onPressOnD7() {
        connectionManager.client.write("$|_VIBE|4,1,500,50,5,1,500,50,6,0,500,50,7,1,500,50|2069\n");
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        connectionManager.disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionManager.disconnect();
    }
}