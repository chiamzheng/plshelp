package com.example.plshelp.android.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import android.content.pm.PackageManager
object LocationManager {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    var targetLat: Double = 1.348310
    var targetLon: Double = 103.683135
    var geofenceRadius: Double = 2500.0

    @SuppressLint("MissingPermission")
    fun checkUserLocation(context: Context, updateText: (String) -> Unit) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateText("⚠️ Fine location permission required!")
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdates(Int.MAX_VALUE)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    val isInside = isWithinRadius(location.latitude, location.longitude)
                    updateText(
                        if (isInside) {
                            "✅ You are inside the area!\n${location.latitude}, ${location.longitude}"
                        } else {
                            "❌ You are outside the area.\n${location.latitude}, ${location.longitude}"
                        }
                    )
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (securityException: SecurityException) {
            updateText("❌ Failed to get location: Permission Denied")
        }
    }

    private fun isWithinRadius(lat: Double, lon: Double): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lon, targetLat, targetLon, results)
        return results[0] <= geofenceRadius
    }

    fun setLocationArea(lat: Double, lon: Double, radius: Float) {
        targetLat = lat
        targetLon = lon
        geofenceRadius = radius.toDouble()
        Log.d("LocationManager", "Area set to ($lat, $lon) with radius: $radius")
    }

    fun removeLocationUpdates() {
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}