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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.hisabboi.ui.HisabTheme
import com.hisabboi.ui.PrimaryButton
import com.hisabboi.ui.SimpleScaffold
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
            auth.signIn(activity).onFailure { error.value = it.message }.also { signingIn.value = false }
        }
    }

    fun resetWithGoogle(activity: Activity) {
        viewModelScope.launch {
            auth.reauthenticate(activity).onSuccess {
                lock.clear()
                resetPin.value = true
            }.onFailure { error.value = it.message }
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
        !ready -> Text("হিসাব বই")
        active == null -> CreateBusinessScreen(onBack = {})
        else -> AppScaffold(onSignOut = viewModel::signOut, isAdmin = user?.isAdmin == true)
    }
}

@Composable
private fun LoginScreen(busy: Boolean, error: String?, onGoogle: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("হিসাব বই", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        Text("ছোট দোকানের ডিজিটাল খাতা। পাবো, দেবো, ক্যাশ আর নিজের QR — এক জায়গায়।")
        Text("গুগল দিয়ে ঢুকুন। ফোন বা ইমেইল পাসওয়ার্ড নেই।")
        PrimaryButton(if (busy) "অপেক্ষা…" else "গুগল দিয়ে চালিয়ে যান", onGoogle, !busy)
        error?.let { Text(it) }
        Text("আপনার হিসাব শুধু আপনার অ্যাকাউন্টে থাকে। অ্যাপ কোনো টাকা গ্রহণ করে না।")
    }
}

@Composable
private fun AppScaffold(onSignOut: () -> Unit, isAdmin: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tabs = listOf("ledger", "cash", "qr", "menu")
    Scaffold(
        bottomBar = {
            if (route in tabs) {
                NavigationBar {
                    NavigationBarItem(selected = route == "ledger", onClick = { nav.tab("ledger") }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("খাতা") })
                    NavigationBarItem(selected = route == "cash", onClick = { nav.tab("cash") }, icon = { Icon(Icons.Default.AccountBalanceWallet, null) }, label = { Text("ক্যাশ") })
                    NavigationBarItem(selected = route == "qr", onClick = { nav.tab("qr") }, icon = { Icon(Icons.Default.QrCode2, null) }, label = { Text("QR") })
                    NavigationBarItem(selected = route == "menu", onClick = { nav.tab("menu") }, icon = { Icon(Icons.Default.Menu, null) }, label = { Text("মেনু") })
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "ledger", modifier = Modifier.padding(padding)) {
            composable("ledger") { LedgerScreen(onAdd = { nav.navigate("edit/new") }, onOpen = { nav.navigate("contact/$it") }) }
            composable("cash") { CashboxScreen(onAdd = { nav.navigate("cash-entry/$it") }) }
            composable("qr") { QrScreen() }
            composable("menu") {
                MenuScreen(
                    isAdmin = isAdmin,
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
            composable("products") { ProductsScreen(onBack = { nav.popBackStack() }) }
            composable("shop-edit") { BusinessEditorScreen(onBack = { nav.popBackStack() }, mapsKey = BuildConfig.MAPS_API_KEY) }
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
                SettingsScreen(onBack = { nav.popBackStack() }, onResetPin = { /* handled by lock on next resume via menu path */ nav.navigate("pin-reset") })
            }
            composable("pin-reset") {
                val root: RootViewModel = hiltViewModel(LocalContext.current as ComponentActivity)
                val activity = LocalContext.current as Activity
                LaunchedEffect(Unit) { root.resetWithGoogle(activity) }
                Text("গুগল দিয়ে নিশ্চিত করুন")
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
private fun MenuScreen(isAdmin: Boolean, onOpen: (String) -> Unit, onSignOut: () -> Unit) {
    SimpleScaffold("মেনু") { padding ->
        LazyColumn(Modifier.padding(padding)) {
            val rows = buildList {
                add("প্রোফাইল" to "profile")
                add("অ্যাকাউন্ট" to "businesses")
                add("লেনদেন বিবরণী" to "statement")
                add("রিপোর্ট" to "reports")
                add("পণ্য তালিকা" to "products")
                add("দোকানের প্রোফাইল" to "shop-edit")
                add("আশপাশের দোকান" to "nearby")
                add("তাগাদা" to "remind/all")
                add("ভেরিফাইড দোকান" to "verify")
                add("ব্যাকআপ" to "backup")
                add("মুছে ফেলা" to "trash")
                add("সেটিংস" to "settings")
                if (isAdmin) add("অ্যাডমিন" to "admin")
            }
            items(rows.size) { index ->
                val (label, dest) = rows[index]
                androidx.compose.material3.TextButton(onClick = { onOpen(dest) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(label)
                }
            }
            item {
                androidx.compose.material3.TextButton(onClick = onSignOut) { Text("লগআউট") }
            }
        }
    }
}
