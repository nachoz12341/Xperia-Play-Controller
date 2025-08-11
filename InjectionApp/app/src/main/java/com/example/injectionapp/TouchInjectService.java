package com.example.injectionapp;

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class TouchInjectService extends Service {

    private static final String TAG = "BluetoothClient";
    private static final String DEVICE_NAME = "ESP32_BT_SENDER";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private Thread readerThread;

    @Override
    public void onCreate() {
        super.onCreate();

        // ===== Legacy Notification for Android 2.3 (contentView required) =====
        try {
            Notification n = new Notification(
                    android.R.drawable.ic_menu_compass,
                    "Touch Injector Running",
                    System.currentTimeMillis()
            );
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
            // reflection for setLatestEventInfo(Context, CharSequence, CharSequence, PendingIntent)
            Method sli = n.getClass().getMethod("setLatestEventInfo",
                    Context.class, CharSequence.class, CharSequence.class, PendingIntent.class);
            sli.invoke(n, this, "Touch Injector", "Waiting for Bluetooth…", pi);
            startForeground(1, n);
        } catch (Throwable t) {
            Log.e("TouchInjectService", "Failed to create notification", t);
            stopSelf();
            return;
        }

        // ===== Bluetooth setup =====
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not available or not enabled");
            stopSelf();
            return;
        }

        BluetoothDevice target = findPairedByName(DEVICE_NAME);
        if (target == null) {
            Log.i(TAG, "Paired Bluetooth Devices:");
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                Log.i(TAG, "Name: " + d.getName() + ", Address: " + d.getAddress() + ", Class: " + d.getBluetoothClass());
            }
            Log.e(TAG, "Device not found: " + DEVICE_NAME);
            stopSelf();
            return;
        }

        // Connect using GB-safe multi-fallback flow
        connectOnGB(target, adapter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeQuietly(readerThread);
        closeQuietly(socket);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ===== GB-safe RFCOMM connect with multiple fallbacks =====
    private void connectOnGB(final BluetoothDevice device, final BluetoothAdapter adapter) {
        new Thread(() -> {
            BluetoothSocket sock = null;
            try {
                if (adapter.isDiscovering()) adapter.cancelDiscovery();

                // 1) Public insecure SPP (API 10+; will throw on API 9)
                try {
                    Method pubInsecure = BluetoothDevice.class
                            .getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
                    sock = (BluetoothSocket) pubInsecure.invoke(device, SPP);
                    sock.connect();
                    onConnected(sock);
                    Log.i(TAG, "Method 1 successful");
                    return;
                } catch (Throwable ignored) {
                    closeQuietly(sock);
                    sock = null;
                }

                // 2) Hidden insecure channel 1 (very reliable on GB)
                try {
                    Method hiddenInsecure = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
                    sock = (BluetoothSocket) hiddenInsecure.invoke(device, 1);
                    sock.connect();
                    onConnected(sock);
                    Log.i(TAG, "Method 2 successful");
                    return;
                } catch (Throwable ignored) {
                    closeQuietly(sock);
                    sock = null;
                }

                // 3) Hidden secure channel 1
                try {
                    Method hiddenSecure = device.getClass().getMethod("createRfcommSocket", int.class);
                    sock = (BluetoothSocket) hiddenSecure.invoke(device, 1);
                    sock.connect();
                    onConnected(sock);
                    Log.i(TAG, "Method 3 successful");
                    return;
                } catch (Throwable ignored) {
                    closeQuietly(sock);
                    sock = null;
                }

                // 4) Public secure SPP as absolute last resort
                try {
                    sock = device.createRfcommSocketToServiceRecord(SPP);
                    sock.connect();
                    onConnected(sock);
                    Log.i(TAG, "Method 4 successful");
                    return;
                } catch (Throwable t) {
                    closeQuietly(sock);
                    throw t;
                }

            } catch (Throwable t) {
                Log.e(TAG, "All connect attempts failed", t);
                stopSelf();
            }
        }, "bt-connect").start();
    }

    private void onConnected(BluetoothSocket s) throws IOException {
        this.socket = s;
        Log.i(TAG, "Bluetooth connected: " + s.getRemoteDevice().getName() + " @ " + s.getRemoteDevice().getAddress());

        // Start reader (optional; if you only need to send, you can skip)
        final InputStream in = s.getInputStream();
        final OutputStream out = s.getOutputStream();

        // Example: nudge the ESP32 once to confirm link (optional)
        try { out.write("HELLO\n".getBytes("UTF-8")); out.flush(); } catch (Throwable ignored) {}

        readerThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            try{
                byte[] buf = new byte[256];
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(su.getOutputStream());
                Gamepad gamepad = new Gamepad();
                Gamepad prevGamepad = new Gamepad();
                long lastBatchMs = 0;

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        int n = in.read(buf);
                        if (n <= 0) break;
                        // Parse your payload and dispatch to input injector here
                        // Example: if first byte == 0x01 => inject DPAD_UP, etc.
                        //handlePayload(buf, n);
                        int count = Math.min(n, 18);
                        StringBuilder batch = new StringBuilder(256);

                        for (int i = 0; i < count; i++) {
                            boolean newState = (buf[i] == 1);
                            boolean oldState = gamepad.buttons[i];

                            prevGamepad.buttons[i] = oldState;
                            gamepad.buttons[i] = newState;

                            if (newState != oldState) {
                                int linuxKey = gamepad.scancode[i]; // must be Linux input keycode
                                int value = newState ? 1 : 0;
                                batch.append("sendevent /dev/input/event4 1 ")
                                        .append(linuxKey).append(' ').append(value).append('\n');
                            }
                        }

                        long now = System.currentTimeMillis();
                        if (batch.length() > 0 && (now - lastBatchMs >= 10)) {
                            batch.append("sendevent /dev/input/event4 0 0 0\n");
                            os.writeBytes(batch.toString());
                            os.flush();
                            lastBatchMs = now;
                        }

                    } catch (IOException e) {
                        break;
                    }

                    Thread.sleep(10);
                }
                closeQuietly(socket);
                stopSelf();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }, "bt-reader");
        readerThread.start();

        // ===== If you want to send periodically, you can do it here using 'out' =====
        // Or keep a reference and write from elsewhere.
    }

    private void handlePayload(byte[] data, int len) {
        // TODO: Map your protocol to sendevent calls.
        // Example: if (data[0] == 0x01) injectDpadUp(); etc.
        // Keep using your existing persistent 'su' shell + DataOutputStream for performance.
        injectDpadUpShort();
    }

    private BluetoothDevice findPairedByName(String name) {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return null;
        for (BluetoothDevice d : bonded) {
            if (name.equals(d.getName())) return d;
        }
        return null;
    }

    private static void closeQuietly(BluetoothSocket s) {
        if (s != null) try { s.close(); } catch (Exception ignored) {}
    }

    private static void closeQuietly(Thread t) {
        if (t != null) try { t.interrupt(); } catch (Exception ignored) {}
    }

    // ===== Example sendevent helper (root shell kept open elsewhere) =====
    @SuppressWarnings("unused")
    private void injectDpadUpShort() {
        // Example only; you likely already have a persistent su shell. This shows the exact events.
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(su.getOutputStream());
                // Replace /dev/input/eventX and keycode 103 as appropriate for your device
                os.writeBytes("sendevent /dev/input/event2 1 103 1\n"); // KEY_UP down
                os.writeBytes("sendevent /dev/input/event2 0 0 0\n");
                os.writeBytes("sendevent /dev/input/event2 1 103 0\n"); // KEY_UP up
                os.writeBytes("sendevent /dev/input/event2 0 0 0\n");
                os.writeBytes("exit\n");
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "inject failed", e);
            }
        }, "inject").start();
    }
}
