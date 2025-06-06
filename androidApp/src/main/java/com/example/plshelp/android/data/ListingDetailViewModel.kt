package com.example.plshelp.android.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import Listing
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch

class ListingDetailViewModel(
    private val listingId: String,
    private val initialListing: Listing? = null // New: optional initial listing
) : ViewModel() {
    var listing: Listing? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(true)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    private val firestore = FirebaseFirestore.getInstance()

    init {
        if (initialListing != null) {
            // If an initial listing is provided, use it directly
            listing = initialListing
            isLoading = false
            Log.d("ListingDetailViewModel", "Initialized with passed listing: ${initialListing.title}")
        } else {
            // Otherwise, fetch details from Firestore using the ID
            fetchListingDetails()
        }
    }

    private fun fetchListingDetails() {
        isLoading = true
        errorMessage = null
        viewModelScope.launch { // Use viewModelScope for coroutine management
            firestore.collection("listings").document(listingId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val title = documentSnapshot.getString("title") ?: "N/A"
                        val subtitle = documentSnapshot.getString("subtitle") ?: "N/A"
                        val description = documentSnapshot.getString("description") ?: "N/A"
                        val price = documentSnapshot.getString("price") ?: "0.00"
                        val categoryList = documentSnapshot.get("category") as? List<String> ?: emptyList()
                        val category = categoryList.joinToString(", ")
                        val coordGeoPoint = documentSnapshot.get("coord") as? GeoPoint
                        val coord = coordGeoPoint?.let { listOf(it.latitude, it.longitude) } ?: emptyList()
                        val radius = documentSnapshot.getLong("radius") ?: 0L
                        val ownerID = documentSnapshot.getString("ownerID") ?: "N/A"
                        val ownerName = documentSnapshot.getString("ownerName") ?: "Anonymous"
                        val timestamp = documentSnapshot.getTimestamp("timestamp")
                        //val imgURL = documentSnapshot.getString("imgURL") // Make sure to retrieve imgURL
                        listing = Listing( //THIS IS ORDERED, BE CAREFUL TO MATCH ACCORDINGLY
                            id = documentSnapshot.id,
                            category = category, // The 'category' variable you retrieved earlier
                            coord = coord,       // The 'coord' variable you prepared
                            subtitle = subtitle, // The 'subtitle' variable you retrieved
                            description = description, // The 'description' variable you retrieved
                            ownerID = ownerID,   // The 'ownerID' variable
                            ownerName = ownerName, // The 'ownerName' variable
                            price = price,       // The 'price' variable
                            radius = radius,     // The 'radius' variable
                            title = title,        // The 'title' variable
                            timestamp = timestamp
                        )
                        Log.d("ListingDetailViewModel", "Fetched listing from Firestore: ${listing?.title}")
                    } else {
                        errorMessage = "Listing not found."
                        Log.w("ListingDetailViewModel", "Listing with ID $listingId not found.")
                    }
                    isLoading = false
                }
                .addOnFailureListener { e ->
                    errorMessage = "Error fetching listing: ${e.localizedMessage}"
                    isLoading = false
                    Log.e("ListingDetailViewModel", "Error fetching listing with ID $listingId", e)
                }
        }
    }

    // Factory to create ViewModel with listingId and optional initialListing
    class Factory(
        private val listingId: String,
        private val initialListing: Listing? // New: optional initial listing for the factory
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListingDetailViewModel::class.java)) {
                return ListingDetailViewModel(listingId, initialListing) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}