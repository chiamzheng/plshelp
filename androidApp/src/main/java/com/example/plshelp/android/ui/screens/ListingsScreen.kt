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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.example.plshelp.android.LocalUserId // Assuming LocalUserId is defined in the Android package
import com.example.plshelp.android.data.DisplayModeRepository
import com.example.plshelp.android.data.ListingsViewModel
import androidx.compose.ui.text.style.TextAlign
import com.google.common.geometry.S2Cell
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotationInteractionsState

enum class DisplayMode {
    DISTANCE,
    WALK_TIME
}

data class ListingStatus(val text: String, val color: Color)

enum class ListingsTab {
    PUBLIC_LISTINGS, YOUR_LISTINGS, MAP_VIEW
}

private const val S2_CELL_LEVEL = 13

fun s2CellToPolygon(s2CellId: S2CellId): Polygon {
    val cell = S2Cell(s2CellId)
    val points = mutableListOf<Point>()
    for (i in 0 until 4) {
        val vertex = cell.getVertex(i)
        val latLng = S2LatLng(vertex)
        points.add(Point.fromLngLat(latLng.lngDegrees(), latLng.latDegrees()))
    }
    points.add(points.first())
    return Polygon.fromLngLats(listOf(points))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    viewModel: ListingsViewModel,
    onNavigateToDetail: (Listing) -> Unit
) {
    val context = LocalContext.current
    val currentUserId = LocalUserId.current

    val currentLat = remember { mutableDoubleStateOf(LocationManager.targetLat) }
    val currentLon = remember { mutableDoubleStateOf(LocationManager.targetLon) }

    val listings by viewModel.listings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastFetchTime = viewModel.lastFetchTimeState.value

    val listingsUserIsFulfilling by viewModel.listingsUserIsFulfilling.collectAsState()
    val isLoadingListingsUserIsFulfilling by viewModel.isLoadingListingsUserIsFulfilling.collectAsState()

    val listingsUserAcceptedButNotFulfilling by viewModel.listingsUserAcceptedButNotFulfilling.collectAsState()
    val isLoadingListingsUserAcceptedButNotFulfilling by viewModel.isLoadingListingsUserAcceptedButNotFulfilling.collectAsState()

    val allOwnedListings by viewModel.listingsOwnedByCurrentUser.collectAsState()
    val isLoadingAllOwnedListings by viewModel.isLoadingListingsOwnedByCurrentUser.collectAsState()

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

    var selectedTab by rememberSaveable { mutableStateOf(ListingsTab.PUBLIC_LISTINGS) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(103.8198, 1.3521))
            zoom(10.0)
        }
    }
    val mapState = rememberMapState()


    var mapCenterS2CellId by remember { mutableStateOf<S2CellId?>(null) }
    var showSelectCellConfirmation by remember { mutableStateOf(false) }

    val activeS2CellId by viewModel.selectedS2CellId.collectAsState()

    val locationPainter = rememberVectorPainter(image = Icons.Default.LocationOn)
    val markerIconId = rememberIconImage(key = "location_marker", painter = locationPainter)

    val userLocationPainter = rememberVectorPainter(image = Icons.Default.Person)
    val userMarkerIconId = rememberIconImage(key = "user_location_marker", painter = userLocationPainter)

    // State to track if the map has already centered initially
    val hasCenteredMap = remember { mutableStateOf(false) }

    // LaunchedEffect to start location updates and select initial S2 cell
    LaunchedEffect(Unit) {
        LocationManager.checkUserLocation(context) { result ->
            val parts = result.split("\n").last().split(", ")
            if (parts.size == 2) {
                currentLat.doubleValue = parts[0].toDoubleOrNull() ?: LocationManager.targetLat
                currentLon.doubleValue = parts[1].toDoubleOrNull() ?: LocationManager.targetLon
            }
            val initialS2Cell = S2CellId.fromLatLng(S2LatLng.fromDegrees(currentLat.doubleValue, currentLon.doubleValue)).parent(S2_CELL_LEVEL)
            if (viewModel.selectedS2CellId.value == null) {
                viewModel.selectS2Cell(initialS2Cell)
            }
        }
    }

    // LaunchedEffect to perform initial flyTo only once when coordinates are updated and tab is MAP_VIEW
    LaunchedEffect(currentLat.doubleValue, currentLon.doubleValue, selectedTab) {
        // Only perform initial flyTo if we are on the MAP_VIEW tab, haven't centered yet,
        // and we have valid coordinates (not the default LocationManager.targetLat/Lon)
        if (selectedTab == ListingsTab.MAP_VIEW && !hasCenteredMap.value &&
            (currentLat.doubleValue != LocationManager.targetLat || currentLon.doubleValue != LocationManager.targetLon)) {
            val initialPoint = Point.fromLngLat(currentLon.doubleValue, currentLat.doubleValue)
            mapViewportState.flyTo(
                CameraOptions.Builder()
                    .center(initialPoint)
                    .zoom(14.0)
                    .build()
            )
            hasCenteredMap.value = true // Set the flag to true after initial centering
        }
    }

    // Reset hasCenteredMap when navigating away from MAP_VIEW
    LaunchedEffect(selectedTab) {
        if (selectedTab != ListingsTab.MAP_VIEW) {
            hasCenteredMap.value = false
        }
    }

    val combinedEngagedListingsRaw = remember(listingsUserIsFulfilling, listingsUserAcceptedButNotFulfilling) {
        (listingsUserIsFulfilling + listingsUserAcceptedButNotFulfilling)
            .distinctBy { it.id }
    }

    val sortedCombinedEngagedListings = remember(combinedEngagedListingsRaw) {
        combinedEngagedListingsRaw.sortedWith(compareBy<Listing> { listing ->
            when {
                listingsUserIsFulfilling.any { it.id == listing.id } -> 0
                listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> 1
                else -> 2
            }
        }.thenByDescending { it.timestamp?.toDate() })
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

    val sortedOwnedListings = remember(allOwnedListings) {
        allOwnedListings.sortedWith(compareBy<Listing> { listing ->
            when {
                listing.status == "fulfilled" -> 0
                listing.fulfilledBy?.isNotEmpty() == true && listing.status == "active" -> 1
                listing.acceptedBy.isNotEmpty() && listing.fulfilledBy.isNullOrEmpty() -> 2
                else -> 3
            }
        }.thenByDescending { it.timestamp?.toDate() })
    }

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
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ListingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.name.replace("_", " ")) }
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
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
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
                        if (overallLoadingEngaged && sortedCombinedEngagedListings.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (sortedCombinedEngagedListings.isEmpty()) {
                            item {
                                Text(
                                    text = "No active or pending engagements.",
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            items(sortedCombinedEngagedListings) { listing ->
                                val statusText = when {
                                    listingsUserIsFulfilling.any { it.id == listing.id } -> "ACTIVE"
                                    listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> "PENDING"
                                    else -> null
                                }
                                val statusColor = when {
                                    listingsUserIsFulfilling.any { it.id == listing.id } -> Color(0xFF338a4d)
                                    listingsUserAcceptedButNotFulfilling.any { it.id == listing.id } -> Color(0xFFb0aa0c)
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

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp),
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
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }

                        if (isLoading && filteredGeneralListings.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (filteredGeneralListings.isEmpty() && !isLoading) {
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
                                    displayMode = displayMode,
                                    status = null
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
                        item {
                            Text(
                                text = "Your Posted Listings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

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
                            items(sortedOwnedListings) { listing ->
                                val statusForUserListing: ListingStatus? = when {
                                    listing.status == "fulfilled" ->
                                        ListingStatus("COMPLETED", Color(0xFF6A1B9A))
                                    listing.fulfilledBy?.isNotEmpty() == true && listing.status == "active" ->
                                        ListingStatus("ACTIVE", Color(0xFF338a4d))
                                    listing.acceptedBy.isNotEmpty() && listing.fulfilledBy.isNullOrEmpty() ->
                                        ListingStatus("OFFER RECEIVED", Color(0xFFb0aa0c))
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

                // --- MAP_VIEW Tab Content ---
                ListingsTab.MAP_VIEW -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MapboxMap(
                            modifier = Modifier.fillMaxSize(),
                            mapViewportState = mapViewportState,
                            mapState = mapState,
                            content = {
                                // Observe camera state to continuously update the S2 cell at the map center
                                LaunchedEffect(mapViewportState.cameraState) {
                                    val cameraState = mapViewportState.cameraState
                                    if (cameraState != null) {
                                        val centerPoint = cameraState.center
                                        val newS2CellId = S2CellId.fromLatLng(S2LatLng.fromDegrees(centerPoint.latitude(), centerPoint.longitude())).parent(S2_CELL_LEVEL)
                                        if (newS2CellId != mapCenterS2CellId) {
                                            mapCenterS2CellId = newS2CellId
                                        }
                                    }
                                }

                                // Draw the actively selected S2 cell outline (using PolygonAnnotation composable)
                                activeS2CellId?.let { cellId ->
                                    val polygon = s2CellToPolygon(cellId)
                                    PolygonAnnotation(
                                        points = polygon.coordinates()
                                    ) {
                                        fillColor = Color.Blue.copy(alpha = 0.2f)
                                        fillOutlineColor = Color.Blue
                                    }
                                }

                                // If a different cell is currently at the map center (and not the active one), draw its temporary outline in red
                                if (mapCenterS2CellId != null && mapCenterS2CellId != activeS2CellId) {
                                    val tempPolygon = s2CellToPolygon(mapCenterS2CellId!!)
                                    PolygonAnnotation(
                                        points = tempPolygon.coordinates()
                                    ) {
                                        fillColor = Color.Red.copy(alpha = 0.1f)
                                        fillOutlineColor = Color.Red
                                    }
                                }

                                // Display marker for user's current location (Green icon)
                                PointAnnotation(
                                    point = Point.fromLngLat(currentLon.doubleValue, currentLat.doubleValue)
                                ) {
                                    iconImage = userMarkerIconId
                                    textField = "You are here"
                                    textOffset = listOf(0.0, -2.0)
                                    textAnchor = TextAnchor.TOP
                                    textColor = Color.Green
                                    textSize = 12.0
                                }

                                // Display markers for the listings (using PointAnnotation composable)
                                listings.forEach { listing ->
                                    if (listing.coord.size == 2) {
                                        val point = Point.fromLngLat(listing.coord[1], listing.coord[0])
                                        PointAnnotation(
                                            point = point
                                        ) {
                                            iconImage = markerIconId
                                            textField = listing.title
                                            textOffset = listOf(0.0, -2.0)
                                            textAnchor = TextAnchor.TOP
                                            textColor = Color.Black
                                            textSize = 12.0
                                            interactionsState = PointAnnotationInteractionsState().apply {
                                                isDraggable = false
                                                onClicked {
                                                    onNavigateToDetail(listing)
                                                    true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        // Existing "Select this area" button
                        FloatingActionButton(
                            onClick = { showSelectCellConfirmation = true },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Done, contentDescription = "Select this area")
                        }

                        // "My Location" button to jump to current location
                        FloatingActionButton(
                            onClick = {
                                val userLocationPoint = Point.fromLngLat(currentLon.doubleValue, currentLat.doubleValue)
                                mapViewportState.flyTo(
                                    CameraOptions.Builder()
                                        .center(userLocationPoint)
                                        .zoom(14.0)
                                        .build()
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 80.dp, end = 16.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = "Go to my location")
                        }
                    }
                }
                // --- End MAP_VIEW Tab Content ---
            }
        }
    }

    // Confirmation dialog for selecting a new S2 cell
    if (showSelectCellConfirmation) {
        AlertDialog(
            onDismissRequest = { showSelectCellConfirmation = false },
            title = { Text("Select This Area?") },
            text = { Text("Do you want to filter listings to the area outlined in red? This will refresh available listings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mapCenterS2CellId?.let {
                            viewModel.selectS2Cell(it)
                        }
                        showSelectCellConfirmation = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectCellConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Filter Sheet (your existing code) ---
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