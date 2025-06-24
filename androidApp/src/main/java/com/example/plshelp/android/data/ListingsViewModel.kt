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
    private var listingsOwnedByCurrentUserListener: ListenerRegistration? = null // NEW Listener

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

    // --- NEW: All Listings owned by the current user (for "Your Listings" tab) ---
    private val _listingsOwnedByCurrentUser = MutableStateFlow<List<Listing>>(emptyList())
    val listingsOwnedByCurrentUser: StateFlow<List<Listing>> = _listingsOwnedByCurrentUser
    private val _isLoadingListingsOwnedByCurrentUser = MutableStateFlow(false)
    val isLoadingListingsOwnedByCurrentUser: StateFlow<Boolean> = _isLoadingListingsOwnedByCurrentUser


    // --- Last Fetch Time for General Listings (for display) ---
    private val _lastFetchTime = mutableLongStateOf(0L)
    val lastFetchTimeState: State<Long> = _lastFetchTime
    private val refreshIntervalMillis = TimeUnit.MINUTES.toMillis(5)

    init {
        Log.d("ListingsViewModel", "VM_INIT: ViewModel initialized for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        refreshListings() // Initial fetch for all categories
        startPeriodicRefresh()
    }

    // --- CRITICAL: Override onCleared() to remove listeners ---
    override fun onCleared() {
        super.onCleared()
        Log.d("ListingsViewModel", "VM_CLEARED: ViewModel cleared for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        generalListingsListener?.remove()
        listingsUserIsFulfillingListener?.remove()
        listingsUserAcceptedButNotFulfillingListener?.remove()
        listingsOwnedAndAcceptedOffersListener?.remove()
        listingsOwnedByCurrentUserListener?.remove() // REMOVE NEW Listener
        Log.d("ListingsViewModel", "VM_CLEARED: All listeners removed.")
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
        listingsOwnedByCurrentUserListener?.remove() // Remove NEW Listener

        // Reset all loading states to false before starting new fetches
        // This prevents the "Already loading, skipping" log.
        _isLoading.value = false
        _isLoadingListingsUserIsFulfilling.value = false
        _isLoadingListingsUserAcceptedButNotFulfilling.value = false
        _isLoadingListingsOwnedAndAcceptedOffers.value = false
        _isLoadingListingsOwnedByCurrentUser.value = false

        fetchGeneralListings()
        fetchListingsUserIsFulfilling()
        fetchListingsUserAcceptedButNotFulfilling()
        fetchListingsOwnedAndAcceptedOffers() // This remains for its specific filtering
        fetchAllListingsOwnedByCurrentUser() // NEW: Fetch all owned listings
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
                // The condition System.currentTimeMillis() - _lastFetchTime.longValue >= refreshIntervalMillis
                // ensures we don't refresh if it was just refreshed by another trigger (e.g. onNewListingCreated)
                if (System.currentTimeMillis() - _lastFetchTime.longValue >= refreshIntervalMillis) {
                    Log.d("ListingsViewModel", "PERIODIC_REFRESH: Periodic refresh triggered for all categories for owner ID: $currentOwnerId")
                    refreshListings()
                }
            }
        }
    }

    /**
     * Fetches general active listings that are not owned by the current user and not yet fulfilled.
     * The `fulfilledBy.isEmpty()` check is done client-side using `isNullOrEmpty()`.
     */
    private fun fetchGeneralListings() {
        // REMOVED: if (_isLoading.value) { Log.d(...); return; }
        _isLoading.value = true // Set loading state immediately upon starting fetch
        Log.d("ListingsViewModel", "FETCH_GENERAL_START: Fetching general listings for currentOwnerId: '$currentOwnerId'")

        generalListingsListener?.remove() // Ensure old listener is removed

        generalListingsListener = db.collection("listings")
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_GENERAL_ERROR: Listen failed for general listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoading.value = false // Ensure loading state is reset on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("ListingsViewModel", "FETCH_GENERAL_SNAPSHOT: Raw document count: ${snapshot.documents.size} for owner ID: $currentOwnerId")
                    val allListings = snapshot.documents.mapNotNull { document ->
                        val listing = parseListingDocument(document)
                        if (listing == null) {
                            Log.w("ListingsViewModel", "FETCH_GENERAL_PARSE_FAIL: Failed to parse document: ${document.id}")
                        }
                        listing
                    }
                    Log.d("ListingsViewModel", "FETCH_GENERAL_PARSED: Parsed listing count: ${allListings.size}")

                    // FIX: Use isNullOrEmpty() for the most robust null/empty check
                    val filteredListings = allListings.filter { listing ->
                        val isNotOwned = listing.ownerID != currentOwnerId
                        val isNotFulfilled = listing.fulfilledBy.isNullOrEmpty()
                        Log.d("ListingsViewModel", "FETCH_GENERAL_FILTER_ITEM: ID: ${listing.id}, Title: '${listing.title}', OwnerID: '${listing.ownerID}', FulfilledBy: ${listing.fulfilledBy}, IsNotOwned: $isNotOwned, IsNotFulfilled: $isNotFulfilled")

                        isNotOwned && isNotFulfilled
                    }
                    _listings.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_GENERAL_COMPLETE: General listings fetched for owner ID: $currentOwnerId. Final Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_GENERAL_NULL: General listings snapshot is null for owner ID: $currentOwnerId.")
                    _listings.value = emptyList()
                }
                _isLoading.value = false // Ensure loading state is reset on success
            }
    }

    /**
     * Fetches listings where the current user is in `fulfilledBy` and the listing is active,
     * and NOT owned by the current user. (Helper's actively fulfilling requests)
     */
    private fun fetchListingsUserIsFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserIsFulfilling.value = emptyList()
            _isLoadingListingsUserIsFulfilling.value = false // Ensure reset for this specific case
            Log.d("ListingsViewModel", "FETCH_FULFILLING: Current user ID is blank, not fetching fulfilled-by listings.")
            return
        }

        // REMOVED: if (_isLoadingListingsUserIsFulfilling.value) { Log.d(...); return; }
        _isLoadingListingsUserIsFulfilling.value = true // Set loading state immediately
        Log.d("ListingsViewModel", "FETCH_FULFILLING: Fetching fulfilled-by listings for user: $currentOwnerId")

        listingsUserIsFulfillingListener?.remove()

        listingsUserIsFulfillingListener = db.collection("listings")
            .whereArrayContains("fulfilledBy", currentOwnerId)
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_FULFILLING: Listen failed for fulfilled-by listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsUserIsFulfilling.value = false // Ensure reset on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    val filteredListings = fetchedListings.filter { it.ownerID != currentOwnerId }
                    _listingsUserIsFulfilling.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_FULFILLING: Listings user is fulfilling fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_FULFILLING: Listings user is fulfilling snapshot is null for owner ID: $currentOwnerId.")
                    _listingsUserIsFulfilling.value = emptyList()
                }
                _isLoadingListingsUserIsFulfilling.value = false // Ensure reset on success
            }
    }

    /**
     * Fetches listings where the current user has `acceptedBy` but is NOT yet `fulfilledBy`,
     * the status is active, and the listing is NOT owned by the current user. (Helper's accepted offers awaiting confirmation)
     */
    private fun fetchListingsUserAcceptedButNotFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserAcceptedButNotFulfilling.value = emptyList()
            _isLoadingListingsUserAcceptedButNotFulfilling.value = false // Ensure reset for this specific case
            Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Current user ID is blank, not fetching accepted but not fulfilling listings.")
            return
        }

        // REMOVED: if (_isLoadingListingsUserAcceptedButNotFulfilling.value) { Log.d(...); return; }
        _isLoadingListingsUserAcceptedButNotFulfilling.value = true // Set loading state immediately
        Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Fetching accepted-but-not-fulfilling listings for user: $currentOwnerId")

        listingsUserAcceptedButNotFulfillingListener?.remove()

        listingsUserAcceptedButNotFulfillingListener = db.collection("listings")
            .whereArrayContains("acceptedBy", currentOwnerId)
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listen failed for accepted-but-not-fulfilling listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsUserAcceptedButNotFulfilling.value = false // Ensure reset on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    // FIX: Use isNullOrEmpty() for the most robust null/empty check
                    val filteredListings = fetchedListings.filter { listing ->
                        listing.acceptedBy.contains(currentOwnerId) &&
                                listing.fulfilledBy.isNullOrEmpty() &&
                                listing.ownerID != currentOwnerId
                    }
                    _listingsUserAcceptedButNotFulfilling.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listings user accepted but not fulfilling fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_ACCEPTED_NOT_FULFILLING: Listings user accepted but not fulfilling snapshot is null for owner ID: $currentOwnerId.")
                    _listingsUserAcceptedButNotFulfilling.value = emptyList()
                }
                _isLoadingListingsUserAcceptedButNotFulfilling.value = false // Ensure reset on success
            }
    }

    /**
     * Fetches listings where the current user is the OWNER and has accepted offers from others.
     * This list is a subset of all owned listings, useful for specific UI elements.
     */
    private fun fetchListingsOwnedAndAcceptedOffers() {
        if (currentOwnerId.isBlank()) {
            _listingsOwnedAndAcceptedOffers.value = emptyList()
            _isLoadingListingsOwnedAndAcceptedOffers.value = false // Ensure reset for this specific case
            Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Current user ID is blank, not fetching owned and accepted offers.")
            return
        }

        // REMOVED: if (_isLoadingListingsOwnedAndAcceptedOffers.value) { Log.d(...); return; }
        _isLoadingListingsOwnedAndAcceptedOffers.value = true // Set loading state immediately
        Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Fetching owned and accepted offers for user: $currentOwnerId")

        listingsOwnedAndAcceptedOffersListener?.remove()

        listingsOwnedAndAcceptedOffersListener = db.collection("listings")
            .whereEqualTo("ownerID", currentOwnerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Listen failed for owned and accepted offers for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsOwnedAndAcceptedOffers.value = false // Ensure reset on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    val filteredListings = fetchedListings.filter { it.acceptedBy.isNotEmpty() }
                    _listingsOwnedAndAcceptedOffers.value = filteredListings
                    Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Owned and accepted offers fetched for owner ID: $currentOwnerId. Count: ${filteredListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_OWNED_ACCEPTED: Owned and accepted offers snapshot is null for owner ID: $currentOwnerId.")
                    _listingsOwnedAndAcceptedOffers.value = emptyList()
                }
                _isLoadingListingsOwnedAndAcceptedOffers.value = false // Ensure reset on success
            }
    }

    /**
     * NEW: Fetches ALL listings owned by the current user, regardless of status or offers.
     * This is the primary source for the "Your Listings" tab.
     */
    private fun fetchAllListingsOwnedByCurrentUser() {
        if (currentOwnerId.isBlank()) {
            _listingsOwnedByCurrentUser.value = emptyList()
            _isLoadingListingsOwnedByCurrentUser.value = false // Ensure reset for this specific case
            Log.d("ListingsViewModel", "FETCH_OWNED_ALL: Current user ID is blank, not fetching all owned listings.")
            return
        }

        // REMOVED: if (_isLoadingListingsOwnedByCurrentUser.value) { Log.d(...); return; }
        _isLoadingListingsOwnedByCurrentUser.value = true // Set loading state immediately
        Log.d("ListingsViewModel", "FETCH_OWNED_ALL: Fetching all owned listings for user: $currentOwnerId")

        listingsOwnedByCurrentUserListener?.remove() // Ensure old listener is removed

        listingsOwnedByCurrentUserListener = db.collection("listings")
            .whereEqualTo("ownerID", currentOwnerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_OWNED_ALL: Listen failed for all owned listings for owner ID: $currentOwnerId. Error: ${e.message}", e)
                    _isLoadingListingsOwnedByCurrentUser.value = false // Ensure reset on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val fetchedListings = snapshot.documents.mapNotNull { document ->
                        parseListingDocument(document)
                    }
                    _listingsOwnedByCurrentUser.value = fetchedListings
                    Log.d("ListingsViewModel", "FETCH_OWNED_ALL: All owned listings fetched for owner ID: $currentOwnerId. Count: ${fetchedListings.size}")
                } else {
                    Log.d("ListingsViewModel", "FETCH_OWNED_ALL: All owned listings snapshot is null for owner ID: $currentOwnerId.")
                    _listingsOwnedByCurrentUser.value = emptyList()
                }
                _isLoadingListingsOwnedByCurrentUser.value = false // Ensure reset on success
            }
    }

    /**
     * Helper function to parse a Firestore DocumentSnapshot into a Listing object.
     * Crucially, `fulfilledBy` will now correctly be `null` if the Firestore field is `null` or absent.
     */
    private fun parseListingDocument(document: com.google.firebase.firestore.DocumentSnapshot): Listing? {
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
        val timestamp = document.getTimestamp("timestamp")
        val status = document.getString("status") ?: "active"

        val deliveryCoordGeoPoint = document.get("deliveryCoord") as? GeoPoint
        val deliveryCoord = deliveryCoordGeoPoint?.let { listOf(it.latitude, it.longitude) }

        val acceptedBy = document.get("acceptedBy") as? List<String> ?: emptyList()
        // FIX: Ensure fulfilledBy is correctly parsed as List<String>? from Firestore
        val fulfilledBy = document.get("fulfilledBy") as? List<String> ?: emptyList() // Make it non-nullable empty list if null/absent

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
            fulfilledBy = fulfilledBy // This will now be `null` if the Firestore field is null or absent
        )
    }

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