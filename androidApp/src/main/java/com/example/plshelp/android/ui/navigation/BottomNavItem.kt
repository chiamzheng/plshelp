package com.example.plshelp.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
//import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
   // object Location : BottomNavItem("location", Icons.Default.LocationOn, "Location") //Used to test location functionality, removed for live app
    object Listings : BottomNavItem("listings", Icons.Default.List, "Listings")
    object CreateRequest : BottomNavItem("create_request", Icons.Default.Add, "Create")
    object Redeem : BottomNavItem("redeem", Icons.Default.ShoppingCart, "Redeem")
    object Profile : BottomNavItem("profile", Icons.Default.Person, "Profile")
}