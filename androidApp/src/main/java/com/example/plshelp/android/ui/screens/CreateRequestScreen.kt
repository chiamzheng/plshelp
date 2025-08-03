package com.example.plshelp.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.selection.selectable
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.lang.Exception
import com.example.plshelp.android.ui.components.CategoryChip
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

// Firebase Storage imports
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID


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

// Enum for Price Option - Make sure this is accessible by your Composable
enum class PriceOption {
    Money, Free, Other
}

// --- Character Limit Constants ---
const val MAX_TITLE_CHARS = 30
const val MAX_PRICE_CHARS = 10 // Applies to both Money value string and Other reward string


@Composable
fun CreateRequestScreen(onNavigateToListings: () -> Unit) {
    // --- STATE VARIABLES ---
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
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

    // --- IMAGE URI STATE ---
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Launcher for selecting an image from the gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri?.toString()
    }

    // --- LOCATION SELECTION STATES ---
    var locationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null))
    }
    var deliveryLocationSelection by rememberSaveable(stateSaver = locationSelectionSaver) {
        mutableStateOf(LocationSelection(LocationType.NO_LOCATION, null))
    }
    // Category selection state
    var categorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    val isDeliverySelected = remember {
        derivedStateOf { categorySelection.selectedCategories.contains("delivery") }
    }
    // --- END LOCATION SELECTION STATES ---

    // --- PRICE RELATED STATES ---
    var selectedPriceOption by rememberSaveable { mutableStateOf(PriceOption.Money) }
    var moneyPriceValue by rememberSaveable { mutableStateOf("") } // For numerical price input
    var otherRewardValue by rememberSaveable { mutableStateOf("") } // For 'Other' text input
    // --- END PRICE RELATED STATES ---


    // Track which fields have errors.
    var titleError by rememberSaveable { mutableStateOf(false) }
    var descriptionError by rememberSaveable { mutableStateOf(false) }
    // Renamed from priceError to priceInputError for clarity with new price options
    var priceInputError by rememberSaveable { mutableStateOf(false) } // General error for price/reward field
    var radiusError by rememberSaveable { mutableStateOf(false) }
    var locationError by rememberSaveable { mutableStateOf(false) }
    var deliveryLocationError by rememberSaveable { mutableStateOf(false) }

    val firestore = FirebaseFirestore.getInstance()

    // Predefined categories.
    val availableCategories = remember {
        listOf("Urgent", "Helper", "Delivery", "Others", "Invite", "Trade", "Advice", "Event", "Study", "Borrow", "Food", "Lost & Found")
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
        // Reset general error message at the start of a new submission attempt
        errorMessage = null

        // --- VALIDATION ---
        var allInputsValid = true // Flag to track overall validity of all inputs

        // Title validation
        titleError = title.isBlank() || title.length > MAX_TITLE_CHARS
        if (titleError) allInputsValid = false

        // Description validation
        descriptionError = description.isBlank()
        if (descriptionError) allInputsValid = false

        // Price validation logic
        var localPriceError = false // Used for the price field's individual error state
        val finalPrice: String // This will hold the string to be saved to Firestore

        when (selectedPriceOption) {
            PriceOption.Free -> {
                finalPrice = "Free"
                // No validation errors for "Free"
            }
            PriceOption.Money -> {
                if (moneyPriceValue.isBlank()) {
                    finalPrice = "0.00" // Store "0.00" if no money entered, but "Money" option selected
                    // Not considered an error, just an unspecified price
                } else if (moneyPriceValue.length > MAX_PRICE_CHARS) {
                    localPriceError = true
                    allInputsValid = false
                    finalPrice = "" // Initialize even in error case
                } else if (moneyPriceValue.toFloatOrNull() == null) {
                    localPriceError = true
                    allInputsValid = false
                    finalPrice = "" // Initialize even in error case
                } else {
                    finalPrice = moneyPriceValue // Valid number string
                }
            }
            PriceOption.Other -> {
                if (otherRewardValue.isBlank()) {
                    localPriceError = true
                    allInputsValid = false
                    finalPrice = "" // Initialize even in error case
                } else if (otherRewardValue.length > MAX_PRICE_CHARS) {
                    localPriceError = true
                    allInputsValid = false
                    finalPrice = "" // Initialize even in error case
                } else {
                    finalPrice = otherRewardValue // Custom reward text
                }
            }
        }
        priceInputError = localPriceError // Update state for UI feedback on the price/reward field


        // Radius validation
        val parsedRadius = radius.toLongOrNull()
        radiusError = radius.isBlank() || parsedRadius == null || parsedRadius <= 0
        if (radiusError) allInputsValid = false

        // Category validation
        showCategoryError = categorySelection.selectedCategories.isEmpty()
        if (showCategoryError) allInputsValid = false

        // Location validation (primary)
        locationError = locationSelection.coordinates == null
        if (locationError) allInputsValid = false

        // Delivery Location validation (conditional)
        deliveryLocationError = if (isDeliverySelected.value) deliveryLocationSelection.coordinates == null else false
        if (deliveryLocationError) allInputsValid = false

        // User ID check (Crucial for ownerID)
        if (currentUserId.isEmpty()) {
            allInputsValid = false
            errorMessage = "User not logged in. Please log in to create a request." // Specific error for login
        }

        // --- FINAL SUBMISSION CHECK ---
        if (allInputsValid) {
            isCreating = true // Indicate that creation is in progress
            scope.launch {
                try {
                    // --- Image Upload Logic ---
                    val imageUrl = if (imageUri != null) {
                        try {
                            uploadImageToFirebase(imageUri!!, currentUserId)
                        } catch (e: Exception) {
                            errorMessage = "Failed to upload image: ${e.localizedMessage}"
                            Log.e("CreateRequestScreen", "Image upload failed", e)
                            isCreating = false
                            return@launch // Still keep return here, as image upload is critical pre-requisite
                        }
                    } else {
                        null
                    }
                    // --- End Image Upload Logic ---

                    val primaryCoordinates = locationSelection.coordinates
                    val deliveryCoordinates = if (isDeliverySelected.value) deliveryLocationSelection.coordinates else null

                    // Final check for coordinates just before Firestore operation
                    if (primaryCoordinates != null && (!isDeliverySelected.value || deliveryCoordinates != null)) {
                        val newListing = hashMapOf(
                            "title" to title,
                            "description" to description,
                            "price" to finalPrice, // Use the determined finalPrice
                            "category" to categorySelection.selectedCategories.toList(),
                            "coord" to primaryCoordinates,
                            "deliveryCoord" to deliveryCoordinates,
                            "radius" to parsedRadius!!, // parsedRadius is guaranteed non-null if allInputsValid is true
                            "ownerID" to currentUserId,
                            "ownerName" to currentUserName,
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "status" to "active",
                            "imageUrl" to imageUrl // Add the uploaded image URL here
                        )

                        firestore.collection("listings").add(newListing).await()

                        // Reset states after successful creation
                        title = ""
                        description = ""
                        moneyPriceValue = ""
                        otherRewardValue = ""
                        selectedPriceOption = PriceOption.Money
                        categorySelection = CategorySelection(mutableSetOf())
                        radius = ""
                        locationSelection = LocationSelection(LocationType.NO_LOCATION, null)
                        deliveryLocationSelection = LocationSelection(LocationType.NO_LOCATION, null)
                        imageUri = null
                        requestCreated = true // Set success flag
                        errorMessage = null // Clear any general error message
                        // Reset all individual error flags
                        titleError = false
                        descriptionError = false
                        priceInputError = false
                        radiusError = false
                        locationError = false
                        deliveryLocationError = false
                        showCategoryError = false

                    } else {
                        // This case should ideally not be hit if allInputsValid was true,
                        // but good for safety.
                        Log.e("CreateRequestScreen", "Missing coordinates after validation!")
                        errorMessage = "Internal error: Missing coordinates despite validation."
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to create request: ${e.localizedMessage}"
                    Log.e("CreateRequestScreen", "Error creating request: ${e.localizedMessage}", e)
                } finally {
                    isCreating = false // End creation progress
                }
            }
        } else {
            // If allInputsValid is false and a specific errorMessage wasn't already set
            // (e.g., by the user ID check), set a general error message.
            if (errorMessage == null) {
                errorMessage = "Please correct the highlighted errors."
            }
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
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Create New Request", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // --- TITLE INPUT ---
        OutlinedTextField(
            value = title,
            onValueChange = { newValue ->
                title = newValue // Allow typing beyond limit for visual feedback
            },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            isError = titleError || title.length > MAX_TITLE_CHARS, // Combined error state
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (titleError || title.length > MAX_TITLE_CHARS) Color.Red else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (titleError || title.length > MAX_TITLE_CHARS) Color.Red else MaterialTheme.colorScheme.onBackground,
                errorIndicatorColor = Color.Red
            ),
            singleLine = true // Often good for titles
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (titleError && !title.isBlank()) { // Only show "required" if blank, otherwise specific length error
                Text("Title is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else if (title.length > MAX_TITLE_CHARS) {
                Text(
                    "Title exceeds ${MAX_TITLE_CHARS} character limit",
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = "${title.length}/${MAX_TITLE_CHARS}",
                style = MaterialTheme.typography.bodySmall,
                color = if (title.length > MAX_TITLE_CHARS) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp)) // Add spacing after title field and its info


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
            ),
            minLines = 3 // Allow multiple lines for description
        )
        if (descriptionError) {
            Text("Description is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp)) // Add spacing after description

        // --- PRICE/REWARD INPUT SECTION ---
        // Price Option Selection
        Text("Reward", style = MaterialTheme.typography.titleMedium)
        // Price Option Selection - A segmented control pill
        val pillCornerRadius = 8.dp
        val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        val selectedBackgroundColor = MaterialTheme.colorScheme.primary
        val unselectedBackgroundColor = MaterialTheme.colorScheme.surface

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(pillCornerRadius))
                .border(1.dp, borderColor, RoundedCornerShape(pillCornerRadius)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriceOption.entries.forEachIndexed { index, option ->
                val isSelected = (option == selectedPriceOption)
                val backgroundColor = if (isSelected) selectedBackgroundColor else unselectedBackgroundColor
                val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                // We use a Box as a segment
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(backgroundColor)
                        .clickable {
                            selectedPriceOption = option
                            priceInputError = false // Clear general price error when switching
                            if (option != PriceOption.Money) moneyPriceValue = ""
                            if (option != PriceOption.Other) otherRewardValue = ""
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = option.name, color = textColor)
                }

                // Add a separator between segments, but not after the last one
                if (index < PriceOption.entries.lastIndex) {
                    Divider(
                        color = borderColor,
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(0.dp))

        // Conditional Price Input Field
        when (selectedPriceOption) {
            PriceOption.Money -> {
                OutlinedTextField(
                    value = moneyPriceValue,
                    onValueChange = { newValue ->
                        // Only allow numbers (and optionally a single decimal point)
                        if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
                            moneyPriceValue = newValue // Allow typing beyond limit for visual feedback
                        }
                    },
                    label = { Text("Price ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = priceInputError || moneyPriceValue.length > MAX_PRICE_CHARS, // Combined error state
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = if (priceInputError || moneyPriceValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = if (priceInputError || moneyPriceValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.onBackground,
                        errorIndicatorColor = Color.Red
                    ),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (priceInputError && moneyPriceValue.isNotBlank()) { // Show specific error if input exists
                        Text(
                            "Invalid number format or length.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (moneyPriceValue.length > MAX_PRICE_CHARS) {
                        Text(
                            "Exceeds ${MAX_PRICE_CHARS} character limit",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${moneyPriceValue.length}/${MAX_PRICE_CHARS}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (moneyPriceValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PriceOption.Other -> {
                OutlinedTextField(
                    value = otherRewardValue,
                    onValueChange = { newValue ->
                        otherRewardValue = newValue // Allow typing beyond limit for visual feedback
                    },
                    label = { Text("Describe Reward") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = priceInputError || otherRewardValue.length > MAX_PRICE_CHARS, // Combined error state
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = if (priceInputError || otherRewardValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = if (priceInputError || otherRewardValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.onBackground,
                        errorIndicatorColor = Color.Red
                    ),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (priceInputError && otherRewardValue.isBlank()) { // Show required if blank
                        Text(
                            "Reward description is required",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (otherRewardValue.length > MAX_PRICE_CHARS) {
                        Text(
                            "Exceeds ${MAX_PRICE_CHARS} character limit",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "${otherRewardValue.length}/${MAX_PRICE_CHARS}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (otherRewardValue.length > MAX_PRICE_CHARS) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PriceOption.Free -> {
                // No input field needed, display a confirmation text
                Text(
                    text = "This listing will be marked as 'Free'.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp)) // Add spacing after price/reward section
        // --- END PRICE/REWARD INPUT SECTION ---


        // --- IMAGE SELECTION UI ---
        Spacer(modifier = Modifier.height(16.dp))
        Text("Image Upload (Optional)", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { imagePickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select an Image")
        }
        if (imageUri != null) {
            Text(
                "Image Selected: ${imageUri?.substringAfterLast("/")}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(16.dp)) // Spacing after image selection
        // --- END IMAGE SELECTION UI ---

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

// Add this function outside of the CreateRequestScreen composable
suspend fun uploadImageToFirebase(imageUri: String, userId: String): String {
    val storage = FirebaseStorage.getInstance()
    val storageRef = storage.reference
    val fileName = UUID.randomUUID().toString() + ".jpg"
    val imageRef = storageRef.child("images/$userId/$fileName")

    val uploadTask = imageRef.putFile(Uri.parse(imageUri)).await()

    return imageRef.downloadUrl.await().toString()
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


// --- Helper function for getting current location ---
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

// --- State Savers ---
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