package com.example.injectionapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start the background service
        startService(new Intent(this, TouchInjectService.class));

        // Optional: close the UI immediately
        finish();
    }
}
