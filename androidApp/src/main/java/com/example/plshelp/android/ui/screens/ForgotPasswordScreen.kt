package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ForgotPasswordScreen(
    onResetSuccess: () -> Unit,
    onResetFailure: (String) -> Unit,
    onBackToLogin: () -> Unit,
    forgotPasswordErrorMessage: String?
) {
    var email by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf(forgotPasswordErrorMessage) }
    var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
    val auth = FirebaseAuth.getInstance()

    LaunchedEffect(forgotPasswordErrorMessage) {
        errorMessage = forgotPasswordErrorMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Forgot Password", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = null
                resetSuccessMessage = null
                if (email.isEmpty()) {
                    errorMessage = "Please enter your email."
                } else {
                    auth.sendPasswordResetEmail(email)
                        .addOnCompleteListener { task ->
                            resetSuccessMessage = "Password reset email sent."
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Password")
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

        if (resetSuccessMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(resetSuccessMessage!!, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview
@Composable
fun ForgotPasswordScreenPreview() {
    MaterialTheme {
        ForgotPasswordScreen(onResetSuccess = {}, onResetFailure = {}, onBackToLogin = {}, forgotPasswordErrorMessage = null)
    }
}