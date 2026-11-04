package com.hisabboi.app

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.messaging.FirebaseMessaging
import com.hisabboi.feature.account.BackupScreen
import com.hisabboi.feature.account.BusinessListScreen
import com.hisabboi.feature.account.CreateBusinessScreen
import com.hisabboi.feature.account.ProfileScreen
import com.hisabboi.feature.account.ReportsScreen
import com.hisabboi.feature.account.SettingsScreen
import com.hisabboi.feature.account.StatementScreen
import com.hisabboi.feature.account.TrashScreen
import com.hisabboi.feature.cashbox.CashEntryScreen
import com.hisabboi.feature.cashbox.CashboxScreen
import com.hisabboi.feature.catalog.InventoryScannerScreen
import com.hisabboi.feature.catalog.ProductFormScreen
import com.hisabboi.feature.catalog.ProductsScreen
import com.hisabboi.feature.catalog.ScannerScreen
import com.hisabboi.feature.ledger.ContactDetailScreen
import com.hisabboi.feature.ledger.ContactEditScreen
import com.hisabboi.feature.ledger.EntryScreen
import com.hisabboi.feature.ledger.LedgerScreen
import com.hisabboi.feature.payments.QrScreen
import com.hisabboi.feature.reminders.ReminderScreen
import com.hisabboi.feature.shop.BusinessEditorScreen
import com.hisabboi.feature.shop.NearbyScreen
import com.hisabboi.feature.shop.PublicShopScreen
import com.hisabboi.feature.trust.AdminScreen
import com.hisabboi.feature.trust.KycScreen
import com.hisabboi.feature.trust.VerificationScreen
import com.hisabboi.model.ActiveBusinessSource
import com.hisabboi.model.AuthGateway
import com.hisabboi.model.LockStore
import com.hisabboi.model.TokenRepository
import com.hisabboi.security.LockScreen
import com.hisabboi.model.NoticeKind
import com.hisabboi.model.UserFacingError
import com.hisabboi.ui.Avatar
import com.hisabboi.ui.HisabTheme
import com.hisabboi.ui.LoginScreen
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
import com.hisabboi.ui.StatusBadge
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var lock: LockStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shopLink = intent?.data?.takeIf { it.host == "shop" }?.lastPathSegment
        setContent {
            HisabTheme {
                Root(lock = lock, shopLink = shopLink)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lock.onForeground()
    }

    override fun onStop() {
        lock.onBackground()
        super.onStop()
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: AuthGateway,
    private val session: ActiveBusinessSource,
    private val tokens: TokenRepository,
    val lock: LockStore,
) : ViewModel() {
    val user = auth.currentUser
    val ready = session.ready
    val active = session.active
    var signingIn = androidx.compose.runtime.mutableStateOf(false)
    var error = androidx.compose.runtime.mutableStateOf<String?>(null)
    var resetPin = androidx.compose.runtime.mutableStateOf(false)

    init {
        viewModelScope.launch {
            auth.currentUser.collect { sessionUser ->
                if (sessionUser == null) return@collect
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    viewModelScope.launch { runCatching { tokens.save(sessionUser.uid, token) } }
                }
            }
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            signingIn.value = true
            error.value = null
            auth.signIn(activity).onFailure { error.value = UserFacingError.auth(it.message) }.also { signingIn.value = false }
        }
    }

    fun resetWithGoogle(activity: Activity) {
        viewModelScope.launch {
            auth.reauthenticate(activity).onSuccess {
                lock.clear()
                resetPin.value = true
            }.onFailure { error.value = UserFacingError.auth(it.message) }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}

@Composable
private fun Root(lock: LockStore, shopLink: String?, viewModel: RootViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val lockState by lock.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user?.uid) {
        if (user != null && Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    when {
        shopLink != null && user == null -> PublicShopScreen(shopLink, onBack = {})
        user == null -> LoginScreen(
            busy = viewModel.signingIn.value,
            error = viewModel.error.value,
            onGoogle = { viewModel.signIn(context as Activity) },
            onRetry = { viewModel.signIn(context as Activity) },
        )
        lockState.locked && !viewModel.resetPin.value -> LockScreen(
            lock = lock,
            resetMode = false,
            onRequestReset = { viewModel.resetWithGoogle(context as Activity) },
        )
        viewModel.resetPin.value -> LockScreen(
            lock = lock,
            resetMode = true,
            onRequestReset = {},
            onFinished = { viewModel.resetPin.value = false },
        )
        !ready -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.CircularProgressIndicator()
                StatusBadge(NoticeKind.PENDING, "অপেক্ষমাণ")
            }
        }
        active == null -> CreateBusinessScreen(onBack = {})
        else -> AppScaffold(
            onSignOut = viewModel::signOut,
            isAdmin = user?.isAdmin == true,
            userName = user?.displayName ?: "দোকানদার",
            userEmail = user?.email.orEmpty(),
            userPhoto = user?.photoUrl.orEmpty(),
            shopName = active?.name.orEmpty(),
            shopVerified = active?.verified == true,
            shopStatus = active?.verificationStatus ?: com.hisabboi.model.VerificationStatus.NONE,
        )
    }
}

@Composable
private fun AppScaffold(
    onSignOut: () -> Unit,
    isAdmin: Boolean,
    userName: String,
    userEmail: String,
    userPhoto: String,
    shopName: String,
    shopVerified: Boolean,
    shopStatus: com.hisabboi.model.VerificationStatus,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tabs = listOf("ledger", "cash", "qr", "menu")
    Scaffold(
        bottomBar = {
            if (route in tabs) {
                Box {
                    NavigationBar {
                        NavigationBarItem(selected = route == "ledger", onClick = { nav.tab("ledger") }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("খাতা") })
                        NavigationBarItem(selected = route == "cash", onClick = { nav.tab("cash") }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("ক্যাশ") })
                        NavigationBarItem(selected = false, onClick = { nav.navigate("inventory-scan") }, icon = { Icon(Icons.Default.QrCodeScanner, null) }, label = { Text("স্ক্যান") })
                        NavigationBarItem(selected = route == "qr", onClick = { nav.tab("qr") }, icon = { Icon(Icons.Default.QrCode2, null) }, label = { Text("QR") })
                        NavigationBarItem(selected = route == "menu", onClick = { nav.tab("menu") }, icon = { Icon(Icons.Default.Menu, null) }, label = { Text("মেনু") })
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "ledger", modifier = Modifier.padding(padding)) {
            composable("ledger") {
                LedgerScreen(
                    onAdd = { nav.navigate("edit/new") },
                    onOpen = { nav.navigate("contact/$it") },
                    onScan = { nav.navigate("inventory-scan") },
                )
            }
            composable("cash") { CashboxScreen(onAdd = { nav.navigate("cash-entry/$it") }) }
            composable("qr") { QrScreen() }
            composable("menu") {
                MenuScreen(
                    isAdmin = isAdmin,
                    userName = userName,
                    userEmail = userEmail,
                    userPhoto = userPhoto,
                    shopName = shopName,
                    shopVerified = shopVerified,
                    shopStatus = shopStatus,
                    onOpen = { nav.navigate(it) },
                    onSignOut = onSignOut,
                )
            }
            composable("contact/{contactId}", listOf(navArgument("contactId") { type = NavType.StringType })) {
                val id = it.arguments?.getString("contactId").orEmpty()
                ContactDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit/$id") },
                    onEntry = { cid, dir -> nav.navigate("entry/$cid/$dir") },
                    onKyc = { nav.navigate("kyc/$id") },
                    onRemind = { nav.navigate("remind/$id") },
                )
            }
            composable("edit/{contactId}", listOf(navArgument("contactId") { type = NavType.StringType })) {
                ContactEditScreen(onBack = { nav.popBackStack() })
            }
            composable(
                "entry/{contactId}/{direction}",
                listOf(
                    navArgument("contactId") { type = NavType.StringType },
                    navArgument("direction") { type = NavType.StringType },
                ),
            ) { EntryScreen(onBack = { nav.popBackStack() }, onScan = { nav.navigate("scan") }) }
            composable("cash-entry/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) {
                CashEntryScreen(onBack = { nav.popBackStack() }, onScan = { nav.navigate("scan") })
            }
            composable("scan") { ScannerScreen(onDone = { nav.popBackStack() }) }
            composable("inventory-scan") {
                InventoryScannerScreen(
                    onBack = { nav.popBackStack() },
                    onMissing = { code -> nav.navigate("product-form?barcode=${Uri.encode(code)}&id=") },
                    onSale = {
                        nav.navigate("cash-entry/sale") { popUpTo("inventory-scan") { inclusive = true } }
                    },
                    onCash = { kind ->
                        nav.navigate("cash-entry/$kind") { popUpTo("inventory-scan") { inclusive = true } }
                    },
                )
            }
            composable(
                "product-form?barcode={barcode}&id={id}",
                listOf(
                    navArgument("barcode") { type = NavType.StringType; defaultValue = "" },
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { ProductFormScreen(onBack = { nav.popBackStack() }) }
            composable("products") {
                ProductsScreen(
                    onBack = { nav.popBackStack() },
                    onScan = { nav.navigate("inventory-scan") },
                    onCreate = { nav.navigate("product-form?barcode=&id=") },
                    onEdit = { product ->
                        nav.navigate("product-form?barcode=${Uri.encode(product.barcode)}&id=${product.id}")
                    },
                )
            }
            composable("shop-edit") {
                BusinessEditorScreen(
                    onBack = { nav.popBackStack() },
                    mapsKey = BuildConfig.MAPS_API_KEY,
                    onVerify = { nav.navigate("verify") },
                )
            }
            composable("shop/{shopId}", listOf(navArgument("shopId") { type = NavType.StringType })) {
                PublicShopScreen(it.arguments?.getString("shopId").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("nearby") { NearbyScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("shop/$it") }, mapsKey = BuildConfig.MAPS_API_KEY) }
            composable("kyc/{contactId}", listOf(navArgument("contactId") { type = NavType.StringType })) {
                KycScreen(onBack = { nav.popBackStack() })
            }
            composable("verify") { VerificationScreen(onBack = { nav.popBackStack() }) }
            composable("admin") { AdminScreen(onBack = { nav.popBackStack() }) }
            composable("remind/{contactId}", listOf(navArgument("contactId") { type = NavType.StringType })) {
                ReminderScreen(onBack = { nav.popBackStack() })
            }
            composable("businesses") { BusinessListScreen(onBack = { nav.popBackStack() }, onCreate = { nav.navigate("create") }) }
            composable("create") { CreateBusinessScreen(onBack = { nav.popBackStack() }) }
            composable("settings") {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onResetPin = { nav.navigate("pin-reset") },
                    onOpenProfile = { nav.navigate("profile") },
                )
            }
            composable("pin-reset") {
                val root: RootViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
                val activity = LocalContext.current as Activity
                LaunchedEffect(Unit) { root.resetWithGoogle(activity) }
                SimpleScaffold("পিন রিসেট", onBack = { nav.popBackStack() }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.hisabboi.ui.StatusBanner(
                            NoticeKind.PENDING,
                            "গুগল দিয়ে নিশ্চিত করুন। একই অ্যাকাউন্ট দিয়ে পিন মুছে যাবে।",
                        )
                        root.error.value?.let { com.hisabboi.ui.StatusBanner(NoticeKind.FAILED, UserFacingError.auth(it)) }
                        PrimaryButton("আবার চেষ্টা", { root.resetWithGoogle(activity) })
                    }
                }
            }
            composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
            composable("statement") { StatementScreen(onBack = { nav.popBackStack() }) }
            composable("reports") { ReportsScreen(onBack = { nav.popBackStack() }) }
            composable("trash") { TrashScreen(onBack = { nav.popBackStack() }) }
            composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }, onEditBusiness = { nav.navigate("shop-edit") }) }
        }
    }
}

