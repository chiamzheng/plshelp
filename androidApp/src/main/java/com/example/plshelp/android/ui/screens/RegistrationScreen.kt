package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun RegistrationScreen(
    onRegisterSuccess: () -> Unit,
    onRegisterFailure: (String) -> Unit,
    onRegister: (String, String) -> Unit,
    onBackToLogin: () -> Unit,
    registerErrorMessage: String? // Added this parameter
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var registrationSuccess by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()

    // Update errorMessage when registerErrorMessage changes
    LaunchedEffect(registerErrorMessage) {
        errorMessage = registerErrorMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Register", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = null
                if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                    errorMessage = "Please enter name, email, and password."
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null) {
                                    val userData = hashMapOf(
                                        "name" to name,
                                        "points" to 500L
                                    )
                                    db.collection("users").document(user.uid)
                                        .set(userData)
                                        .addOnSuccessListener {
                                            Log.d("RegistrationScreen", "Firestore write successful")
                                            registrationSuccess = true
                                            scope.launch {
                                                delay(2000)
                                                onRegisterSuccess()
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("RegistrationScreen", "Firestore write failed: ${e.message}")
                                            onRegisterFailure("Failed to save user data: ${e.message}")
                                        }
                                }
                            } else {
                                val exception = task.exception
                                if (exception != null) {
                                    if ((exception as FirebaseAuthException).errorCode == "ERROR_EMAIL_ALREADY_IN_USE") {
                                        onRegisterFailure("Email address is already in use.")
                                    } else {
                                        onRegisterFailure(exception.message ?: "Registration failed.")
                                    }
                                } else {
                                    onRegisterFailure("Registration failed.")
                                }
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = { onBackToLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Login")
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
        }
        if (registrationSuccess) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Registration Successful", color = MaterialTheme.colorScheme.primary)
        }
    }
}