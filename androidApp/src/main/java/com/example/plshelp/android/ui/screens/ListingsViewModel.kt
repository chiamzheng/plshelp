package com.example.plshelp.android.ui.screens

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
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

data class Listing(
    val id: String = "",
    val category: String = "",
    val coord: List<Double> = emptyList(),
    val description: String = "",
    val imgURL: String? = null,
    val ownerID: String = "",
    val price: String = "",
    val radius: Long = 0,
    val title: String = ""
)

class ListingsViewModel : ViewModel() {
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
                        description = document.getString("description") ?: "",
                        imgURL = document.getString("imgURL"),
                        ownerID = document.getString("ownerID") ?: "",
                        price = document.getString("price") ?: "",
                        radius = document.getLong("radius") ?: 0,
                        title = document.getString("title") ?: ""
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
}