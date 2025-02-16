package com.example.plshelp.android.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Safely get the geofencing event from the intent
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e("GeofenceReceiver", "GeofencingEvent is null!")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            Log.e("GeofenceReceiver", "Geofencing error occurred. Error code: $errorCode")
            return
        }

        // Now that we've ensured geofencingEvent is non-null and doesn't have an error, handle the transition
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Check for the geofence transition types
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("GeofenceReceiver", "Entered the geofence area!")
                // Handle the enter event (update UI or take any actions)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("GeofenceReceiver", "Exited the geofence area!")
                // Handle the exit event (update UI or take any actions)
            }
            else -> {
                Log.e("GeofenceReceiver", "Unknown geofence transition")
            }
        }
    }
}

