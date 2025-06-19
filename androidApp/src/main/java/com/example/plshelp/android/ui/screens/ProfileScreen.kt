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
import Listing
import kotlinx.coroutines.tasks.await
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import com.example.plshelp.android.data.DisplayModeRepository
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuthException // Import for specific exception handling

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onNavigateToDetail: (Listing) -> Unit,
    modifier: Modifier = Modifier
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

    var myListings by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var isLoadingMyListings by remember { mutableStateOf(true) }
    var myListingsErrorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val displayModeRepository = remember { DisplayModeRepository(context) }
    val displayMode by displayModeRepository.displayModeFlow.collectAsState(initial = DisplayMode.DISTANCE)
    val scope = rememberCoroutineScope()

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
                myListings = fetchedListings
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
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) // Reverted to adaptive color
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "$currentUserName's Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Use onSurfaceVariant for text on surfaceVariant
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Email: ${user?.email ?: "Email not available"}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Use onSurfaceVariant
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showChangeNameDialog = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Change Name", fontSize = 12.sp)
            }
            Button(
                onClick = { showChangePasswordDialog = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Change Password", fontSize = 12.sp)
            }
            Button(
                onClick = { onSignOut() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            ) {
                Text("Sign Out", fontSize = 12.sp)
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display Mode Toggle in Profile Screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Display Mode", fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Distance", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = displayMode == DisplayMode.WALK_TIME,
                    onCheckedChange = { isChecked ->
                        val newMode = if (isChecked) DisplayMode.WALK_TIME else DisplayMode.DISTANCE
                        scope.launch {
                            displayModeRepository.saveDisplayMode(newMode)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Walk Time", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("My Listings", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start).padding(start = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))

        if (isLoadingMyListings) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myListingsErrorMessage != null) {
            Text(myListingsErrorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myListings.isEmpty()) {
            Text("You have no listings.", modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                myListings.forEach { listing ->
                    ListingCard(
                        listing = listing,
                        onNavigateToDetail = onNavigateToDetail,
                        currentLat = null, // Still dummy for profile's own listings
                        currentLon = null, // Still dummy for profile's own listings
                        displayMode = displayMode // Pass the actual display mode from DataStore
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // --- Start: Full Dialog Code ---
    if (showChangeNameDialog) {
        AlertDialog(
            onDismissRequest = { showChangeNameDialog = false },
            title = { Text("Change Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showChangeNameDialog = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
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
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.isBlank() || confirmPassword.isBlank()) {
                            errorMessage = "New password and confirm password cannot be empty."
                        } else if (newPassword == confirmPassword) {
                            showReAuthDialog = true
                            errorMessage = null
                        } else {
                            errorMessage = "Passwords do not match."
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showChangePasswordDialog = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
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
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Confirm", fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showReAuthDialog = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }
}