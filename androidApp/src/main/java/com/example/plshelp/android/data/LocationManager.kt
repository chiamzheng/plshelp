package com.example.plshelp.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

object LocationManager {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient

    // Function to check and get user location
    @SuppressLint("MissingPermission")
    fun checkUserLocation(context: Context, updateText: (String) -> Unit) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // Check if the foreground permission (ACCESS_FINE_LOCATION) is granted
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateText("⚠️ Fine location permission required!")
            return
        }

        // Check if the background permission (ACCESS_BACKGROUND_LOCATION) is granted
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateText("⚠️ Background location permission required!")
            return
        }

        // Both permissions granted, get the location
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val isInside = isWithinRadius(location.latitude, location.longitude)
                    updateText(
                        if (isInside)
                            "✅ You are inside the area!\n${location.latitude}, ${location.longitude}"
                        else
                            "❌ You are outside the area.\n${location.latitude}, ${location.longitude}"
                    )
                } else {
                    updateText("⚠️ Location unavailable")
                }
            }
            .addOnFailureListener {
                updateText("❌ Failed to get location")
            }
    }

    // Check if user is within a specific radius
    private fun isWithinRadius(lat: Double, lon: Double): Boolean {
        val targetLat = 1.348310  // Example latitude (NTU)
        val targetLon = 103.683135 // Example longitude
        val radius = 4000.0        // Radius in meters (4 km)

        val results = FloatArray(1)
        Location.distanceBetween(lat, lon, targetLat, targetLon, results)
        return results[0] <= radius
    }

    // Setup geofence
    @SuppressLint("MissingPermission")
    fun setupGeofence(context: Context, lat: Double, lon: Double, radius: Float) {
        if (!::geofencingClient.isInitialized) {
            geofencingClient = LocationServices.getGeofencingClient(context)
        }

        val geofence = Geofence.Builder()
            .setRequestId("MY_GEOFENCE")
            .setCircularRegion(lat, lon, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        Log.d("Geofence", "Geofence created with ID: ${geofence.requestId} at ($lat, $lon) with radius: $radius")

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val pendingIntent = getGeofencePendingIntent(context)

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                Log.d("Geofence", "Geofence added successfully!")
            }
            .addOnFailureListener { e ->
                Log.e("Geofence", "Failed to add geofence: ${e.message}")
            }
    }

    // Create a PendingIntent for geofence transitions
    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, GeofenceReceiver::class.java)
        // You could log the Intent to ensure it's pointing to the correct receiver
        Log.d("Geofence", "PendingIntent created for GeofenceReceiver.")
        return PendingIntent.getBroadcast(
            context.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }


}
