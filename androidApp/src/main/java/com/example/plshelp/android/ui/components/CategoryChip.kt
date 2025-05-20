package com.example.plshelp.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun CategoryChip(
    categoryString: String,
    isSelected: Boolean,
    onCategoryClick: (String) -> Unit = {} // Make it optional with a default empty lambda
) {
    val categories = categoryString.split(", ").map { it.trim().lowercase(Locale.getDefault()) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.forEach { category ->
            val originalColor = when (category) {
                "urgent" -> Color.Red
                "helper" -> Color.Blue
                "delivery" -> Color(0xFF4CAF50) // Green
                "free" -> Color(0xFFFF9800) // Orange
                "others" -> Color.Gray
                "invite" -> Color(0xFF00b0b3) // Cyan
                "trade" -> Color(0xFF9C27B0) // Purple
                "advice" -> Color(0xFF607D8B) // Blue Grey
                "event" -> Color(0xFFFFC107) // Amber
                "study" -> Color(0xFF2196F3) // Light Blue
                "borrow" -> Color(0xFFf305ff) // Pink
                "food" -> Color(0xFFAFBB00) // Lime
                else -> Color.LightGray
            }
            val backgroundColor = if (isSelected) originalColor else Color.LightGray
            val text = when (category) {
                "urgent" -> "Urgent"
                "helper" -> "Helper"
                "delivery" -> "Delivery"
                "free" -> "Free"
                "others" -> "Others"
                "invite" -> "Invite"
                "trade" -> "Trade"
                "advice" -> "Advice"
                "event" -> "Event"
                "study" -> "Study"
                "borrow" -> "Borrow"
                "food" -> "Food"
                else -> category.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(color = backgroundColor) // Determine background based on isSelected
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .then(if (onCategoryClick != {}) Modifier.clickable { onCategoryClick(categoryString) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}