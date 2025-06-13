package com.example.plshelp.android.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class to hold user information for accepted users
data class AcceptedUser(
    val id: String,
    val name: String
)

class AcceptedRequestsViewModel(private val listingId: String) : ViewModel() {

    private val _acceptedUsers = MutableStateFlow<List<AcceptedUser>>(emptyList())
    val acceptedUsers: StateFlow<List<AcceptedUser>> = _acceptedUsers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _listingStatus = MutableStateFlow("active") // Initial status
    val listingStatus: StateFlow<String> = _listingStatus

    private val _fulfilledBy = MutableStateFlow<String?>(null)
    val fulfilledBy: StateFlow<String?> = _fulfilledBy


    private val firestore = FirebaseFirestore.getInstance()

    init {
        listenToAcceptedUsers()
    }

    private fun listenToAcceptedUsers() {
        _isLoading.value = true
        _errorMessage.value = null

        firestore.collection("listings").document(listingId)
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    _errorMessage.value = "Error listening to listing updates: ${e.localizedMessage}"
                    _isLoading.value = false
                    Log.e("AcceptedRequestsVM", "Error listening to listing updates", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val acceptedByIds = documentSnapshot.get("acceptedBy") as? List<String> ?: emptyList()
                    _listingStatus.value = documentSnapshot.getString("status") ?: "active"
                    _fulfilledBy.value = documentSnapshot.getString("fulfilledBy")

                    if (acceptedByIds.isNotEmpty()) {
                        fetchUserNames(acceptedByIds)
                    } else {
                        _acceptedUsers.value = emptyList()
                        _isLoading.value = false
                    }
                } else {
                    _errorMessage.value = "Listing not found."
                    _acceptedUsers.value = emptyList()
                    _isLoading.value = false
                }
            }
    }

    private fun fetchUserNames(userIds: List<String>) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val usersCollection = firestore.collection("users")
                val fetchedUsers = mutableListOf<AcceptedUser>()

                // Fetch each user's name
                for (uid in userIds) {
                    val userDoc = usersCollection.document(uid).get().await()
                    if (userDoc.exists()) {
                        val userName = userDoc.getString("name") ?: "Anonymous"
                        fetchedUsers.add(AcceptedUser(uid, userName))
                    } else {
                        Log.w("AcceptedRequestsVM", "User document for UID $uid not found.")
                        fetchedUsers.add(AcceptedUser(uid, "Unknown User")) // Fallback
                    }
                }
                _acceptedUsers.value = fetchedUsers
            } catch (e: Exception) {
                _errorMessage.value = "Error fetching user names: ${e.localizedMessage}"
                Log.e("AcceptedRequestsVM", "Error fetching user names", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- NEW FUNCTION: Fulfill request (called by owner from this screen) ---
    fun fulfillRequest(acceptorId: String) {
        if (_isLoading.value) return // Prevent multiple calls
        if (_fulfilledBy.value != null) {
            _errorMessage.value = "This request is already fulfilled."
            return
        }

        _isLoading.value = true
        _errorMessage.value = null
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
                // The snapshot listener will update the state, no need to manually set it here
                Log.d("AcceptedRequestsVM", "Request $listingId fulfilled by $acceptorId")
            } catch (e: Exception) {
                _errorMessage.value = "Failed to fulfill request: ${e.localizedMessage}"
                Log.e("AcceptedRequestsVM", "Error fulfilling request $listingId by $acceptorId", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    class Factory(private val listingId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AcceptedRequestsViewModel::class.java)) {
                return AcceptedRequestsViewModel(listingId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}