// ListingDetailScreen.kt
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
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.plshelp.android.ui.components.CategoryChip
import com.example.plshelp.android.data.ListingDetailViewModel

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

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
import com.mapbox.maps.extension.compose.rememberMapState
import com.example.plshelp.android.LocalUserId
import com.google.firebase.firestore.FirebaseFirestore
import com.example.plshelp.android.data.ChatType
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBackClick: () -> Unit,
    initialListing: Listing? = null,
    onNavigateToAcceptedRequests: (String, String?) -> Unit,
    onNavigateToChat: (List<String>, String, ChatType) -> Unit // participant UIDs, listingId, ChatType
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
    val context = LocalContext.current

    val locationPainter = rememberVectorPainter(image = Icons.Default.LocationOn)
    val markerIconId = rememberIconImage(key = "location_marker", painter = locationPainter)

    val currentUserId = LocalUserId.current

    var showUnacceptConfirmationDialog by remember { mutableStateOf(false) }
    var showOffersModal by remember { mutableStateOf(false) }
    var showMarkCompletedConfirmationDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fontWeight = FontWeight.ExtraBold, text = listing?.title ?: "Loading Listing...") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValuesFromScaffold ->
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
                val isFulfilledBySomeone = !listing.fulfilledBy.isNullOrEmpty()
                val isFulfilledByCurrentUser = listing.fulfilledBy?.contains(currentUserId) == true
                val isRequestCompleted = listing.status == "fulfilled"

                // --- Resolve names for acceptedBy & fulfilledBy ---
                var acceptedByNames by remember { mutableStateOf<List<String>>(emptyList()) }
                var fulfilledByNames by remember { mutableStateOf<List<String>>(emptyList()) }

                LaunchedEffect(listing.acceptedBy) {
                    val names = mutableListOf<String>()
                    listing.acceptedBy.forEach { uid ->
                        viewModel.getUserName(uid) { name -> names.add(name) }
                    }
                    acceptedByNames = names
                }
                LaunchedEffect(listing.fulfilledBy) {
                    val names = mutableListOf<String>()
                    listing.fulfilledBy?.forEach { uid ->
                        viewModel.getUserName(uid) { name -> names.add(name) }
                    }
                    fulfilledByNames = names
                }

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState, enabled = parentScrollEnabled)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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

                        // --- Image Display ---
                        listing.imageUrl?.let { imageUrl ->
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x38393b)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "Listing Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Text(
                            text = "Description:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
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
                            val rewardText = if (listing.price.toFloatOrNull() != null) {
                                "Reward: ${listing.price} points"
                            } else {
                                "Reward: ${listing.price}"
                            }
                            Text(
                                text = rewardText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Posted by: ${listing.ownerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }

                        // --- Mapbox Map section remains unchanged ---
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
                                content = { /* Camera positioning & annotations unchanged */ }
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

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // --- Bottom Buttons Section ---
                    Surface(
                        tonalElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, top = 8.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isOwner) {
                                if (isRequestCompleted) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Request Fulfilled",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        if (isFulfilledBySomeone) {
                                            val fulfillers = fulfilledByNames.joinToString(", ")
                                            Text(
                                                "By: $fulfillers",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val participants = mutableListOf(currentUserId).apply {
                                                        listing.fulfilledBy?.let { addAll(it) }
                                                    }.distinct().sorted()
                                                    val chatTypeToNavigate =
                                                        if (participants.size > 2) ChatType.GROUP else ChatType.ONE_ON_ONE
                                                    onNavigateToChat(participants, listingId, chatTypeToNavigate)
                                                },
                                                enabled = !isLoading
                                            ) {
                                                Text("Chat with Fulfiller(s)")
                                            }
                                        }
                                    }
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Button(
                                            onClick = { showOffersModal = true },
                                            enabled = !isLoading
                                        ) {
                                            Text("View Offers (${listing.acceptedBy.size})")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (isFulfilledBySomeone) {
                                            val fulfillers = fulfilledByNames.joinToString(", ")
                                            Text(
                                                "Assigned to: $fulfillers",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { showMarkCompletedConfirmationDialog = true },
                                                    enabled = !isLoading,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF6c2694),
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Text("Mark as Completed")
                                                }
                                                Button(
                                                    onClick = {
                                                        val participants = mutableListOf(currentUserId).apply {
                                                            listing.fulfilledBy?.let { addAll(it) }
                                                        }.distinct().sorted()
                                                        val chatTypeToNavigate =
                                                            if (participants.size > 2) ChatType.GROUP else ChatType.ONE_ON_ONE
                                                        onNavigateToChat(participants, listingId, chatTypeToNavigate)
                                                    },
                                                    enabled = !isLoading
                                                ) {
                                                    Text("Chat with Fulfiller(s)")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else { // Not owner
                                if (isRequestCompleted) {
                                    Text(
                                        "Request Fulfilled",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    if (isFulfilledByCurrentUser || hasAccepted) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val ownerId = listing.ownerID
                                                val participants = listOf(currentUserId, ownerId).sorted()
                                                onNavigateToChat(participants, listingId, ChatType.ONE_ON_ONE)
                                            },
                                            enabled = !isLoading
                                        ) {
                                            Text("Chat with Owner")
                                        }
                                    }
                                } else { // Active request
                                    if (hasAccepted) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                if (isFulfilledByCurrentUser) "You are selected!" else "Help Offered",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { showUnacceptConfirmationDialog = true },
                                                    enabled = !isLoading && !isFulfilledByCurrentUser,
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.error
                                                    )
                                                ) {
                                                    Text("Withdraw Offer")
                                                }
                                                Button(
                                                    onClick = {
                                                        val ownerId = listing.ownerID
                                                        val participants = listOf(currentUserId, ownerId).sorted()
                                                        onNavigateToChat(participants, listingId, ChatType.ONE_ON_ONE)
                                                    },
                                                    enabled = !isLoading
                                                ) {
                                                    Text("Chat with Owner")
                                                }
                                            }
                                        }
                                    } else { // User has not offered
                                        if (!isFulfilledBySomeone) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { viewModel.acceptRequest(currentUserId) },
                                                    enabled = !isLoading
                                                ) {
                                                    Text("Offer to Help")
                                                }
                                                Button(
                                                    onClick = {
                                                        val ownerId = listing.ownerID
                                                        val participants = listOf(currentUserId, ownerId).sorted()
                                                        onNavigateToChat(participants, listingId, ChatType.ONE_ON_ONE)
                                                    },
                                                    enabled = !isLoading
                                                ) {
                                                    Text("Chat with Owner")
                                                }
                                            }
                                        } else {
                                            Text(
                                                "Request Active (Assigned)",
                                                color = Color.DarkGray,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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

    if (showMarkCompletedConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showMarkCompletedConfirmationDialog = false },
            title = { Text("Mark Request as Completed") },
            text = { Text("Are you sure you want to mark this request as completed? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.markRequestAsCompleted(currentUserId)
                        showMarkCompletedConfirmationDialog = false
                    },
                    enabled = !isLoading
                ) {
                    Text("Confirm Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMarkCompletedConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOffersModal && listing != null && !isRequestCompleted(listing.status)) {
        OffersModal(
            acceptedByUids = listing.acceptedBy,
            alreadyFulfilledBy = listing.fulfilledBy,
            onAcceptOffer = { acceptedUserId ->
                viewModel.fulfillRequest(acceptedUserId)
                showOffersModal = false
            },
            onChatWithOfferor = { offerorId ->
                val participants = listOf(currentUserId, offerorId).sorted()
                onNavigateToChat(participants, listingId, ChatType.ONE_ON_ONE)
                showOffersModal = false
            },
            onDismiss = { showOffersModal = false }
        )
    }
}


private fun isRequestCompleted(status: String?): Boolean {
    return status == "fulfilled"
}

@Composable
fun OffersModal(
    acceptedByUids: List<String>,
    alreadyFulfilledBy: List<String>?, // New parameter to check selected fulfillers
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
                        val isAlreadySelected = alreadyFulfilledBy?.contains(uid) == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = userNames[uid] ?: uid,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (isAlreadySelected) {
                                Text(
                                    "Selected",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else {
                                Button(
                                    onClick = { onAcceptOffer(uid) },
                                    enabled = !isLoadingNames
                                ) {
                                    Text("Select")
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onChatWithOfferor(uid) },
                                enabled = !isLoadingNames
                            ) {
                                Text("Chat")
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

// Helper function to format timestamp to time ago
fun formatTimestampToTimeAgo(timestamp: Timestamp): String {
    val now = Date()
    val then = timestamp.toDate()
    val diffMillis = now.time - then.time

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        seconds < 60 -> "$seconds seconds"
        minutes < 60 -> "$minutes minutes"
        hours < 24 -> "$hours hours"
        days < 7 -> "$days days"
        else -> "${days / 7} weeks" // Simple approximation for weeks
    }
}
