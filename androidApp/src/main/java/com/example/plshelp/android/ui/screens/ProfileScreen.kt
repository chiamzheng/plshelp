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

@Composable
fun ProfileScreen(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    var userName by rememberSaveable { mutableStateOf("") }
    var newName by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordToConfirm by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showChangeNameDialog by rememberSaveable { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showReAuthDialog by rememberSaveable { mutableStateOf(false) }

    // Function to reload user data from Firestore
    val reloadUserData = {
        user?.let {
            db.collection("users").document(it.uid).get() // Use UID as document ID
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        userName = document.getString("name") ?: ""
                    }
                }
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        reloadUserData()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Profile Screen", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Name: $userName")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Email: ${user?.email ?: "Email not available"}") // Get email from Auth
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
                    user?.let {
                        db.collection("users").document(it.uid).update("name", newName) // Use UID
                            .addOnSuccessListener {
                                userName = newName
                                showChangeNameDialog = false
                                newName = ""
                                reloadUserData()
                            }
                            .addOnFailureListener { e ->
                                errorMessage = "Failed to update name: ${e.message}"
                            }
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
                        label = { Text("New Password") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPassword == confirmPassword) {
                        showReAuthDialog = true
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
                    val authCredential =
                        com.google.firebase.auth.EmailAuthProvider.getCredential(user?.email ?: "", passwordToConfirm) // Use Auth email

                    user?.reauthenticate(authCredential)
                        ?.addOnCompleteListener { task ->
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