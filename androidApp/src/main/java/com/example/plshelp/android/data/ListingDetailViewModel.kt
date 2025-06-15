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
import com.google.firebase.firestore.FieldValue // Import FieldValue for arrayUnion, arrayRemove
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp // Assuming this is correctly imported for your Listing data class

class ListingDetailViewModel(
    private val listingId: String,
    private val initialListing: Listing? = null
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
            listing = initialListing
            isLoading = false
            Log.d("ListingDetailViewModel", "Initialized with passed listing: ${initialListing.title}")
            // Consider adding a listener here if you want real-time updates for acceptedBy/fulfilledBy
            // For now, we'll only fetch if initialListing is null to avoid double fetching on first load
            // and assume direct UI updates via ViewModel functions are sufficient.
        } else {
            fetchListingDetails()
        }
    }

    private fun fetchListingDetails() {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            firestore.collection("listings").document(listingId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        // Safely retrieve data, providing defaults
                        val title = documentSnapshot.getString("title") ?: "N/A"
                        val subtitle = documentSnapshot.getString("subtitle") ?: "N/A"
                        val description = documentSnapshot.getString("description") ?: "N/A"
                        val price = documentSnapshot.getString("price") ?: "0.00"
                        val categoryList = documentSnapshot.get("category") as? List<String> ?: emptyList()
                        val category = categoryList.joinToString(", ")
                        val coordGeoPoint = documentSnapshot.get("coord") as? GeoPoint
                        val coord = coordGeoPoint?.let { listOf(it.latitude, it.longitude) } ?: emptyList()

                        val deliveryCoordGeoPoint = documentSnapshot.get("deliveryCoord") as? GeoPoint
                        val deliveryCoord = deliveryCoordGeoPoint?.let { listOf(it.latitude, it.longitude) }

                        val radius = documentSnapshot.getLong("radius") ?: 0L
                        val ownerID = documentSnapshot.getString("ownerID") ?: "N/A"
                        val ownerName = documentSnapshot.getString("ownerName") ?: "Anonymous"
                        val timestamp = documentSnapshot.getTimestamp("timestamp")
                        val status = documentSnapshot.getString("status") ?: "active"

                        val acceptedBy = documentSnapshot.get("acceptedBy") as? List<String> ?: emptyList()
                        val fulfilledBy = documentSnapshot.getString("fulfilledBy")

                        listing = Listing(
                            id = documentSnapshot.id,
                            category = category,
                            coord = coord,
                            subtitle = subtitle,
                            description = description,
                            ownerID = ownerID,
                            ownerName = ownerName,
                            price = price,
                            radius = radius,
                            title = title,
                            timestamp = timestamp,
                            status = status,
                            deliveryCoord = deliveryCoord,
                            acceptedBy = acceptedBy,
                            fulfilledBy = fulfilledBy
                        )
                        Log.d("ListingDetailViewModel", "Fetched listing from Firestore: ${listing?.title}, Accepted by: ${listing?.acceptedBy?.size}, Fulfilled by: ${listing?.fulfilledBy}")
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

    fun acceptRequest(userId: String) {
        if (listing == null) {
            errorMessage = "Listing data not available."
            return
        }
        if (isLoading) {
            Log.d("ListingDetailViewModel", "Operation in progress, cannot accept.")
            return
        }

        val currentListing = listing!! // Assert non-null after check
        if (userId == currentListing.ownerID) {
            errorMessage = "You cannot offer to help your own request."
            return
        }
        if (currentListing.acceptedBy.contains(userId)) {
            errorMessage = "You have already offered to help for this request."
            return
        }
        if (currentListing.fulfilledBy != null) {
            errorMessage = "This request has already been fulfilled."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update("acceptedBy", FieldValue.arrayUnion(userId))
                    .await()
                // Update the local listing state immediately for UI responsiveness
                listing = currentListing.copy(acceptedBy = currentListing.acceptedBy + userId)
                errorMessage = null
                Log.d("ListingDetailViewModel", "Request $listingId accepted by $userId")
            } catch (e: Exception) {
                errorMessage = "Failed to offer help: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error offering help for request $listingId by $userId", e)
            } finally {
                isLoading = false
            }
        }
    }

    // --- NEW FUNCTION: To unaccept/withdraw an offer ---
    fun unacceptRequest(userId: String) {
        if (listing == null) {
            errorMessage = "Listing data not available."
            return
        }
        if (isLoading) {
            Log.d("ListingDetailViewModel", "Operation in progress, cannot unaccept.")
            return
        }

        val currentListing = listing!!
        if (!currentListing.acceptedBy.contains(userId)) {
            errorMessage = "You have not offered to help for this request."
            return
        }
        if (currentListing.fulfilledBy != null) {
            errorMessage = "This request has already been fulfilled."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update("acceptedBy", FieldValue.arrayRemove(userId)) // Atomically remove UID from array
                    .await()
                // Update the local listing state immediately
                listing = currentListing.copy(acceptedBy = currentListing.acceptedBy - userId)
                errorMessage = null
                Log.d("ListingDetailViewModel", "Request $listingId unaccepted by $userId")
            } catch (e: Exception) {
                errorMessage = "Failed to withdraw offer: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error withdrawing offer for request $listingId by $userId", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun fulfillRequest(acceptorId: String) {
        if (listing == null) {
            errorMessage = "Listing data not available."
            return
        }
        if (isLoading) {
            Log.d("ListingDetailViewModel", "Operation in progress, cannot fulfill.")
            return
        }

        val currentListing = listing!!
        if (currentListing.fulfilledBy != null) {
            errorMessage = "This request has already been fulfilled."
            return
        }
        // Ensure the acceptorId is actually in the acceptedBy list, if you want to enforce this
        if (!currentListing.acceptedBy.contains(acceptorId)) {
            errorMessage = "The selected user has not offered to help for this request."
            return
        }


        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update(
                        mapOf(
                            "fulfilledBy" to acceptorId,
                            "status" to "fulfilled"
                        )
                    )
                    .await()
                listing = currentListing.copy(fulfilledBy = acceptorId, status = "fulfilled")
                errorMessage = null
                Log.d("ListingDetailViewModel", "Request $listingId fulfilled by $acceptorId")
            } catch (e: Exception) {
                errorMessage = "Failed to fulfill request: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error fulfilling request $listingId by $acceptorId", e)
            } finally {
                isLoading = false
            }
        }
    }

    class Factory(
        private val listingId: String,
        private val initialListing: Listing?
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