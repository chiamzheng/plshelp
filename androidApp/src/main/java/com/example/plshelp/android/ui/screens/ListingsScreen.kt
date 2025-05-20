package com.example.plshelp.android.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.data.LocationManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.location.Location
import androidx.compose.runtime.LaunchedEffect
import com.example.plshelp.android.ui.components.CategoryChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(onNavigateToDetail: (String) -> Unit) {
    val viewModel: ListingsViewModel = viewModel()
    val listings by viewModel.listings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastFetchTime by viewModel.lastFetchTimeState
    val context = LocalContext.current

    // Replace with your actual current location retrieval logic
    val currentLat = remember { mutableStateOf(LocationManager.targetLat) } // Placeholder
    val currentLon = remember { mutableStateOf(LocationManager.targetLon) } // Placeholder

    // You'll need to update currentLat and currentLon based on actual location updates
    LaunchedEffect(Unit) {
        LocationManager.checkUserLocation(context) { result ->
            // You might want to parse the latitude and longitude from the result
            // and update currentLat.value and currentLon.value
            // This is a basic example; adapt to your actual location update mechanism
            val parts = result.split("\n").last().split(", ")
            if (parts.size == 2) {
                currentLat.value = parts[0].toDoubleOrNull() ?: LocationManager.targetLat
                currentLon.value = parts[1].toDoubleOrNull() ?: LocationManager.targetLon
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
                                timeDifferenceSeconds < 60 -> "${timeDifferenceSeconds} secs ago"
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
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (listings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No listings available.", fontSize = 18.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(listings) { listing ->
                            ExpandableListingCard(
                                listing = listing,
                                onNavigateToDetail = onNavigateToDetail,
                                currentLat = currentLat.value,
                                currentLon = currentLon.value
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableListingCard(
    listing: Listing,
    onNavigateToDetail: (String) -> Unit,
    currentLat: Double,
    currentLon: Double
) {
    var isExpanded by remember { mutableStateOf(false) }
    val distance = remember(listing.coord, currentLat, currentLon) {
        if (listing.coord.size == 2) {
            val listingLat = listing.coord[0]
            val listingLon = listing.coord[1]
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon, listingLat, listingLon, results)
            val distanceInKm = results[0] / 1000
            String.format("%.2f km", distanceInKm)
        } else {
            "N/A"
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
                Text(text = distance, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Expand")
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
                Text(text = " ${listing.description}", fontSize = 14.sp)
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
                    Button(onClick = { onNavigateToDetail(listing.id) }) {
                        Text("View Details")
                    }
                }
            }
        }
    }
}