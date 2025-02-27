package com.huard.barebonesserialexample;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.HashMap;

public class SerialWriter implements AutoCloseable {
    private static final String _TAG = "SerialWriter";
    private static final String ACTION_USB_PERMISSION = "com.huard.arescuedemo.USB_PERMISSION";

    private final Context _context;
    private final UsbManager _usbManager;
    private final Handler _terminalHandler;
    private final Handler _connectionHandler;
    private PendingIntent _usbPermissionIntent;
    private UsbDevice _device;
    private UsbDeviceConnection _connection;
    private UsbInterface _usbInterface;
    private UsbEndpoint _inputEndpoint;
    private UsbEndpoint _outputEndpoint;
    private boolean _running;
    private Thread _readerThread;

    public SerialWriter(@NonNull Context context, Handler terminalHandler, Handler connectionHandler) {
        this._context = context;
        this._usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this._terminalHandler = terminalHandler;
        this._connectionHandler = connectionHandler;
        _usbPermissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

        // Register USB event receivers
        ContextCompat.registerReceiver(context, _usbPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED);
        context.registerReceiver(_usbAttachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));
        context.registerReceiver(_usbDetachReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        connect();
    }

    /**
     * Request USB permission and connect to the device
     */
    public void connect() {
        HashMap<String, UsbDevice> deviceList = _usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == 9025 && device.getProductId() == 67) { // Match Arduino
                this._device = device;
                if (!_usbManager.hasPermission(device)) {
                    _usbManager.requestPermission(device, _usbPermissionIntent);
                } else {
                    initializeUsbDevice();
                }
                break;
            }
        }
    }

    /**
     * Disconnect and release resources
     */
    public void disconnect() {
        close();
    }

    /**
     * Initialize the USB device and set up communication
     */
    private void initializeUsbDevice() {
        if (_device == null) {
            Log.e(_TAG, "No compatible USB device found.");
            return;
        }

        _connection = _usbManager.openDevice(_device);
        if (_connection == null) {
            Log.e(_TAG, "Could not open USB connection.");
            return;
        }

        _usbInterface = _device.getInterface(1); // Use data interface
        if (!_connection.claimInterface(_usbInterface, true)) {
            Log.e(_TAG, "Failed to claim USB interface.");
            return;
        }

        // Find endpoints
        for (int i = 0; i < _usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = _usbInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    _inputEndpoint = endpoint;
                } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    _outputEndpoint = endpoint;
                }
            }
        }

        if (_outputEndpoint == null || _inputEndpoint == null) {
            Log.e(_TAG, "Could not find required bulk endpoints.");
            return;
        }

        Log.d(_TAG, "USB Device Initialized Successfully!");
        updateConnectionStatus("Connected");
        startSerialMonitor();
    }

    /**
     * Start a background thread to read incoming serial data
     */
    private void startSerialMonitor() {
        _running = true;
        _readerThread = new Thread(() -> {
            byte[] buffer = new byte[64];
            while (_running) {
                if (_connection != null && _inputEndpoint != null) {
                    int receivedBytes = _connection.bulkTransfer(_inputEndpoint, buffer, buffer.length, 1000);
                    if (receivedBytes > 0) {
                        String receivedData = new String(buffer, 0, receivedBytes);
                        Log.d(_TAG, "Received: " + receivedData);
                        updateTerminal(receivedData);
                    }
                }
            }
        });
        _readerThread.start();
    }

    /**
     * Write a command to the USB serial device
     */
    public void write(String command) {
        if (_connection == null || _outputEndpoint == null) {
            Log.e(_TAG, "USB connection or OUT endpoint is not initialized.");
            return;
        }

        byte[] buffer = command.getBytes();
        int numBytesWritten = _connection.bulkTransfer(_outputEndpoint, buffer, buffer.length, 5000);

        if (numBytesWritten < 0) {
            Log.e(_TAG, "Write error: " + numBytesWritten);
        } else if (numBytesWritten == 0) {
            Log.e(_TAG, "Write timeout: " + numBytesWritten);
        } else {
            Log.d(_TAG, "Sent: " + command + " (Bytes written: " + numBytesWritten + ")");
        }
    }

    /**
     * Update UI handlers with received data
     */
    private void updateTerminal(String message) {
        if (_terminalHandler != null) {
            Message msg = _terminalHandler.obtainMessage();
            msg.obj = message;
            _terminalHandler.sendMessage(msg);
        }
    }

    private void updateConnectionStatus(String status) {
        if (_connectionHandler != null) {
            Message msg = _connectionHandler.obtainMessage();
            msg.obj = status;
            _connectionHandler.sendMessage(msg);
        }
    }

    /**
     * USB Permission Receiver
     */
    private final BroadcastReceiver _usbPermissionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                if (_device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    initializeUsbDevice();
                } else {
                    Log.e(_TAG, "USB permission denied.");
                }
            }
        }
    };

    /**
     * USB Attach Receiver
     */
    private final BroadcastReceiver _usbAttachReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                connect();
            }
        }
    };

    /**
     * USB Detach Receiver
     */
    private final BroadcastReceiver _usbDetachReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                disconnect();
            }
        }
    };

    @Override
    public void close() {
        _running = false;
        if (_readerThread != null) {
            _readerThread.interrupt();
        }
        if (_connection != null) {
            _connection.releaseInterface(_usbInterface);
            _connection.close();
        }
        _context.unregisterReceiver(_usbPermissionReceiver);
        _context.unregisterReceiver(_usbAttachReceiver);
        _context.unregisterReceiver(_usbDetachReceiver);
        Log.d(_TAG, "USB connection closed.");
        updateConnectionStatus("Disconnected");
    }
}
