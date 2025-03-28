package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.plshelp.android.data.LocationManager

@Composable
fun LocationScreen(
    onCheckLocation: (updateText: (String) -> Unit) -> Unit,
    paddingValues: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var displayText by remember { mutableStateOf("Tap anywhere to get location") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("") }
    val context = LocalContext.current

    var geofenceLat by remember { mutableStateOf<Double?>(null) }
    var geofenceLon by remember { mutableStateOf<Double?>(null) }
    var geofenceRadius by remember { mutableStateOf<Float?>(null) }

    val updateLocation = {
        LocationManager.checkUserLocation(context) { result ->
            displayText = result
        }
    }

    LaunchedEffect(Unit) {
        LocationManager.setLocationArea(
            LocationManager.targetLat,
            LocationManager.targetLon,
            LocationManager.geofenceRadius.toFloat()
        )

        geofenceLat = LocationManager.targetLat
        geofenceLon = LocationManager.targetLon
        geofenceRadius = LocationManager.geofenceRadius.toFloat()

        updateLocation()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (geofenceLat != null && geofenceLon != null && geofenceRadius != null) {
            Text(text = "Area:")
            Text(text = "Lat: ${geofenceLat}, Lon: ${geofenceLon}, Radius: ${geofenceRadius}")
            Spacer(modifier = Modifier.height(8.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                modifier = Modifier.clickable {
                    displayText = "📍 Fetching location..."
                    onCheckLocation { result -> displayText = result }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it },
                label = { Text("Radius (meters)") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val lat = latitude.toDoubleOrNull() ?: 0.0
                    val lon = longitude.toDoubleOrNull() ?: 0.0
                    val rad = radius.toFloatOrNull() ?: 0f
                    LocationManager.setLocationArea(lat, lon, rad)

                    geofenceLat = lat
                    geofenceLon = lon
                    geofenceRadius = rad

                    updateLocation()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Area")
            }
        }
    }
}

@Preview
@Composable
fun LocationScreenPreview() {
    MyApplicationTheme {
        LocationScreen(onCheckLocation = {}, paddingValues = PaddingValues(0.dp))
    }
}