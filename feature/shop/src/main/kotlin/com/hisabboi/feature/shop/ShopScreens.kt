package com.hisabboi.feature.shop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.Business
import com.hisabboi.model.BusinessType
import com.hisabboi.model.Geo
import com.hisabboi.model.Money
import com.hisabboi.model.PaymentProfile
import com.hisabboi.model.PaymentQrContent
import com.hisabboi.model.PaymentRepository
import com.hisabboi.model.PublicShop
import com.hisabboi.model.PublicShopRepository
import com.hisabboi.model.label
import com.hisabboi.ui.ChipRow
import com.hisabboi.ui.EmptyHint
import com.hisabboi.ui.Files
import com.hisabboi.ui.Images
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.QrImage
import com.hisabboi.ui.SimpleScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShopEditorViewModel @Inject constructor(
    private val session: ActiveBusinessSource,
) : ViewModel() {
    val active = session.active
    var name by mutableStateOf("")
    var owner by mutableStateOf("")
    var phone by mutableStateOf("")
    var type by mutableStateOf(BusinessType.GROCERY)
    var address by mutableStateOf("")
    var hours by mutableStateOf("")
    var lat by mutableStateOf("")
    var lng by mutableStateOf("")
    var photo by mutableStateOf("")
    var shopPhoto by mutableStateOf("")
    var publicOptIn by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun load(business: Business) {
        if (loaded && name == business.name) return
        name = business.name
        owner = business.ownerName
        phone = business.phone
        type = business.type
        address = business.address
        hours = business.openingHours
        lat = business.latitude?.toString().orEmpty()
        lng = business.longitude?.toString().orEmpty()
        photo = business.photoBase64
        shopPhoto = business.shopPhotoBase64
        publicOptIn = business.publicOptIn
        loaded = true
    }

    fun save() {
        val current = session.active.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            session.updateBusiness(
                current.copy(
                    name = name.trim(),
                    ownerName = owner.trim(),
                    phone = phone.trim(),
                    type = type,
                    address = address.trim(),
                    openingHours = hours.trim(),
                    latitude = lat.toDoubleOrNull(),
                    longitude = lng.toDoubleOrNull(),
                    photoBase64 = photo,
                    shopPhotoBase64 = shopPhoto,
                    publicOptIn = publicOptIn,
                ),
            ).onSuccess { message = "সেভ হয়েছে" }.onFailure { message = it.message }
        }
    }
}

