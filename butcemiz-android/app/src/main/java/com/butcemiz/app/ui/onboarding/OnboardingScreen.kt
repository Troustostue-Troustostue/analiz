package com.butcemiz.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.butcemiz.app.R
import com.butcemiz.app.sync.PairingCode
import com.butcemiz.app.ui.components.AmountTextField
import com.butcemiz.app.ui.components.AppCard
import com.butcemiz.app.ui.components.LocalContainer
import com.butcemiz.app.ui.components.MemberAvatar
import com.butcemiz.app.ui.components.amountToKurus
import com.butcemiz.app.ui.components.scanQr
import com.butcemiz.app.ui.settings.pasteText
import com.butcemiz.app.ui.theme.LocalSemantic
import com.butcemiz.app.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * İlk kurulum: "Yeni bütçe oluştur" ya da "Eşimin bütçesine katıl".
 * onFinished(true) → davet ekranını aç.
 */
@Composable
fun OnboardingFlow(onFinished: (openInvite: Boolean) -> Unit) {
    var step by rememberSaveable { mutableStateOf("welcome") }
    val c = LocalContainer.current
    // Katılma adımındayken bağlantı açık kalsın
    if (step == "waiting") {
        DisposableEffect(Unit) {
            c.engine.acquire("onboarding")
            onDispose { c.engine.release("onboarding") }
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (step) {
            "welcome" -> Welcome(onCreate = { step = "create" }, onJoin = { step = "join" })
            "create" -> {
                BackHandler { step = "welcome" }
                CreateForm(onBack = { step = "welcome" }, onDone = { onFinished(true) })
            }
            "join" -> {
                BackHandler { step = "welcome" }
                JoinForm(onBack = { step = "welcome" }, onJoined = { step = "waiting" })
            }
            "waiting" -> WaitingForPartner(onDone = { onFinished(false) })
        }
    }
}

@Composable
private fun Logo(size: Int = 96) {
    val sem = LocalSemantic.current
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3).dp)).background(sem.heroBrush),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size((size * 1.5).dp))
    }
}

