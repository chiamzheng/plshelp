package com.example.plshelp.android.ui.screens

import Listing // Correct import for Listing
import android.location.Location
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.plshelp.android.LocalUserId // Assuming LocalUserId is defined in the Android package
import com.example.plshelp.android.data.DisplayModeRepository
import com.example.plshelp.android.data.ListingsViewModel
import androidx.compose.ui.text.style.TextAlign


// New enum to define the display mode
enum class DisplayMode {
    DISTANCE,
    WALK_TIME
}

// Define the Status data class for the ListingCard
data class ListingStatus(val text: String, val color: Color)

// Enum to define the tabs
enum class ListingsTab {
    PUBLIC_LISTINGS, YOUR_LISTINGS
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

    // Collect states from ViewModel for Public Listings
    val listings by viewModel.listings.collectAsState() // General listings from DB
    val isLoading by viewModel.isLoading.collectAsState() // Loading for general listings
    val lastFetchTime = viewModel.lastFetchTimeState.value // Directly access the State value

    // Collect helper-centric lists from ViewModel (used in "Your Engaged Listings" section)
    val listingsUserIsFulfilling by viewModel.listingsUserIsFulfilling.collectAsState()
    val isLoadingListingsUserIsFulfilling by viewModel.isLoadingListingsUserIsFulfilling.collectAsState()

    val listingsUserAcceptedButNotFulfilling by viewModel.listingsUserAcceptedButNotFulfilling.collectAsState()
    val isLoadingListingsUserAcceptedButNotFulfilling by viewModel.isLoadingListingsUserAcceptedButNotFulfilling.collectAsState()

    // FIX: Collect ALL listings owned by the current user for "Your Listings" tab
    val allOwnedListings by viewModel.listingsOwnedByCurrentUser.collectAsState()
    val isLoadingAllOwnedListings by viewModel.isLoadingListingsOwnedByCurrentUser.collectAsState()


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

    // State for selected tab
    var selectedTab by rememberSaveable { mutableStateOf(ListingsTab.PUBLIC_LISTINGS) }

    LaunchedEffect(Unit) {
        LocationManager.checkUserLocation(context) { result ->
            val parts = result.split("\n").last().split(", ")
            if (parts.size == 2) {
                currentLat.doubleValue = parts[0].toDoubleOrNull() ?: LocationManager.targetLat
                currentLon.doubleValue = parts[1].toDoubleOrNull() ?: LocationManager.targetLon
            }
        }
    }

    // --- REMOVED THE LaunchedEffect(currentUserId) BLOCK FROM HERE ---
    // The initial refresh is now handled by MainActivity's LaunchedEffect(listingsViewModel)

    // --- Public Listings Filtering Logic ---
    val combinedEngagedListingsRaw = remember(listingsUserIsFulfilling, listingsUserAcceptedButNotFulfilling) {
        (listingsUserIsFulfilling + listingsUserAcceptedButNotFulfilling)
            .distinctBy { it.id }
        // Removed direct sorting here, will be sorted by status below
    }

    // NEW: Sorting for combinedEngagedListings
    val sortedCombinedEngagedListings = remember(combinedEngagedListingsRaw) {
        combinedEngagedListingsRaw.sortedWith(compareBy<Listing> { listing ->
            when {
                // "ACTIVE" if you are currently fulfilling
                listingsUserIsFulfilling.any { it.id == listing.id } -> 0
                // "PENDING" if you accepted, awaiting owner confirmation
                listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> 1
                // Fallback for any other unexpected status (shouldn't happen with the current logic)
                else -> 2
            }
        }.thenByDescending { it.timestamp?.toDate() }) // Secondary sort by timestamp
    }


    val engagedListingIds = remember(sortedCombinedEngagedListings) {
        sortedCombinedEngagedListings.map { it.id }.toSet()
    }

    val generalListingsDeduplicated = remember(listings, engagedListingIds) {
        listings.filter { listing ->
            listing.id !in engagedListingIds
        }
    }

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
    // --- End Public Listings Filtering Logic ---


