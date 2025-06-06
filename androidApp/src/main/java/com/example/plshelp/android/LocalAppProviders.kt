package com.example.plshelp.android

import androidx.compose.runtime.MutableState // Import MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf // Import mutableStateOf

/**
 * CompositionLocal for providing the current authenticated user's ID.
 * Defaults to an empty string if no user is signed in or provided.
 */
val LocalUserId: ProvidableCompositionLocal<String> = compositionLocalOf { "" }

/**
 * CompositionLocal for providing the current authenticated user's name as a reactive state.
 * Defaults to a mutableStateOf holding an empty string.
 */
val LocalUserName: ProvidableCompositionLocal<MutableState<String>> = compositionLocalOf { mutableStateOf("") }