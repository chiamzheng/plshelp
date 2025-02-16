package com.example.plshelp.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import com.example.plshelp.android.data.LocationManager
import com.example.plshelp.android.ui.screens.LocationScreen
import com.example.plshelp.android.ui.screens.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Register for the fine location permission request
    private val fineLocationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                // If fine location is granted, check for background permission
                checkBackgroundLocationPermission()
            } else {
                // Fine location permission not granted
                Toast.makeText(this, "Fine location permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    // Register for the background location permission request
    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                // Both permissions granted, set up geofence
                setupGeofence()
            } else {
                // Background location permission not granted
                Toast.makeText(this, "Background location permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                LocationScreen { updateText ->
                    // Check user location; this will trigger permission requests if needed
                    LocationManager.checkUserLocation(this) { result ->
                        updateText(result)
                    }
                }
            }
        }

        // Check and request foreground location permission when the app starts
        checkForegroundLocationPermission()
    }

    // Check if foreground location permission is granted
    private fun checkForegroundLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Fine location permission granted, now check background permission
            checkBackgroundLocationPermission()
        } else {
            // Request fine location permission
            fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Check if background location permission is granted
    private fun checkBackgroundLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setupGeofence()
        } else {
            // Request background location permission
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }


    // Setup geofence if both permissions are granted
    private fun setupGeofence() {
        LocationManager.setupGeofence(
            this, 1.348310, 103.683135, 5000f // Example coordinates and radius
        )
    }
}