    // --- Your Listings Sorting Logic (Hoisted outside LazyColumn content lambda) ---
    val sortedOwnedListings = remember(allOwnedListings) {
        allOwnedListings.sortedWith(compareBy<Listing> { listing ->
            when {
                // "COMPLETED" if status is explicitly "fulfilled" (owner marked as completed)
                listing.status == "fulfilled" -> 0
                // "ACTIVE" if fulfilledBy is not null and not empty AND status is "active" (someone is actively helping)
                // FIX: Null-safe check for fulfilledBy
                listing.fulfilledBy?.isNotEmpty() == true && listing.status == "active" -> 1
                // "OFFER RECEIVED" if acceptedBy is not empty AND fulfilledBy is null or empty
                // This means someone has offered, but not yet fulfilling
                // FIX: Correct logic for "OFFER RECEIVED"
                listing.acceptedBy.isNotEmpty() && listing.fulfilledBy.isNullOrEmpty() -> 2
                // No special banner for open listings without accepted offers
                else -> 3
            }
        }.thenByDescending { it.timestamp?.toDate() }) // Secondary sort by timestamp
    }
    // --- End Your Listings Sorting Logic ---


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Listings") },
                actions = {
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

                    IconButton(onClick = { viewModel.refreshListings() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ListingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.name.replace("_", " ")) } // Replaces underscore with space
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                ListingsTab.PUBLIC_LISTINGS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        // Your Engaged Listings Section
                        item { // This item holds the header and conditional loading/empty states for engaged listings
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp) // Smaller bottom padding here as items will add their own
                            ) {
                                Text(
                                    text = "Your Engaged Listings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        val overallLoadingEngaged = isLoadingListingsUserIsFulfilling || isLoadingListingsUserAcceptedButNotFulfilling
                        if (overallLoadingEngaged && sortedCombinedEngagedListings.isEmpty()) { // Use sorted list here
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (sortedCombinedEngagedListings.isEmpty()) { // Use sorted list here
                            item {
                                Text(
                                    text = "No active or pending engagements.",
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), // Add padding here
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            // FIX: Use sortedCombinedEngagedListings for rendering list content in LazyColumn
                            items(sortedCombinedEngagedListings) { listing ->
                                // Status logic for listings YOU are fulfilling/accepted (as a helper)
                                val statusText = when {
                                    listingsUserIsFulfilling.any { it.id == listing.id } -> "ACTIVE" // You are currently fulfilling
                                    listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> "PENDING" // You accepted, awaiting owner confirmation
                                    else -> null
                                }
                                val statusColor = when {
                                    listingsUserIsFulfilling.any { it.id == listing.id } -> Color(0xFF338a4d) // Green
                                    listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> Color(0xFFb0aa0c) // Orange/Yellow
                                    else -> null
                                }

                                ListingCard(
                                    listing = listing,
                                    onNavigateToDetail = onNavigateToDetail,
                                    currentLat = currentLat.doubleValue,
                                    currentLon = currentLon.doubleValue,
                                    displayMode = displayMode,
                                    status = if (statusText != null && statusColor != null) ListingStatus(statusText, statusColor) else null
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // All Other Listings Section (Public listings, deduplicated and filtered)
                        item { // This item holds the header and filter button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp), // Add top padding to separate sections
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Available Listings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Button(
                                    onClick = { showFilterSheet = true },
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Filter by",
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp // Ensure lineHeight matches fontSize for tight fit
                                    )
                                }
                            }
                        }

                        // Corrected loading and empty state check for Available Listings
                        if (isLoading && filteredGeneralListings.isEmpty()) { // No need to check combinedEngagedListings here
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (filteredGeneralListings.isEmpty() && !isLoading) { // If no general listings after loading
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No other listings available.", fontSize = 18.sp, color = Color.Gray)
                                }
                            }
                        } else {
                            // These are direct items of LazyColumn
                            items(filteredGeneralListings) { listing ->
                                ListingCard(
                                    listing = listing,
                                    onNavigateToDetail = onNavigateToDetail,
                                    currentLat = currentLat.doubleValue,
                                    currentLon = currentLon.doubleValue,
                                    displayMode = displayMode,
                                    status = null // Public listings typically don't have a banner unless they are "urgent" or special
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                ListingsTab.YOUR_LISTINGS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        item { // This item holds the header for your posted listings
                            Text(
                                text = "Your Posted Listings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Use allOwnedListings for display and apply custom sorting
                        if (isLoadingAllOwnedListings && allOwnedListings.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (allOwnedListings.isEmpty() && !isLoadingAllOwnedListings) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "You haven't posted any listings yet.",
                                        fontSize = 18.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // These are direct items of LazyColumn
                            items(sortedOwnedListings) { listing ->
                                // Determine status for your own listings
                                val statusForUserListing: ListingStatus? = when {
                                    // "COMPLETED" if status is explicitly "fulfilled" (owner marked as completed)
                                    listing.status == "fulfilled" ->
                                        ListingStatus("COMPLETED", Color(0xFF6A1B9A)) // Purple for completed
                                    // "ACTIVE" if fulfilledBy is not null and not empty AND status is "active" (someone is actively helping)
                                    // FIX: Null-safe check for fulfilledBy
                                    listing.fulfilledBy?.isNotEmpty() == true && listing.status == "active" ->
                                        ListingStatus("ACTIVE", Color(0xFF338a4d)) // Green
                                    // "OFFER RECEIVED" if acceptedBy is not empty AND fulfilledBy is null or empty
                                    // This means someone has offered, but not yet fulfilling
                                    // FIX: Correct logic for "OFFER RECEIVED"
                                    listing.acceptedBy.isNotEmpty() && listing.fulfilledBy.isNullOrEmpty() ->
                                        ListingStatus("OFFER RECEIVED", Color(0xFFb0aa0c)) // Orange/Yellow
                                    // No special banner for open listings without accepted offers
                                    else ->
                                        null
                                }

                                ListingCard(
                                    listing = listing,
                                    onNavigateToDetail = onNavigateToDetail,
                                    currentLat = currentLat.doubleValue,
                                    currentLon = currentLon.doubleValue,
                                    displayMode = displayMode,
                                    status = statusForUserListing
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
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
                            // Add spacers if the row has fewer than 4 categories to maintain alignment
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
fun ListingCard(
    listing: Listing,
    onNavigateToDetail: (Listing) -> Unit,
    currentLat: Double?,
    currentLon: Double?,
    displayMode: DisplayMode,
    status: ListingStatus? = null // New nullable parameter for status
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

    val cardCornerRadius = 16.dp
    val bannerHeight = 12.dp // Height of the status banner (You confirmed this is now 12.dp)

    Card(
        shape = RoundedCornerShape(cardCornerRadius),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToDetail(listing) }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main content column for title, distance, and categories
            // Apply top padding if a status banner exists
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (status != null) bannerHeight else 6.dp, // Add space for banner
                        start = 6.dp,
                        end = 6.dp,
                        bottom = 6.dp
                    )
            ) {
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
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = formattedDistanceOrTime, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val categories = listing.category.split(", ").map { it.trim() }
                    categories.forEach { category ->
                        CategoryChip(categoryString = category, isSelected = true, onCategoryClick = {})
                    }
                }
            }

            // Status Banner at the top
            status?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter) // Align to the top of the card
                        .fillMaxWidth()
                        .height(bannerHeight) // Set banner height to 12.dp
                        .background(
                            color = it.color,
                            shape = RoundedCornerShape(
                                topStart = cardCornerRadius,
                                topEnd = cardCornerRadius,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp
                            )
                        ),
                    contentAlignment = Alignment.Center // Center the text within the banner
                ) {
                    Text(
                        text = it.text,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp, // Your desired font size
                        lineHeight = 9.sp, // Manually set lineHeight to match fontSize to minimize extra space
                        modifier = Modifier.offset(y = (-1).dp) // Current offset
                    )
                }
            }

            // Reward text, aligned to the bottom-right with its own background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = Color(0xFF51367a),
                        shape = RoundedCornerShape(
                            topStart = 6.dp,
                            topEnd = 0.dp,
                            bottomStart = 6.dp,
                            bottomEnd = cardCornerRadius
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Reward: $${listing.price}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
    }
}