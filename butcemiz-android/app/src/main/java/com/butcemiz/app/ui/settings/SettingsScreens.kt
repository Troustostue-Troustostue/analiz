@file:Suppress("DEPRECATION")

package com.butcemiz.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.butcemiz.app.AppContainer
import com.butcemiz.app.BuildConfig
import com.butcemiz.app.ButcemizApp
import com.butcemiz.app.core.Dates
import com.butcemiz.app.data.Category
import com.butcemiz.app.data.CategoryKind
import com.butcemiz.app.data.Household
import com.butcemiz.app.data.Member
import com.butcemiz.app.prefs.SyncConfig
import com.butcemiz.app.prefs.ThemeMode
import com.butcemiz.app.sync.PairingCode
import com.butcemiz.app.sync.SyncEngine
import com.butcemiz.app.ui.assets.FormLabel
import com.butcemiz.app.ui.assets.FormSheet
import com.butcemiz.app.ui.assets.NameField
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.AppIcons
import com.butcemiz.app.ui.components.ChipRow
import com.butcemiz.app.ui.components.ColorPicker
import com.butcemiz.app.ui.components.ConfirmDialog
import com.butcemiz.app.ui.components.IconBadge
import com.butcemiz.app.ui.components.IconPicker
import com.butcemiz.app.ui.components.LocalContainer
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.Pill
import com.butcemiz.app.ui.components.QrImage
import com.butcemiz.app.ui.components.ScreenScaffold
import com.butcemiz.app.ui.components.SectionHeader
import com.butcemiz.app.ui.components.SettingRow
import com.butcemiz.app.ui.components.SwitchRow
import com.butcemiz.app.ui.components.colorOf
import com.butcemiz.app.ui.components.scanQr
import com.butcemiz.app.ui.home.SyncChip
import com.butcemiz.app.ui.nav.Routes
import com.butcemiz.app.ui.shell.LocalToast
import com.butcemiz.app.ui.theme.LocalSemantic
import com.butcemiz.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ======================================================================
//                               DİĞER
// ======================================================================

@Composable
fun MoreScreen(nav: NavController) {
    val c = LocalContainer.current
    val sync by c.engine.status.collectAsStateWithLifecycle()
    val paired by c.prefs.pairedFlow.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Diğer") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(onClick = { nav.navigate(Routes.SYNC) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aile & eşleştirme", style = MaterialTheme.typography.titleMedium)
                            Text(if (paired) "Eşinizle bağlı" else "Eşinizi QR kod ile davet edin", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SyncChip(sync, paired) { nav.navigate(Routes.SYNC) }
                    }
                }
            }
            item { SectionHeader("Planlama") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.EventRepeat, "Düzenli ödemeler", "Fatura, kira, aidat, abonelik, taksit") { nav.navigate(Routes.PAYMENTS) }
                    SettingRow(Icons.Rounded.Work, "Maaş ve gelirler", "Maaş tutarı, yatma günü, ek gelirler", iconColor = LocalSemantic.current.income) { nav.navigate(Routes.INCOMES) }
                    SettingRow(Icons.Rounded.PieChart, "Bütçe limitleri", "Aylık ve kategori bazında limitler", iconColor = LocalSemantic.current.warning) { nav.navigate(Routes.BUDGETS) }
                }
            }
            item { SectionHeader("Düzen") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.Category, "Kategoriler", "Harcama ve gelir kategorileri") { nav.navigate(Routes.CATEGORIES) }
                    SettingRow(Icons.Rounded.Groups, "Aile üyeleri", "İsimler, renkler") { nav.navigate(Routes.MEMBERS) }
                    SettingRow(Icons.Rounded.AccountBalanceWallet, "Hesaplar", "Banka, nakit, kredi kartı") { nav.navigate(Routes.ASSETS) }
                }
            }
            item { SectionHeader("Uygulama") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.Download, "Yedekleme ve dışa aktarma", "JSON yedek, Excel (CSV) tablosu") { nav.navigate(Routes.BACKUP) }
                    SettingRow(Icons.Rounded.Settings, "Ayarlar", "Tema, kilit, bildirimler, bütçe dönemi") { nav.navigate(Routes.SETTINGS) }
                    SettingRow(Icons.Rounded.Info, "Nasıl çalışır?", "Senkronizasyon, gizlilik, ipuçları") { nav.navigate(Routes.ABOUT) }
                }
            }
        }
    }
}

