// app/src/main/java/com/example/plshelp/android/data/ListingDetailViewModel.kt
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
import com.google.firebase.firestore.FieldValue // Import FieldValue for arrayUnion
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
            // Even if initial listing is passed, we might want to fetch fresh details
            // to ensure `acceptedBy` and `fulfilledBy` are up-to-date.
            // For now, let's keep it simple and only fetch if initialListing is null.
            // If you need real-time updates for these fields, consider an onSnapshot listener.
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

                        // --- NEW: Retrieve acceptedBy and fulfilledBy ---
                        val acceptedBy = documentSnapshot.get("acceptedBy") as? List<String> ?: emptyList()
                        val fulfilledBy = documentSnapshot.getString("fulfilledBy")
                        // --- END NEW ---

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
                            acceptedBy = acceptedBy, // Assign the retrieved list
                            fulfilledBy = fulfilledBy // Assign the retrieved string
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

    // --- NEW FUNCTION: To accept a request ---
    fun acceptRequest(userId: String) {
        if (listing == null) {
            errorMessage = "Listing data not available."
            return
        }
        if (isLoading) {
            Log.d("ListingDetailViewModel", "Still loading, cannot accept.")
            return
        }

        // Prevent accepting if already accepted or if it's the owner's own listing
        if (userId == listing!!.ownerID) {
            errorMessage = "You cannot accept your own request."
            return
        }
        if (listing!!.acceptedBy.contains(userId)) {
            errorMessage = "You have already accepted this request."
            return
        }
        if (listing!!.fulfilledBy != null) {
            errorMessage = "This request has already been fulfilled."
            return
        }

        isLoading = true // Indicate that an operation is in progress
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update("acceptedBy", FieldValue.arrayUnion(userId)) // Atomically add UID to array
                    .await()
                // Update the local listing state immediately for UI responsiveness
                listing = listing?.copy(acceptedBy = listing!!.acceptedBy + userId)
                errorMessage = null // Clear any previous error
                Log.d("ListingDetailViewModel", "Request $listingId accepted by $userId")
            } catch (e: Exception) {
                errorMessage = "Failed to accept request: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error accepting request $listingId by $userId", e)
            } finally {
                isLoading = false // Reset loading state
            }
        }
    }

    // --- NEW FUNCTION: To fulfill a request with a specific acceptor (ONLY FOR OWNER) ---
    fun fulfillRequest(acceptorId: String) {
        if (listing == null) {
            errorMessage = "Listing data not available."
            return
        }
        if (isLoading) {
            Log.d("ListingDetailViewModel", "Still loading, cannot fulfill.")
            return
        }
        if (listing!!.fulfilledBy != null) {
            errorMessage = "This request has already been fulfilled."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update(
                        mapOf(
                            "fulfilledBy" to acceptorId,
                            "status" to "fulfilled" // Optionally change status to fulfilled
                        )
                    )
                    .await()
                // Update local state
                listing = listing?.copy(fulfilledBy = acceptorId, status = "fulfilled")
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


    // Factory to create ViewModel with listingId and optional initialListing
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