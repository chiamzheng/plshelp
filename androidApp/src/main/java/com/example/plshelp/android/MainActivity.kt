package com.example.plshelp.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plshelp.android.data.LocationManager
import com.example.plshelp.android.data.LocationService
import com.example.plshelp.android.ui.navigation.BottomNavItem
import com.example.plshelp.android.ui.screens.CreateRequestScreen
import com.example.plshelp.android.ui.screens.ForgotPasswordScreen
import com.example.plshelp.android.ui.screens.ListingDetailScreen
import com.example.plshelp.android.ui.screens.ListingsScreen
import com.example.plshelp.android.ui.screens.LocationScreen
import com.example.plshelp.android.ui.screens.LoginScreen
import com.example.plshelp.android.ui.screens.MyApplicationTheme
import com.example.plshelp.android.ui.screens.ProfileScreen
import com.example.plshelp.android.ui.screens.RegistrationScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import android.util.Log
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val fineLocationPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("MainActivity", "Foreground location permission granted.")
            } else {
                Toast.makeText(this, "Fine location permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        auth = FirebaseAuth.getInstance()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
                var showRegistration by remember { mutableStateOf(false) }
                var loginErrorMessage by remember { mutableStateOf<String?>(null) }
                var registerErrorMessage by remember { mutableStateOf<String?>(null) }
                var showForgotPassword by remember { mutableStateOf(false) }
                var forgotPasswordErrorMessage by remember { mutableStateOf<String?>(null) }

                // State to trigger navigation after successful login/registration
                var navigateAfterAuth by remember { mutableStateOf(false) }

                if (isLoggedIn) {
                    Scaffold(
                        bottomBar = {
                            BottomNavigationBar(navController = navController)
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = BottomNavItem.Location.route,
                            Modifier.padding(paddingValues)
                        ) {
                            composable(BottomNavItem.Location.route) {
                                LocationScreen(
                                    onCheckLocation = { updateText ->
                                        LocationManager.checkUserLocation(context) { result ->
                                            updateText(result)
                                        }
                                    },
                                    paddingValues = paddingValues,
                                    modifier = Modifier.padding(paddingValues)
                                )
                            }
                            composable(BottomNavItem.Listings.route) {
                                ListingsScreen(onNavigateToDetail = { listingId ->
                                    navController.navigate("listingDetail/$listingId")
                                })
                            }
                            composable("listingDetail/{listingId}") { backStackEntry ->
                                val listingId = backStackEntry.arguments?.getString("listingId")
                                if (listingId != null) {
                                    ListingDetailScreen(listingId = listingId)
                                }
                            }
                            composable(BottomNavItem.Profile.route) {
                                ProfileScreen(
                                    onSignOut = {
                                        auth.signOut()
                                        isLoggedIn = false
                                        navigateAfterAuth = true // Trigger navigation
                                    },
                                    modifier = Modifier.padding(paddingValues)
                                )
                            }
                            composable(BottomNavItem.CreateRequest.route) {
                                CreateRequestScreen(onNavigateToListings = {
                                    navController.navigate(BottomNavItem.Listings.route) {
                                        popUpTo(BottomNavItem.Listings.route) {
                                            inclusive = true
                                        }
                                    }
                                })
                            }
                        }
                    }
                } else {
                    if (showRegistration) {
                        RegistrationScreen(
                            onRegisterSuccess = {
                                showRegistration = false
                                navigateAfterAuth = true // Trigger navigation
                                registerErrorMessage = null //Clear error on success
                            },
                            onRegisterFailure = { errorMessage ->
                                registerErrorMessage = errorMessage
                            },
                            onRegister = { email, password ->
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this) { task ->
                                        if (task.isSuccessful) {
                                            showRegistration = false
                                            navigateAfterAuth = true // Trigger navigation
                                            registerErrorMessage = null//Clear error on success
                                        } else {
                                            val exception = task.exception
                                            if (exception is FirebaseAuthException) {
                                                registerErrorMessage = exception.message
                                            } else {
                                                registerErrorMessage = "Registration failed."
                                            }
                                        }
                                    }
                            },
                            onBackToLogin = { showRegistration = false },
                            registerErrorMessage = registerErrorMessage//Pass error message
                        )
                    } else if (showForgotPassword) {
                        ForgotPasswordScreen(
                            onResetSuccess = {
                                showForgotPassword = false
                                forgotPasswordErrorMessage = null
                            },
                            onResetFailure = { errorMessage ->
                                forgotPasswordErrorMessage = errorMessage
                            },
                            onBackToLogin = {
                                showForgotPassword = false
                                forgotPasswordErrorMessage = null
                            },
                            forgotPasswordErrorMessage = forgotPasswordErrorMessage
                        )
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                isLoggedIn = true
                                loginErrorMessage = null //clear error on success
                                navigateAfterAuth = true
                            },
                            onLoginFailure = { errorMessage ->
                                loginErrorMessage = errorMessage
                            },
                            onRegisterClick = { showRegistration = true },
                            onLogin = { email, password ->
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this) { task ->
                                        if (task.isSuccessful) {
                                            isLoggedIn = true
                                            loginErrorMessage = null //clear error on success
                                            navigateAfterAuth = true
                                        } else {
                                            loginErrorMessage = "Invalid email or password." //Simplified error
                                        }
                                    }
                            },
                            loginErrorMessage = loginErrorMessage,
                            onForgotPasswordClick = {
                                showForgotPassword = true
                                forgotPasswordErrorMessage = null
                            }
                        )
                    }
                }

                // Navigate after auth if triggered
                if (navigateAfterAuth) {
                    LaunchedEffect(Unit) {
                        navController.navigate(BottomNavItem.Location.route) {
                            popUpTo(0)
                        }
                        navigateAfterAuth = false // Reset trigger
                    }
                }
            }
        }

        checkForegroundLocationPermission()
    }

    @Composable
    fun BottomNavigationBar(navController: androidx.navigation.NavController) {
        val items = listOf(
            BottomNavItem.Location,
            BottomNavItem.Listings,
            BottomNavItem.CreateRequest,
            BottomNavItem.Profile
        )

        NavigationBar {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            items.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = currentRoute == item.route,
                    onClick = {
                        navController.navigate(item.route) {
                            navController.graph.startDestinationRoute?.let { route ->
                                popUpTo(route) {
                                    saveState = true
                                }
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }

    private fun checkForegroundLocationPermission() { // Added function
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Foreground location permission already granted.")
        } else {
            Log.d("MainActivity", "Requesting foreground location permission.")
            fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}