// ======================================================================
//                         AİLE & EŞLEŞTİRME
// ======================================================================

fun copyText(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun pasteText(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

fun isIgnoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

fun requestIgnoreBattery(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

/** Davet koduyla mevcut bir bütçeye katılma (ayarlar ekranından). */
fun joinHousehold(c: AppContainer, app: ButcemizApp, cfg: SyncConfig, scope: kotlinx.coroutines.CoroutineScope, onDone: () -> Unit) {
    scope.launch {
        c.repo.prepareJoin()
        c.prefs.resetSyncState()
        c.prefs.syncConfig = cfg
        c.prefs.awaitingInitialSync = true
        c.engine.restart()
        c.engine.requestNow(force = true)
        app.ensureBackgroundService()
        onDone()
    }
}

@Composable
fun SyncScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val app = context.applicationContext as ButcemizApp
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val sem = LocalSemantic.current
    val status by c.engine.status.collectAsStateWithLifecycle()
    val paired by c.prefs.pairedFlow.collectAsStateWithLifecycle()
    val cfg by c.prefs.syncConfigFlow.collectAsStateWithLifecycle()
    val peerSeen by c.prefs.lastPeerSeenFlow.collectAsStateWithLifecycle()
    val bg by c.prefs.backgroundSyncFlow.collectAsStateWithLifecycle()
    val devices by c.repo.devices.collectAsStateWithLifecycle(emptyList())
    val members by c.repo.members.collectAsStateWithLifecycle(emptyList())
    val household by c.repo.household.collectAsStateWithLifecycle(Household())
    var showJoin by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(isIgnoringBattery(context)) }

    LaunchedEffect(Unit) {
        if (cfg == null && c.prefs.onboarded) {
            // eski kurulum: yapılandırma yoksa oluştur
            c.prefs.syncConfig = PairingCode.newConfig()
            c.engine.restart()
        }
    }

    ScreenScaffold(title = "Aile & eşleştirme", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (paired) Icons.Rounded.CloudDone else Icons.Rounded.Sync, if (paired) sem.income else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (status.state) {
                                    SyncEngine.State.CONNECTED -> "Bağlı ve senkron"
                                    SyncEngine.State.WAITING_PARTNER -> "Eşinizin katılması bekleniyor"
                                    SyncEngine.State.CONNECTING -> "Bağlanıyor…"
                                    SyncEngine.State.OFFLINE -> "Çevrimdışı — internet gelince eşitlenir"
                                    SyncEngine.State.QUOTA -> "Gönderim sınırına ulaşıldı"
                                    SyncEngine.State.NOT_CONFIGURED -> "Eşleştirme yapılmadı"
                                    SyncEngine.State.IDLE -> if (paired) "Hazır" else "Eşleştirme bekleniyor"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val lines = buildList {
                                if (peerSeen > 0) add("Eşinizden son veri: ${Dates.relative(peerSeen)}")
                                if (status.pendingOps > 0 && paired) add("${status.pendingOps} değişiklik gönderilmeyi bekliyor")
                                status.detail?.let { add(it) }
                            }
                            if (lines.isNotEmpty()) Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { c.engine.requestNow(force = true); c.engine.poke(); toast("Eşitleme istendi", null, null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Şimdi eşitle")
                    }
                }
            }
            cfg?.let { config ->
                item {
                    AppCard {
                        Text(if (paired) "Yeni cihaz ekle / eşinizi yeniden bağlayın" else "Eşinizi davet edin", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Eşinizin telefonunda Bütçemiz'i kurun → \"Eşimin bütçesine katıl\" → \"QR okut\" ile bu kodu okutun. Ya da kodu WhatsApp ile gönderin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        val code = PairingCode.encode(config)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrImage(code) }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { shareText(context, PairingCode.shareText(config, household.name), "Davet kodunu paylaş") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Share, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Paylaş")
                            }
                            OutlinedButton(onClick = { copyText(context, "Bütçemiz davet kodu", code); toast("Kod kopyalandı", null, null) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.ContentCopy, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Kopyala")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("🔒 Kod şifreleme anahtarını içerir; yalnızca eşinizle paylaşın.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (devices.isNotEmpty()) {
                item { SectionHeader("Bağlı cihazlar") }
                item {
                    AppCard(padding = PaddingValues(4.dp)) {
                        devices.forEach { d ->
                            val m = members.firstOrNull { it.id == d.memberId }
                            SettingRow(
                                Icons.Rounded.Devices,
                                d.name.ifBlank { d.model.ifBlank { "Telefon" } },
                                listOfNotNull(m?.name, if (d.id == c.prefs.deviceId) "bu cihaz" else null, d.appVersion.takeIf { it.isNotBlank() }?.let { "v$it" }).joinToString(" · "),
                                trailing = if (d.id == c.prefs.deviceId) ({ Pill("Bu cihaz", MaterialTheme.colorScheme.primary) }) else null,
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Anlık bildirim") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SwitchRow(
                        Icons.Rounded.Notifications,
                        "Arka planda bağlı kal",
                        "Uygulama kapalıyken de eşinizin harcamaları saniyeler içinde gelir (sessiz kalıcı bildirim gösterilir)",
                        bg,
                        { v -> c.prefs.backgroundSync = v; app.ensureBackgroundService() },
                    )
                    if (!batteryOk) {
                        SettingRow(
                            Icons.Rounded.BatteryAlert,
                            "Pil optimizasyonunu kapat",
                            "Samsung'da bildirimlerin gecikmemesi için önerilir",
                            iconColor = sem.warning,
                        ) {
                            requestIgnoreBattery(context)
                            batteryOk = true
                        }
                    }
                }
            }
            item { SectionHeader("Eşleştirme işlemleri") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.QrCodeScanner, "Başka bir bütçeye katıl", "Eşinizin davet kodunu okutun; verileriniz birleşir") { showJoin = true }
                    SettingRow(Icons.Rounded.Refresh, "Eşleştirmeyi sıfırla", "Yeni gizli anahtar oluşturur; eşinizin yeniden katılması gerekir", iconColor = MaterialTheme.colorScheme.error) { confirmReset = true }
                    SettingRow(Icons.Rounded.Settings, "Gelişmiş", "Sunucu ayarları (isteğe bağlı)") { showAdvanced = true }
                }
            }
        }
    }

    if (showJoin) {
        JoinCodeDialog(onDismiss = { showJoin = false }) { newCfg ->
            showJoin = false
            joinHousehold(c, app, newCfg, scope) { toast("Katılma isteği gönderildi; eşinizin verileri birazdan gelir", null, null) }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Eşleştirme sıfırlansın mı?",
            text = "Yeni bir gizli anahtar oluşturulur. Verileriniz silinmez; ancak eşinizin yeni QR kodu ile tekrar katılması gerekir.",
            confirm = "Sıfırla",
            onConfirm = {
                c.prefs.resetSyncState()
                c.prefs.syncConfig = PairingCode.newConfig(cfg?.server ?: com.butcemiz.app.prefs.AppPrefs.DEFAULT_SERVER)
                c.engine.restart()
                toast("Yeni davet kodu oluşturuldu", null, null)
            },
            onDismiss = { confirmReset = false },
        )
    }
    if (showAdvanced) {
        AdvancedSyncDialog(cfg, onDismiss = { showAdvanced = false }) { server, token ->
            val cur = c.prefs.syncConfig
            if (cur != null) {
                c.prefs.syncConfig = cur.copy(server = server, token = token)
                c.engine.restart()
                toast("Sunucu ayarı kaydedildi", null, null)
            }
            showAdvanced = false
        }
    }
}

@Composable
fun JoinCodeDialog(onDismiss: () -> Unit, onJoin: (SyncConfig) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Davet koduyla katıl") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Eşinizin telefonundaki QR kodu okutun ya da paylaştığı kodu yapıştırın.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        scanQr(context, { v -> text = v; error = null }, { e -> error = e })
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.QrCodeScanner, null)
                        Spacer(Modifier.width(4.dp))
                        Text("QR okut")
                    }
                    OutlinedButton(onClick = { pasteText(context)?.let { text = it; error = null } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.ContentPaste, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Yapıştır")
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("Davet kodu") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cfg = PairingCode.decode(text)
                if (cfg == null) error = "Kod geçersiz. \"BTZ1.\" ile başlayan kodun tamamını girin." else onJoin(cfg)
            }) { Text("Katıl") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun AdvancedSyncDialog(cfg: SyncConfig?, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var server by remember { mutableStateOf(cfg?.server ?: com.butcemiz.app.prefs.AppPrefs.DEFAULT_SERVER) }
    var token by remember { mutableStateOf(cfg?.token ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gelişmiş senkronizasyon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Varsayılan aktarıcı ücretsiz ntfy.sh'tır; içerik uçtan uca şifreli olduğu için sunucu verilerinizi göremez. Kendi ntfy sunucunuzu kullanmak isterseniz iki telefonda da aynı adresi girin.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = server, onValueChange = { server = it.trim() }, label = { Text("Sunucu adresi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = token, onValueChange = { token = it.trim() }, label = { Text("Erişim anahtarı (isteğe bağlı)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = server.trimEnd('/')
                if (s.startsWith("https://") || s.startsWith("http://")) onSave(s, token.ifBlank { null })
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

// ======================================================================
//                               AYARLAR
// ======================================================================

@Composable
fun SettingsScreen(nav: NavController) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val theme by c.prefs.themeFlow.collectAsStateWithLifecycle()
    val dynamic by c.prefs.dynamicColorFlow.collectAsStateWithLifecycle()
    val hide by c.prefs.hideAmountsFlow.collectAsStateWithLifecycle()
    val lock by c.prefs.appLockFlow.collectAsStateWithLifecycle()
    val secure by c.prefs.secureScreenFlow.collectAsStateWithLifecycle()
    val nPartner by c.prefs.notifyPartnerFlow.collectAsStateWithLifecycle()
    val nRemind by c.prefs.notifyRemindersFlow.collectAsStateWithLifecycle()
    val nBudget by c.prefs.notifyBudgetFlow.collectAsStateWithLifecycle()
    val me by c.prefs.myMemberIdFlow.collectAsStateWithLifecycle()
    val household by c.repo.household.collectAsStateWithLifecycle(Household())
    val members by c.repo.members.collectAsStateWithLifecycle(emptyList())
    var editHousehold by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Ayarlar", onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionHeader("Bütçe") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.Home, "Bütçe adı ve dönemi", "${household.name} · ay başlangıcı: ${if (household.monthStartDay == 1) "ayın 1'i" else "her ayın ${household.monthStartDay}. günü"}") { editHousehold = true }
                    if (members.size > 1) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("Bu telefonu kim kullanıyor?", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(6.dp))
                            ChipRow(members, members.firstOrNull { it.id == me }, { it.name }, { m ->
                                scope.launch { c.repo.chooseMember(m.id) }
                            })
                        }
                    }
                }
            }
            item { SectionHeader("Görünüm") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Tema", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        ChipRow(ThemeMode.entries, theme, { it.label }, { c.prefs.theme = it })
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        SwitchRow(Icons.Rounded.Palette, "Duvar kağıdı renkleri", "Material You renklerini kullan", dynamic, { c.prefs.dynamicColor = it })
                    }
                    SwitchRow(Icons.Rounded.Visibility, "Tutarları gizle", "Ana sayfada ve widget'ta tutarlar ••• olarak görünür", hide, { c.prefs.hideAmounts = it })
                }
            }
            item { SectionHeader("Güvenlik") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SwitchRow(Icons.Rounded.Fingerprint, "Uygulama kilidi", "Açılışta parmak izi / yüz / ekran kilidi sorulur", lock, { v ->
                        if (v) {
                            val bm = BiometricManager.from(context)
                            val ok = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
                            if (ok) c.prefs.appLock = true else toast("Önce telefonda ekran kilidi veya parmak izi tanımlayın", null, null)
                        } else {
                            c.prefs.appLock = false
                        }
                    })
                    SwitchRow(Icons.Rounded.Lock, "Ekran görüntüsünü engelle", "Son uygulamalar ekranında içerik gizlenir", secure, { c.prefs.secureScreen = it })
                }
            }
            item { SectionHeader("Bildirimler") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SwitchRow(Icons.Rounded.Notifications, "Eşimin harcama ve gelirleri", "Eşiniz bir hareket girdiğinde anında bildirim", nPartner, { c.prefs.notifyPartner = it })
                    SwitchRow(Icons.Rounded.Schedule, "Fatura ve maaş hatırlatmaları", "Vadesi yaklaşan ödemeler, maaş günü", nRemind, { c.prefs.notifyReminders = it })
                    SwitchRow(Icons.Rounded.PieChart, "Bütçe uyarıları", "Limitin %80'i ve %100'ü", nBudget, { c.prefs.notifyBudget = it })
                    SettingRow(Icons.Rounded.Settings, "Sistem bildirim ayarları", "Ses, titreşim, kilit ekranı") {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }
                }
            }
            item { SectionHeader("Veri") }
            item {
                AppCard(padding = PaddingValues(4.dp)) {
                    SettingRow(Icons.Rounded.Download, "Yedekleme ve dışa aktarma", null) { nav.navigate(Routes.BACKUP) }
                    SettingRow(Icons.Rounded.DeleteForever, "Bu telefondaki tüm verileri sil", "Eşinizin telefonundaki veriler silinmez", iconColor = MaterialTheme.colorScheme.error) { confirmWipe = true }
                }
            }
            item {
                Text("Bütçemiz v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }

    if (editHousehold) {
        HouseholdSheet(household, onDismiss = { editHousehold = false }) { h ->
            scope.launch { c.repo.saveHousehold(h) }
            editHousehold = false
            toast("Kaydedildi", null, null)
        }
    }
    if (confirmWipe) {
        ConfirmDialog(
            title = "Tüm veriler silinsin mi?",
            text = "Bu telefondaki tüm hareketler, ayarlar ve eşleştirme silinir; uygulama ilk kurulum ekranına döner. Eşinizin telefonu etkilenmez. Önce yedek almanız önerilir.",
            confirm = "Hepsini sil",
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        com.butcemiz.app.sync.SyncService.stop(context)
                        c.db.clearAllTables()
                        c.prefs.wipe()
                    }
                    val i = context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    if (i != null) context.startActivity(i)
                    Runtime.getRuntime().exit(0)
                }
            },
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
private fun HouseholdSheet(h: Household, onDismiss: () -> Unit, onSave: (Household) -> Unit) {
    var name by remember { mutableStateOf(h.name) }
    var day by remember { mutableIntStateOf(h.monthStartDay) }
    FormSheet(title = "Bütçe adı ve dönemi", onDismiss = onDismiss, canSave = name.isNotBlank(), onSave = { onSave(h.copy(name = name.trim(), monthStartDay = day)) }) {
        NameField("Bütçe adı", name) { name = it }
        FormLabel("Ay başlangıç günü")
        Text(
            "Maaşınız ayın 15'inde yatıyorsa 15 seçin; \"Bu ay\" istatistikleri 15'inden 14'üne kadar hesaplanır. İki telefonda da aynı uygulanır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChipRow((1..28).toList(), day, { it.toString() }, { day = it })
    }
}

// ======================================================================
//                             KATEGORİLER
// ======================================================================

@Composable
fun CategoriesScreen(nav: NavController) {
    val c = LocalContainer.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val categories by c.repo.categories.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    val kind = if (tab == 0) CategoryKind.EXPENSE else CategoryKind.INCOME

    ScreenScaffold(
        title = "Kategoriler",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Kategori ekle") } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Gider") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Gelir") })
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val list = categories.filter { it.kind == kind }.sortedWith(compareBy<Category> { it.archived }.thenBy { it.sortOrder })
                items(list, key = { it.id }) { cat ->
                    AppCard(onClick = { editing = cat }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcons.of(cat.icon), colorOf(cat.color))
                            Spacer(Modifier.width(12.dp))
                            Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (cat.archived) Pill("Arşivde", MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CategorySheet(
            initial = editing,
            kind = kind,
            onDismiss = { creating = false; editing = null },
            onRemove = { cat -> scope.launch { c.repo.removeCategory(cat) }; editing = null; toast("Kategori kaldırıldı", null, null) },
            onRestore = { cat -> scope.launch { c.repo.saveCategory(cat.copy(archived = false)) }; editing = null },
        ) { cat ->
            scope.launch { c.repo.saveCategory(cat) }
            creating = false
            editing = null
            toast("Kaydedildi", null, null)
        }
    }
}

@Composable
private fun CategorySheet(
    initial: Category?,
    kind: CategoryKind,
    onDismiss: () -> Unit,
    onRemove: (Category) -> Unit,
    onRestore: (Category) -> Unit,
    onSave: (Category) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "more") }
    var color by remember { mutableStateOf(initial?.color ?: Palette.choices[0]) }
    var confirm by remember { mutableStateOf(false) }
    FormSheet(
        title = if (initial == null) "Yeni kategori" else "Kategoriyi düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank(),
        onSave = { onSave((initial ?: Category(id = "", name = "", kind = kind, sortOrder = 50)).copy(name = name.trim(), icon = icon, color = color)) },
    ) {
        NameField("Kategori adı", name) { name = it }
        FormLabel("Renk")
        ColorPicker(color, Palette.choices, { color = it })
        FormLabel("Simge")
        IconPicker(icon, color, { icon = it })
        if (initial != null) {
            if (initial.archived) {
                TextButton(onClick = { onRestore(initial) }) { Text("Arşivden çıkar") }
            } else {
                TextButton(onClick = { confirm = true }) { Text("Kategoriyi kaldır", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirm && initial != null) {
        ConfirmDialog(
            "Kategori kaldırılsın mı?",
            "Kullanılmışsa arşivlenir (eski hareketlerde görünmeye devam eder), hiç kullanılmamışsa silinir.",
            confirm = "Kaldır",
            onConfirm = { onRemove(initial) },
            onDismiss = { confirm = false },
        )
    }
}

// ======================================================================
//                               ÜYELER
// ======================================================================

@Composable
fun MembersScreen(nav: NavController) {
    val c = LocalContainer.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val members by c.repo.members.collectAsStateWithLifecycle(emptyList())
    val me by c.prefs.myMemberIdFlow.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Member?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Member?>(null) }

    ScreenScaffold(
        title = "Aile üyeleri",
        onBack = { nav.popBackStack() },
        actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Üye ekle") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(members, key = { it.id }) { m ->
                AppCard(onClick = { editing = m }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(m, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(m.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (m.id == me) Pill("Bu telefon", MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Text(
                    "Her harcama ve gelirde \"Kim?\" seçilebilir; analiz ekranında kişi bazında dağılım görünür.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (creating || editing != null) {
        MemberSheet(
            initial = editing,
            canDelete = members.size > 1,
            onDismiss = { creating = false; editing = null },
            onDelete = { m -> deleting = m; editing = null },
        ) { m ->
            scope.launch { c.repo.saveMember(m) }
            creating = false
            editing = null
            toast("Kaydedildi", null, null)
        }
    }
    deleting?.let { m ->
        val others = members.filter { it.id != m.id }
        ConfirmDialog(
            "${m.name} silinsin mi?",
            "Bu kişiye ait hareketler ${others.firstOrNull()?.name ?: "diğer üye"} adına aktarılır.",
            onConfirm = { scope.launch { c.repo.deleteMember(m, others.firstOrNull()?.id) } },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun MemberSheet(initial: Member?, canDelete: Boolean, onDismiss: () -> Unit, onDelete: (Member) -> Unit, onSave: (Member) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "") }
    var color by remember { mutableStateOf(initial?.color ?: Palette.members[1]) }
    FormSheet(
        title = if (initial == null) "Yeni üye" else "Üyeyi düzenle",
        onDismiss = onDismiss,
        canSave = name.isNotBlank(),
        onSave = { onSave((initial ?: Member(id = "", name = "")).copy(name = name.trim(), emoji = emoji.trim().take(2), color = color)) },
    ) {
        NameField("İsim", name) { name = it }
        OutlinedTextField(
            value = emoji,
            onValueChange = { emoji = it.take(4) },
            label = { Text("Emoji (isteğe bağlı, ör. 👩)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        FormLabel("Renk")
        ColorPicker(color, Palette.members + Palette.choices.take(8), { color = it })
        if (initial != null && canDelete) {
            TextButton(onClick = { onDelete(initial) }) { Text("Üyeyi sil", color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ======================================================================
//                         YEDEKLEME & DIŞA AKTARMA
// ======================================================================

@Composable
fun BackupScreen(nav: NavController) {
    val c = LocalContainer.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val r = runCatching { c.backup.exportJson(uri) }
            busy = false
            toast(if (r.isSuccess) "Yedek kaydedildi" else "Yedeklenemedi: ${r.exceptionOrNull()?.message}", null, null)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val r = runCatching { c.backup.importJson(uri) }
            busy = false
            toast(r.fold({ "${it.imported} kayıt alındı, ${it.skipped} kayıt zaten günceldi" }, { "İçe aktarılamadı: ${it.message}" }), null, null)
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val r = runCatching { c.backup.exportCsv(uri) }
            busy = false
            toast(r.fold({ "$it hareket CSV olarak kaydedildi" }, { "Dışa aktarılamadı: ${it.message}" }), null, null)
        }
    }

    ScreenScaffold(title = "Yedekleme ve dışa aktarma", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard {
                Text("Verileriniz iki telefonda birden durur", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Telefonlardan biri kaybolsa bile yeni telefonda \"Eşimin bütçesine katıl\" demeniz yeterli; tüm veriler diğer telefondan geri gelir. Yine de ara sıra yedek almanızı öneririz (ör. Google Drive'a kaydedin).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppCard(padding = PaddingValues(4.dp)) {
                SettingRow(Icons.Rounded.Download, "Yedek al (JSON)", "Tüm hesaplar, hareketler, birikimler, borçlar ve planlar") {
                    if (!busy) exportLauncher.launch("butcemiz_yedek_${Dates.fileStamp()}.json")
                }
                SettingRow(Icons.Rounded.Upload, "Yedekten geri yükle", "Mevcut verilerle birleştirilir; eşinize de gönderilir") {
                    if (!busy) importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
                SettingRow(Icons.Rounded.TableChart, "Excel tablosu (CSV)", "Tüm hareketler; Excel'de doğrudan açılır") {
                    if (!busy) csvLauncher.launch("butcemiz_hareketler_${Dates.fileStamp()}.csv")
                }
            }
            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

// ======================================================================
//                             NASIL ÇALIŞIR
// ======================================================================

@Composable
fun AboutScreen(nav: NavController) {
    ScreenScaffold(title = "Nasıl çalışır?", onBack = { nav.popBackStack() }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutCard(
                "⚡ Anlık senkronizasyon",
                "Bir harcama girdiğinizde şifrelenip birkaç saniye içinde eşinizin telefonuna gider; eşinize \"Alperen harcama yaptı · −2.000 ₺ · Yeni bakiye 118.000 ₺\" gibi bildirim düşer. İnternet yoksa değişiklikler bekletilir ve bağlantı gelince otomatik gönderilir.",
            )
            AboutCard(
                "🔒 Gizlilik",
                "Veriler telefonlarınızda saklanır. Telefonlar arasında taşınırken AES-256-GCM ile uçtan uca şifrelenir; anahtar yalnızca QR kodla paylaştığınız iki telefonda bulunur. Aracı sunucu (ntfy.sh) yalnızca anlamsız şifreli metin görür ve bunları en çok 12 saat tutar.",
            )
            AboutCard(
                "🧮 Bakiye nasıl hesaplanır?",
                "Toplam bakiye = hesapların başlangıç bakiyeleri + gelirler − giderler (transferler hesaplar arasında yer değiştirir). İkiniz aynı anda harcama girse bile hiçbir kayıt kaybolmaz. Bankadaki gerçek tutar farklıysa hesap ayrıntısında \"Bakiyeyi düzelt\"i kullanın.",
            )
            AboutCard(
                "💡 İpuçları",
                "• Ana ekrana Bütçemiz widget'ını ekleyin; tek dokunuşla harcama girin.\n" +
                    "• Uygulama simgesine basılı tutun: \"Harcama ekle\" kısayolu.\n" +
                    "• Maaşınız 15'inde yatıyorsa Ayarlar → Bütçe dönemi'nden ay başlangıcını 15 yapın.\n" +
                    "• Samsung'da bildirimler gecikirse: Ayarlar → Pil → Arka plan kullanım sınırları → Bütçemiz'i \"Hiç uyutulmayan uygulamalar\"a ekleyin.",
            )
            Text(
                "Bütçemiz v${BuildConfig.VERSION_NAME} · Aileniz için ❤️ ile yapıldı",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutCard(title: String, text: String) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
