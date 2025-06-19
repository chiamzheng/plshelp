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
import com.example.plshelp.android.data.LocationService
import com.example.plshelp.android.ui.navigation.BottomNavItem
import com.example.plshelp.android.ui.screens.CreateRequestScreen
import com.example.plshelp.android.ui.screens.ForgotPasswordScreen
import com.example.plshelp.android.ui.screens.ListingDetailScreen
import com.example.plshelp.android.ui.screens.ListingsScreen
import com.example.plshelp.android.ui.screens.LoginScreen
import com.example.plshelp.android.ui.screens.MyApplicationTheme
import com.example.plshelp.android.ui.screens.ProfileScreen
import com.example.plshelp.android.ui.screens.RegistrationScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import Listing
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.data.ListingsViewModel

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import com.example.plshelp.android.ui.screens.AcceptedRequestsScreen


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
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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

                var navigateAfterAuth by remember { mutableStateOf(false) }
                val currentUserId = auth.currentUser?.uid ?: ""
                val globalUserNameState = remember { mutableStateOf("") }

                val listingsViewModel: ListingsViewModel = viewModel(
                    factory = ListingsViewModel.Factory(currentUserId)
                )

                LaunchedEffect(currentUserId) {
                    if (currentUserId.isNotEmpty()) {
                        try {
                            val userDocument = db.collection("users").document(currentUserId).get().await()
                            globalUserNameState.value = userDocument.getString("name") ?: "Anonymous"
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error fetching username: ${e.message}")
                            globalUserNameState.value = "Anonymous"
                        }
                    } else {
                        globalUserNameState.value = ""
                    }
                }

                CompositionLocalProvider(
                    LocalUserId provides currentUserId,
                    LocalUserName provides globalUserNameState
                ){
                    if (isLoggedIn) {
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(navController = navController)
                            }
                        ) { paddingValuesFromScaffold ->
                            NavHost(
                                navController = navController,
                                startDestination = BottomNavItem.Listings.route,
                                modifier = Modifier.padding(paddingValuesFromScaffold)
                            ) {
                                composable(BottomNavItem.Listings.route) {
                                    ListingsScreen(
                                        listings = listingsViewModel.listings.collectAsState().value,
                                        isLoading = listingsViewModel.isLoading.collectAsState().value,
                                        lastFetchTime = listingsViewModel.lastFetchTimeState.value,
                                        onRefresh = { listingsViewModel.refreshListings() },
                                        onNavigateToDetail = { listing ->
                                            navController.currentBackStackEntry?.savedStateHandle?.set("listing", listing)
                                            navController.navigate("listingDetail/${listing.id}")
                                        }
                                    )
                                }
                                composable(
                                    "listingDetail/{listingId}",
                                    arguments = listOf(
                                        navArgument("listingId") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val listingId = backStackEntry.arguments?.getString("listingId")
                                    val initialListing = backStackEntry.savedStateHandle.get<Listing>("listing")

                                    if (listingId != null) {
                                        ListingDetailScreen(
                                            listingId = listingId,
                                            onBackClick = { navController.popBackStack() },
                                            initialListing = initialListing,
                                            onNavigateToAcceptedRequests = { id, ownerId ->
                                                navController.navigate("acceptedRequests/$id/$ownerId")
                                            }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) {
                                            Log.e("MainActivity", "Error: listingId was null for detail screen. Navigating back.")
                                            navController.popBackStack()
                                        }
                                    }
                                }
                                composable(
                                    "acceptedRequests/{listingId}/{ownerId}",
                                    arguments = listOf(
                                        navArgument("listingId") { type = NavType.StringType },
                                        navArgument("ownerId") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val listingId = backStackEntry.arguments?.getString("listingId")
                                    val ownerId = backStackEntry.arguments?.getString("ownerId")
                                    if (listingId != null && ownerId != null) {
                                        AcceptedRequestsScreen(
                                            listingId = listingId,
                                            listingOwnerId = ownerId,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    } else {
                                        LaunchedEffect(Unit) {
                                            Log.e("MainActivity", "Error: listingId or ownerId was null for accepted requests screen. Navigating back.")
                                            navController.popBackStack()
                                        }
                                    }
                                }
                                composable(BottomNavItem.Profile.route) {
                                    ProfileScreen(
                                        onSignOut = {
                                            auth.signOut()
                                            isLoggedIn = false
                                            navigateAfterAuth = true
                                        },
                                        onNavigateToDetail = { listing ->
                                            navController.currentBackStackEntry?.savedStateHandle?.set("listing", listing)
                                            navController.navigate("listingDetail/${listing.id}")
                                        }
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
                    } else { // Not logged in
                        if (showRegistration) {
                            RegistrationScreen(
                                onRegisterSuccess = {
                                    showRegistration = false
                                    navigateAfterAuth = true
                                    registerErrorMessage = null
                                },
                                onRegisterFailure = { errorMessage ->
                                    registerErrorMessage = errorMessage
                                },
                                onRegister = { email, password ->
                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener(this) { task ->
                                            if (task.isSuccessful) {
                                                showRegistration = false
                                                navigateAfterAuth = true
                                                registerErrorMessage = null
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
                                registerErrorMessage = registerErrorMessage
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
                        } else { // Login Screen
                            LoginScreen(
                                onLoginSuccess = {
                                    isLoggedIn = true
                                    loginErrorMessage = null
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
                                                loginErrorMessage = null
                                                navigateAfterAuth = true
                                            } else {
                                                loginErrorMessage = "Invalid email or password."
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

                    if (navigateAfterAuth) {
                        LaunchedEffect(Unit) {
                            navController.navigate(BottomNavItem.Listings.route) {
                                popUpTo(0) {
                                    inclusive = true
                                }
                            }
                            navigateAfterAuth = false
                        }
                    }
                }
            }
        }

        checkForegroundLocationPermission()
    }

    @Composable
    fun BottomNavigationBar(navController: NavController) {
        val items = listOf(
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

    private fun checkForegroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "Foreground location permission already granted.")
        } else {
            Log.d("MainActivity", "Requesting foreground location permission.")
            fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}