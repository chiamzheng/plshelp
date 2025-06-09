// app/src/main/java/com/example/plshelp/android/data/ListingsViewModel.kt
package com.example.plshelp.android.data

import Listing // Correct import for Listing
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider // Import for ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ListingsViewModel(private val currentOwnerId: String) : ViewModel() {
    private val _listings = MutableStateFlow<SnapshotStateList<Listing>>(mutableStateListOf())
    val listings: StateFlow<SnapshotStateList<Listing>> = _listings
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _lastUpdated = MutableStateFlow<String?>(null)
    private val _lastFetchTime = mutableLongStateOf(0L)
    val lastFetchTimeState: State<Long> = _lastFetchTime
    private val refreshIntervalMillis = TimeUnit.MINUTES.toMillis(5)

    init {
        fetchListings()
        startPeriodicRefresh()
    }

    fun refreshListings() {
        fetchListings()
    }

    fun onNewListingCreated() {
        fetchListings()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(refreshIntervalMillis)
                if (System.currentTimeMillis() - _lastFetchTime.longValue >= refreshIntervalMillis) {
                    Log.d("ListingsViewModel", "Periodic refresh triggered")
                    fetchListings()
                }
            }
        }
    }

    private fun fetchListings() {
        if (_isLoading.value) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("listings")
                    .whereEqualTo("status", "active") // <--- Filter for 'active' listings
                    .whereNotEqualTo("ownerID", currentOwnerId) // <--- Filter out current user's listings
                    .get()
                    .await()

                Log.d("ListingsViewModel", "Number of documents fetched: ${snapshot.size()}")
                val fetchedListings = snapshot.documents.map { document ->
                    val geoPoint = document.get("coord") as? GeoPoint
                    val coordinates = if (geoPoint != null) {
                        listOf(geoPoint.latitude, geoPoint.longitude)
                    } else {
                        emptyList()
                    }

                    Listing(
                        id = document.id,
                        category = (document.get("category") as? List<String>)?.joinToString(", ") ?: "",
                        coord = coordinates,
                        subtitle = document.getString("subtitle") ?: "",
                        description = document.getString("description") ?: "",
                        imgURL = document.getString("imgURL"),
                        ownerID = document.getString("ownerID") ?: "",
                        ownerName = document.getString("ownerName") ?: "", // Make sure ownerName is retrieved
                        price = document.getString("price") ?: "",
                        radius = document.getLong("radius") ?: 0,
                        title = document.getString("title") ?: "",
                        timestamp = document.getTimestamp("timestamp"),
                        status = document.getString("status") ?: "active" // <--- Retrieve status
                    )
                }
                _listings.value.clear()
                _listings.value.addAll(fetchedListings)
                _lastFetchTime.longValue = System.currentTimeMillis()
                _lastUpdated.value = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(_lastFetchTime.longValue))
                Log.d("ListingsViewModel", "Number of listings in ViewModel: ${_listings.value.size}")
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("ListingsViewModel", "Error fetching listings: $e")
                _isLoading.value = false
                // Handle error
            }
        }
    }

    // Factory to create ListingsViewModel with currentOwnerId
    class Factory(private val currentOwnerId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListingsViewModel::class.java)) {
                return ListingsViewModel(currentOwnerId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}