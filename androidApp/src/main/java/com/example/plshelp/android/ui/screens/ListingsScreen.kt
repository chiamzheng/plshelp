package com.example.plshelp.android.ui.screens

import Listing
import android.location.Location
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.border // Import for Modifier.border
import com.example.plshelp.android.LocalUserId // Import LocalUserId
import com.example.plshelp.android.data.DisplayModeRepository
import com.example.plshelp.android.data.ListingsViewModel // Import your ViewModel

// New enum to define the display mode
enum class DisplayMode {
    DISTANCE,
    WALK_TIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    viewModel: ListingsViewModel, // Inject the ViewModel here
    onNavigateToDetail: (Listing) -> Unit
) {
    val context = LocalContext.current
    val currentUserId = LocalUserId.current // Get current user ID

    val currentLat = remember { mutableDoubleStateOf(LocationManager.targetLat) }
    val currentLon = remember { mutableDoubleStateOf(LocationManager.targetLon) }

    // Collect states from ViewModel
    val listings by viewModel.listings.collectAsState() // General listings from DB
    val isLoading by viewModel.isLoading.collectAsState() // Loading for general listings
    val lastFetchTime = viewModel.lastFetchTimeState.value // Directly access the State value

    // Collect helper-centric lists from ViewModel (NEW/RENAMED)
    val listingsUserIsFulfilling by viewModel.listingsUserIsFulfilling.collectAsState()
    val isLoadingListingsUserIsFulfilling by viewModel.isLoadingListingsUserIsFulfilling.collectAsState()

    val listingsUserAcceptedButNotFulfilling by viewModel.listingsUserAcceptedButNotFulfilling.collectAsState()
    val isLoadingListingsUserAcceptedButNotFulfilling by viewModel.isLoadingListingsUserAcceptedButNotFulfilling.collectAsState()


    // Retrieve display mode from DataStore (still used by ListingCard)
    val displayModeRepository = remember { DisplayModeRepository(context) }
    val displayMode by displayModeRepository.displayModeFlow.collectAsState(initial = DisplayMode.DISTANCE)

    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var filterCategorySelection by rememberSaveable(stateSaver = categorySelectionSaver) {
        mutableStateOf(CategorySelection(mutableSetOf()))
    }

    val availableFilterCategories = remember {
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

    // --- NEW FILTERING LOGIC (Using pre-filtered lists from ViewModel) ---

    // 1. Active Requests (Your Offer Accepted) - directly use ViewModel's list
    val activeRequestsFiltered = listingsUserIsFulfilling

    // 2. Accepted Requests (Awaiting Confirmation) - directly use ViewModel's list
    val acceptedRequestsFiltered = listingsUserAcceptedButNotFulfilling

    // Deduplicate and filter general listings:
    // - Exclude listings already shown in active/accepted sections
    // - Exclude listings owned by the current user (this is already done by ViewModel.listings)
    // - Only include active listings (this is already done by ViewModel.listings)
    val displayedListingIds = remember(activeRequestsFiltered, acceptedRequestsFiltered) {
        (activeRequestsFiltered.map { it.id } + acceptedRequestsFiltered.map { it.id }).toSet()
    }

    val generalListingsDeduplicated = remember(listings, displayedListingIds) {
        listings.filter { listing ->
            listing.id !in displayedListingIds
        }
    }

    // Apply category filter to the deduplicated general listings
    val filteredGeneralListings = remember(generalListingsDeduplicated, filterCategorySelection.selectedCategories) {
        if (filterCategorySelection.selectedCategories.isEmpty()) {
            generalListingsDeduplicated
        } else {
            generalListingsDeduplicated.filter { listing ->
                val listingCategories = listing.category
                    .split(", ")
                    .map { it.trim().lowercase(Locale.getDefault()) }
                    .toSet()
                listingCategories.any { it in filterCategorySelection.selectedCategories }
            }
        }
    }

    // --- END NEW FILTERING LOGIC ---


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listings") },
                actions = { /* Actions slot is empty as content moved below */ }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("Filter by")
                }

                // Updated status and refresh button
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

                    // Call the ViewModel's refreshAllData
                    IconButton(onClick = { viewModel.refreshListings() }) { // Refresh all listings for simplicity
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                // 1. Active Requests Section (Green Border)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(BorderStroke(2.dp, Color.Green), MaterialTheme.shapes.medium)
                            .padding(8.dp) // Inner padding
                    ) {
                        Text(
                            text = "Active Requests (Your Offer Accepted)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (isLoadingListingsUserIsFulfilling && activeRequestsFiltered.isEmpty()) { // Use appropriate loading state
                            Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (activeRequestsFiltered.isEmpty()) {
                            Text(
                                text = "No active requests where your offer was accepted.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            activeRequestsFiltered.forEach { listing ->
                                ListingCard(
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

                // 2. Accepted Requests Section (Orange Border)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(BorderStroke(2.dp, Color.Yellow), MaterialTheme.shapes.medium)
                            .padding(8.dp) // Inner padding
                    ) {
                        Text(
                            text = "Accepted Requests (Awaiting Confirmation)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (isLoadingListingsUserAcceptedButNotFulfilling && acceptedRequestsFiltered.isEmpty()) { // Use appropriate loading state
                            Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (acceptedRequestsFiltered.isEmpty()) {
                            Text(
                                text = "No offers accepted for your listings (or you are pending confirmation).",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = Color.Gray
                            )
                        } else {
                            acceptedRequestsFiltered.forEach { listing ->
                                ListingCard(
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

                // 3. All Other Listings Section (General listings, deduplicated and filtered)
                item {
                    Text(
                        text = "All Other Listings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (isLoading && filteredGeneralListings.isEmpty() && activeRequestsFiltered.isEmpty() && acceptedRequestsFiltered.isEmpty()) {
                    // Show a general loading indicator if all sections are loading AND empty
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (filteredGeneralListings.isEmpty() && !isLoading) { // Ensure not just loading
                    // Only show "No listings" if the general section itself is empty after filtering
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No other listings available.", fontSize = 18.sp, color = Color.Gray)
                        }
                    }
                } else {
                    items(filteredGeneralListings) { listing ->
                        ListingCard(
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

// ListingCard remains the same
@Composable
fun ListingCard(
    listing: Listing,
    onNavigateToDetail: (Listing) -> Unit,
    currentLat: Double?,
    currentLon: Double?,
    displayMode: DisplayMode
) {
    val formattedDistanceOrTime = remember(listing.coord, currentLat, currentLon, displayMode) {
        if (currentLat == null || currentLon == null || listing.coord.size < 2) {
            "N/A"
        } else {
            val listingLat = listing.coord[0]
            val listingLon = listing.coord[1]
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon,
                listingLat, listingLon, results)
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
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetail(listing) }
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
            Spacer(modifier = Modifier.height(8.dp))
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
            }
        }
    }
}