private fun androidx.navigation.NavHostController.tab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MenuScreen(
    isAdmin: Boolean,
    userName: String,
    userEmail: String,
    userPhoto: String,
    shopName: String,
    shopVerified: Boolean,
    shopStatus: com.hisabboi.model.VerificationStatus,
    onOpen: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var confirmTrash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmLogout by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val danger = androidx.compose.ui.graphics.Color(0xFFC62828)
    SimpleScaffold("মেনু") {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen("profile") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(userName, userPhoto, Modifier.size(72.dp))
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        com.hisabboi.ui.NameWithSeal(userName, shopVerified, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        if (userEmail.isNotBlank()) Text(userEmail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shopName.ifBlank { "দোকান" }, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(6.dp))
                            if (shopVerified) {
                                com.hisabboi.ui.VerifiedSeal()
                            } else {
                                com.hisabboi.ui.ShopVerifyMark(false, shopStatus, onVerify = { onOpen("verify") })
                            }
                        }
                    }
                }
            }
            MenuSection("ব্যবসা", listOf(
                Triple(Icons.Default.Store, "দোকানের প্রোফাইল", "shop-edit"),
                Triple(Icons.Default.Inventory2, "পণ্য তালিকা", "products"),
                Triple(Icons.Default.Notifications, "তাগাদা", "remind/all"),
                Triple(Icons.Default.Verified, "ভেরিফাইড দোকান", "verify"),
                Triple(Icons.Default.Place, "আশপাশের দোকান", "nearby"),
            ), onOpen)
            MenuSection("হিসাব", listOf(
                Triple(Icons.Default.ReceiptLong, "লেনদেন বিবরণী", "statement"),
                Triple(Icons.Default.Assessment, "রিপোর্ট", "reports"),
            ), onOpen)
            MenuSection("অ্যাকাউন্ট", buildList {
                add(Triple(Icons.Default.Person, "প্রোফাইল", "profile"))
                add(Triple(Icons.Default.AccountBalance, "অ্যাকাউন্ট", "businesses"))
                add(Triple(Icons.Default.CloudDownload, "ব্যাকআপ", "backup"))
                add(Triple(Icons.Default.Settings, "সেটিংস", "settings"))
                if (isAdmin) add(Triple(Icons.Default.AdminPanelSettings, "অ্যাডমিন", "admin"))
            }, onOpen)
            com.hisabboi.ui.SectionCard("বিপজ্জনক") {
                MenuRow(Icons.Default.Delete, "মুছে ফেলা", danger) { confirmTrash = true }
                MenuRow(Icons.Default.Logout, "লগআউট", danger) { confirmLogout = true }
            }
            Text(
                "সংস্করণ ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (confirmTrash) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("মুছে ফেলা") },
            text = { Text("মুছে ফেলা জিনিসের তালিকা খুলবেন? সেখান থেকে ফেরত আনা যায়।") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmTrash = false; onOpen("trash") }) { Text("খুলুন") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmTrash = false }) { Text("না") } },
        )
    }
    if (confirmLogout) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("লগআউট") },
            text = { Text("লগআউট করবেন? হিসাব শুধু আপনার অ্যাকাউন্টে থাকে।") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmLogout = false; onSignOut() }) { Text("লগআউট", color = danger) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmLogout = false }) { Text("না") } },
        )
    }
}

@Composable
private fun MenuSection(title: String, rows: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String>>, onOpen: (String) -> Unit) {
    com.hisabboi.ui.SectionCard(title) {
        rows.forEach { (icon, label, dest) ->
            MenuRow(icon, label, androidx.compose.material3.MaterialTheme.colorScheme.onSurface) { onOpen(dest) }
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), color = tint)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tint.copy(alpha = 0.45f))
    }
}
