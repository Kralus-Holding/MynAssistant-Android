package com.mynkralus.mynassistant

import android.content.Context
import android.os.Bundle
import java.security.MessageDigest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.AccountCircle
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun hashPassword(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

private val Accent = Color(0xFF8B9CFF)
private val Mint = Color(0xFF9DD9D2)
private const val PREFS = "mynassistant_prefs"
private enum class Page { HOME, LOGIN, REGISTER, DEVICES, SETTINGS, REMOTE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
            var phone by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
            var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
            var passwordSaved by remember { mutableStateOf(prefs.getString("demo_password", "") ?: "") }
            var signedIn by remember { mutableStateOf(prefs.getBoolean("signed_in", false)) }
            val scheme = if (dark) darkColorScheme(
                primary = Accent, onPrimary = Color(0xFF10142C), secondary = Mint,
                background = Color(0xFF0B0D14), surface = Color(0xFF171C2A),
                onBackground = Color(0xFFF2F3FA), onSurface = Color(0xFFF2F3FA)
            ) else lightColorScheme(
                primary = Color(0xFF4D5FD7), secondary = Color(0xFF16796F),
                background = Color(0xFFF5F6FA), surface = Color.White,
                onBackground = Color(0xFF171923), onSurface = Color(0xFF171923)
            )
            MaterialTheme(colorScheme = scheme) {
                AssistantScreen(
                    dark = dark,
                    toggleTheme = { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() },
                    signedIn = signedIn, name = name, email = email, phone = phone,
                    login = { identity, password ->
                        if ((identity.equals(email, true) && email.isNotBlank() || identity == phone && phone.isNotBlank()) &&
                            hashPassword(password) == passwordSaved && name.isNotBlank()) {
                            signedIn = true
                            prefs.edit().putBoolean("signed_in", true).apply()
                            "Giriş yapıldı."
                        } else "Demo hesap bulunamadı veya bilgiler yanlış. Önce bu cihazda kayıt ol."
                    },
                    register = { newName, identity, password ->
                        if (newName.isBlank() || identity.isBlank() || password.length < 6) {
                            "Ad ve e-posta/telefon gerekli; şifre en az 6 karakter olmalı."
                        } else {
                            name = newName.trim()
                            email = if (identity.contains("@")) identity.trim() else ""
                            phone = if (identity.contains("@")) "" else identity.trim()
                            passwordSaved = hashPassword(password)
                            signedIn = true
                            prefs.edit().putString("name", name).putString("email", email)
                                .putString("phone", phone).putString("demo_password", passwordSaved)
                                .putBoolean("signed_in", true).apply()
                            "Demo hesap oluşturuldu. Oturum bu cihazda hatırlanacak."
                        }
                    },
                    logout = { signedIn = false; prefs.edit().putBoolean("signed_in", false).apply() }
                )
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    dark: Boolean, toggleTheme: () -> Unit, signedIn: Boolean, name: String,
    email: String, phone: String, login: (String, String) -> String,
    register: (String, String, String) -> String, logout: () -> Unit
) {
    var page by remember { mutableStateOf(Page.HOME) }
    var drawer by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, top = 48.dp, end = 22.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drawer = true }) { Icon(Icons.Default.Menu, contentDescription = "Menüyü aç", modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(42.dp).clickable { page = Page.REMOTE }, contentAlignment = Alignment.Center) { Icon(Icons.Default.SettingsRemote, contentDescription = "PC kumandası", modifier = Modifier.size(32.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = toggleTheme, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp)); Text(if (dark) "Açık" else "Koyu", fontSize = 14.sp) }
                    }
                }
                if (page == Page.HOME) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MynLogo()
                        Column {
                            Text("MynAssistant", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            Text("Kişisel asistanın için yeni başlangıç.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .68f))
                        }
                    }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("KOMUT MERKEZİ", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Sana nasıl yardımcı olabilirim?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text("Metin veya sesli komutlarını buradan yönet.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                        }
                    }
                    Text("Ne yapmamı istersin?", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(command, { command = it; feedback = null }, Modifier.fillMaxWidth(), placeholder = { Text("Bir komut yaz...") }, minLines = 2, shape = RoundedCornerShape(16.dp))
                    Button(
                        onClick = { feedback = if (command.isBlank()) "Önce bir komut yaz." else "Komut alındı. Gerçek komut çalıştırma sonraki aşamada eklenecek." },
                        Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
                    ) { Text("Komutu gönder", fontWeight = FontWeight.SemiBold) }
                    feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                } else {
                    Text(when (page) {
                        Page.LOGIN -> if (signedIn) "Hesabım" else "Giriş yap"
                        Page.REGISTER -> "Kayıt ol"
                        Page.DEVICES -> "Cihazlarım"
                        Page.SETTINGS -> "Ayarlar"
                        Page.REMOTE -> "PC Kumandası"
                        else -> "Ana sayfa"
                    }, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    when (page) {
                        Page.LOGIN -> if (signedIn) {
                            Text("Oturum açık", color = MaterialTheme.colorScheme.secondary)
                            Text("Ad: " + name)
                            Text("E-posta: " + email.ifBlank { "Eklenmemiş" })
                            Text("Telefon: " + phone.ifBlank { "Eklenmemiş" })
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(false, login = login, goRegister = { page = Page.REGISTER })
                        Page.REGISTER -> AuthForm(true, register = register, goRegister = { page = Page.LOGIN })
                        Page.DEVICES -> {
                            Text(
                                if (signedIn) "Yalnızca bu hesaba ait cihazlar burada görünür."
                                else "Cihazlarını görmek için önce hesabına giriş yap.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f),
                                fontSize = 14.sp
                            )
                            if (!signedIn) {
                                Button(onClick = { page = Page.LOGIN }, modifier = Modifier.fillMaxWidth()) { Text("Giriş yap") }
                                OutlinedButton(onClick = { page = Page.REGISTER }, modifier = Modifier.fillMaxWidth()) { Text("Kayıt ol") }
                            } else {
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text("Windows bilgisayarım", fontWeight = FontWeight.SemiBold)
                                                Text("Hesap: " + email.ifBlank { phone }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                                            }
                                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.background) {
                                                Text("Bağlantı bekleniyor", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 10.sp)
                                            }
                                        }
                                        Text(
                                            "Windows Agent bu hesapla sunucuya kaydolduğunda bilgisayar otomatik burada görünecek. Başka hesaplardaki kullanıcılar bu cihazı göremeyecek.",
                                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)
                                        )
                                        Text(
                                            "Not: Bu ekran şu an arayüz taslağıdır. Gerçek cihaz listesini göstermek için Google hesabı doğrulaması ve ortak sunucu bağlantısı gerekiyor.",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                OutlinedButton(onClick = { page = Page.SETTINGS }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Hesap ve bağlantı ayarları")
                                }
                            }
                        }
                        Page.SETTINGS -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (dark) "Koyu tema" else "Açık tema", Modifier.weight(1f))
                                Switch(checked = dark, onCheckedChange = { toggleTheme() })
                            }
                            Text("Tema tercihi uygulama yeniden açıldığında korunur.", fontSize = 13.sp)
                            Text(if (signedIn) "Oturum bu cihazda hatırlanıyor." else "Şu anda giriş yapılmamış.")
                        }
                        Page.REMOTE -> {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text("Windows bilgisayar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Bağlı değil • Windows Agent bekleniyor", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                                }
                                Surface(shape = CircleShape, color = Color(0xFFB85C5C)) { Box(Modifier.size(10.dp)) }
                            }
                            TvRemotePanel(
                                onHome = { feedback = "Ana ekran komutu Windows Agent bağlantısı kurulduğunda çalışacak." },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> Unit
                    }
                }
                Text("MynAssistant  •  Android 0.2.0", Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
            }
            if (drawer) {
                Row(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .48f))) {
                    Column(
                        Modifier.fillMaxHeight().fillMaxWidth(.84f).background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MynLogo()
                            Column(Modifier.weight(1f)) {
                                Text(if (signedIn) name.ifBlank { "MynAssistant hesabı" } else "MynAssistant", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(if (signedIn) email.ifBlank { phone } else "Kişisel asistanın", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DrawerItem("Ana sayfa") { page = Page.HOME; drawer = false }
                        DrawerItem(if (signedIn) "Hesabım" else "Giriş yap") { page = Page.LOGIN; drawer = false }
                        if (!signedIn) DrawerItem("Kayıt ol") { page = Page.REGISTER; drawer = false }
                        DrawerItem("Cihazlarım") { page = Page.DEVICES; drawer = false }
                        DrawerItem("Ayarlar") { page = Page.SETTINGS; drawer = false }
                        DrawerItem("TV kumandası") { page = Page.REMOTE; drawer = false }
                        Spacer(Modifier.weight(1f))
                        if (signedIn) OutlinedButton(onClick = { logout(); page = Page.HOME; drawer = false }, Modifier.fillMaxWidth()) { Text("Çıkış yap") }
                        Text("MynAssistant • Android 0.2.0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    }
                    Spacer(Modifier.fillMaxSize().clickable { drawer = false })
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, action: () -> Unit) {
    val icon = when (label) {
        "Ana sayfa" -> Icons.Default.Home
        "Giriş yap", "Hesabım" -> Icons.Default.Login
        "Kayıt ol" -> Icons.Default.PersonAdd
        "Cihazlarım" -> Icons.Default.Devices
        "Ayarlar" -> Icons.Default.Settings
        else -> Icons.Default.Tv
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AuthForm(
    registerMode: Boolean,
    login: (String, String) -> String = { _, _ -> "" },
    register: (String, String, String) -> String = { _, _, _ -> "" },
    goRegister: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (registerMode) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ad soyad") }, singleLine = true)
            OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("E-posta veya telefon numarası") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Şifre (en az 6 karakter)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = { message = if (registerMode) register(name, identity, password) else login(identity, password) }, Modifier.fillMaxWidth()) {
                Text(if (registerMode) "Kayıt ol" else "Giriş yap")
            }
            OutlinedButton(onClick = { message = "Google ile gerçek giriş için Google Cloud Console üzerinden Android uygulamasına ait OAuth yapılandırması ve Web Client ID gerekiyor. Bu bilgi eklenmeden güvenli giriş başlatılamaz." }, Modifier.fillMaxWidth()) { Text("G  Google ile devam et") }
            TextButton(onClick = goRegister, Modifier.fillMaxWidth()) { Text(if (registerMode) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
            Text("Geliştirme sürümü: demo hesap yalnızca bu telefonda saklanır; gerçek sunucu doğrulaması değildir.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
    }
}

@Composable
private fun MynLogo() {
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = "https://raw.githubusercontent.com/kralus24/MynConverter/master/app/src/main/res/drawable/splash_logo.png",
            contentDescription = "MynKralus logosu",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TvRemotePanel(onHome: () -> Unit, modifier: Modifier = Modifier) {
    val buttons = listOf(
        Triple("Aç / Kapat", Icons.Default.PowerSettingsNew, "power"),
        Triple("Sesi kapat", Icons.Default.VolumeOff, "mute"),
        Triple("Sesi artır", Icons.Default.VolumeUp, "up"),
        Triple("Sesi kıs", Icons.Default.VolumeDown, "down"),
        Triple("Ana ekran", Icons.Default.Home, "home"),
        Triple("Diğer", Icons.Default.MoreHoriz, "more")
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC KUMANDASI", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            buttons.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        Column(Modifier.weight(1f).height(82.dp).background(MaterialTheme.colorScheme.background, RoundedCornerShape(18.dp)).clickable { if (item.third == "home") onHome() }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(item.second, contentDescription = item.first, modifier = Modifier.size(27.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(5.dp))
                            Text(item.first, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().height(250.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).clickable { }.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Dokunmatik alan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Bilgisayar faresini kontrol etmek için", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Fare hareketleri, tıklama ve kaydırma bağlantı aşamasında etkinleştirilecek.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), textAlign = TextAlign.Center)
        }
    }
}
