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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.firestore.ListenerRegistration

import java.util.concurrent.TimeUnit
import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng

class ListingsViewModel(private val currentOwnerId: String) : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // --- Listener Registrations ---
    private var generalListingsListener: ListenerRegistration? = null
    private var listingsUserIsFulfillingListener: ListenerRegistration? = null
    private var listingsUserAcceptedButNotFulfillingListener: ListenerRegistration? = null
    private var listingsOwnedAndAcceptedOffersListener: ListenerRegistration? = null
    private var listingsOwnedByCurrentUserListener: ListenerRegistration? = null
    private var userPointsListener: ListenerRegistration? = null

    // --- S2 Cell Filtering States ---
    private val _selectedS2CellId = MutableStateFlow<S2CellId?>(null)
    val selectedS2CellId: StateFlow<S2CellId?> = _selectedS2CellId.asStateFlow()
    private val S2_CELL_LEVEL = 13

    fun selectS2Cell(cellId: S2CellId?) {
        _selectedS2CellId.value = cellId
    }

    // --- General Listings ---
    private val _allPublicListingsFromRepo = MutableStateFlow<List<Listing>>(emptyList())
    val allPublicListingsFromRepo: StateFlow<List<Listing>> = _allPublicListingsFromRepo

    val listings: StateFlow<List<Listing>> = combine(
        _allPublicListingsFromRepo,
        _selectedS2CellId
    ) { allListings, s2CellId ->
        s2CellId?.let { cell ->
            allListings.filter { listing ->
                listing.coord.size == 2 &&
                        S2CellId.fromLatLng(S2LatLng.fromDegrees(listing.coord[0], listing.coord[1]))
                            .parent(S2_CELL_LEVEL) == cell
            }
        } ?: allListings
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // --- Listings for current user ---
    private val _listingsUserIsFulfilling = MutableStateFlow<List<Listing>>(emptyList())
    val listingsUserIsFulfilling: StateFlow<List<Listing>> = _listingsUserIsFulfilling
    private val _isLoadingListingsUserIsFulfilling = MutableStateFlow(false)
    val isLoadingListingsUserIsFulfilling: StateFlow<Boolean> = _isLoadingListingsUserIsFulfilling

    private val _listingsUserAcceptedButNotFulfilling = MutableStateFlow<List<Listing>>(emptyList())
    val listingsUserAcceptedButNotFulfilling: StateFlow<List<Listing>> = _listingsUserAcceptedButNotFulfilling
    private val _isLoadingListingsUserAcceptedButNotFulfilling = MutableStateFlow(false)
    val isLoadingListingsUserAcceptedButNotFulfilling: StateFlow<Boolean> = _isLoadingListingsUserAcceptedButNotFulfilling

    private val _listingsOwnedAndAcceptedOffers = MutableStateFlow<List<Listing>>(emptyList())
    val listingsOwnedAndAcceptedOffers: StateFlow<List<Listing>> = _listingsOwnedAndAcceptedOffers
    private val _isLoadingListingsOwnedAndAcceptedOffers = MutableStateFlow(false)
    val isLoadingListingsOwnedAndAcceptedOffers: StateFlow<Boolean> = _isLoadingListingsOwnedAndAcceptedOffers

    private val _listingsOwnedByCurrentUser = MutableStateFlow<List<Listing>>(emptyList())
    val listingsOwnedByCurrentUser: StateFlow<List<Listing>> = _listingsOwnedByCurrentUser
    private val _isLoadingListingsOwnedByCurrentUser = MutableStateFlow(false)
    val isLoadingListingsOwnedByCurrentUser: StateFlow<Boolean> = _isLoadingListingsOwnedByCurrentUser

    // --- User Points ---
    private val _userPoints = MutableStateFlow<Long?>(null)
    val userPoints: StateFlow<Long?> = _userPoints

    // --- Last Fetch Time ---
    private val _lastFetchTime = mutableLongStateOf(0L)
    val lastFetchTimeState: State<Long> = _lastFetchTime
    private val refreshIntervalMillis = TimeUnit.MINUTES.toMillis(5)

    init {
        Log.d("ListingsViewModel", "VM_INIT: ViewModel initialized for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        refreshListings()
        startPeriodicRefresh()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ListingsViewModel", "VM_CLEARED: ViewModel cleared for owner ID: $currentOwnerId. Hash: ${this.hashCode()}")
        generalListingsListener?.remove()
        listingsUserIsFulfillingListener?.remove()
        listingsUserAcceptedButNotFulfillingListener?.remove()
        listingsOwnedAndAcceptedOffersListener?.remove()
        listingsOwnedByCurrentUserListener?.remove()
        userPointsListener?.remove()
        Log.d("ListingsViewModel", "VM_CLEARED: All listeners removed.")
    }

    fun refreshListings() {
        _lastFetchTime.longValue = System.currentTimeMillis()
        Log.d("ListingsViewModel", "REFRESH: Refreshing all listings for owner ID: $currentOwnerId")

        generalListingsListener?.remove()
        listingsUserIsFulfillingListener?.remove()
        listingsUserAcceptedButNotFulfillingListener?.remove()
        listingsOwnedAndAcceptedOffersListener?.remove()
        listingsOwnedByCurrentUserListener?.remove()
        userPointsListener?.remove()

        _isLoading.value = false
        _isLoadingListingsUserIsFulfilling.value = false
        _isLoadingListingsUserAcceptedButNotFulfilling.value = false
        _isLoadingListingsOwnedAndAcceptedOffers.value = false
        _isLoadingListingsOwnedByCurrentUser.value = false

        fetchGeneralListings()
        fetchListingsUserIsFulfilling()
        fetchListingsUserAcceptedButNotFulfilling()
        fetchListingsOwnedAndAcceptedOffers()
        fetchAllListingsOwnedByCurrentUser()
        fetchUserPoints()
    }

    fun onNewListingCreated() {
        Log.d("ListingsViewModel", "NEW_LISTING: New listing created, triggering full refresh for owner ID: $currentOwnerId")
        refreshListings()
    }

    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(refreshIntervalMillis)
                if (System.currentTimeMillis() - _lastFetchTime.longValue >= refreshIntervalMillis) {
                    Log.d("ListingsViewModel", "PERIODIC_REFRESH: Periodic refresh triggered for owner ID: $currentOwnerId")
                    refreshListings()
                }
            }
        }
    }

    private fun fetchGeneralListings() {
        _isLoading.value = true
        generalListingsListener?.remove()
        generalListingsListener = db.collection("listings")
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("ListingsViewModel", "FETCH_GENERAL_ERROR: ${e.message}", e)
                    _isLoading.value = false
                    return@addSnapshotListener
                }
                val allListings = snapshot?.documents?.mapNotNull { parseListingDocument(it) } ?: emptyList()
                val publicListings = allListings.filter { it.ownerID != currentOwnerId && it.fulfilledBy.isNullOrEmpty() }
                _allPublicListingsFromRepo.value = publicListings
                _isLoading.value = false
            }
    }

    private fun fetchListingsUserIsFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserIsFulfilling.value = emptyList()
            _isLoadingListingsUserIsFulfilling.value = false
            return
        }
        _isLoadingListingsUserIsFulfilling.value = true
        listingsUserIsFulfillingListener?.remove()
        listingsUserIsFulfillingListener = db.collection("listings")
            .whereArrayContains("fulfilledBy", currentOwnerId)
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val filtered = snapshot?.documents?.mapNotNull { parseListingDocument(it) }
                    ?.filter { it.ownerID != currentOwnerId } ?: emptyList()
                _listingsUserIsFulfilling.value = filtered
                _isLoadingListingsUserIsFulfilling.value = false
            }
    }

    private fun fetchListingsUserAcceptedButNotFulfilling() {
        if (currentOwnerId.isBlank()) {
            _listingsUserAcceptedButNotFulfilling.value = emptyList()
            _isLoadingListingsUserAcceptedButNotFulfilling.value = false
            return
        }
        _isLoadingListingsUserAcceptedButNotFulfilling.value = true
        listingsUserAcceptedButNotFulfillingListener?.remove()
        listingsUserAcceptedButNotFulfillingListener = db.collection("listings")
            .whereArrayContains("acceptedBy", currentOwnerId)
            .whereEqualTo("status", "active")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val filtered = snapshot?.documents?.mapNotNull { parseListingDocument(it) }
                    ?.filter { it.acceptedBy.contains(currentOwnerId) && it.fulfilledBy.isNullOrEmpty() && it.ownerID != currentOwnerId }
                    ?: emptyList()
                _listingsUserAcceptedButNotFulfilling.value = filtered
                _isLoadingListingsUserAcceptedButNotFulfilling.value = false
            }
    }

    private fun fetchListingsOwnedAndAcceptedOffers() {
        if (currentOwnerId.isBlank()) {
            _listingsOwnedAndAcceptedOffers.value = emptyList()
            _isLoadingListingsOwnedAndAcceptedOffers.value = false
            return
        }
        _isLoadingListingsOwnedAndAcceptedOffers.value = true
        listingsOwnedAndAcceptedOffersListener?.remove()
        listingsOwnedAndAcceptedOffersListener = db.collection("listings")
            .whereEqualTo("ownerID", currentOwnerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val filtered = snapshot?.documents?.mapNotNull { parseListingDocument(it) }
                    ?.filter { it.acceptedBy.isNotEmpty() } ?: emptyList()
                _listingsOwnedAndAcceptedOffers.value = filtered
                _isLoadingListingsOwnedAndAcceptedOffers.value = false
            }
    }

    private fun fetchAllListingsOwnedByCurrentUser() {
        if (currentOwnerId.isBlank()) {
            _listingsOwnedByCurrentUser.value = emptyList()
            _isLoadingListingsOwnedByCurrentUser.value = false
            return
        }
        _isLoadingListingsOwnedByCurrentUser.value = true
        listingsOwnedByCurrentUserListener?.remove()
        listingsOwnedByCurrentUserListener = db.collection("listings")
            .whereEqualTo("ownerID", currentOwnerId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val allOwned = snapshot?.documents?.mapNotNull { parseListingDocument(it) } ?: emptyList()
                _listingsOwnedByCurrentUser.value = allOwned
                _isLoadingListingsOwnedByCurrentUser.value = false
            }
    }

    private fun fetchUserPoints() {
        if (currentOwnerId.isBlank()) {
            _userPoints.value = null
            return
        }
        userPointsListener?.remove()
        userPointsListener = db.collection("users").document(currentOwnerId)
            .addSnapshotListener { snapshot, _ ->
                _userPoints.value = snapshot?.getLong("points") ?: 0L
            }
    }

    private fun parseListingDocument(document: com.google.firebase.firestore.DocumentSnapshot): Listing? {
        val id = document.id
        val category = (document.get("category") as? List<String>)?.joinToString(", ") ?: ""
        val coordGeoPoint = document.get("coord") as? GeoPoint
        val coord = coordGeoPoint?.let { listOf(it.latitude, it.longitude) } ?: emptyList()
        val description = document.getString("description") ?: ""
        val imgUrl = document.getString("imgUrl")
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
        val fulfilledBy = document.get("fulfilledBy") as? List<String>

        return Listing(
            id = id,
            category = category,
            coord = coord,
            description = description,
            imageUrl = imgUrl,
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

    // --- Action Methods ---
    fun acceptRequest(listingId: String, userId: String) {
        db.collection("listings").document(listingId).update("acceptedBy", listOf(userId))
    }

    fun unacceptRequest(listingId: String, userId: String) {
        db.collection("listings").document(listingId).update("acceptedBy", emptyList<String>())
    }

    fun fulfillRequest(listingId: String, acceptedUserId: String) {
        db.collection("listings").document(listingId)
            .update("fulfilledBy", listOf(acceptedUserId), "status", "active")
    }

    fun markRequestAsCompleted(listingId: String) {
        db.collection("listings").document(listingId).update("status", "fulfilled")
    }

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
