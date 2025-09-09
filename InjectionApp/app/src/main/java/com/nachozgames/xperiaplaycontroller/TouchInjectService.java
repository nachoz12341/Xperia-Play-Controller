package com.nachozgames.xperiaplaycontroller;

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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

public class TouchInjectService extends Service {

    private static final String TAG = "BluetoothClient";
    private static final String DEVICE_NAME = "Xperia Play Controller";
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

        readerThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

            try{
                byte[] buf = new byte[32];
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(su.getOutputStream());
                Gamepad gamepad = new Gamepad();
                Gamepad prevGamepad = new Gamepad();
                long lastBatchMs = 0;
                long lastRumble = 0;
                long lastRumbleDur = 0;
                boolean rumble = false;
                boolean touch_down = false;
                BufferedReader br = new BufferedReader(new FileReader("/sys/class/timed_output/vibrator/enable"), 64);

                while (!Thread.currentThread().isInterrupted())
                {
                    try {
                        String line = br.readLine();
                        int dur = 0;

                        if(line!=null)
                        {
                            dur =  Integer.parseInt(line);
                        }

                        if (dur>0)
                        {
                            if(!rumble || dur>=lastRumbleDur-(System.currentTimeMillis()-lastRumble))
                            {
                                rumble = true;
                                lastRumble = System.currentTimeMillis();
                                lastRumbleDur = dur;
                                try {
                                    byte[] bytes = ByteBuffer.allocate(512).putInt(dur).array();
                                    out.write(bytes);
                                    out.flush();
                                    //Log.i(TAG, "Sent rumble message");
                                } catch (Throwable ignored) {

                                }
                            }
                        }
                        else
                            rumble = false;

                        int avail = in.available();
                        if (avail > 0)
                        {
                            //Log.i(TAG, "Received response");
                            int n = in.read(buf);
                            if (n <= 0) break;

                            StringBuilder batch = new StringBuilder(256);

                            boolean keyPressed = false;

                            for (int i = 0; i < 16; i++) {
                                boolean newState = (buf[i] == 1);
                                boolean oldState = gamepad.buttons[i];

                                prevGamepad.buttons[i] = oldState;
                                gamepad.buttons[i] = newState;

                                if (newState != oldState) {
                                    int linuxKey = gamepad.scancode[i]; // must be Linux input keycode
                                    int value = newState ? 1 : 0;

                                    Log.i(TAG, "Button received: " + Integer.toString(i));

                                    switch (gamepad.eventcode[i]) {
                                        case 1:
                                            break;

                                        case 2:
                                            batch.append("sendevent /dev/input/event2 4 4 ").append(gamepad.scancode[i] == gamepad.BUTTON_SELECT ? 58 : 59).append(' ').append('\n');
                                            batch.append("sendevent /dev/input/event2 1 ").append(linuxKey).append(' ').append(value).append('\n');
                                            batch.append("sendevent /dev/input/event2 0 0 0 \n");
                                            break;

                                        case 5:
                                            batch.append("sendevent /dev/input/event5 1 ").append(linuxKey).append(' ').append(value).append('\n');
                                            batch.append("sendevent /dev/input/event5 0 0 0\n");
                                            break;
                                    }
                                    keyPressed = true;
                                }
                            }

                            //Received touch input 1
                            if(buf[16]==1)
                            {
                                int touch_x = ((buf[18]&0xFF)<<8)|(buf[17]&0xFF);
                                touch_x = (int)(((float)touch_x/1919.0)*966.0);
                                int touch_y = ((buf[20]&0xFF)<<8)|(buf[19]&0xFF);
                                touch_y = (int)(((float)touch_y/941.0)*360.0);
                                Log.i(TAG, "Received touch: "+Integer.toString(touch_x)+","+Integer.toString(touch_y));

                                if(!touch_down)
                                {
                                    batch.append(InjectTouchDown(touch_x, touch_y));
                                    touch_down = true;
                                }
                                else
                                {
                                    batch.append(InjectTouchMove(touch_x, touch_y));
                                }
                            }
                            else if(touch_down)
                            {
                                touch_down = false;
                                batch.append(InjectTouchUp());
                            }

                            if (batch.length() > 0) {
                                os.writeBytes(batch.toString());
                                os.flush();
                                //Log.i(TAG, "Batch submitted");
                            }
                        }
                    }
                    catch(IOException e)
                    {
                        e.printStackTrace();
                        break;
                    }

                    Thread.sleep(2);
                }
                br.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            closeQuietly(socket);
            stopSelf();
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

    private String InjectTouchDown(int touch_x, int touch_y)
    {
        StringBuilder batch = new StringBuilder(256);
        batch.append("sendevent /dev/input/event4 2 3 57 0\n");
        batch.append("sendevent /dev/input/event2 3 53 ").append(touch_x).append("\n");
        batch.append("sendevent /dev/input/event2 3 54 ").append(touch_y).append("\n");
        batch.append("sendevent /dev/input/event2 3 58 80\n");
        batch.append("sendevent /dev/input/event2 0 2 0\n");
        batch.append("sendevent /dev/input/event2 0 0 0\n");
        return batch.toString();
    }

    private String InjectTouchMove(int touch_x, int touch_y)
    {
        StringBuilder batch = new StringBuilder(256);
        batch.append("sendevent /dev/input/event2 3 53 ").append(touch_x).append("\n");
        batch.append("sendevent /dev/input/event2 3 54 ").append(touch_y).append("\n");
        batch.append("sendevent /dev/input/event2 0 2 0\n");
        batch.append("sendevent /dev/input/event2 0 0 0\n");
        return batch.toString();
    }
    private String InjectTouchUp()
    {
        StringBuilder batch = new StringBuilder(256);
        batch.append("sendevent /dev/input/event4 2 3 57 -1\n");
        batch.append("sendevent /dev/input/event2 0 2 0\n");
        batch.append("sendevent /dev/input/event2 0 0 0\n");
        return batch.toString();
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
