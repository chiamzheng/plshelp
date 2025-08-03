package com.example.plshelp.android.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.LocalUserId
import com.example.plshelp.android.LocalUserName
import com.example.plshelp.android.data.DisplayModeRepository
import com.example.plshelp.android.data.ListingsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import Listing
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onNavigateToDetail: (Listing) -> Unit,
    modifier: Modifier = Modifier,
    listingsViewModel: ListingsViewModel = viewModel(factory = ListingsViewModel.Factory(LocalUserId.current))
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser

    val currentUserId = LocalUserId.current
    var currentUserName by LocalUserName.current

    Log.d("PROFILE_DEBUG", "ProfileScreen recomposes. currentUserId: $currentUserId, currentUserName: $currentUserName")

    var newName by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordToConfirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeNameDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showReAuthDialog by rememberSaveable { mutableStateOf(false) }

    val myListings by listingsViewModel.listingsOwnedByCurrentUser.collectAsState()
    val isLoadingMyListings by listingsViewModel.isLoadingListingsOwnedByCurrentUser.collectAsState()

    val context = LocalContext.current
    val displayModeRepository = remember { DisplayModeRepository(context) }
    val displayMode by displayModeRepository.displayModeFlow.collectAsState(initial = DisplayMode.DISTANCE)
    val scope = rememberCoroutineScope()

    // Sorting logic
    val sortedMyListings by remember(myListings) {
        derivedStateOf {
            myListings.sortedWith(compareBy { listing ->
                when {
                    // Priority 1: "Active" listings where `fulfilledBy` is not empty
                    listing.status == "active" && listing.fulfilledBy?.isNotEmpty() == true -> 0
                    // Priority 2: "Active" listings with accepted offers
                    listing.status == "active" && listing.acceptedBy.isNotEmpty() -> 1
                    // Priority 3: All other listings (active with no offers, or fulfilled status)
                    else -> 2
                }
            })
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Text(
                    text = "${if (currentUserName.isNotEmpty()) currentUserName else "Guest"}'s Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Email: ${user?.email ?: "Email not available"}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text("Change Name", fontSize = 12.sp)
            }
            Button(
                onClick = { showChangePasswordDialog = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text("Change Password", fontSize = 12.sp)
            }
            Button(
                onClick = { onSignOut() },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
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

        // --- YOUR LISTINGS SECTION ---
        Text(
            text = "Your Posted Listings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.Start)
        )

        if (isLoadingMyListings && myListings.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (myListings.isEmpty()) {
            Text(
                text = "You haven't posted any listings yet.",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedMyListings.forEach { listing ->
                    // Corrected Listing Status logic based on your request
                    val statusForUserListing: ListingStatus? = when {
                        listing.status == "active" && listing.fulfilledBy?.isNotEmpty() == true ->
                            ListingStatus("ACTIVE", Color(0xFF338a4d))
                        listing.status == "active" && listing.acceptedBy.isNotEmpty() ->
                            ListingStatus("OFFER RECEIVED", Color(0xFFb0aa0c))
                        // The user specified "status active with neither = nothing", so no else block
                        else -> null
                    }

                    ListingCard(
                        listing = listing,
                        onNavigateToDetail = onNavigateToDetail,
                        currentLat = null,
                        currentLon = null,
                        displayMode = displayMode,
                        status = statusForUserListing
                    )
                }
            }
        }
        // --- END YOUR LISTINGS SECTION ---
        Spacer(modifier = Modifier.height(16.dp))
    }

    // --- Dialog Code ---
    if (showChangeNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangeNameDialog = false
                newName = ""
                errorMessage = null
            },
            title = { Text("Change Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isBlank()) {
                            errorMessage = "Name cannot be empty."
                            return@Button
                        }
                        if (currentUserId.isNotEmpty()) {
                            db.collection("users").document(currentUserId).update("name", newName)
                                .addOnSuccessListener {
                                    currentUserName = newName
                                    Log.d("ProfileScreen", "Firestore and LocalUserName updated to: $newName")
                                    val currentUser = auth.currentUser
                                    if (currentUser != null) {
                                        val profileUpdates = UserProfileChangeRequest.Builder()
                                            .setDisplayName(newName)
                                            .build()
                                        currentUser.updateProfile(profileUpdates)
                                            ?.addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    Log.d("ProfileScreen", "Firebase Auth profile display name updated to: $newName")
                                                } else {
                                                    Log.e("ProfileScreen", "Failed to update Firebase Auth profile display name: ${task.exception?.message}", task.exception)
                                                }
                                            }
                                    } else {
                                        Log.w("ProfileScreen", "User is null when trying to update Firebase Auth profile name.")
                                    }
                                    showChangeNameDialog = false
                                    newName = ""
                                    errorMessage = null
                                }
                                .addOnFailureListener { e ->
                                    errorMessage = "Failed to update name: ${e.message}"
                                    Log.e("ProfileScreen", "Name update failed: ${e.message}", e)
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
                    onClick = {
                        showChangeNameDialog = false
                        newName = ""
                        errorMessage = null
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showChangePasswordDialog = false
                newPassword = ""
                confirmPassword = ""
                errorMessage = null
            },
            title = { Text("Change Password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword.isBlank() || confirmPassword.isBlank()) {
                            errorMessage = "New password and confirm password cannot be empty."
                        } else if (newPassword.length < 6) {
                            errorMessage = "Password must be at least 6 characters long."
                        }
                        else if (newPassword == confirmPassword) {
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
                    onClick = {
                        showChangePasswordDialog = false
                        newPassword = ""
                        confirmPassword = ""
                        errorMessage = null
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    if (showReAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showReAuthDialog = false
                passwordToConfirm = ""
                errorMessage = null
            },
            title = { Text("Re-authenticate") },
            text = {
                Column {
                    OutlinedTextField(
                        value = passwordToConfirm,
                        onValueChange = { passwordToConfirm = it },
                        label = { Text("Enter your current password to confirm") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = user?.email
                        val currentUser = auth.currentUser
                        if (email != null && passwordToConfirm.isNotBlank() && currentUser != null) {
                            val authCredential =
                                com.google.firebase.auth.EmailAuthProvider.getCredential(email, passwordToConfirm)

                            currentUser.reauthenticate(authCredential)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        currentUser.updatePassword(newPassword)
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
                                                    Log.e("ProfileScreen", "Password update failed: ${passwordTask.exception?.message}", passwordTask.exception)
                                                }
                                            }
                                    } else {
                                        val authException = task.exception
                                        if (authException is FirebaseAuthException && authException.errorCode == "ERROR_INVALID_CREDENTIAL") {
                                            errorMessage = "Invalid current password. Please try again."
                                        } else {
                                            errorMessage = "Re-authentication failed: ${authException?.message}"
                                        }
                                        Log.e("ProfileScreen", "Re-authentication failed: ${authException?.message}", authException)
                                    }
                                }
                        } else {
                            errorMessage = "Email or current password cannot be empty for re-authentication, or user is null."
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Confirm", fontSize = 12.sp)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showReAuthDialog = false
                        passwordToConfirm = ""
                        errorMessage = null
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }
}