@Composable
fun BusinessEditorScreen(onBack: () -> Unit, mapsKey: String, viewModel: ShopEditorViewModel = hiltViewModel()) {
    val business by viewModel.active.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedOnce(business) { if (it != null) viewModel.load(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.shopPhoto = Images.jpeg(context, uri, 640, 60)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                viewModel.lat = loc.latitude.toString()
                viewModel.lng = loc.longitude.toString()
            }
        }
    }
    SimpleScaffold("ব্যবসার প্রোফাইল", onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(viewModel.name, { viewModel.name = it }, label = { Text("ব্যবসার নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.owner, { viewModel.owner = it }, label = { Text("মালিকের নাম") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.phone, { viewModel.phone = it }, label = { Text("ফোন") }, modifier = Modifier.fillMaxWidth())
            ChipRow(BusinessType.entries.map { it.label() to (viewModel.type == it) }) { index ->
                viewModel.type = BusinessType.entries[index]
            }
            OutlinedTextField(viewModel.address, { viewModel.address = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.hours, { viewModel.hours = it }, label = { Text("খোলার সময়") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.lat, { viewModel.lat = it }, label = { Text("অক্ষাংশ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(viewModel.lng, { viewModel.lng = it }, label = { Text("দ্রাঘিমাংশ") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                if (fine == PackageManager.PERMISSION_GRANTED) {
                    permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }) { Text("বর্তমান অবস্থান") }
            val latitude = viewModel.lat.toDoubleOrNull()
            val longitude = viewModel.lng.toDoubleOrNull()
            if (mapsKey.isBlank()) {
                Text("ম্যাপ দেখতে local.properties-এ MAPS_API_KEY দিন। ঠিকানা ও অবস্থান এখানেই সেভ হবে। দিকনির্দেশ বাটন কী ছাড়াই গুগল ম্যাপ খোলে।")
            } else if (latitude != null && longitude != null) {
                ShopMap(latitude, longitude, mapsKey) { la, ln ->
                    viewModel.lat = la.toString()
                    viewModel.lng = ln.toString()
                }
            }
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("দোকানের ছবি") }
            RowSwitch("সবার জন্য দোকান পেজ", viewModel.publicOptIn) { viewModel.publicOptIn = it }
            PrimaryButton("সেভ", viewModel::save, viewModel.name.isNotBlank())
            business?.let { biz ->
                TextButton(onClick = {
                    val link = "hisabboi://shop/${biz.id}"
                    Files.shareText(context, "${biz.name}\n$link")
                }) { Text("দোকান পেজের লিংক") }
            }
            viewModel.message?.let { Text(it) }
        }
    }
}

@Composable
private fun ShopMap(lat: Double, lng: Double, mapsKey: String, onPick: (Double, Double) -> Unit) {
    if (mapsKey.isBlank()) return
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 15f) }
    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        cameraPositionState = camera,
        onMapClick = { onPick(it.latitude, it.longitude) },
    ) {
        Marker(state = MarkerState(LatLng(lat, lng)), title = "দোকান")
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
private fun LaunchedOnce(business: Business?, block: (Business?) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(business?.id) { block(business) }
}

@HiltViewModel
class PublicShopViewModel @Inject constructor(
    private val shops: PublicShopRepository,
    private val session: ActiveBusinessSource,
) : ViewModel() {
    private val cache = mutableMapOf<String, kotlinx.coroutines.flow.StateFlow<PublicShop?>>()
    fun shop(id: String): kotlinx.coroutines.flow.StateFlow<PublicShop?> = cache.getOrPut(id) {
        shops.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
    fun mine(): String? = session.user.value?.uid
}

@Composable
fun PublicShopScreen(shopId: String, onBack: () -> Unit, viewModel: PublicShopViewModel = hiltViewModel()) {
    val shop by viewModel.shop(shopId).collectAsStateWithLifecycle()
    val context = LocalContext.current
    SimpleScaffold(shop?.name ?: "দোকান", onBack) { padding ->
        val place = shop
        if (place == null) {
            EmptyHint("দোকান নেই", "মালিক দোকান পেজ চালু করেননি।")
        } else {
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(place.type.label())
                if (place.verified) Text("ভেরিফাইড দোকান")
                Text(place.address)
                Text(place.phone)
                Text(place.openingHours)
                val lat = place.latitude
                val lng = place.longitude
                if (lat != null && lng != null) {
                    PrimaryButton("দিকনির্দেশ", { Files.directions(context, lat, lng) })
                }
                PaymentBlock(place.payment, place.name)
            }
        }
    }
}

@Composable
private fun PaymentBlock(payment: PaymentProfile, shopName: String) {
    if (!payment.hasAny()) return
    Text("পেমেন্ট সরাসরি দোকানদারের অ্যাকাউন্টে। এই অ্যাপ টাকা গ্রহণ করে না।")
    QrImage(PaymentQrContent.build(payment, shopName), Modifier.fillMaxWidth())
    if (payment.bkash.isNotBlank()) Text("bKash ${payment.bkash}")
    if (payment.nagad.isNotBlank()) Text("Nagad ${payment.nagad}")
    if (payment.rocket.isNotBlank()) Text("Rocket ${payment.rocket}")
    if (payment.bankAccount.isNotBlank()) Text("${payment.bankName} ${payment.bankAccount}")
    if (payment.holderName.isNotBlank()) Text(payment.holderName)
}

@HiltViewModel
class NearbyViewModel @Inject constructor(
    shops: PublicShopRepository,
) : ViewModel() {
    val shops = shops.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var here by mutableStateOf<Pair<Double, Double>?>(null)
}

@Composable
fun NearbyScreen(onBack: () -> Unit, onOpen: (String) -> Unit, mapsKey: String, viewModel: NearbyViewModel = hiltViewModel()) {
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) viewModel.here = loc.latitude to loc.longitude
        }
    }
    val origin = viewModel.here
    val sorted = remember(shops, origin) {
        if (origin == null) shops else shops.sortedBy { shop ->
            val lat = shop.latitude
            val lng = shop.longitude
            if (lat == null || lng == null) Double.MAX_VALUE else Geo.km(origin.first, origin.second, lat, lng)
        }
    }
    SimpleScaffold("আশপাশের দোকান", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            TextButton(onClick = { permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("আমার অবস্থান") }
            if (mapsKey.isNotBlank() && origin != null) {
                ShopMap(origin.first, origin.second, mapsKey) { _, _ -> }
            }
            if (sorted.isEmpty()) EmptyHint("কোনো পাবলিক দোকান নেই", "দোকানদার প্রোফাইলে ‘সবার জন্য দোকান পেজ’ চালু করলে এখানে দেখা যাবে।")
            LazyColumn {
                items(sorted, key = { it.id }) { shop ->
                    val distance = origin?.let { here ->
                        val lat = shop.latitude
                        val lng = shop.longitude
                        if (lat != null && lng != null) Money.digits("%.1f কিমি".format(Geo.km(here.first, here.second, lat, lng))) else ""
                    }.orEmpty()
                    ListItem(
                        headlineContent = { Text(shop.name + if (shop.verified) "  · ভেরিফাইড" else "") },
                        supportingContent = { Text("${shop.address} $distance") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        trailingContent = { TextButton(onClick = { onOpen(shop.id) }) { Text("দেখুন") } },
                    )
                }
            }
        }
    }
}
