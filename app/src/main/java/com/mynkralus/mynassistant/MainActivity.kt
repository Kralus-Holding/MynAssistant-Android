package com.mynkralus.mynassistant

import android.content.Context
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource

private val Accent = Color(0xFF8B9CFF)
private val Mint = Color(0xFF9DD9D2)
private const val PREFS = "mynassistant_prefs"

private fun firebaseAuthErrorMessage(error: Exception?): String {
    val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
    return when {
        code.contains("EMAIL_ALREADY_IN_USE") -> "Bu e-posta adresiyle zaten bir hesap var. Giriş yapmayı dene."
        code.contains("INVALID_EMAIL") -> "E-posta adresi geçerli değil."
        code.contains("WEAK_PASSWORD") -> "Şifre çok zayıf. Daha güçlü bir şifre kullan."
        code.contains("USER_NOT_FOUND") || code.contains("INVALID_CREDENTIAL") || code.contains("WRONG_PASSWORD") ->
            "E-posta veya şifre hatalı."
        code.contains("OPERATION_NOT_ALLOWED") -> "Bu giriş yöntemi Firebase Console'da etkin değil."
        code.contains("TOO_MANY_REQUESTS") -> "Çok fazla deneme yapıldı. Bir süre sonra tekrar dene."
        code.contains("NETWORK_REQUEST_FAILED") -> "Bağlantı hatası. İnternetini kontrol edip tekrar dene."
        code.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") ->
            "Bu e-posta başka bir giriş yöntemiyle kayıtlı. Önce o yöntemle giriş yap."
        else -> "Firebase işlemi başarısız${if (code.isNotBlank()) " ($code)" else ""}: ${error?.localizedMessage ?: "bilinmeyen hata"}"
    }
}

private fun saveFirebaseProfile(
    user: FirebaseUser,
    provider: String,
    isNewUser: Boolean,
    displayNameOverride: String? = null,
    onComplete: (String?) -> Unit
) {
    val profile = hashMapOf<String, Any>(
        "uid" to user.uid,
        "displayName" to (displayNameOverride?.takeIf { it.isNotBlank() } ?: user.displayName.orEmpty()),
        "email" to user.email.orEmpty(),
        "phoneNumber" to user.phoneNumber.orEmpty(),
        "provider" to provider,
        "lastSignInAt" to FieldValue.serverTimestamp()
    )
    if (isNewUser) profile["createdAt"] = FieldValue.serverTimestamp()

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.uid)
        .set(profile, SetOptions.merge())
        .addOnSuccessListener { onComplete(null) }
        .addOnFailureListener { error ->
            onComplete(error.localizedMessage ?: "Firestore'a yazılamadı.")
        }
}
private const val WEB_CLIENT_ID = "994290744643-em1j9c951okq1flhq9n00jcuduqpvjls.apps.googleusercontent.com"
private enum class Page { HOME, LOGIN, REGISTER, DEVICES, SETTINGS, REMOTE }

