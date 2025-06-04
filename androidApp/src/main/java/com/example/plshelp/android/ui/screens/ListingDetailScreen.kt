package com.example.plshelp.android.ui.screens

import android.util.Log
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

// New imports for pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.pointer.pointerInput

import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage // Import for marker fix
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.google.firebase.Timestamp // Import for Timestamp

import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, MapboxExperimental::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBackClick: () -> Unit,
    initialListing: Listing? = null // New parameter: nullable initial Listing object
) {
    val viewModel: ListingDetailViewModel = viewModel(
        factory = ListingDetailViewModel.Factory(listingId, initialListing)
    )

    val listing = viewModel.listing
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage

    val scrollState = rememberScrollState()

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            // Default center if listing coordinates aren't available yet (Singapore)
            center(Point.fromLngLat(103.8198, 1.3521))
            zoom(10.0)
        }
    }

    // Prepare the icon for the map marker
    val customLocationPainter = rememberVectorPainter(
        image = Icons.Default.LocationOn,
    )
    // CRITICAL for marker: Convert the painter to an IconImage that Mapbox can use
    val markerIconId = rememberIconImage(key = "listing_location_marker", painter = customLocationPainter)


    // Update map camera when listing coordinates are available
    LaunchedEffect(listing?.coord) {
        listing?.coord?.let { coords ->
            if (coords.size == 2) {
                // Mapbox uses (longitude, latitude)
                // Assuming coords[0] is latitude and coords[1] is longitude from ViewModel
                val point = Point.fromLngLat(coords[1], coords[0])
                mapViewportState.flyTo(
                    CameraOptions.Builder()
                        .center(point)
                        .zoom(14.0) // Zoom in on the specific location
                        .build()
                )
            }
        }
    }

    // We remove Scaffold's topBar and manage padding manually within the content.
    // The padding values from Scaffold are still useful if you want to reuse them.
    Scaffold(
        // topBar = { ... removed ... }
    ) { paddingValuesFromScaffold -> // Rename to avoid confusion with internal padding
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValuesFromScaffold), // Apply Scaffold's padding here if needed
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            } else if (listing != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp), // Main padding for the content
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Custom Header: Back Button and Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between icon and title
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
                            modifier = Modifier.weight(1f) // Allow title to take remaining space
                        )
                    }

                    // Categories
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listing.category.split(", ").map { it.trim() }
                        categories.forEach { category ->
                            CategoryChip(categoryString = category, isSelected = true, onCategoryClick = {})
                        }
                    }

                    // Description
                    Text(
                        text = "Description:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listing.description,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Price and Posted By (in one line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween // Pushes items to ends
                    ) {
                        Text(
                            text = "Price: $${listing.price}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp)) // Add some space if both might be long
                        Text(
                            text = "Posted by: ${listing.ownerName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }


                    Text(
                        text = "Location:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Mapbox Map displaying listing location
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp) // Fixed height for the map
                    ) {
                        MapboxMap(
                            modifier = Modifier
                                .fillMaxSize()
                                // Keeping your existing pointerInput for now, but be aware it blocks map pan/zoom
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { /* Optional: actions when drag starts */ },
                                        onDragEnd = { /* Optional: actions when drag ends */ },
                                        onDragCancel = { /* Optional: actions when drag is cancelled */ },
                                        onDrag = { change, _ -> change.consume() } // Consume all changes during drag
                                    )
                                },
                            mapViewportState = mapViewportState
                        ) {
                            listing.coord.let { coords ->
                                if (coords.size == 2) {
                                    PointAnnotation(
                                        point = Point.fromLngLat(coords[1], coords[0]), // Lon, Lat for Mapbox
                                    ) {
                                        iconImage = markerIconId
                                    }
                                }
                            }
                        }
                    }

                    // Created "X time ago"
                    listing.timestamp?.let { timestamp ->
                        Text(
                            text = "Created ${formatTimestampToTimeAgo(timestamp)} ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End) // Align to the right
                        )
                    }
                }
            }
        }
    }
}

// Helper function to format timestamp to "X time ago"
fun formatTimestampToTimeAgo(timestamp: Timestamp): String {
    val date = timestamp.toDate()
    val now = Date()
    val diff = now.time - date.time

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val weeks = days / 7
    val months = days / 30 // Approximation
    val years = days / 365 // Approximation

    return when {
        years > 0 -> "${years} year${if (years > 1) "s" else ""}"
        months > 0 -> "${months} month${if (months > 1) "s" else ""}"
        weeks > 0 -> "${weeks} week${if (weeks > 1) "s" else ""}"
        days > 0 -> "${days} day${if (days > 1) "s" else ""}"
        hours > 0 -> "${hours} hour${if (hours > 1) "s" else ""}"
        minutes > 0 -> "${minutes} minute${if (minutes > 1) "s" else ""}"
        else -> "${seconds} second${if (seconds > 1) "s" else ""}"
    }
}