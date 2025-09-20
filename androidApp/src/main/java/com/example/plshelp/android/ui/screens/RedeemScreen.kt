package com.example.plshelp.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

data class RedeemableItem(
    val id: String,
    val name: String,
    val cost: Int,
    val imageUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedeemScreen(
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            RedeemableItem("item1", "Coffee Voucher", 100, "https://yourcdn.com/images/coffee.png"),
            RedeemableItem("item2", "Movie Ticket", 900, "https://yourcdn.com/images/movie.png"),
            RedeemableItem("item3", "Gym Pass", 300, "https://yourcdn.com/images/giftcard.png"),
            RedeemableItem("item4", "Meal Voucher", 400, "https://yourcdn.com/images/meal.png"),
            RedeemableItem("item5", "NTUC $5", 500, "https://yourcdn.com/images/gym.png"),
            RedeemableItem("item6", "NTUC $10", 1000, "https://yourcdn.com/images/shopping.png"),
        )
    }

    var selectedItem by remember { mutableStateOf<RedeemableItem?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Redeem Rewards") },
                actions = {
                    TextButton(onClick = { onNavigateToHistory() }) {
                        Text("History")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedItem = item },
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(end = 12.dp)
                            )
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${item.cost} pts", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text("Redeem ${item.name}?") },
            text = { Text("This will cost ${item.cost} points.") },
            confirmButton = {
                TextButton(onClick = {
                    redeemItem(item.name, item.cost) { success, msg ->
                        message = msg
                        selectedItem = null
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { selectedItem = null }) { Text("Cancel") }
            }
        )
    }
}

private fun redeemItem(itemName: String, pointsCost: Int, onResult: (Boolean, String) -> Unit) {
    val data = hashMapOf(
        "pointsCost" to pointsCost,
        "itemName" to itemName
    )

    Firebase.functions
        .getHttpsCallable("redeemItem")
        .call(data)
        .addOnSuccessListener { result ->
            val resultData = result.data as? Map<*, *>
            val remainingPoints = resultData?.get("remainingPoints") as? Long
            val msg = if (remainingPoints != null) {
                "Redeemed successfully! Points left: $remainingPoints"
            } else {
                "Redeemed successfully!"
            }
            onResult(true, msg)
        }
        .addOnFailureListener { e ->
            val msg = if (e is FirebaseFunctionsException) {
                when (e.code) {
                    FirebaseFunctionsException.Code.PERMISSION_DENIED -> "You are not allowed to redeem this item."
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Insufficient points to redeem this item."
                    FirebaseFunctionsException.Code.NOT_FOUND -> "User not found."
                    else -> e.message ?: "Error redeeming item."
                }
            } else e.message ?: "Error redeeming item."
            onResult(false, msg)
        }
}
