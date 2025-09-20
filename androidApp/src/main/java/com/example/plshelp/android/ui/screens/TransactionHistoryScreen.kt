package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class RedemptionHistory(
    val id: String = "",
    val itemName: String = "",
    val cost: Int = 0,
    val confirmationCode: String = "",
    val timestamp: Long = 0L
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var history by remember { mutableStateOf<List<RedemptionHistory>>(emptyList()) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("redemptionHistory") // ✅ fixed collection name
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    history = snapshot.documents.map {
                        RedemptionHistory(
                            id = it.id,
                            itemName = it.getString("itemName") ?: "",
                            cost = (it.getLong("pointsCost") ?: 0L).toInt(),
                            confirmationCode = it.getString("confirmationCode") ?: "",
                            timestamp = it.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                        )
                    }.sortedByDescending { it.timestamp } // ✅ local sort
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redemption History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (history.isEmpty()) {
                Text("No redemptions yet.")
            } else {
                val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                history.forEach { redemption ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${redemption.cost} pts - ${redemption.itemName}")
                            Text("Code: ${redemption.confirmationCode}")
                            Text(formatter.format(Date(redemption.timestamp)))
                        }
                    }
                }
            }
        }
    }
}
