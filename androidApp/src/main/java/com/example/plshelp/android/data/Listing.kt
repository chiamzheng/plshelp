import com.google.firebase.Timestamp // Ensure Timestamp is imported
import android.os.Parcelable // For passing via SavedStateHandle
import kotlinx.parcelize.Parcelize // For Parcelize annotation

@Parcelize // Add @Parcelize for Parcelable implementation
data class Listing(
    val id: String,
    val category: String,
    val coord: List<Double>,       // Primary/Pickup Location (Lat, Lng)
    val subtitle: String,
    val description: String,
    val imgURL: String? = null,    // Optional image URL
    val ownerID: String,
    val ownerName: String,
    val price: String,
    val radius: Long,
    val title: String,
    val timestamp: Timestamp? = null, // Firebase Timestamp
    val status: String = "active", // Listing status (e.g., active, fulfilled, cancelled)
    val deliveryCoord: List<Double>? = null, // Optional Delivery Location (Lat, Lng)
    val acceptedBy: List<String> = emptyList(), // NEW: List of UIDs who accepted
    val fulfilledBy: String? = null // NEW: UID of the user who fulfilled the request
) : Parcelable // Implement Parcelable