package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import Listing
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.plshelp.android.ui.components.CategoryChip
import com.example.plshelp.android.data.ListingDetailViewModel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.material.icons.automirrored.filled.ArrowBack

import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.google.firebase.Timestamp
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor

import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.tasks.await
import kotlin.math.min
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.extension.compose.rememberMapState
import com.example.plshelp.android.LocalUserId
import com.google.firebase.firestore.FirebaseFirestore // Added for OffersModal

@Composable
fun ListingDetailScreen(
    listingId: String,
    onBackClick: () -> Unit,
    initialListing: Listing? = null,
    onNavigateToAcceptedRequests: (String, String?) -> Unit // This callback might become redundant if offers handled here
) {
    val viewModel: ListingDetailViewModel = viewModel(
        factory = ListingDetailViewModel.Factory(listingId, initialListing)
    )

    val listing = viewModel.listing
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage

    var parentScrollEnabled by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(103.8198, 1.3521)) // Default to Singapore
            zoom(10.0)
        }
    }

    val mapState = rememberMapState()

    val locationPainter = rememberVectorPainter(image = Icons.Default.LocationOn)
    val markerIconId = rememberIconImage(key = "location_marker", painter = locationPainter)

    val currentUserId = LocalUserId.current

    var showUnacceptConfirmationDialog by remember { mutableStateOf(false) }
    var showOffersModal by remember { mutableStateOf(false) } // NEW STATE for showing offers modal

    Scaffold { paddingValuesFromScaffold ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValuesFromScaffold),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && listing == null) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            } else if (listing != null) {
                val isDeliveryListing = listing.category.contains("Delivery", ignoreCase = true)
                val isOwner = (currentUserId == listing.ownerID)
                val hasAccepted = listing.acceptedBy.contains(currentUserId)
                val isFulfilled = listing.fulfilledBy != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = parentScrollEnabled)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = listing.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listing.category.split(", ").map { it.trim() }
                        categories.forEach { category ->
                            CategoryChip(categoryString = category, isSelected = true, onCategoryClick = {})
                        }
                    }

                    Text(
                        text = "Description:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listing.description,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Price: $${listing.price}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Posted by: ${listing.ownerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }

                    if (isDeliveryListing && listing.deliveryCoord != null && listing.deliveryCoord.size == 2) {
                        Text(
                            text = "Pickup Location:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lat: %.4f, Lon: %.4f".format(listing.coord[0], listing.coord[1]),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Delivery Location:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lat: %.4f, Lon: %.4f".format(listing.deliveryCoord[0], listing.deliveryCoord[1]),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.DarkGray
                        )
                    } else {
                        Text(
                            text = "Location:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lat: %.4f, Lon: %.4f".format(listing.coord[0], listing.coord[1]),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.DarkGray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        MapboxMap(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(pass = PointerEventPass.Initial)
                                        parentScrollEnabled = false
                                        do {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                        parentScrollEnabled = true
                                    }
                                },
                            mapViewportState = mapViewportState,
                            mapState = mapState,
                            content = {
                                LaunchedEffect(listing.coord, listing.deliveryCoord) {
                                    val pickupPoint = if (listing.coord.size == 2)
                                        Point.fromLngLat(listing.coord[1], listing.coord[0])
                                    else null

                                    val isDeliveryListingEffect = listing.category.contains("Delivery", ignoreCase = true)
                                    val deliveryPoint = if (isDeliveryListingEffect) {
                                        val coords = listing.deliveryCoord
                                        if (coords != null && coords.size == 2) {
                                            Point.fromLngLat(coords[1], coords[0])
                                        } else null
                                    } else null

                                    val cameraOptions = when {
                                        pickupPoint != null && deliveryPoint != null -> {
                                            val midLat = (pickupPoint.latitude() + deliveryPoint.latitude()) / 2
                                            val midLon = (pickupPoint.longitude() + deliveryPoint.longitude()) / 2

                                            val latDiff = abs(pickupPoint.latitude() - deliveryPoint.latitude())
                                            val lonDiff = abs(pickupPoint.longitude() - deliveryPoint.longitude())

                                            val maxDiff = max(latDiff, lonDiff)

                                            val zoom = when {
                                                maxDiff < 0.01 -> 14.0
                                                maxDiff < 0.05 -> 12.0
                                                maxDiff < 0.1 -> 10.5
                                                maxDiff < 0.5 -> 9.5
                                                else -> 8.5
                                            }

                                            CameraOptions.Builder()
                                                .center(Point.fromLngLat(midLon, midLat))
                                                .zoom(zoom)
                                                .build()
                                        }

                                        pickupPoint != null -> {
                                            CameraOptions.Builder()
                                                .center(pickupPoint)
                                                .zoom(14.0)
                                                .build()
                                        }

                                        else -> {
                                            CameraOptions.Builder()
                                                .center(Point.fromLngLat(103.8198, 1.3521)) // Default to Singapore
                                                .zoom(10.0)
                                                .build()
                                        }
                                    }

                                    mapViewportState.flyTo(cameraOptions)
                                }

                                if (listing.coord.isNotEmpty() && listing.coord.size == 2) {
                                    PointAnnotation(
                                        point = Point.fromLngLat(listing.coord[1], listing.coord[0]),
                                    ) {
                                        iconImage = markerIconId
                                        textField = if (listing.category.contains("Delivery", ignoreCase = true)) "Pickup" else "Location"
                                        textSize = 12.0
                                        textHaloWidth = 1.0
                                        textAnchor = TextAnchor.TOP
                                        textOffset = listOf(0.0, -1.5)
                                    }
                                }

                                if (listing.category.contains("Delivery", ignoreCase = true) && listing.deliveryCoord != null && listing.deliveryCoord.size == 2) {
                                    PointAnnotation(
                                        point = Point.fromLngLat(listing.deliveryCoord[1], listing.deliveryCoord[0]),
                                    ) {
                                        iconImage = markerIconId
                                        textField = "Delivery"
                                        textSize = 12.0
                                        textHaloWidth = 1.0
                                        textAnchor = TextAnchor.TOP
                                        textOffset = listOf(0.0, -1.5)
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOwner) {
                            Button(
                                onClick = { showOffersModal = true }, // NEW: Show the offers modal
                                // Button is always enabled for owner to view offers
                                enabled = !isLoading // Only disable if the page itself is loading
                            ) {
                                Text("View Offers (${listing.acceptedBy.size})")
                            }
                        } else { // Not the owner
                            if (!isFulfilled) {
                                if (!hasAccepted) {
                                    Button(
                                        onClick = { viewModel.acceptRequest(currentUserId) },
                                        enabled = !isLoading
                                    ) {
                                        Text("Offer to Help")
                                    }
                                } else { // User has offered to help
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Help Offered",
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Button(
                                            onClick = { showUnacceptConfirmationDialog = true },
                                            enabled = !isLoading,
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Withdraw Offer")
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Only show "Chat with Owner" if the current user is NOT the owner
                                    // and they have either offered help OR the request is fulfilled by them
                                    Button(
                                        onClick = { /* TODO: Implement chat with owner */ },
                                        enabled = !isLoading
                                    ) {
                                        Text("Chat with Owner")
                                    }
                                }
                            } else { // Request is fulfilled
                                Text(
                                    "Request Fulfilled",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                // If the current user is the one who fulfilled it, or they had offered help
                                // they can chat with the owner even if not the fulfiller, to discuss closure
                                if (currentUserId == listing.fulfilledBy || hasAccepted) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { /* TODO: Implement chat with owner */ },
                                        enabled = !isLoading
                                    ) {
                                        Text("Chat with Owner")
                                    }
                                }
                            }
                        }
                    }

                    // Dialog for withdrawing offer
                    if (showUnacceptConfirmationDialog) {
                        AlertDialog(
                            onDismissRequest = { showUnacceptConfirmationDialog = false },
                            title = { Text("Withdraw Offer") },
                            text = { Text("Are you sure you want to withdraw your offer to help? You can offer again later if it's still available.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.unacceptRequest(currentUserId)
                                        showUnacceptConfirmationDialog = false
                                    },
                                    enabled = !isLoading
                                ) {
                                    Text("Confirm Withdraw")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUnacceptConfirmationDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // NEW: Offers Modal
                    if (showOffersModal) {
                        OffersModal(
                            acceptedByUids = listing.acceptedBy,
                            onAcceptOffer = { acceptedUserId ->
                                viewModel.fulfillRequest(acceptedUserId)
                                showOffersModal = false // Close modal after accepting
                            },
                            onChatWithOfferor = { offerorId ->
                                // TODO: Implement chat with specific offeror
                                // For now, just log or show a toast
                                println("Chat with offeror: $offerorId")
                                showOffersModal = false // Close modal
                            },
                            onDismiss = { showOffersModal = false }
                        )
                    }


                    if (isFulfilled) {
                        Text(
                            "Fulfilled by: ${listing.fulfilledBy ?: "Unknown"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        )
                    }

                    listing.timestamp?.let { timestamp ->
                        Text(
                            text = "Created ${formatTimestampToTimeAgo(timestamp)} ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OffersModal(
    acceptedByUids: List<String>,
    onAcceptOffer: (String) -> Unit,
    onChatWithOfferor: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    var userNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingNames by remember { mutableStateOf(false) }

    LaunchedEffect(acceptedByUids) {
        isLoadingNames = true
        val fetchedNames = mutableMapOf<String, String>()
        for (uid in acceptedByUids) {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                fetchedNames[uid] = userDoc.getString("name") ?: "Unknown User"
            } catch (e: Exception) {
                fetchedNames[uid] = "Error fetching name"
                // Log the error for debugging
                println("Error fetching user name for $uid: ${e.message}")
            }
        }
        userNames = fetchedNames
        isLoadingNames = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offers to Help") },
        text = {
            if (isLoadingNames) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (acceptedByUids.isEmpty()) {
                Text("No one has offered to help yet.")
            } else {
                Column {
                    acceptedByUids.forEach { uid ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(userNames[uid] ?: "Loading...", fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onAcceptOffer(uid) },
                                    enabled = !isLoadingNames // Disable if names are still loading
                                ) {
                                    Text("Accept")
                                }
                                Button(
                                    onClick = { onChatWithOfferor(uid) },
                                    enabled = !isLoadingNames,
                                    colors = ButtonDefaults.outlinedButtonColors() // Chat button
                                ) {
                                    Text("Chat")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTimestampToTimeAgo(timestamp: Timestamp): String {
    val date = timestamp.toDate()
    val now = Date()
    val diff = now.time - date.time

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    return when {
        years > 0 -> "$years year${if (years > 1) "s" else ""}"
        months > 0 -> "$months month${if (months > 1) "s" else ""}"
        weeks > 0 -> "$weeks week${if (weeks > 1) "s" else ""}"
        days > 0 -> "$days day${if (days > 1) "s" else ""}"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
        else -> "$seconds second${if (seconds > 1) "s" else ""}"
    }
}