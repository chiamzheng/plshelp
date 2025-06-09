import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize

@Parcelize
data class Listing(
    val id: String = "",
    val category: String = "",
    val coord: List<Double> = emptyList(),
    val subtitle: String = "",
    val description: String = "",
    val imgURL: String? = null,
    val ownerID: String = "",
    val ownerName: String = "",
    val price: String = "",
    val radius: Long = 0,
    val title: String = "",
    val timestamp: Timestamp? = null,
    val status: String = "",
    val deliveryCoord: List<Double>? = null

) : Parcelable