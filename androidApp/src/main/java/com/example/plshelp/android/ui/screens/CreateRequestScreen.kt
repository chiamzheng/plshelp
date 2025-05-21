package com.example.plshelp.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import com.example.plshelp.android.ui.components.CategoryChip // Import CategoryChip


// Data class for Location Selection
data class LocationSelection(
    val type: LocationType,
    val coordinates: GeoPoint? = null
)

enum class LocationType {
    CURRENT_LOCATION,
    SELECT_FROM_MAP,
    NO_LOCATION // Added for the initial state or when location is not selected
}

// Data class for Category Selection
data class CategorySelection(
    val selectedCategories: MutableSet<String>
)

@Composable
fun CreateRequestScreen(onNavigateToListings: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val scope = rememberCoroutineScope()
    val user = auth.currentUser!!
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var requestCreated by rememberSaveable { mutableStateOf(false) } // New state for success message
    var showCategoryError by rememberSaveable { mutableStateOf(false) }

    // Location Selection State
    var locationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null)) // Initialize with no location
    }

    // Category selection state
    var categorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    // Track which fields have errors.
    var titleError by rememberSaveable { mutableStateOf(false) }
    var descriptionError by rememberSaveable { mutableStateOf(false) }
    var priceError by rememberSaveable { mutableStateOf(false) }
    var radiusError by rememberSaveable { mutableStateOf(false) }
    var locationError by rememberSaveable { mutableStateOf(false) } // New error state for location

    val firestore = FirebaseFirestore.getInstance()

    // Predefined categories.
    val availableCategories = remember {
        listOf("Urgent", "Helper", "Delivery", "Free", "Others", "Invite", "Trade", "Advice", "Event", "Study", "Borrow", "Food")
    }
    val locationSelectionOptions = listOf("Current Location", "Select on Map") // Options for location

    // Location Permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocation(context) { lat, lon ->
                locationSelection = LocationSelection(LocationType.CURRENT_LOCATION, GeoPoint(lat, lon))
                locationError = false
            }
        } else {
            errorMessage = "Location permission denied."
            locationSelection = LocationSelection(LocationType.SELECT_FROM_MAP, GeoPoint(1.35, 103.9)) // Default to a fallback
        }
    }

    val getCurrentLocationOnClick = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation(context) { lat, lon ->
                locationSelection = LocationSelection(LocationType.CURRENT_LOCATION, GeoPoint(lat, lon))
                locationError = false
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val createListing: () -> Unit = {
        // Update error states based on current input
        titleError = title.isBlank()
        descriptionError = description.isBlank()
        priceError = price.isBlank()
        radiusError = radius.isBlank()
        locationError = locationSelection.type == LocationType.NO_LOCATION // Check if any location is selected
        showCategoryError = categorySelection.selectedCategories.isEmpty()

        val parsedRadius = radius.toLongOrNull()
        radiusError = radius.isBlank() || parsedRadius == null

        // Check if there are any errors before proceeding to write to the database
        if (!titleError && !descriptionError && !priceError && !showCategoryError && !radiusError && !locationError) {
            isCreating = true
            scope.launch {
                try {
                    val userDocument = firestore.collection("users").document(user.uid).get().await()
                    val userName = userDocument.getString("name") ?: "Anonymous"

                    // Obtain the current value of locationSelection.coordinates within the coroutine
                    val currentCoordinates = locationSelection.coordinates
                    if (currentCoordinates != null || locationSelection.type == LocationType.SELECT_FROM_MAP) { // Allow map selection without current coordinates
                        val newListing = hashMapOf(
                            "title" to title,
                            "description" to description,
                            "price" to price,
                            "category" to categorySelection.selectedCategories.toList(),
                            "coord" to (currentCoordinates ?: GeoPoint(1.35, 103.9)), // Use default if currentCoordinates is null
                            "radius" to parsedRadius!!,
                            "ownerID" to user.uid,
                            "ownerName" to userName,
                        )

                        firestore.collection("listings").add(newListing).await()

                        // Reset form and show success message
                        title = ""
                        description = ""
                        price = ""
                        categorySelection = CategorySelection(mutableSetOf()) // Reset category selection
                        radius = ""
                        locationSelection = LocationSelection(LocationType.NO_LOCATION, null) // Reset location
                        requestCreated = true // Set success state
                        showCategoryError = false
                        // Optionally, you can use a LaunchedEffect with a delay to reset the success message
                    } else {
                        Log.e("CreateRequestScreen", "Coordinates are null after validation!")
                        errorMessage = "Location is required."
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to create request: ${e.localizedMessage}"
                } finally {
                    isCreating = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Create New Request", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                titleError = false // Clear error on input
            },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            isError = titleError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (titleError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (titleError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (titleError) {
            Text("Title is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                descriptionError = false
            },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            isError = descriptionError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (descriptionError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (descriptionError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (descriptionError) {
            Text("Description is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = price,
            onValueChange = {
                price = it
                priceError = false
            },
            label = { Text("Price") },
            modifier = Modifier.fillMaxWidth(),
            isError = priceError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (priceError) {
            Text("Price is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Category Selection using CategoryChip in a multi-row layout with equal spacing
        Text("Category", style = MaterialTheme.typography.titleMedium)
        if (showCategoryError) {
            Text("Category is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            availableCategories.chunked(4).forEach { rowCategories -> // Display up to 4 chips per row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween, // Equal spacing
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowCategories.forEach { category ->
                        val isSelected = categorySelection.selectedCategories.contains(category.lowercase())

                        CategoryChip(
                            categoryString = category, // Use the capitalized category directly
                            isSelected = isSelected,
                            onCategoryClick = { clickedCategory ->
                                val lowercasedCategory = clickedCategory.lowercase()
                                val newSelection = categorySelection.selectedCategories.toMutableSet()
                                if (lowercasedCategory in newSelection) {
                                    newSelection.remove(lowercasedCategory)
                                } else {
                                    newSelection.add(lowercasedCategory)
                                }
                                categorySelection = categorySelection.copy(selectedCategories = newSelection)
                            }
                        )
                    }
                    // Fill remaining space with Spacer to maintain equal spacing
                    if (rowCategories.size < 4) {
                        for (i in rowCategories.size until 4) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp)) // Add vertical spacing between rows
            }
        }

        // Location Selection
        Text("Location", style = MaterialTheme.typography.titleMedium)
        if (locationError) {
            Text("Location is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = getCurrentLocationOnClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Current Location")
            }
            Button(
                onClick = {
                    // Navigate to a map screen if you implement one later
                    locationSelection =
                        LocationSelection(LocationType.SELECT_FROM_MAP, GeoPoint(1.29, 103.85)) //Marina Bay Sands
                    locationError = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Select on Map")
            }
        }

        // Show the selected location
        if (locationSelection.coordinates != null) {
            Text(
                text = "Selected Location: ${
                    when (locationSelection.type) {
                        LocationType.CURRENT_LOCATION -> "Lat: ${locationSelection.coordinates!!.latitude}, Lon: ${locationSelection.coordinates!!.longitude}"
                        LocationType.SELECT_FROM_MAP -> "Lat: ${locationSelection.coordinates!!.latitude}, Lon: ${locationSelection.coordinates!!.longitude}"
                        LocationType.NO_LOCATION -> "No location selected"
                    }
                }",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = radius,
            onValueChange = {
                radius = it
                radiusError = false
            },
            label = { Text("Radius (meters)") },
            modifier = Modifier.fillMaxWidth(),
            isError = radiusError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (radiusError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (radiusError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (radius.isBlank()) {
            Text("Radius is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = createListing, enabled = !isCreating) {
            if (isCreating) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Text("Create Request")
            }
        }

        // Display success message
        if (requestCreated) {
            Text(
                text = "Request created successfully!",
                color = Color.Green,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
fun getCurrentLocation(context: android.content.Context, onLocationResult: (Double, Double) -> Unit) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationResult(location.latitude, location.longitude)
                Log.d("LocationUpdate", "Fetched Last Location: ${location.latitude}, ${location.longitude}")
            } else {
                // Request a single fresh location if last location is null
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLocation: android.location.Location? ->
                        if (freshLocation != null) {
                            onLocationResult(freshLocation.latitude, freshLocation.longitude)
                            Log.d("LocationUpdate", "Fetched Current Location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                        } else {
                            Log.e("LocationUpdate", "Could not get current location.")
                            // Handle the case where location is still not available
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        Log.e("LocationUpdate", "Error getting current location: ${e.localizedMessage}")
                    }
            }
        }
    } else {
        Log.e("LocationUpdate", "Location permission not granted (getCurrentLocation function).")
        // Handle the case where permission is not granted
    }
}

// Saver for LocationSelection
val locationSelectionSaver = run {
    val typeKey = "type"
    val latitudeKey = "latitude"
    val longitudeKey = "longitude"
    mapSaver(
        save = { locationSelection ->
            mapOf(
                typeKey to locationSelection.type,
                latitudeKey to locationSelection.coordinates?.latitude,
                longitudeKey to locationSelection.coordinates?.longitude
            )
        },
        restore = { saved ->
            val type = saved[typeKey] as LocationType
            val latitude = saved[latitudeKey] as? Double
            val longitude = saved[longitudeKey] as? Double
            val coordinates = if (latitude != null && longitude != null) {
                GeoPoint(latitude, longitude)
            } else {
                null
            }
            LocationSelection(type, coordinates)
        }
    )
}

// Saver for CategorySelection
val categorySelectionSaver = run {
    val selectedCategoriesKey = "selectedCategories"
    mapSaver(
        save = { categorySelection ->
            mapOf(selectedCategoriesKey to categorySelection.selectedCategories.toList())
        },
        restore = { saved ->
            val savedList = saved[selectedCategoriesKey] as List<String>
            CategorySelection(savedList.toMutableSet())
        }
    )
}