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
                /*
                "urgent" -> Color(0xFFE57373)
                "helper" -> Color(0xFF81D4FA) // Soft Light Blue
                "delivery" -> Color(0xFFA5D6A7) // Soft Green
                "free" -> Color(0xFFFFCC80) // Soft Orange
                "others" -> Color(0xFFB0BEC5) // Soft Blue Grey
                "invite" -> Color(0xFF80DEEA) // Soft Cyan
                "trade" -> Color(0xFFCE93D8) // Soft Purple
                "advice" -> Color(0xFF398f86) // Lighter Grey
                "event" -> Color(0xFFccab41) // Soft Amber
                "study" -> Color(0xFF90CAF9) // Softer Blue
                "borrow" -> Color(0xFFF48FB1) // Soft Pink
                "food" -> Color(0xFFa3ba88) // Softer Lime
                */
                else -> Color.Gray
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(color = backgroundColor) // Determine background based on isSelected
                    .padding(horizontal = 6.dp, vertical = 0.dp)
                    .then(if (onCategoryClick != {}) Modifier.clickable { onCategoryClick(categoryString) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}