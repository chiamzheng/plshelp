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
import Listing // Assuming your Listing data class is available here or correctly imported
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.data.ListingsViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.plshelp.android.ui.screens.AcceptedRequestsScreen
import com.google.firebase.auth.FirebaseUser



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

    // This mutable state will hold the current FirebaseUser, and its changes will trigger recomposition.
    private var _firebaseUser by mutableStateOf<FirebaseUser?>(null)

    // Define AuthStateListener to update our reactive _firebaseUser state
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        Log.d("UID_DEBUG_AUTH", "AuthStateListener: User changed: ${user?.uid ?: "null"}. Email: ${user?.email ?: "null"}")
        _firebaseUser = user // Update the reactive state, triggering recomposition
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Attach AuthStateListener and initialize _firebaseUser
        auth.addAuthStateListener(authStateListener)
        _firebaseUser = auth.currentUser // Initialize with current user on start

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                // Observe our reactive _firebaseUser state directly
                val firebaseUser = _firebaseUser // Directly observe the reactive state
                val isLoggedIn by remember(firebaseUser) { mutableStateOf(firebaseUser != null) }

                var showRegistration by remember { mutableStateOf(false) }
                var loginErrorMessage by remember { mutableStateOf<String?>(null) }
                var registerErrorMessage by remember { mutableStateOf<String?>(null) }
                var showForgotPassword by remember { mutableStateOf(false) }
                var forgotPasswordErrorMessage by remember { mutableStateOf<String?>(null) }

                // This will now ALWAYS be synchronous with our reactive `firebaseUser.uid`
                val currentUserId: String = remember(firebaseUser) {
                    val id = firebaseUser?.uid ?: ""
                    Log.d("UID_DEBUG", "MainActivity recomposition: currentUserId derived from firebaseUser: $id")
                    id
                }

                // This MutableState will hold the user's name, provided via CompositionLocal.
                val globalUserNameMutableState = remember { mutableStateOf("") }

                // --- IMPORTANT LOGGING START ---
                Log.d("UID_DEBUG", "MainActivity recomposition: Top of setContent block.")
                Log.d("UID_DEBUG", "  firebaseUser?.uid: ${firebaseUser?.uid ?: "null"}")
                Log.d("UID_DEBUG", "  isLoggedIn (derived): $isLoggedIn")
                Log.d("UID_DEBUG", "  currentUserId (derived): $currentUserId")
                Log.d("UID_DEBUG", "  globalUserNameMutableState.value (before LE): ${globalUserNameMutableState.value}")
                // --- IMPORTANT LOGGING END ---


                // LaunchedEffect to fetch/update the user's name when firebaseUser changes
                LaunchedEffect(firebaseUser) {
                    Log.d("UID_DEBUG", "LaunchedEffect(firebaseUser) triggered. Key changed to: ${firebaseUser?.uid ?: "null"}")

                    if (firebaseUser != null) {
                        val uid = firebaseUser!!.uid
                        try {
                            val userDocument = db.collection("users").document(uid).get().await()
                            val fetchedName = userDocument.getString("name") ?: firebaseUser.displayName ?: "Anonymous"
                            globalUserNameMutableState.value = fetchedName
                            Log.d("UID_DEBUG", "Fetched user name from Firestore for UID $uid: $fetchedName")
                        } catch (e: Exception) {
                            Log.e("UID_DEBUG", "Error fetching user name for UID $uid: ${e.message}", e)
                            globalUserNameMutableState.value = firebaseUser.displayName ?: "Anonymous" // Fallback
                            Log.d("UID_DEBUG", "Falling back to Firebase Auth display name: ${globalUserNameMutableState.value}")
                        }
                    } else {
                        globalUserNameMutableState.value = "" // Clear username if no user
                        Log.d("UID_DEBUG", "firebaseUser is null (signed out). Setting globalUserNameMutableState to empty.")
                    }
                    Log.d("UID_DEBUG", "globalUserNameMutableState.value after LaunchedEffect update: ${globalUserNameMutableState.value}")
                }


                // --- THE FIX: Instantiate the ListingsViewModel here, outside the NavHost. ---
                // It will be scoped to the MainActivity's content (or AppScreen if you had one).
                val listingsViewModel: ListingsViewModel = viewModel(
                    // Key the ViewModel by currentUserId. This ensures a new VM is created
                    // if the user ID *fundamentally changes* (e.g., a different user logs in).
                    key = currentUserId,
                    factory = ListingsViewModel.Factory(currentUserId)
                )

                // --- IMPORTANT: Trigger the initial refresh here, tied to the ViewModel's existence ---
                LaunchedEffect(listingsViewModel) {
                    // This LaunchedEffect will run only once when the listingsViewModel instance is created
                    // (or re-created if currentUserId changes).
                    Log.d("UID_DEBUG", "LaunchedEffect(listingsViewModel) triggered. ViewModel hash: ${listingsViewModel.hashCode()}. Triggering refresh.")
                    listingsViewModel.refreshListings()
                }
                // --- END FIX ---


                // --- IMPORTANT LOGGING START ---
                Log.d("UID_DEBUG", "ListingsViewModel instance (hashCode): ${listingsViewModel.hashCode()}")
                Log.d("UID_DEBUG", "ListingsViewModel created/re-created with ID: $currentUserId")
                // --- IMPORTANT LOGGING END ---


                // Provide the CompositionLocals
                CompositionLocalProvider(
                    LocalUserId provides currentUserId,
                    LocalUserName provides globalUserNameMutableState
                ){
                    // The main content based on login state
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
                                        viewModel = listingsViewModel, // <--- Pass the SAME hoisted ViewModel instance
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
                                            Log.d("UID_DEBUG", "Signing out user... (before auth.signOut())")
                                            auth.signOut()
                                            Log.d("UID_DEBUG", "Signing out user... (after auth.signOut())")
                                        },
                                        onNavigateToDetail = { listing ->
                                            navController.currentBackStackEntry?.savedStateHandle?.set("listing", listing)
                                            navController.navigate("listingDetail/${listing.id}")
                                        }
                                    )
                                }
                                composable(BottomNavItem.CreateRequest.route) {
                                    CreateRequestScreen(onNavigateToListings = {
                                        // This is a good place to trigger refresh for the listings screen
                                        listingsViewModel.onNewListingCreated()
                                        navController.navigate(BottomNavItem.Listings.route) {
                                            popUpTo(BottomNavItem.Listings.route) {
                                                inclusive = true
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    } else { // Not logged in: Show auth screens
                        // This LaunchedEffect ensures navigation to the login screen when isLoggedIn becomes false.
                        LaunchedEffect(isLoggedIn) {
                            if (!isLoggedIn) {
                                val currentRoute = navController.currentBackStackEntry?.destination?.route
                                val isAlreadyOnAuthScreen = when (currentRoute) {
                                    "login", "registration", "forgotPassword" -> true
                                    else -> false
                                }
                                if (!isAlreadyOnAuthScreen) {
                                    Log.d("UID_DEBUG", "Not logged in. Navigating to login screen.")
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true } // Clear back stack
                                    }
                                } else {
                                    Log.d("UID_DEBUG", "Not logged in, but already on auth screen ($currentRoute). No navigation needed.")
                                }
                            }
                        }

                        // NavHost for auth screens
                        NavHost(
                            navController = navController,
                            startDestination = if (showRegistration) "registration" else if (showForgotPassword) "forgotPassword" else "login"
                        ) {
                            composable("login") {
                                LoginScreen(
                                    onLoginSuccess = {
                                        loginErrorMessage = null
                                        Log.d("UID_DEBUG", "Login success callback triggered. AuthStateListener will update FirebaseUser.")
                                    },
                                    onLoginFailure = { errorMessage ->
                                        loginErrorMessage = errorMessage
                                    },
                                    onRegisterClick = {
                                        showRegistration = true
                                        navController.navigate("registration") { popUpTo("login") { inclusive = true } }
                                    },
                                    onLogin = { email, password ->
                                        auth.signInWithEmailAndPassword(email, password)
                                            // *** FIX: Use this@MainActivity for the Activity context ***
                                            .addOnCompleteListener(this@MainActivity) { task ->
                                                if (task.isSuccessful) {
                                                    Log.d("UID_DEBUG", "Auth signInWithEmailAndPassword success. AuthStateListener will process.")
                                                } else {
                                                    loginErrorMessage = "Invalid email or password."
                                                    Log.e("UID_DEBUG", "Auth signInWithEmailAndPassword failed: ${task.exception?.message}")
                                                }
                                            }
                                    },
                                    loginErrorMessage = loginErrorMessage,
                                    onForgotPasswordClick = {
                                        showForgotPassword = true
                                        navController.navigate("forgotPassword") { popUpTo("login") { inclusive = true } }
                                    }
                                )
                            }
                            composable("registration") {
                                RegistrationScreen(
                                    onRegisterSuccess = {
                                        showRegistration = false
                                        registerErrorMessage = null
                                        Log.d("UID_DEBUG", "Registration success callback. AuthStateListener will update FirebaseUser.")
                                    },
                                    onRegisterFailure = { errorMessage ->
                                        registerErrorMessage = errorMessage
                                    },
                                    onRegister = { email, password ->
                                        auth.createUserWithEmailAndPassword(email, password)
                                            // *** FIX: Use this@MainActivity for the Activity context ***
                                            .addOnCompleteListener(this@MainActivity) { task ->
                                                if (task.isSuccessful) {
                                                    Log.d("UID_DEBUG", "Auth createUserWithEmailAndPassword success. AuthStateListener will process.")
                                                } else {
                                                    val exception = task.exception
                                                    if (exception is FirebaseAuthException) {
                                                        registerErrorMessage = exception.message
                                                    } else {
                                                        registerErrorMessage = "Registration failed."
                                                    }
                                                    Log.e("UID_DEBUG", "Auth createUserWithEmailAndPassword failed: ${task.exception?.message}")
                                                }
                                            }
                                    },
                                    onBackToLogin = {
                                        showRegistration = false
                                        navController.popBackStack()
                                    },
                                    registerErrorMessage = registerErrorMessage
                                )
                            }
                            composable("forgotPassword") {
                                ForgotPasswordScreen(
                                    onResetSuccess = {
                                        showForgotPassword = false
                                        forgotPasswordErrorMessage = null
                                        Toast.makeText(this@MainActivity, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                                        navController.popBackStack() // Go back to login after reset
                                    },
                                    onResetFailure = { errorMessage ->
                                        forgotPasswordErrorMessage = errorMessage
                                    },
                                    onBackToLogin = {
                                        showForgotPassword = false
                                        forgotPasswordErrorMessage = null
                                        navController.popBackStack()
                                    },
                                    forgotPasswordErrorMessage = forgotPasswordErrorMessage
                                )
                            }
                        }
                    }
                }
            }
        }

        checkForegroundLocationPermission()
    }

    // Remove the listener when the activity is destroyed to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        auth.removeAuthStateListener(authStateListener)
    }

    @Composable
    fun BottomNavigationBar(navController: NavController) {
        val items = listOf(
            BottomNavItem.Listings,
            BottomNavItem.CreateRequest,
            BottomNavItem.Profile
        )

        NavigationBar(
            // You can set overall padding/modifier for the whole NavigationBar if needed
            modifier = Modifier.height(65.dp)
        ) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            items.forEach { item ->
                val isSelected = currentRoute == item.route
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            // --- Customize Icon Size ---
                            modifier = Modifier.size(24.dp) // Adjust icon size here (e.g., 24.dp, 32.dp)
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            // --- Customize Text Size ---
                            fontSize = 10.sp, // Adjust font size here
                            lineHeight = 10.sp,
                            // You can also change fontWeight, color, etc. based on selection
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = isSelected,
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
                    },
                    // --- Customize Item Padding ---
                    modifier = Modifier.padding(vertical = 0.dp), // Adjust vertical padding for each item
                    // You can also adjust the colors for selected/unselected states
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.2f) // Example indicator color
                    )
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