@Composable
private fun Welcome(onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Logo()
        Spacer(Modifier.height(20.dp))
        Text("Bütçemiz", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Evinizin ekonomisini eşinizle birlikte, anlık olarak yönetin.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.Bolt, "Anlık senkron", "Biri harcama girince diğerinin telefonuna saniyeler içinde bildirim ve yeni bakiye gelir.")
        Feature(Icons.Rounded.Savings, "Her şey tek yerde", "Bakiye, maaş, ek gelir, birikim (altın, döviz), borç, fatura ve aylık/haftalık gider tabloları.")
        Feature(Icons.Rounded.Lock, "Gizli ve ücretsiz", "Hesap açmak yok, reklam yok. Veriler uçtan uca şifreli; yalnızca ikinizin telefonunda.")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Yeni ortak bütçe oluştur", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp)) {
            Text("Eşimin bütçesine katıl", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "İlk telefonda bütçeyi oluşturun, ikinci telefonda \"katıl\"ı seçip QR kodu okutun.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CreateForm(onBack: () -> Unit, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var myName by rememberSaveable { mutableStateOf("") }
    var partner by rememberSaveable { mutableStateOf("") }
    var household by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var salary by rememberSaveable { mutableStateOf("") }
    var payDay by rememberSaveable { mutableStateOf("15") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader("Yeni ortak bütçe", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Birkaç bilgiyle başlayalım. Hepsini sonradan değiştirebilirsiniz.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = myName, onValueChange = { myName = it }, label = { Text("Adınız *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            OutlinedTextField(
                value = partner, onValueChange = { partner = it }, label = { Text("Eşinizin adı") }, singleLine = true,
                supportingText = { Text("Eşiniz katılınca kendini bu isimle seçer") },
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            OutlinedTextField(
                value = household, onValueChange = { household = it }, label = { Text("Bütçe adı") }, placeholder = { Text("Evimizin bütçesi") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            AmountTextField("Şu an toplam ne kadar paranız var?", balance, { balance = it }, supporting = "Banka + nakit toplamı (ör. 120.000). Sonradan hesaplara bölebilirsiniz.")
            AmountTextField("Aylık net maaşınız", salary, { salary = it }, supporting = "İsteğe bağlı; maaş günü hatırlatması için")
            OutlinedTextField(
                value = payDay,
                onValueChange = { v -> payDay = v.filter { it.isDigit() }.take(2) },
                label = { Text("Maaş günü (ayın kaçı?)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    if (c.prefs.syncConfig == null) c.prefs.syncConfig = PairingCode.newConfig()
                    c.repo.createHousehold(
                        householdName = household.ifBlank { "Evimizin bütçesi" },
                        myName = myName,
                        partnerName = partner.ifBlank { null },
                        startBalance = amountToKurus(balance).takeIf { it > 0 },
                        salary = amountToKurus(salary).takeIf { it > 0 },
                        payDay = payDay.toIntOrNull() ?: 1,
                        memberColors = Palette.members[0] to Palette.members[1],
                    )
                    c.engine.restart()
                    onDone()
                }
            },
            enabled = myName.isNotBlank() && !busy,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Bütçeyi oluştur", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun JoinForm(onBack: () -> Unit, onJoined: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun tryJoin(text: String) {
        val cfg = PairingCode.decode(text)
        if (cfg == null) {
            error = "Kod tanınmadı. Eşinizin telefonundaki QR'ı okutun ya da \"BTZ1.\" ile başlayan kodun tamamını yapıştırın."
            return
        }
        scope.launch {
            c.repo.prepareJoin()
            c.prefs.resetSyncState()
            c.prefs.syncConfig = cfg
            c.prefs.awaitingInitialSync = true
            c.engine.restart()
            c.engine.requestNow(force = true)
            onJoined()
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader("Eşimin bütçesine katıl", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                Text("1. Eşinizin telefonunda", style = MaterialTheme.typography.titleSmall)
                Text("Bütçemiz → Diğer → Aile & eşleştirme ekranındaki QR kodu açın.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("2. Bu telefonda", style = MaterialTheme.typography.titleSmall)
                Text("\"QR okut\"a dokunup kodu okutun ya da eşinizin gönderdiği kodu yapıştırın.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { scanQr(context, { v -> code = v; error = null; tryJoin(v) }, { e -> error = e }) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("QR okut", style = MaterialTheme.typography.titleMedium)
            }
            FilledTonalButton(
                onClick = { pasteText(context)?.let { code = it; error = null } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.ContentPaste, null)
                Spacer(Modifier.width(8.dp))
                Text("Kodu yapıştır")
            }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it; error = null },
                label = { Text("Davet kodu") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
            )
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { tryJoin(code) },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp).height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Katıl", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun WaitingForPartner(onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val members by c.repo.members.collectAsStateWithLifecycle(emptyList())
    val paired by c.prefs.pairedFlow.collectAsStateWithLifecycle()
    var manual by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Logo(80)
        if (members.isEmpty() && !manual) {
            Text("Eşinizin telefonuna bağlanılıyor…", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            CircularProgressIndicator()
            Text(
                if (paired) "Bağlantı kuruldu, veriler alınıyor…" else "Eşinizin telefonunda Bütçemiz açık ve internete bağlı olsun. Veriler birkaç saniye içinde gelir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { manual = true }) { Text("Beklemeden devam et") }
        } else if (!manual) {
            Text("Hoş geldiniz! Siz hangisisiniz?", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Bu telefonda girilen harcamalar seçtiğiniz kişi adına kaydedilir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            members.forEach { m ->
                AppCard(onClick = {
                    scope.launch {
                        c.repo.chooseMember(m.id)
                        c.prefs.onboarded = true
                        onDone()
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(m, size = 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            TextButton(onClick = { manual = true }) { Text("Listede yokum") }
        } else {
            Text("Adınız nedir?", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Adınız") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Text(
                "Eşinizin verileri bağlantı kurulunca otomatik gelecek.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    scope.launch {
                        c.repo.createMemberAndChoose(name, Palette.members[1])
                        c.prefs.onboarded = true
                        onDone()
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("Başla") }
            if (members.isNotEmpty()) TextButton(onClick = { manual = false }) { Text("Listeye dön") }
        }
    }
}

/** Uygulama kilidi ekranı */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(onClick = onUnlock),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Logo(88)
            Text("Bütçemiz kilitli", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onUnlock, shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text("Kilidi aç")
            }
            Text("Parmak izi, yüz ya da ekran kilidiyle", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
