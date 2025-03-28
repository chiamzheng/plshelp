// ProfileScreen.kt
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

@Composable
fun ProfileScreen(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    var userName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        user?.let {
            db.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        userName = document.getString("name") ?: ""
                        userEmail = document.getString("email") ?: ""
                    }
                }
        }
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
        Text("Email: $userEmail")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onSignOut() }) {
            Text("Sign Out")
        }
    }
}