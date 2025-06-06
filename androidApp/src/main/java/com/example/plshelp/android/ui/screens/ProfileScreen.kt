package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.plshelp.android.LocalUserId
import com.example.plshelp.android.LocalUserName

@Composable
fun ProfileScreen(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser // Still need this for re-authentication and email

    // --- USE GLOBAL USER ID AND USER NAME ---
    val currentUserId = LocalUserId.current
    var currentUserName by LocalUserName.current // This is a mutable state, so we can update it
    // --- END GLOBAL ---

    var newName by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordToConfirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeNameDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showReAuthDialog by rememberSaveable { mutableStateOf(false) }

    // No longer need reloadUserData() directly, as currentUserName is already a mutable state
    // and should be updated via LocalUserName.current if the name changes.
    // LaunchedEffect(Unit) {
    //     reloadUserData()
    // }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Profile Screen", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Name: $currentUserName") // Use global user name
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
    }

    // Change Name Dialog
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
                    if (currentUserId.isNotEmpty()) { // Ensure we have a valid UID
                        db.collection("users").document(currentUserId).update("name", newName)
                            .addOnSuccessListener {
                                currentUserName = newName // Update the global state
                                showChangeNameDialog = false
                                newName = ""
                                errorMessage = null // Clear any previous error
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

    // Change Password Dialog
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
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation() // Added for password fields
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation() // Added for password fields
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPassword.isBlank() || confirmPassword.isBlank()) {
                        errorMessage = "New password and confirm password cannot be empty."
                    } else if (newPassword == confirmPassword) {
                        showReAuthDialog = true
                        errorMessage = null // Clear error before re-auth
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

    // Re-authentication Dialog
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