package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plshelp.android.LocalUserId
import com.example.plshelp.android.LocalUserName
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import Listing // Ensure this is imported for your data class
import kotlinx.coroutines.tasks.await
import android.util.Log // For logging
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onNavigateToDetail: (Listing) -> Unit, // This is still needed
    modifier: Modifier = Modifier // The modifier will now directly contain the Scaffold's padding
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    val currentUserId = LocalUserId.current
    var currentUserName by LocalUserName.current

    var newName by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordToConfirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeNameDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showReAuthDialog by rememberSaveable { mutableStateOf(false) }

    // State for My Listings (now a regular mutableStateOf List)
    var myListings by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var isLoadingMyListings by remember { mutableStateOf(true) }
    var myListingsErrorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch My Listings when the screen is composed
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            isLoadingMyListings = true
            myListingsErrorMessage = null
            try {
                val querySnapshot = db.collection("listings")
                    .whereEqualTo("ownerID", currentUserId)
                    .get()
                    .await()

                val fetchedListings = mutableListOf<Listing>()
                for (document in querySnapshot.documents) {
                    try {
                        val title = document.getString("title") ?: "N/A"
                        val description = document.getString("description") ?: "N/A"
                        val price = document.getString("price") ?: "0.00"
                        val categoryList = document.get("category") as? List<String> ?: emptyList()
                        val category = categoryList.joinToString(", ")
                        val coordGeoPoint = document.get("coord") as? com.google.firebase.firestore.GeoPoint
                        val coord = coordGeoPoint?.let { listOf(it.latitude, it.longitude) } ?: emptyList()

                        val deliveryCoordGeoPoint = document.get("deliveryCoord") as? com.google.firebase.firestore.GeoPoint
                        val deliveryCoord = deliveryCoordGeoPoint?.let { listOf(it.latitude, it.longitude) }

                        val radius = document.getLong("radius") ?: 0L
                        val ownerID = document.getString("ownerID") ?: "N/A"
                        val ownerName = document.getString("ownerName") ?: "Anonymous"
                        val timestamp = document.getTimestamp("timestamp")
                        val status = document.getString("status") ?: "active"

                        val acceptedBy = document.get("acceptedBy") as? List<String> ?: emptyList()
                        val fulfilledBy = document.getString("fulfilledBy")


                        fetchedListings.add(
                            Listing(
                                id = document.id,
                                category = category,
                                coord = coord,
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
                        )
                    } catch (e: Exception) {
                        Log.e("ProfileScreen", "Error parsing listing document ${document.id}: ${e.message}", e)
                    }
                }
                myListings = fetchedListings // Update the state with the fetched list
            } catch (e: Exception) {
                myListingsErrorMessage = "Error fetching your listings: ${e.message}"
                Log.e("ProfileScreen", "Error fetching user listings: ${e.message}", e)
            } finally {
                isLoadingMyListings = false
            }
        } else {
            isLoadingMyListings = false
            myListingsErrorMessage = "User ID not available to fetch listings."
        }
    }


    Column(
        modifier = modifier // The Scaffold padding is already applied via this modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp) // Apply *only* horizontal padding here for the content
            .verticalScroll(rememberScrollState()), // Make the entire Column scrollable
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Profile Screen", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Name: $currentUserName")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Email: ${user?.email ?: "Email not available"}")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showChangeNameDialog = true }) {
            Text("Change Name")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { showChangePasswordDialog = true }) {
            Text("Change Password")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSignOut() }) {
            Text("Sign Out")
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("My Listings", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingMyListings) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myListingsErrorMessage != null) {
            Text(myListingsErrorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myListings.isEmpty()) {
            Text("You have no listings.", modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            // Use a regular Column and forEach to render all listings directly
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp) // Maintain spacing between cards
            ) {
                myListings.forEach { listing ->
                    // Reusing ExpandableListingCard with dummy values
                    ListingCard(
                        listing = listing,
                        onNavigateToDetail = onNavigateToDetail,
                        currentLat = null, // Dummy value
                        currentLon = null,
                        displayMode = DisplayMode.DISTANCE // Dummy value
                    )
                }
            }
        }
        // Add some bottom padding here to ensure the last listing isn't too close to the edge
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showChangeNameDialog) {
        AlertDialog(
            onDismissRequest = { showChangeNameDialog = false },
            title = { Text("Change Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (currentUserId.isNotEmpty()) {
                        db.collection("users").document(currentUserId).update("name", newName)
                            .addOnSuccessListener {
                                currentUserName = newName
                                showChangeNameDialog = false
                                newName = ""
                                errorMessage = null
                            }
                            .addOnFailureListener { e ->
                                errorMessage = "Failed to update name: ${e.message}"
                            }
                    } else {
                        errorMessage = "User not logged in."
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { showChangeNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPassword.isBlank() || confirmPassword.isBlank()) {
                        errorMessage = "New password and confirm password cannot be empty."
                    } else if (newPassword == confirmPassword) {
                        showReAuthDialog = true
                        errorMessage = null
                    } else {
                        errorMessage = "Passwords do not match."
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showReAuthDialog) {
        AlertDialog(
            onDismissRequest = { showReAuthDialog = false },
            title = { Text("Re-authenticate") },
            text = {
                OutlinedTextField(
                    value = passwordToConfirm,
                    onValueChange = { passwordToConfirm = it },
                    label = { Text("Enter your password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val email = user?.email
                    if (email != null && passwordToConfirm.isNotBlank()) {
                        val authCredential =
                            com.google.firebase.auth.EmailAuthProvider.getCredential(email, passwordToConfirm)

                        user.reauthenticate(authCredential)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    user.updatePassword(newPassword)
                                        .addOnCompleteListener { passwordTask ->
                                            if (passwordTask.isSuccessful) {
                                                errorMessage = "Password updated successfully."
                                                showChangePasswordDialog = false
                                                showReAuthDialog = false
                                                newPassword = ""
                                                confirmPassword = ""
                                                passwordToConfirm = ""
                                            } else {
                                                errorMessage =
                                                    "Failed to update password: ${passwordTask.exception?.message}"
                                            }
                                        }
                                } else {
                                    errorMessage = "Re-authentication failed: ${task.exception?.message}"
                                }
                            }
                    } else {
                        errorMessage = "Email or password cannot be empty for re-authentication."
                    }
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                Button(onClick = { showReAuthDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}