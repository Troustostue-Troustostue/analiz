@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import com.butcemiz.app.data.AccountType
import com.butcemiz.app.data.SavingGroup

/** Kategori simgeleri (anahtar → vektör). Anahtarlar senkronize edildiği için değiştirilmemeli. */
object AppIcons {
    val map: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "cart" to Icons.Rounded.ShoppingCart,
        "bolt" to Icons.Rounded.Bolt,
        "water" to Icons.Rounded.WaterDrop,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "wifi" to Icons.Rounded.Wifi,
        "phone" to Icons.Rounded.Smartphone,
        "home" to Icons.Rounded.Home,
        "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "bus" to Icons.Rounded.DirectionsBus,
        "taxi" to Icons.Rounded.LocalTaxi,
        "train" to Icons.Rounded.Train,
        "parking" to Icons.Rounded.LocalParking,
        "restaurant" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.LocalCafe,
        "fastfood" to Icons.Rounded.Fastfood,
        "bakery" to Icons.Rounded.BakeryDining,
        "bar" to Icons.Rounded.LocalBar,
        "health" to Icons.Rounded.LocalHospital,
        "medication" to Icons.Rounded.Medication,
        "clothes" to Icons.Rounded.Checkroom,
        "mall" to Icons.Rounded.LocalMall,
        "store" to Icons.Rounded.Store,
        "school" to Icons.Rounded.School,
        "book" to Icons.Rounded.MenuBook,
        "movie" to Icons.Rounded.Movie,
        "music" to Icons.Rounded.MusicNote,
        "game" to Icons.Rounded.SportsEsports,
        "sports" to Icons.Rounded.FitnessCenter,
        "chair" to Icons.Rounded.Chair,
        "kitchen" to Icons.Rounded.Kitchen,
        "tools" to Icons.Rounded.Handyman,
        "build" to Icons.Rounded.Build,
        "garden" to Icons.Rounded.Yard,
        "devices" to Icons.Rounded.Devices,
        "spa" to Icons.Rounded.Spa,
        "beauty" to Icons.Rounded.Face,
        "brush" to Icons.Rounded.Brush,
        "gift" to Icons.Rounded.CardGiftcard,
        "donate" to Icons.Rounded.VolunteerActivism,
        "subscriptions" to Icons.Rounded.Subscriptions,
        "child" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "card" to Icons.Rounded.CreditCard,
        "tax" to Icons.Rounded.RequestQuote,
        "insurance" to Icons.Rounded.Shield,
        "receipt" to Icons.Rounded.Receipt,
        "travel" to Icons.Rounded.Flight,
        "smoking" to Icons.Rounded.SmokingRooms,
        "work" to Icons.Rounded.Work,
        "payments" to Icons.Rounded.Payments,
        "celebration" to Icons.Rounded.Celebration,
        "trending" to Icons.Rounded.TrendingUp,
        "handshake" to Icons.Rounded.Handshake,
        "sell" to Icons.Rounded.Sell,
        "savings" to Icons.Rounded.Savings,
        "money" to Icons.Rounded.AttachMoney,
        "bank" to Icons.Rounded.AccountBalance,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "diamond" to Icons.Rounded.Diamond,
        "euro" to Icons.Rounded.Euro,
        "notebook" to Icons.Rounded.Book,
        "more" to Icons.Rounded.MoreHoriz,
    )

    fun of(key: String?): ImageVector = map[key] ?: Icons.Rounded.MoreHoriz

    fun account(type: AccountType): ImageVector = when (type) {
        AccountType.BANK -> Icons.Rounded.AccountBalance
        AccountType.CASH -> Icons.Rounded.Payments
        AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
        AccountType.OTHER -> Icons.Rounded.AccountBalanceWallet
    }

    fun savingGroup(g: SavingGroup): ImageVector = when (g) {
        SavingGroup.CASH -> Icons.Rounded.Savings
        SavingGroup.FX -> Icons.Rounded.Euro
        SavingGroup.GOLD -> Icons.Rounded.Diamond
        SavingGroup.METAL -> Icons.Rounded.Diamond
        SavingGroup.INVESTMENT -> Icons.Rounded.TrendingUp
        SavingGroup.OTHER -> Icons.Rounded.Savings
    }
}
