package com.example.plshelp.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.padding // Keep this for applying scaffold padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.example.plshelp.android.ui.screens.RedeemScreen
import com.example.plshelp.android.ui.screens.TransactionHistoryScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import android.util.Log
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import Listing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plshelp.android.data.ListingsViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.plshelp.android.ui.screens.AcceptedRequestsScreen
import com.google.firebase.auth.FirebaseUser
import com.example.plshelp.android.ui.screens.ChatScreen
import com.example.plshelp.android.data.ChatType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
    private lateinit var var_db: FirebaseFirestore

    private var _firebaseUser by mutableStateOf<FirebaseUser?>(null)

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        Log.d("UID_DEBUG_AUTH", "AuthStateListener: User changed: ${user?.uid ?: "null"}. Email: ${user?.email ?: "null"}")
        _firebaseUser = user
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        auth = FirebaseAuth.getInstance()
        var_db = FirebaseFirestore.getInstance()

        auth.addAuthStateListener(authStateListener)
        _firebaseUser = auth.currentUser

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()

                val firebaseUser = _firebaseUser
                val isLoggedIn by remember(firebaseUser) { mutableStateOf(firebaseUser != null) }

                var showRegistration by remember { mutableStateOf(false) }
                var loginErrorMessage by remember { mutableStateOf<String?>(null) }
                var registerErrorMessage by remember { mutableStateOf<String?>(null) }
                var showForgotPassword by remember { mutableStateOf(false) }
                var forgotPasswordErrorMessage by remember { mutableStateOf<String?>(null) }

                val currentUserId: String = remember(firebaseUser) {
                    val id = firebaseUser?.uid ?: ""
                    Log.d("UID_DEBUG", "MainActivity recomposition: currentUserId derived from firebaseUser: $id")
                    id
                }

                val globalUserNameMutableState = remember { mutableStateOf("") }

                LaunchedEffect(firebaseUser) {
                    Log.d("UID_DEBUG", "LaunchedEffect(firebaseUser) triggered. Key changed to: ${firebaseUser?.uid ?: "null"}")

                    if (firebaseUser != null) {
                        val uid = firebaseUser.uid
                        try {
                            val userDocument = var_db.collection("users").document(uid).get().await()
                            val fetchedName = userDocument.getString("name") ?: firebaseUser.displayName ?: "Anonymous"
                            globalUserNameMutableState.value = fetchedName
                            Log.d("UID_DEBUG", "Fetched user name from Firestore for UID $uid: $fetchedName")
                        } catch (e: Exception) {
                            Log.e("UID_DEBUG", "Error fetching user name for UID $uid: ${e.message}", e)
                            globalUserNameMutableState.value = firebaseUser.displayName ?: "Anonymous"
                            Log.d("UID_DEBUG", "Falling back to Firebase Auth display name: ${globalUserNameMutableState.value}")
                        }
                    } else {
                        globalUserNameMutableState.value = ""
                        Log.d("UID_DEBUG", "firebaseUser is null (signed out). Setting globalUserNameMutableState to empty.")
                    }
                    Log.d("UID_DEBUG", "globalUserNameMutableState.value after LaunchedEffect update: ${globalUserNameMutableState.value}")
                }

                val listingsViewModel: ListingsViewModel = viewModel(
                    key = currentUserId,
                    factory = ListingsViewModel.Factory(currentUserId)
                )

                LaunchedEffect(listingsViewModel) {
                    Log.d("UID_DEBUG", "LaunchedEffect(listingsViewModel) triggered. ViewModel hash: ${listingsViewModel.hashCode()}. Triggering refresh.")
                    listingsViewModel.refreshListings()
                }

                Log.d("UID_DEBUG", "ListingsViewModel instance (hashCode): ${listingsViewModel.hashCode()}")
                Log.d("UID_DEBUG", "ListingsViewModel created/re-created with ID: $currentUserId")


                CompositionLocalProvider(
                    LocalUserId provides currentUserId,
                    LocalUserName provides globalUserNameMutableState
                ){
                    if (isLoggedIn) {
                        Scaffold(
                            bottomBar = {
                                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                                val showBottomNav = currentRoute != "login" &&
                                        currentRoute != "registration" &&
                                        currentRoute != "forgotPassword" &&
                                        !currentRoute.orEmpty().startsWith("chatScreen")

                                if (showBottomNav) {
                                    BottomNavigationBar(navController = navController)
                                }
                            }
                        ) { paddingValuesFromMainActivityScaffold ->
                            NavHost(
                                navController = navController,
                                startDestination = BottomNavItem.Listings.route,
                                modifier = Modifier.padding(paddingValuesFromMainActivityScaffold)
                            ) {
                                composable(BottomNavItem.Listings.route) {
                                    ListingsScreen(
                                        viewModel = listingsViewModel,
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
                                            onNavigateToAcceptedRequests = { currentListingId, ownerId ->
                                                navController.navigate("acceptedRequests/$currentListingId/$ownerId")
                                            },
                                            onNavigateToChat = { participantIds, currentListingId, chatType ->
                                                val participantsJson = Gson().toJson(participantIds)
                                                navController.navigate(
                                                    "chatScreen/" +
                                                            "${currentListingId}/" +
                                                            "${participantsJson.encodeURL()}/" +
                                                            "${chatType.name}"
                                                )
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

                                composable(
                                    "chatScreen/{listingId}/{participantsJson}/{chatType}",
                                    arguments = listOf(
                                        navArgument("listingId") { type = NavType.StringType },
                                        navArgument("participantsJson") { type = NavType.StringType },
                                        navArgument("chatType") { type = NavType.EnumType(ChatType::class.java) }
                                    )
                                ) { backStackEntry ->
                                    val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
                                    val participantsJson = backStackEntry.arguments?.getString("participantsJson") ?: "[]"
                                    val chatType = backStackEntry.arguments?.getSerializable("chatType") as? ChatType ?: ChatType.ONE_ON_ONE

                                    val participants: List<String> = try {
                                        Gson().fromJson(participantsJson.decodeURL(), object : TypeToken<List<String>>() {}.type)
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Error deserializing participantsJson: $e")
                                        emptyList()
                                    }

                                    ChatScreen(
                                        listingId = listingId,
                                        participantIds = participants,
                                        chatType = chatType,
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }

                                composable(BottomNavItem.Profile.route) {
                                    ProfileScreen(
                                        onSignOut = {
                                            Log.d("UID_DEBUG", "Signing out user... (before auth.signOut())")
                                            auth.signOut()
                                        },
                                        onNavigateToDetail = { listing ->
                                            navController.currentBackStackEntry?.savedStateHandle?.set("listing", listing)
                                            navController.navigate("listingDetail/${listing.id}")
                                        }
                                    )
                                }

                                composable(BottomNavItem.CreateRequest.route) {
                                    CreateRequestScreen(onNavigateToListings = {
                                        listingsViewModel.onNewListingCreated()
                                        navController.navigate(BottomNavItem.Listings.route) {
                                            popUpTo(BottomNavItem.Listings.route) {
                                                inclusive = true
                                            }
                                        }
                                    })
                                }

                                composable(BottomNavItem.Redeem.route) {
                                    RedeemScreen(
                                        onNavigateToHistory = {
                                            navController.navigate("transactionHistory")
                                        }
                                    )
                                }

                                composable("transactionHistory") {
                                    TransactionHistoryScreen(onBack = { navController.popBackStack() })
                                }

                            }
                        }
                    } else {
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
                                        popUpTo(0) { inclusive = true }
                                    }
                                } else {
                                    Log.d("UID_DEBUG", "Not logged in, but already on auth screen ($currentRoute). No navigation needed.")
                                }
                            }
                        }

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
                                        navController.popBackStack()
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

    override fun onDestroy() {
        super.onDestroy()
        auth.removeAuthStateListener(authStateListener)
    }

    @Composable
    fun BottomNavigationBar(navController: NavController) {
        val items = listOf(
            BottomNavItem.Listings,
            BottomNavItem.CreateRequest,
            BottomNavItem.Redeem,
            BottomNavItem.Profile
        )

        NavigationBar(
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
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            item.label,
                            fontSize = 10.sp,
                            lineHeight = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selected = isSelected,
                    onClick = {
                        if (item.route == BottomNavItem.Listings.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        } else {
                            navController.navigate(item.route) {
                                navController.graph.startDestinationRoute?.let { route ->
                                    popUpTo(route) { saveState = true }
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.padding(vertical = 0.dp),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.2f)
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

// Extension functions for URL encoding/decoding strings
fun String.encodeURL(): String = java.net.URLEncoder.encode(this, "UTF-8")
fun String.decodeURL(): String = java.net.URLDecoder.decode(this, "UTF-8")
