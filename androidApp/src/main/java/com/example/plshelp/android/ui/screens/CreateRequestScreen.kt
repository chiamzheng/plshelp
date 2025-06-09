package com.example.plshelp.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.* // This import is crucial for remember, mutableStateOf, derivedStateOf
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable // This import is crucial for rememberSaveable
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import com.example.plshelp.android.ui.components.CategoryChip // Ensure this import is correct
import com.example.plshelp.android.LocalUserId
import com.example.plshelp.android.LocalUserName

// Mapbox Imports
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.geojson.Point
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn

// Mapbox Compose specific imports for icon handling
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import androidx.compose.ui.graphics.vector.rememberVectorPainter

// Needed for pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

// Import for ActivityResultLauncher for the LocationSelectionBlock
import androidx.activity.result.ActivityResultLauncher


// Data class for Location Selection
data class LocationSelection(
    val type: LocationType,
    val coordinates: GeoPoint? = null
)

enum class LocationType {
    CURRENT_LOCATION,
    SELECTED_ON_MAP,
    NO_LOCATION
}

// Data class for Category Selection
data class CategorySelection(
    val selectedCategories: MutableSet<String>
)

@Composable
fun CreateRequestScreen(onNavigateToListings: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var subtitle by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- CONSUME GLOBAL VARIABLES HERE ---
    val currentUserId = LocalUserId.current // Get the global UID
    val currentUserName = LocalUserName.current.value // Get the global UserName
    // --- END GLOBAL VARIABLES ---

    var isCreating by rememberSaveable { mutableStateOf(false) }
    var requestCreated by rememberSaveable { mutableStateOf(false) }
    var showCategoryError by rememberSaveable { mutableStateOf(false) }

    // --- LOCATION SELECTION STATES ---
    var locationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null))
    }
    var deliveryLocationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null))
    }
    // Category selection state (THIS IS THE ONE YOU WERE ASKING ABOUT)
    var categorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    val isDeliverySelected = remember {
        derivedStateOf { categorySelection.selectedCategories.contains("delivery") }
    }
    // --- END LOCATION SELECTION STATES ---


    // Track which fields have errors.
    var titleError by rememberSaveable { mutableStateOf(false) }
    var subtitleError by rememberSaveable { mutableStateOf(false) }
    var descriptionError by rememberSaveable { mutableStateOf(false) }
    var priceError by rememberSaveable { mutableStateOf(false) }
    var radiusError by rememberSaveable { mutableStateOf(false) }
    var locationError by rememberSaveable { mutableStateOf(false) }
    var deliveryLocationError by rememberSaveable { mutableStateOf(false) }

    val firestore = FirebaseFirestore.getInstance()

    // Predefined categories.
    val availableCategories = remember {
        listOf("Urgent", "Helper", "Delivery", "Free", "Others", "Invite", "Trade", "Advice", "Event", "Study", "Borrow", "Food")
    }

    // Location Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("CreateRequestScreen", "Location permission granted after request.")
        } else {
            errorMessage = "Location permission denied. Please select locations manually on the map if needed."
        }
    }

    val createListing: () -> Unit = {
        titleError = title.isBlank()
        subtitleError = subtitle.isBlank()
        descriptionError = description.isBlank()
        priceError = price.isBlank()
        radiusError = radius.isBlank()
        showCategoryError = categorySelection.selectedCategories.isEmpty()

        val parsedRadius = radius.toLongOrNull()
        radiusError = radius.isBlank() || parsedRadius == null || parsedRadius <= 0

        locationError = locationSelection.coordinates == null

        if (isDeliverySelected.value) {
            deliveryLocationError = deliveryLocationSelection.coordinates == null
        } else {
            deliveryLocationError = false
        }

        if (!titleError && !subtitleError && !descriptionError && !priceError && !showCategoryError && !radiusError && !locationError && !deliveryLocationError && currentUserId.isNotEmpty()) {
            isCreating = true
            scope.launch {
                try {
                    val primaryCoordinates = locationSelection.coordinates
                    val deliveryCoordinates = if (isDeliverySelected.value) deliveryLocationSelection.coordinates else null

                    if (primaryCoordinates != null && (!isDeliverySelected.value || deliveryCoordinates != null)) {
                        val newListing = hashMapOf(
                            "title" to title,
                            "subtitle" to subtitle,
                            "description" to description,
                            "price" to price,
                            "category" to categorySelection.selectedCategories.toList(),
                            "coord" to primaryCoordinates,
                            "deliveryCoord" to deliveryCoordinates,
                            "radius" to parsedRadius!!,
                            "ownerID" to currentUserId,
                            "ownerName" to currentUserName,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "status" to "active"
                        )

                        firestore.collection("listings").add(newListing).await()

                        title = ""
                        subtitle = ""
                        description = ""
                        price = ""
                        categorySelection = CategorySelection(mutableSetOf())
                        radius = ""
                        locationSelection = LocationSelection(LocationType.NO_LOCATION, null)
                        deliveryLocationSelection = LocationSelection(LocationType.NO_LOCATION, null)
                        requestCreated = true
                        showCategoryError = false
                        errorMessage = null
                        titleError = false
                        subtitleError = false
                        descriptionError = false
                        priceError = false
                        radiusError = false
                        locationError = false
                        deliveryLocationError = false
                    } else {
                        Log.e("CreateRequestScreen", "Missing coordinates after validation!")
                        errorMessage = "Please ensure all required locations are selected."
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to create request: ${e.localizedMessage}"
                    Log.e("CreateRequestScreen", "Error creating request: ${e.localizedMessage}", e)
                } finally {
                    isCreating = false
                }
            }
        } else if (currentUserId.isEmpty()) {
            errorMessage = "User not logged in. Please log in to create a request."
        } else {
            errorMessage = "Please fill in all required fields and select categories/locations."
        }
    }

    var parentScrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState, enabled = parentScrollEnabled),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Create New Request", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                titleError = false
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
            value = subtitle,
            onValueChange = {
                subtitle = it
                subtitleError = false
            },
            label = { Text("Brief Summary") },
            modifier = Modifier.fillMaxWidth(),
            isError = subtitleError,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (subtitleError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (subtitleError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (subtitleError) {
            Text("Subtitle is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (priceError) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            )
        )
        if (priceError) {
            Text("Price is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text("Category", style = MaterialTheme.typography.titleMedium)
        if (showCategoryError) {
            Text("Category is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            availableCategories.chunked(4).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowCategories.forEach { category ->
                        val isSelected = categorySelection.selectedCategories.contains(category.lowercase())

                        CategoryChip(
                            categoryString = category,
                            isSelected = isSelected,
                            onCategoryClick = { clickedCategory ->
                                val lowercasedCategory = clickedCategory.lowercase()
                                val newSelection = categorySelection.selectedCategories.toMutableSet()
                                if (newSelection.contains(lowercasedCategory)) {
                                    newSelection.remove(lowercasedCategory)
                                } else {
                                    newSelection.add(lowercasedCategory)
                                }
                                categorySelection = categorySelection.copy(selectedCategories = newSelection)
                            }
                        )
                    }
                    if (rowCategories.size < 4) {
                        for (i in rowCategories.size until 4) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LocationSelectionBlock(
            title = if (isDeliverySelected.value) "Pickup Location" else "Location",
            locationSelection = locationSelection,
            onLocationSelected = { newLocation ->
                locationSelection = newLocation
                locationError = false
            },
            locationError = locationError,
            parentScrollEnabled = parentScrollEnabled,
            onParentScrollEnabledChanged = { parentScrollEnabled = it },
            locationPermissionLauncher = locationPermissionLauncher
        )

        if (isDeliverySelected.value) {
            Spacer(modifier = Modifier.height(16.dp))
            LocationSelectionBlock(
                title = "Delivery Location",
                locationSelection = deliveryLocationSelection,
                onLocationSelected = { newLocation ->
                    deliveryLocationSelection = newLocation
                    deliveryLocationError = false
                },
                locationError = deliveryLocationError,
                parentScrollEnabled = parentScrollEnabled,
                onParentScrollEnabledChanged = { parentScrollEnabled = it },
                locationPermissionLauncher = locationPermissionLauncher
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
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
        if (radiusError) {
            Text("Radius is required and must be a positive number", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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

        if (requestCreated) {
            Text(
                text = "Request created successfully!",
                color = Color.Green,
                style = MaterialTheme.typography.bodyMedium
            )
            LaunchedEffect(requestCreated) {
                if (requestCreated) {
                    kotlinx.coroutines.delay(3000)
                    requestCreated = false
                }
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun LocationSelectionBlock(
    title: String,
    locationSelection: LocationSelection,
    onLocationSelected: (LocationSelection) -> Unit,
    locationError: Boolean,
    parentScrollEnabled: Boolean,
    onParentScrollEnabledChanged: (Boolean) -> Unit,
    locationPermissionLauncher: ActivityResultLauncher<String>
) {
    val context = LocalContext.current

    val singaporePoint = Point.fromLngLat(103.8198, 1.3521)
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(singaporePoint)
            zoom(10.0)
        }
    }

    LaunchedEffect(locationSelection.coordinates) {
        locationSelection.coordinates?.let { geoPoint ->
            val point = Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(14.0)
                    .build()
            )
        }
    }

    val customLocationPainter = rememberVectorPainter(
        image = Icons.Default.LocationOn,
    )
    val markerId = rememberIconImage(key = "${title.replace(" ", "_")}_marker_icon", painter = customLocationPainter)


    Text(title, style = MaterialTheme.typography.titleMedium)
    if (locationError) {
        Text("Location is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(
        onClick = {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                getCurrentLocation(context) { lat, lon ->
                    onLocationSelected(LocationSelection(LocationType.CURRENT_LOCATION, GeoPoint(lat, lon)))
                }
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Use Current Location")
    }

    Spacer(modifier = Modifier.height(8.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        MapboxMap(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        onParentScrollEnabledChanged(false)

                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        onParentScrollEnabledChanged(true)
                    }
                },
            mapViewportState = mapViewportState,
            onMapClickListener = { point ->
                onLocationSelected(
                    LocationSelection(
                        LocationType.SELECTED_ON_MAP,
                        GeoPoint(point.latitude(), point.longitude())
                    )
                )
                true
            }
        ) {
            locationSelection.coordinates?.let { geoPoint ->
                PointAnnotation(point = Point.fromLngLat(geoPoint.longitude, geoPoint.latitude)) {
                    iconImage = markerId
                }
            }
        }
    }

    if (locationSelection.coordinates != null) {
        Text(
            text = "Selected Location: ${
                when (locationSelection.type) {
                    LocationType.CURRENT_LOCATION -> "Current - Lat: %.4f, Lon: %.4f".format(
                        locationSelection.coordinates.latitude, locationSelection.coordinates.longitude)
                    LocationType.SELECTED_ON_MAP -> "Map - Lat: %.4f, Lon: %.4f".format(
                        locationSelection.coordinates.latitude, locationSelection.coordinates.longitude)
                    LocationType.NO_LOCATION -> "No location selected"
                }
            }",
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        Text(
            text = "No location selected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
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
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLocation: android.location.Location? ->
                        if (freshLocation != null) {
                            onLocationResult(freshLocation.latitude, freshLocation.longitude)
                            Log.d("LocationUpdate", "Fetched Current Location: ${freshLocation.latitude}, ${freshLocation.longitude}")
                        } else {
                            Log.e("LocationUpdate", "Could not get current location (fresh request).")
                            onLocationResult(1.3521, 103.8198) // Default to Singapore if all fails
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        Log.e("LocationUpdate", "Error getting current location: ${e.localizedMessage}")
                        onLocationResult(1.3521, 103.8198) // Default to Singapore
                    }
            }
        }.addOnFailureListener { e: Exception ->
            Log.e("LocationUpdate", "Error getting last location: ${e.localizedMessage}")
            onLocationResult(1.3521, 103.8198) // Default to Singapore
        }
    } else {
        Log.e("LocationUpdate", "Location permission not granted (getCurrentLocation function).")
        onLocationResult(1.3521, 103.8198) // Default to Singapore
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
                typeKey to locationSelection.type.name,
                latitudeKey to locationSelection.coordinates?.latitude,
                longitudeKey to locationSelection.coordinates?.longitude
            )
        },
        restore = { saved ->
            val type = LocationType.valueOf(saved[typeKey] as String)
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