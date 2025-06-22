// app/src/main/java/com/example/plshelp/android/data/ListingsViewModel.kt
package com.example.plshelp.android.data

import Listing // Correct import for Listing
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.Timestamp // Import Timestamp
import com.google.firebase.firestore.ListenerRegistration // Import ListenerRegistration

import java.util.concurrent.TimeUnit

class ListingsViewModel(private val currentOwnerId: String) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // --- Listener Registrations ---
    // Declare private variables to hold the ListenerRegistration for each query
    private var generalListingsListener: ListenerRegistration? = null
    private var listingsUserIsFulfillingListener: ListenerRegistration? = null
    private var listingsUserAcceptedButNotFulfillingListener: ListenerRegistration? = null
    private var listingsOwnedAndAcceptedOffersListener: ListenerRegistration? = null


    // --- General Listings (Active, not owned by current user) ---
    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings: StateFlow<List<Listing>> = _listings
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // --- NEW: Listings where current user is fulfilling (helper's active requests) ---
    private val _listingsUserIsFulfilling = MutableStateFlow<List<Listing>>(emptyList())
    val listingsUserIsFulfilling: StateFlow<List<Listing>> = _listingsUserIsFulfilling
    private val _isLoadingListingsUserIsFulfilling = MutableStateFlow(false)
    val isLoadingListingsUserIsFulfilling: StateFlow<Boolean> = _isLoadingListingsUserIsFulfilling

    // --- RENAMED: Listings where current user accepted but not yet fulfilling (helper's accepted offers awaiting confirmation) ---
    private val _listingsUserAcceptedButNotFulfilling = MutableStateFlow<List<Listing>>(emptyList())
    val listingsUserAcceptedButNotFulfilling: StateFlow<List<Listing>> = _listingsUserAcceptedButNotFulfilling
    private val _isLoadingListingsUserAcceptedButNotFulfilling = MutableStateFlow(false)
    val isLoadingListingsUserAcceptedButNotFulfilling: StateFlow<Boolean> = _isLoadingListingsUserAcceptedButNotFulfilling

    // --- RENAMED: Listings owned by current user where they've accepted others' offers (owner's accepted requests) ---
    // (This list is not used in the main ListingsScreen as per "owned by other people" rule, but kept for completeness)
    private val _listingsOwnedAndAcceptedOffers = MutableStateFlow<List<Listing>>(emptyList())
    val listingsOwnedAndAcceptedOffers: StateFlow<List<Listing>> = _listingsOwnedAndAcceptedOffers
    private val _isLoadingListingsOwnedAndAcceptedOffers = MutableStateFlow(false)
    val isLoadingListingsOwnedAndAcceptedOffers: StateFlow<Boolean> = _isLoadingListingsOwnedAndAcceptedOffers

    // --- Last Fetch Time for General Listings (for display) ---
    private val _lastFetchTime = mutableLongStateOf(0L)
    val lastFetchTimeState: State<Long> = _lastFetchTime
    private val refreshIntervalMillis = TimeUnit.MINUTES.toMillis(5)

    init {
        Log.d("ListingsViewModel", "VM_INIT: ViewModel initialized for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        // Initial fetch for all categories
        refreshListings()
        startPeriodicRefresh() // The periodic refresh should still run
    }

    // --- CRITICAL: Override onCleared() to remove listeners ---
    override fun onCleared() {
        super.onCleared()
        Log.d("ListingsViewModel", "VM_CLEARED: ViewModel cleared for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        generalListingsListener?.let {
            it.remove()
            Log.d("ListingsViewModel", "VM_CLEARED: Removed generalListingsListener.")
        } ?: Log.d("ListingsViewModel", "VM_CLEARED: generalListingsListener was null.")

        listingsUserIsFulfillingListener?.let {
            it.remove()
            Log.d("ListingsViewModel", "VM_CLEARED: Removed listingsUserIsFulfillingListener.")
        } ?: Log.d("ListingsViewModel", "VM_CLEARED: listingsUserIsFulfillingListener was null.")

        listingsUserAcceptedButNotFulfillingListener?.let {
            it.remove()
            Log.d("ListingsViewModel", "VM_CLEARED: Removed listingsUserAcceptedButNotFulfillingListener.")
        } ?: Log.d("ListingsViewModel", "VM_CLEARED: listingsUserAcceptedButNotFulfillingListener was null.")

        listingsOwnedAndAcceptedOffersListener?.let {
            it.remove()
            Log.d("ListingsViewModel", "VM_CLEARED: Removed listingsOwnedAndAcceptedOffersListener.")
        } ?: Log.d("ListingsViewModel", "VM_CLEARED: listingsOwnedAndAcceptedOffersListener was null.")
    }

    /**
     * Triggers a refresh for all listing categories.
     * This will now first remove existing listeners, then set up new ones.
     */
    fun refreshListings() {
        _lastFetchTime.longValue = System.currentTimeMillis() // Update last fetch time immediately
        Log.d("ListingsViewModel", "REFRESH: Refreshing all listings for owner ID: $currentOwnerId. Removing old listeners.")
        // Remove existing listeners before re-fetching/re-attaching to prevent duplicates
        generalListingsListener?.remove()
        listingsUserIsFulfillingListener?.remove()
        listingsUserAcceptedButNotFulfillingListener?.remove()
        listingsOwnedAndAcceptedOffersListener?.remove()

        fetchGeneralListings()
        fetchListingsUserIsFulfilling()
        fetchListingsUserAcceptedButNotFulfilling()
        fetchListingsOwnedAndAcceptedOffers()
    }

    /**
     * Call this when a new listing is created to refresh relevant sections.
     */
    fun onNewListingCreated() {
        Log.d("ListingsViewModel", "NEW_LISTING: New listing created, triggering full refresh for owner ID: $currentOwnerId")
        refreshListings()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(refreshIntervalMillis)
                // Only refresh if enough time has passed since the last *manual/triggered* fetch.
                // We'll use the _lastFetchTime for general listings as a proxy for overall refresh time.
                if (System.currentTimeMillis() - _lastFetchTime.longValue >= refreshIntervalMillis) {
                    Log.d("ListingsViewModel", "PERIODIC_REFRESH: Periodic refresh triggered for all categories for owner ID: $currentOwnerId")
                    refreshListings() // Call the main refresh function
                }
            }
        }
    }

    /**
     * Fetches general active listings that are not owned by the current user.
     */
    private fun fetchGeneralListings() {
        if (_isLoading.value) {
            Log.d("ListingsViewModel", "FETCH_GENERAL: Already loading, skipping fetchGeneralListings.")
            return // Prevent multiple concurrent fetches
        }

        _isLoading.value = true
        Log.d("ListingsViewModel", "FETCH_GENERAL: Fetching general listings for currentOwnerId: $currentOwnerId")

        // Remove existing listener before adding a new one (important if this function is called multiple times without VM recreation)
        generalListingsListener?.remove()
        Log.d("ListingsViewModel", "FETCH_GENERAL: Old generalListingsListener removed (if any).")

        generalListingsListener = db.collection("listings")
            .whereEqualTo("status", "active") // Filter for 'active' listings
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e -> // Using addSnapshotListener for real-time updates
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_GENERAL: Listen failed for general listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val allListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }

                    // Filter out listings owned by the current user AND listings that have a fulfilledBy value
                    val filteredListings = allListings.filter { listing ->
                        listing.ownerID != currentOwnerId && listing.fulfilledBy.isNullOrEmpty()
                    }
                    _listings.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_GENERAL: General listings fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_GENERAL: General listings snapshot is null for owner ID: $currentOwnerId.")
                    _listings.value = emptyList()
                }
                _isLoading.value = false
            }
    }

    /**
     * NEW: Fetches listings where the current user is in `fulfilledBy` and the listing is active,
     * and NOT owned by the current user.
     * This corresponds to "Active Requests (Your Offer Accepted)" in the UI.
     */
    private fun fetchListingsUserIsFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserIsFulfilling.value = emptyList()
            _isLoadingListingsUserIsFulfilling.value = false
            Log.d("ListingsViewModel", "FETCH_FULFILLING: Current user ID is blank, not fetching fulfilled-by listings.")
            return
        }

        _isLoadingListingsUserIsFulfilling.value = true
        Log.d("ListingsViewModel", "FETCH_FULFILLING: Fetching fulfilled-by listings for user: $currentOwnerId")

        // Remove existing listener before adding a new one
        listingsUserIsFulfillingListener?.remove()
        Log.d("ListingsViewModel", "FETCH_FULFILLING: Old listingsUserIsFulfillingListener removed (if any).")

        listingsUserIsFulfillingListener = db.collection("listings")
            .whereArrayContains("fulfilledBy", currentOwnerId)
            .whereEqualTo("status", "active") // Ensure it's still active
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_FULFILLING: Listen failed for fulfilled-by listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsUserIsFulfilling.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    // Final in-memory filter to ensure it's not owned by current user (Firestore doesn't allow != in queries without complex indexes)
                    val filteredListings = fetchedListings.filter { it.ownerID != currentOwnerId }
                    _listingsUserIsFulfilling.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_FULFILLING: Listings user is fulfilling fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_FULFILLING: Listings user is fulfilling snapshot is null for owner ID: $currentOwnerId.")
                    _listingsUserIsFulfilling.value = emptyList()
                }
                _isLoadingListingsUserIsFulfilling.value = false
            }
    }

    /**
     * RENAMED: Fetches listings where the current user has `acceptedBy` but is NOT yet `fulfilledBy`,
     * the status is active, and the listing is NOT owned by the current user.
     * This corresponds to "Accepted Requests (Awaiting Confirmation)" in the UI.
     */
    private fun fetchListingsUserAcceptedButNotFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserAcceptedButNotFulfilling.value = emptyList()
            _isLoadingListingsUserAcceptedButNotFulfilling.value = false
            Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Current user ID is blank, not fetching accepted but not fulfilling listings.")
            return
        }

        _isLoadingListingsUserAcceptedButNotFulfilling.value = true
        Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Fetching accepted-but-not-fulfilling listings for user: $currentOwnerId")

        // Remove existing listener before adding a new one
        listingsUserAcceptedButNotFulfillingListener?.remove()
        Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Old listingsUserAcceptedButNotFulfillingListener removed (if any).")

        listingsUserAcceptedButNotFulfillingListener = db.collection("listings")
            .whereArrayContains("acceptedBy", currentOwnerId)
            .whereEqualTo("status", "active") // Ensure it's still active
            .orderBy("timestamp", Query.Direction.DESCENDING) // Requires composite index if combined with whereArrayContains
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listen failed for accepted-but-not-fulfilling listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsUserAcceptedButNotFulfilling.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    // In-memory filter: current user is in acceptedBy, NOT in fulfilledBy, and NOT the owner
                    val filteredListings = fetchedListings.filter { listing ->
                        listing.acceptedBy.contains(currentOwnerId) && // User is in acceptedBy
                                !listing.fulfilledBy.orEmpty().contains(currentOwnerId) && // But not in fulfilledBy
                                listing.ownerID != currentOwnerId // And not owned by the current user
                    }
                    _listingsUserAcceptedButNotFulfilling.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listings user accepted but not fulfilling fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listings user accepted but not fulfilling snapshot is null for owner ID: $currentOwnerId.")
                    _listingsUserAcceptedButNotFulfilling.value = emptyList()
                }
                _isLoadingListingsUserAcceptedButNotFulfilling.value = false
            }
    }

    /**
     * RENAMED: Fetches listings where the current user is the OWNER and has accepted offers from others.
     * This list is *not* displayed in the main `ListingsScreen` as it's for listings owned by the user.
     */
    private fun fetchListingsOwnedAndAcceptedOffers() {
        if (currentOwnerId.isBlank()) {
            _listingsOwnedAndAcceptedOffers.value = emptyList()
            _isLoadingListingsOwnedAndAcceptedOffers.value = false
            Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Current user ID is blank, not fetching owned and accepted offers.")
            return
        }

        _isLoadingListingsOwnedAndAcceptedOffers.value = true
        Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Fetching owned and accepted offers for user: $currentOwnerId")

        // Remove existing listener before adding a new one
        listingsOwnedAndAcceptedOffersListener?.remove()
        Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Old listingsOwnedAndAcceptedOffersListener removed (if any).")

        listingsOwnedAndAcceptedOffersListener = db.collection("listings")
            .whereEqualTo("ownerID", currentOwnerId) // Listings owned by the current user
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Listen failed for owned and accepted offers for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsOwnedAndAcceptedOffers.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    // Filter in-memory to only include listings where acceptedBy is not empty
                    val filteredListings = fetchedListings.filter { it.acceptedBy.isNotEmpty() }
                    _listingsOwnedAndAcceptedOffers.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Owned and accepted offers fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Owned and accepted offers snapshot is null for owner ID: $currentOwnerId.")
                    _listingsOwnedAndAcceptedOffers.value = emptyList()
                }
                _isLoadingListingsOwnedAndAcceptedOffers.value = false
            }
    }

    /**
     * Helper function to parse a Firestore DocumentSnapshot into a Listing object.
     */
    private fun parseListingDocument(document: com.google.firebase.firestore.DocumentSnapshot): Listing? {
        // Manual parsing for each field
        val id = document.id
        val category = (document.get("category") as? List<String>)?.joinToString(", ") ?: ""
        val coordGeoPoint = document.get("coord") as? GeoPoint
        val coord = coordGeoPoint?.let { listOf(it.latitude, it.longitude) } ?: emptyList()
        val description = document.getString("description") ?: ""
        val imgURL = document.getString("imgURL")
        val ownerID = document.getString("ownerID") ?: ""
        val ownerName = document.getString("ownerName") ?: ""
        val price = document.getString("price") ?: ""
        val radius = document.getLong("radius") ?: 0L
        val title = document.getString("title") ?: ""
        val timestamp = document.getTimestamp("timestamp") // Keep Timestamp type
        val status = document.getString("status") ?: "active"

        val deliveryCoordGeoPoint = document.get("deliveryCoord") as? GeoPoint
        val deliveryCoord = deliveryCoordGeoPoint?.let { listOf(it.latitude, it.longitude) }

        // Safely cast to List<String>, defaulting to emptyList() if null or wrong type
        val acceptedBy = document.get("acceptedBy") as? List<String> ?: emptyList()
        val fulfilledBy = document.get("fulfilledBy") as? List<String> ?: emptyList() // Correctly parse as List<String>

        // Ensure Listing constructor matches the order and types
        return Listing(
            id = id,
            category = category,
            coord = coord,
            description = description,
            imgURL = imgURL,
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
    }


    // Factory to create ListingsViewModel with currentOwnerId
    class Factory(private val currentOwnerId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ListingsViewModel::class.java)) {
                Log.d("ListingsViewModel", "FACTORY: Creating new ListingsViewModel for ID: $currentOwnerId")
                return ListingsViewModel(currentOwnerId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}