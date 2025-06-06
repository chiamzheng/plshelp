package com.example.plshelp.android.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState // Import for animation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plshelp.android.R
import com.example.plshelp.android.data.LocationManager
import com.example.plshelp.android.ui.components.CategoryChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.location.Location
import Listing
import androidx.compose.runtime.snapshots.SnapshotStateList

// New enum to define the display mode
enum class DisplayMode {
    DISTANCE,
    WALK_TIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    // <--- THESE ARE THE PARAMETERS PASSED FROM MainActivity
    listings: SnapshotStateList<Listing>, // The list of listings to display
    isLoading: Boolean,                   // Boolean indicating if data is currently loading
    lastFetchTime: Long,                  // Timestamp of the last data refresh
    onRefresh: () -> Unit,                // Callback to trigger a data refresh
    // --- END PASSED PARAMETERS ---

    onNavigateToDetail: (Listing) -> Unit // Your existing navigation callback
) {
    val context = LocalContext.current

    val currentLat = remember { mutableDoubleStateOf(LocationManager.targetLat) }
    val currentLon = remember { mutableDoubleStateOf(LocationManager.targetLon) }

    var displayMode by remember { mutableStateOf(DisplayMode.DISTANCE) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // State for selected filter categories
    // Ensure categorySelectionSaver is correctly imported or defined above/in a shared file
    var filterCategorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    // Predefined categories for filtering
    val availableFilterCategories = remember { // Use remember to avoid recreating on every recomposition
        listOf(
            "Urgent", "Helper", "Delivery", "Free", "Others",
            "Invite", "Trade", "Advice", "Event", "Study",
            "Borrow", "Food"
        )
    }

    LaunchedEffect(Unit) {
        LocationManager.checkUserLocation(context) { result ->
            val parts = result.split("\n").last().split(", ")
            if (parts.size == 2) {
                currentLat.doubleValue = parts[0].toDoubleOrNull() ?: LocationManager.targetLat
                currentLon.doubleValue = parts[1].toDoubleOrNull() ?: LocationManager.targetLon
            }
        }
    }

    // Filter the listings based on selected categories
    val filteredListings = remember(listings, filterCategorySelection.selectedCategories) {
        if (filterCategorySelection.selectedCategories.isEmpty()) {
            listings
        } else {
            listings.filter { listing ->
                val listingCategories = listing.category
                    .split(", ")
                    .map { it.trim().lowercase(Locale.getDefault()) }
                    .toSet()
                listingCategories.any { it in filterCategorySelection.selectedCategories }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listings") },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentTimeMillis = System.currentTimeMillis()
                        val timeDifferenceSeconds = (currentTimeMillis - lastFetchTime) / 1000

                        val lastUpdatedText = remember(timeDifferenceSeconds) {
                            when {
                                timeDifferenceSeconds < 60 -> "$timeDifferenceSeconds secs ago"
                                timeDifferenceSeconds < 3600 -> "${timeDifferenceSeconds / 60} mins ago"
                                else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastFetchTime))
                            }
                        }

                        if (lastFetchTime > 0) {
                            Text(
                                text = "Updated: $lastUpdatedText",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        // Call the onRefresh callback passed from MainActivity
                        IconButton(onClick = { onRefresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Filter by")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Distance", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = displayMode == DisplayMode.WALK_TIME,
                        onCheckedChange = { isChecked ->
                            displayMode = if (isChecked) DisplayMode.WALK_TIME else DisplayMode.DISTANCE
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Walk Time", style = MaterialTheme.typography.bodySmall)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (filteredListings.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No listings available.", fontSize = 18.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            items(filteredListings) { listing ->
                                ExpandableListingCard(
                                    listing = listing,
                                    onNavigateToDetail = onNavigateToDetail,
                                    currentLat = currentLat.doubleValue,
                                    currentLon = currentLon.doubleValue,
                                    displayMode = displayMode
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Modal Bottom Sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Filter Categories", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Category", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.fillMaxWidth()) {
                    availableFilterCategories.chunked(4).forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowCategories.forEach { category ->
                                val isSelected = filterCategorySelection.selectedCategories.contains(category.lowercase(Locale.getDefault()))

                                CategoryChip(
                                    categoryString = category,
                                    isSelected = isSelected,
                                    onCategoryClick = { clickedCategory ->
                                        val lowercasedCategory = clickedCategory.lowercase(Locale.getDefault())
                                        val newSelection = filterCategorySelection.selectedCategories.toMutableSet()
                                        if (lowercasedCategory in newSelection) {
                                            newSelection.remove(lowercasedCategory)
                                        } else {
                                            newSelection.add(lowercasedCategory)
                                        }
                                        filterCategorySelection = filterCategorySelection.copy(selectedCategories = newSelection)
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
            }
        }
    }
}

@Composable
fun ExpandableListingCard(
    listing: Listing,
    onNavigateToDetail: (Listing) -> Unit,
    currentLat: Double,
    currentLon: Double,
    displayMode: DisplayMode
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Animate arrow rotation
    val rotationDegree by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ArrowRotation"
    )

    val formattedDistanceOrTime = remember(listing.coord, currentLat, currentLon, displayMode) {
        // Ensure listing.coord is not null and has at least two elements
        if (listing.coord.size >= 2) {
            val listingLat = listing.coord[0]
            val listingLon = listing.coord[1]
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon, listingLat.toDouble(), listingLon.toDouble(), results) // Ensure conversion to Double
            val distanceInMeters = results[0]

            when (displayMode) {
                DisplayMode.DISTANCE -> {
                    if (distanceInMeters < 1000) {
                        String.format("%.0f m", distanceInMeters)
                    } else {
                        val distanceInKm = distanceInMeters / 1000
                        String.format("%.2f km", distanceInKm)
                    }
                }
                DisplayMode.WALK_TIME -> {
                    val walkingSpeedMetersPerSecond = 1.4 // Approx 5 km/h
                    val timeInSeconds = distanceInMeters / walkingSpeedMetersPerSecond
                    val timeInMinutes = (timeInSeconds / 60).toInt()

                    when {
                        timeInMinutes < 1 -> "<1 min"
                        timeInMinutes < 60 -> "$timeInMinutes min"
                        else -> {
                            val hours = timeInMinutes / 60
                            val remainingMinutes = timeInMinutes % 60
                            "$hours hr $remainingMinutes min"
                        }
                    }
                }
            }
        } else {
            "N/A" // Handle cases where coordinates might be missing or malformed
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = listing.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_directions_walk_24),
                        contentDescription = "Distance or Walk Time",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = formattedDistanceOrTime, fontSize = 16.sp)
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(rotationDegree)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val categories = listing.category.split(", ").map { it.trim() }
                categories.forEach { category ->
                    CategoryChip(categoryString = category, isSelected = true, onCategoryClick = {})
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = " ${listing.subtitle}", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Price: $${listing.price}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Button(onClick = { onNavigateToDetail(listing) }) {
                        Text("View Details")
                    }
                }
            }
        }
    }
}