class MainActivity : ComponentActivity() {
    private val googleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(WEB_CLIENT_ID)
            .build()
        GoogleSignIn.getClient(this, options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Remove old local-only demo account data. Firebase now owns auth sessions.
        prefs.edit().remove("signed_in").remove("demo_password").remove("auth_provider")
            .remove("name").remove("email").remove("phone").apply()
        val auth = FirebaseAuth.getInstance()
        setContent {
            val currentUser = auth.currentUser
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            var email by remember { mutableStateOf(currentUser?.email.orEmpty()) }
            var phone by remember { mutableStateOf(currentUser?.phoneNumber.orEmpty()) }
            var name by remember {
                mutableStateOf(currentUser?.displayName?.takeIf { it.isNotBlank() }
                    ?: currentUser?.email?.substringBefore("@").orEmpty())
            }
            var signedIn by remember { mutableStateOf(currentUser != null) }
            var photoUrl by remember { mutableStateOf(currentUser?.photoUrl?.toString().orEmpty()) }
            var googleStatus by remember { mutableStateOf<String?>(null) }
            var accountMenuExpanded by remember { mutableStateOf(false) }
            val googleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { activityResult ->
                try {
                    val account = GoogleSignIn.getSignedInAccountFromIntent(activityResult.data)
                        .getResult(ApiException::class.java)
                    val idToken = account.idToken
                    if (idToken.isNullOrBlank()) {
                        googleStatus = "Google kimlik anahtarı alınamadı. OAuth yapılandırmasını ve SHA-1 değerini kontrol et."
                    } else {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(credential).addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                googleStatus = firebaseAuthErrorMessage(task.exception)
                            } else {
                                val user = task.result?.user ?: auth.currentUser
                                if (user == null) {
                                    googleStatus = "Google girişi tamamlandı ancak Firebase kullanıcı bilgisi alınamadı."
                                } else {
                                    name = user.displayName?.takeIf { it.isNotBlank() }
                                        ?: user.email?.substringBefore("@").orEmpty()
                                    email = user.email.orEmpty()
                                    phone = user.phoneNumber.orEmpty()
                                    photoUrl = user.photoUrl?.toString()?.takeIf { it.isNotBlank() }
                                        ?: account.photoUrl?.toString().orEmpty()
                                    signedIn = true
                                    saveFirebaseProfile(
                                        user = user,
                                        provider = "google.com",
                                        isNewUser = task.result?.additionalUserInfo?.isNewUser == true
                                    ) { firestoreError ->
                                        googleStatus = if (firestoreError == null) {
                                            "Google ile giriş yapıldı."
                                        } else {
                                            "Giriş başarılı; profil Firestore'a kaydedilemedi. Firestore veritabanını ve güvenlik kurallarını kontrol et. ($firestoreError)"
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (error: ApiException) {
                    googleStatus = when (error.statusCode) {
                        10 -> "Google OAuth yapılandırma hatası (10). Android OAuth istemcisindeki paket adı ve SHA-1 eşleşmesini kontrol et."
                        12501 -> "Google hesabı seçilmedi veya giriş iptal edildi (12501)."
                        else -> "Google giriş başarısız (kod ${error.statusCode}). OAuth ve test kullanıcısı ayarlarını kontrol et."
                    }
                } catch (error: Exception) {
                    googleStatus = "Google giriş sonucu okunamadı (${error.javaClass.simpleName}). Tekrar dene."
                }
            }
            val startGoogleSignIn = { googleLauncher.launch(googleSignInClient.signInIntent) }
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
                    signedIn = signedIn, name = name, email = email, phone = phone, photoUrl = photoUrl,
                    login = { identity, password, report ->
                        val cleanEmail = identity.trim()
                        if (!cleanEmail.contains("@")) {
                            val message = "Firebase hesabıyla giriş için e-posta adresini gir."
                            googleStatus = message
                            report(message)
                        } else {
                            auth.signInWithEmailAndPassword(cleanEmail, password)
                                .addOnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        val message = firebaseAuthErrorMessage(task.exception)
                                        googleStatus = message
                                        report(message)
                                    } else {
                                        val user = task.result?.user ?: auth.currentUser
                                        if (user == null) {
                                            val message = "Giriş tamamlandı ancak kullanıcı bilgisi alınamadı."
                                            googleStatus = message
                                            report(message)
                                        } else {
                                            name = user.displayName?.takeIf { it.isNotBlank() }
                                                ?: user.email?.substringBefore("@").orEmpty()
                                            email = user.email.orEmpty()
                                            phone = user.phoneNumber.orEmpty()
                                            photoUrl = user.photoUrl?.toString().orEmpty()
                                            signedIn = true
                                            saveFirebaseProfile(user, "password", false) { firestoreError ->
                                                val message = if (firestoreError == null) {
                                                    "Firebase hesabıyla giriş başarılı."
                                                } else {
                                                    "Giriş başarılı; profil Firestore'a kaydedilemedi. Firestore veritabanı ve güvenlik kurallarını kontrol et. ($firestoreError)"
                                                }
                                                googleStatus = message
                                                report(message)
                                            }
                                        }
                                    }
                                }
                        }
                    },
                    register = { newName, identity, password, report ->
                        val cleanName = newName.trim()
                        val cleanEmail = identity.trim()
                        if (cleanName.isBlank() || !cleanEmail.contains("@") || password.length < 6) {
                            val message = "Ad soyad, geçerli e-posta adresi ve en az 6 karakterli şifre gerekli."
                            googleStatus = message
                            report(message)
                        } else {
                            auth.createUserWithEmailAndPassword(cleanEmail, password)
                                .addOnCompleteListener { task ->
                                    if (!task.isSuccessful) {
                                        val message = firebaseAuthErrorMessage(task.exception)
                                        googleStatus = message
                                        report(message)
                                    } else {
                                        val user = task.result?.user
                                        if (user == null) {
                                            val message = "Hesap oluşturuldu ancak Firebase kullanıcı bilgisi alınamadı."
                                            googleStatus = message
                                            report(message)
                                        } else {
                                            val profileRequest = UserProfileChangeRequest.Builder()
                                                .setDisplayName(cleanName)
                                                .build()
                                            user.updateProfile(profileRequest).addOnCompleteListener {
                                                val activeUser = auth.currentUser ?: user
                                                name = cleanName
                                                email = activeUser.email.orEmpty()
                                                phone = activeUser.phoneNumber.orEmpty()
                                                photoUrl = activeUser.photoUrl?.toString().orEmpty()
                                                signedIn = true
                                                activeUser.sendEmailVerification().addOnCompleteListener { verifyTask ->
                                                    saveFirebaseProfile(
                                                        user = activeUser,
                                                        provider = "password",
                                                        isNewUser = true,
                                                        displayNameOverride = cleanName
                                                    ) { firestoreError ->
                                                        val verificationMessage = if (verifyTask.isSuccessful) {
                                                            " E-posta doğrulama bağlantısı gönderildi."
                                                        } else {
                                                            " Hesap oluşturuldu ancak doğrulama e-postası gönderilemedi."
                                                        }
                                                        val profileMessage = if (firestoreError == null) {
                                                            ""
                                                        } else {
                                                            " Profil Firestore'a kaydedilemedi; veritabanını ve güvenlik kurallarını kontrol et. ($firestoreError)"
                                                        }
                                                        val message = "Firebase hesabın oluşturuldu." + verificationMessage + profileMessage
                                                        googleStatus = message
                                                        report(message)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }
                    },
                    logout = {
                        auth.signOut()
                        googleSignInClient.signOut()
                        signedIn = false
                        name = ""
                        email = ""
                        phone = ""
                        photoUrl = ""
                        googleStatus = "Oturum kapatıldı."
                    },
                    googleLogin = startGoogleSignIn,
                    googleStatus = googleStatus,
                    accountMenuExpanded = accountMenuExpanded,
                    toggleAccountMenu = { accountMenuExpanded = !accountMenuExpanded }
                )
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    dark: Boolean, toggleTheme: () -> Unit, signedIn: Boolean, name: String,
    email: String, phone: String, photoUrl: String, login: (String, String, (String) -> Unit) -> Unit,
    register: (String, String, String, (String) -> Unit) -> Unit, logout: () -> Unit,
    googleLogin: () -> Unit,
    googleStatus: String?, accountMenuExpanded: Boolean, toggleAccountMenu: () -> Unit
) {
    var page by remember { mutableStateOf(Page.HOME) }
    var drawer by remember { mutableStateOf(false) }
    var command by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 24.dp),
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
                            googleStatus?.let { status -> Text(status, color = if (status == "Google ile giriş yapıldı.") Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(false, login = login, googleLogin = googleLogin, googleStatus = googleStatus, goRegister = { page = Page.REGISTER })
                        Page.REGISTER -> if (signedIn) {
                            Text("Firebase hesabın açık.", color = MaterialTheme.colorScheme.secondary)
                            Text("E-posta: " + email.ifBlank { "Eklenmemiş" })
                            googleStatus?.let { status -> Text(status, color = if (status == "Google ile giriş yapıldı.") Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(true, register = register, googleLogin = googleLogin, googleStatus = googleStatus, goRegister = { page = Page.LOGIN })
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
                            .statusBarsPadding().verticalScroll(rememberScrollState())
                            .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = toggleAccountMenu).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (signedIn) {
                                    if (photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Google profil fotoğrafı",
                                            modifier = Modifier.size(58.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = "Hesap fotoğrafı",
                                            modifier = Modifier.size(58.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    MynLogo()
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (signedIn) name.ifBlank { email.substringBefore("@") } else "MynAssistant", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text(if (signedIn) email.ifBlank { phone } else "Google hesabını bağla", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), maxLines = 1)
                                }
                                Text(if (accountMenuExpanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = accountMenuExpanded) {
                                Column(
                                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 8.dp)
                                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp)).padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (signedIn) {
                                        Text("Google hesabı", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                        Text(email.ifBlank { phone.ifBlank { name } }, fontSize = 14.sp)
                                        Text("Başka bir hesap kullanmak için aşağıdan hesap seç.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                                        OutlinedButton(
                                            onClick = { toggleAccountMenu(); googleLogin() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Hesabı değiştir") }
                                    } else {
                                        Text("Google hesabınla giriş yaparak cihazlarını bu hesaba bağlayabilirsin.", fontSize = 12.sp)
                                        Button(
                                            onClick = { toggleAccountMenu(); googleLogin() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                GoogleGLogo(Modifier.size(20.dp))
                                                Text("Google ile devam et")
                                            }
                                        }
                                    }
                                    googleStatus?.let { status -> Text(status, fontSize = 11.sp, color = if (status == "Google ile giriş yapıldı.") Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary) }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DrawerItem("Ana sayfa") { page = Page.HOME; drawer = false }
                        DrawerItem(if (signedIn) "Hesabım" else "Giriş yap") { page = Page.LOGIN; drawer = false }
                        if (!signedIn) DrawerItem("Kayıt ol") { page = Page.REGISTER; drawer = false }
                        DrawerItem("Cihazlarım") { page = Page.DEVICES; drawer = false }
                        DrawerItem("Ayarlar") { page = Page.SETTINGS; drawer = false }
                        DrawerItem("PC Kumandası") { page = Page.REMOTE; drawer = false }
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
        "PC Kumandası" -> Icons.Default.Computer
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
    login: (String, String, (String) -> Unit) -> Unit = { _, _, done -> done("Giriş işlemi yapılandırılmadı.") },
    register: (String, String, String, (String) -> Unit) -> Unit = { _, _, _, done -> done("Kayıt işlemi yapılandırılmadı.") },
    googleLogin: () -> Unit = {},
    googleStatus: String? = null,
    goRegister: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (registerMode) OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Ad soyad") }, singleLine = true)
            OutlinedTextField(identity, { identity = it }, Modifier.fillMaxWidth(), label = { Text("E-posta adresi") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Şifre (en az 6 karakter)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(
                onClick = {
                    message = "İşlem yapılıyor..."
                    if (registerMode) {
                        register(name, identity, password) { message = it }
                    } else {
                        login(identity, password) { message = it }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (registerMode) "Kayıt ol" else "Giriş yap")
            }
            OutlinedButton(onClick = googleLogin, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoogleGLogo(Modifier.size(20.dp))
                    Text("Google ile devam et")
                }
            }
            googleStatus?.let { status -> Text(status, color = if (status == "Google ile giriş yapıldı.") Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
            TextButton(onClick = goRegister, Modifier.fillMaxWidth()) { Text(if (registerMode) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
            Text("Hesap Firebase Authentication ile yönetilir. E-posta doğrulaması için gelen kutunu kontrol et.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
    }
}


@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.google_logo),
        contentDescription = "Google",
        modifier = modifier
    )
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
