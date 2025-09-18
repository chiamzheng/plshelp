package com.example.plshelp.android.data

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import Listing // Assuming Listing is in the root of your 'android' module, adjust if different
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import com.google.firebase.functions.functions

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
            listenForListingUpdates()
        } else {
            fetchListingDetails()
        }
    }

    private fun listenForListingUpdates() {
        firestore.collection("listings").document(listingId)
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    errorMessage = "Error listening for listing updates: ${e.localizedMessage}"
                    isLoading = false
                    Log.e("ListingDetailViewModel", "Error listening for listing updates for ID $listingId", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val title = documentSnapshot.getString("title") ?: "N/A"
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
                    val fulfilledBy = documentSnapshot.get("fulfilledBy") as? List<String> ?: emptyList()

                    // --- CORRECTED: Fetching with "imageURL" ---
                    val imageUrl = documentSnapshot.getString("imageUrl")

                    listing = Listing(
                        id = documentSnapshot.id,
                        category = category,
                        coord = coord,
                        description = description,
                        imageUrl = imageUrl, // Pass the fetched imgURL
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
                    errorMessage = null
                    isLoading = false
                    Log.d("ListingDetailViewModel", "Listing updated from Firestore listener: ${listing?.title}, Status: ${listing?.status}, Accepted by: ${listing?.acceptedBy?.size}, Fulfilled by: ${listing?.fulfilledBy?.joinToString()}, Image URL: ${listing?.imageUrl}")
                } else {
                    errorMessage = "Listing not found or no longer exists."
                    isLoading = false
                    Log.w("ListingDetailViewModel", "Listing with ID $listingId not found or no longer exists in listener.")
                }
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
                        val fulfilledBy = documentSnapshot.get("fulfilledBy") as? List<String> ?: emptyList()

                        // --- CORRECTED: Fetching with "imageURL" ---
                        val imageUrl = documentSnapshot.getString("imageUrl")

                        listing = Listing(
                            id = documentSnapshot.id,
                            category = category,
                            coord = coord,
                            description = description,
                            imageUrl = imageUrl, // Pass the fetched imgURL
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
                        Log.d("ListingDetailViewModel", "Fetched listing from Firestore: ${listing?.title}, Status: ${listing?.status}, Accepted by: ${listing?.acceptedBy?.size}, Fulfilled by: ${listing?.fulfilledBy?.joinToString()}, Image URL: ${listing?.imageUrl}")
                        listenForListingUpdates()
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

        val currentListing = listing!!
        if (currentListing.status != "active") {
            errorMessage = "This request is no longer active."
            return
        }
        if (!currentListing.fulfilledBy.isNullOrEmpty()) {
            errorMessage = "This request has already been fulfilled by another party."
            return
        }
        if (userId == currentListing.ownerID) {
            errorMessage = "You cannot offer to help your own request."
            return
        }
        if (currentListing.acceptedBy.contains(userId)) {
            errorMessage = "You have already offered to help for this request."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update("acceptedBy", FieldValue.arrayUnion(userId))
                    .await()
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
        if (currentListing.status != "active") {
            errorMessage = "This request is no longer active."
            return
        }
        if (!currentListing.fulfilledBy.isNullOrEmpty()) {
            errorMessage = "This request has already been fulfilled by another party."
            return
        }
        if (!currentListing.acceptedBy.contains(userId)) {
            errorMessage = "You have not offered to help for this request."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update("acceptedBy", FieldValue.arrayRemove(userId))
                    .await()
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
        if (currentListing.status != "active") {
            errorMessage = "This request is no longer active."
            return
        }
        if (currentListing.fulfilledBy?.contains(acceptorId) == true) {
            errorMessage = "This user has already been selected to fulfill the request."
            return
        }
        if (!currentListing.acceptedBy.contains(acceptorId)) {
            errorMessage = "The selected user has not offered to help for this request."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                firestore.collection("listings").document(listingId)
                    .update(
                        "fulfilledBy", FieldValue.arrayUnion(acceptorId)
                    )
                    .await()
                errorMessage = null
                Log.d("ListingDetailViewModel", "Request $listingId assigned to $acceptorId (fulfilledBy field updated)")
            } catch (e: Exception) {
                errorMessage = "Failed to assign fulfiller: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error assigning fulfiller for request $listingId by $acceptorId", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun markRequestAsCompleted(currentUserId: String?) {
        val fulfillerId = listing?.fulfilledBy?.firstOrNull()
        if (listing == null || currentUserId == null || listing!!.ownerID != currentUserId || fulfillerId == null) {
            errorMessage = "Cannot mark as completed."
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                // Call Cloud Function
                Firebase.functions
                    .getHttpsCallable("completeListing")
                    .call(mapOf("listingId" to listingId, "fulfillerId" to fulfillerId))
                    .await()
                errorMessage = null
                Log.d("ListingDetailViewModel", "Request $listingId marked as completed via Cloud Function.")
            } catch (e: Exception) {
                errorMessage = "Failed to mark as completed: ${e.localizedMessage}"
                Log.e("ListingDetailViewModel", "Error marking request $listingId as completed", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun getUserName(uid: String, onNameFetched: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                onNameFetched(userDoc.getString("name") ?: "Unknown User")
            } catch (e: Exception) {
                Log.e("ListingDetailViewModel", "Error fetching user name for $uid: ${e.message}", e)
                onNameFetched("Unknown User")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No explicit listener removal here as addSnapshotListener is active and typically tied to ViewModel lifecycle
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