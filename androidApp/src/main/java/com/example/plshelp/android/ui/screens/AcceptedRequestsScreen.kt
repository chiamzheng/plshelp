package com.example.plshelp.android.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.data.ListingDetailViewModel
import com.example.plshelp.android.data.AcceptedUser
import com.example.plshelp.android.data.AcceptedRequestsViewModel
import com.example.plshelp.android.LocalUserId // Import LocalUserId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptedRequestsScreen(
    listingId: String,
    onBackClick: () -> Unit,
    listingOwnerId: String // Pass the owner ID to filter visibility of "Fulfill" button
) {
    val viewModel: AcceptedRequestsViewModel = viewModel(
        factory = AcceptedRequestsViewModel.Factory(listingId)
    )

    val acceptedUsers = viewModel.acceptedUsers.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value
    val errorMessage = viewModel.errorMessage.collectAsState().value
    val listingStatus = viewModel.listingStatus.collectAsState().value
    val fulfilledBy = viewModel.fulfilledBy.collectAsState().value

    val currentUserId = LocalUserId.current // Get current user's UID

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accepted Offers") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            } else if (acceptedUsers.isEmpty()) {
                Text("No one has accepted this request yet.", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = "Users who accepted:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(acceptedUsers) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = user.id, // Display UID for debugging/identification
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Chat button (always visible to owner for all acceptors, and to acceptor themselves)
                                    // Placeholder for actual chat functionality
                                    Button(onClick = {
                                        Log.d("AcceptedRequestsScreen", "Chat with ${user.name} (${user.id})")
                                        // TODO: Navigate to chat screen with user.id
                                    }) {
                                        Text("Chat")
                                    }

                                    // Fulfill button (only visible to owner, and if not already fulfilled)
                                    if (currentUserId == listingOwnerId && listingStatus != "fulfilled") {
                                        Button(
                                            onClick = {
                                                viewModel.fulfillRequest(user.id)
                                            },
                                            enabled = !isLoading && fulfilledBy == null // Enable if not loading and not yet fulfilled
                                        ) {
                                            if (user.id == fulfilledBy) {
                                                Text("Fulfilled") // If this user is the fulfilled one
                                            } else {
                                                Text("Fulfill")
                                            }
                                        }
                                    } else if (user.id == fulfilledBy) {
                                        // If already fulfilled, show "Fulfilled" text next to the chosen acceptor
                                        Text(
                                            "Fulfilled",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (listingStatus == "fulfilled" && fulfilledBy != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Request Fulfilled by: ${fulfilledBy}", // Will need to fetch name here
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}