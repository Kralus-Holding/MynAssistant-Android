package com.mynkralus.mynassistant

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.os.Build
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val Accent = Color(0xFF8B9CFF)
private val Mint = Color(0xFF9DD9D2)
private const val PREFS = "mynassistant_prefs"
private const val GOOGLE_ACCOUNTS_KEY = "saved_google_accounts"

private data class SavedGoogleAccount(val email: String, val name: String, val photoUrl: String)

private data class PcDevice(
    val id: String,
    val name: String,
    val platform: String,
    val status: String,
    val connectionMode: String = "",
    val screenJpegBase64: String = ""
) {
    val isOnline: Boolean
        get() = status.trim().lowercase() in setOf("online", "connected", "ready", "active")
}

private fun loadSavedGoogleAccounts(prefs: android.content.SharedPreferences): List<SavedGoogleAccount> {
    val array = try { JSONArray(prefs.getString(GOOGLE_ACCOUNTS_KEY, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val email = item.optString("email").trim()
            if (email.isNotBlank()) add(SavedGoogleAccount(email, item.optString("name"), item.optString("photoUrl")))
        }
    }.distinctBy { it.email.lowercase() }
}

private fun saveGoogleAccount(prefs: android.content.SharedPreferences, account: SavedGoogleAccount): List<SavedGoogleAccount> {
    val updated = (listOf(account) + loadSavedGoogleAccounts(prefs)).distinctBy { it.email.lowercase() }
    val array = JSONArray()
    updated.forEach { array.put(JSONObject().put("email", it.email).put("name", it.name).put("photoUrl", it.photoUrl)) }
    prefs.edit().putString(GOOGLE_ACCOUNTS_KEY, array.toString()).apply()
    return updated
}

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
private data class PcBridgeResult(
    val connected: Boolean,
    val success: Boolean,
    val message: String
)

private fun callMynAiBridge(baseUrl: String, token: String, command: String? = null): PcBridgeResult {
    var base = baseUrl.trim().trimEnd('/')
    if (base.endsWith("/health")) base = base.removeSuffix("/health")
    if (base.endsWith("/command")) base = base.removeSuffix("/command")
    base = base.trimEnd('/')
    if (base.isBlank() || !(base.startsWith("http://") || base.startsWith("https://"))) {
        return PcBridgeResult(false, false, "Windows köprü adresini http://192.168.x.x:8765 biçiminde gir.")
    }

    var connection: java.net.HttpURLConnection? = null
    return try {
        val endpoint = if (command == null) "$base/health" else "$base/command"
        connection = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (command != null) {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            } else {
                requestMethod = "GET"
            }
        }
        if (command != null) {
            val body = JSONObject().put("text", command).put("token", token.trim()).toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
        }

        val statusCode = connection.responseCode
        val responseStream = try {
            if (statusCode in 200..299) connection.inputStream else connection.errorStream
        } catch (_: Exception) {
            connection.errorStream
        }
        val bodyText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (statusCode !in 200..299) {
            val serverError = try { JSONObject(bodyText).optString("error") } catch (_: Exception) { "" }
            return PcBridgeResult(
                false, false,
                "PC köprüsü HTTP " + statusCode + " yanıtı verdi" +
                    (if (serverError.isNotBlank()) ": " + serverError else ".")
            )
        }

        val response = try { JSONObject(bodyText) } catch (_: Exception) {
            return PcBridgeResult(true, false, "PC köprüsüne ulaşıldı ancak yanıt JSON biçiminde değil.")
        }
        if (command == null) {
            val ok = response.optBoolean("ok", true)
            val message = response.optString("message").takeIf { it.isNotBlank() }
                ?: if (ok) "Windows köprüsü yanıt verdi." else "Windows köprüsü hazır değil."
            PcBridgeResult(ok, ok, message)
        } else {
            val result = response.optJSONObject("result")
            val ok = response.optBoolean("ok", result?.optBoolean("ok", false) ?: false)
            val message = result?.optString("message").orEmpty()
            val actionText = result?.optJSONObject("data")?.optString("action_text").orEmpty()
            val displayText = when {
                message.isBlank() -> actionText.ifBlank { "PC komutu yanıtlandı." }
                actionText.isBlank() || actionText == message -> message
                else -> message + "\n" + actionText
            }
            PcBridgeResult(true, ok, displayText)
        }
    } catch (error: Exception) {
        PcBridgeResult(
            false, false,
            "PC köprüsüne ulaşılamadı: " + (error.message ?: error.javaClass.simpleName) +
                ". Telefon ve bilgisayarın aynı Wi-Fi ağında olduğundan emin ol."
        )
    } finally {
        connection?.disconnect()
    }
}

private fun saveWindowsBridgeDevice(
    user: FirebaseUser,
    bridgeUrl: String,
    connected: Boolean,
    onComplete: (String?) -> Unit
) {
    val data = hashMapOf<String, Any>(
        "ownerId" to user.uid,
        "name" to "Windows bilgisayarım",
        "platform" to "Windows",
        "type" to "Windows",
        "status" to if (connected) "online" else "offline",
        "connectionMode" to "mynai-local-bridge",
        "bridgeUrl" to bridgeUrl.trim(),
        "lastCheckedAt" to FieldValue.serverTimestamp()
    )
    if (connected) data["lastSeenAt"] = FieldValue.serverTimestamp()
    FirebaseFirestore.getInstance()
        .collection("devices")
        .document("windows-" + user.uid)
        .set(data, SetOptions.merge())
        .addOnSuccessListener { onComplete(null) }
        .addOnFailureListener { error -> onComplete(error.localizedMessage ?: "Firestore cihaz kaydı başarısız.") }
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
            val context = LocalContext.current
            val profileImageLoader = remember(context) {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
            }
            val currentUser = auth.currentUser
            var savedGoogleAccounts by remember { mutableStateOf(loadSavedGoogleAccounts(prefs)) }
            if (currentUser != null && currentUser.providerData.any { it.providerId == "google.com" } &&
                !currentUser.email.isNullOrBlank() &&
                savedGoogleAccounts.none { it.email.equals(currentUser.email, ignoreCase = true) }) {
                savedGoogleAccounts = saveGoogleAccount(
                    prefs,
                    SavedGoogleAccount(currentUser.email.orEmpty(), currentUser.displayName.orEmpty(), currentUser.photoUrl?.toString().orEmpty())
                )
            }
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
                                    savedGoogleAccounts = saveGoogleAccount(prefs, SavedGoogleAccount(email, name, photoUrl))
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
            val startGoogleSignIn: () -> Unit = {
                googleSignInClient.signOut().addOnCompleteListener {
                    googleLauncher.launch(googleSignInClient.signInIntent)
                }
            }
            val selectSavedGoogleAccount: (String) -> Unit = { selectedEmail ->
                val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail().requestProfile().requestIdToken(WEB_CLIENT_ID)
                    .setAccountName(selectedEmail).build()
                val client = GoogleSignIn.getClient(this@MainActivity, options)
                client.signOut().addOnCompleteListener { googleLauncher.launch(client.signInIntent) }
            }
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
                    firebaseUserId = auth.currentUser?.uid,
                    initialPcBridgeUrl = prefs.getString("pc_bridge_url_" + (auth.currentUser?.uid ?: "guest"), "") ?: "",
                    initialPcBridgeToken = prefs.getString("pc_bridge_token_" + (auth.currentUser?.uid ?: "guest"), "meyene") ?: "meyene",
                    savePcBridgeSettings = { url, token ->
                        val scope = auth.currentUser?.uid ?: "guest"
                        prefs.edit()
                            .putString("pc_bridge_url_$scope", url.trim())
                            .putString("pc_bridge_token_$scope", token.trim())
                            .apply()
                    },
                    checkPcBridge = { url, token, report ->
                        Thread {
                            val result = callMynAiBridge(url, token)
                            runOnUiThread {
                                val user = auth.currentUser
                                if (user != null && url.isNotBlank()) {
                                    saveWindowsBridgeDevice(user, url, result.connected) { firestoreError ->
                                        val finalResult = if (firestoreError == null) result else result.copy(
                                            message = result.message + "\nCihaz bağlantısı Firebase'e kaydedilemedi: " + firestoreError
                                        )
                                        report(finalResult)
                                    }
                                } else {
                                    report(result)
                                }
                            }
                        }.start()
                    },
                    sendPcBridgeCommand = { url, token, commandText, report ->
                        Thread {
                            val result = callMynAiBridge(url, token, commandText)
                            runOnUiThread { report(result) }
                        }.start()
                    },
                    sendRemoteCommand = { deviceId, commandName, payload, report ->
                        val user = auth.currentUser
                        if (user == null) {
                            report("Önce Firebase hesabına giriş yap.")
                        } else {
                            val commandData = hashMapOf<String, Any>(
                                "ownerId" to user.uid,
                                "deviceId" to deviceId,
                                "command" to commandName,
                                "status" to "pending",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                            commandData.putAll(payload)
                            FirebaseFirestore.getInstance().collection("commands")
                                .add(commandData)
                                .addOnSuccessListener {
                                    report("Komut kuyruğa gönderildi. Windows Agent komutu dinlediğinde bilgisayarda uygulanacak.")
                                }
                                .addOnFailureListener { error ->
                                    report("Komut kaydedilemedi: ${error.localizedMessage ?: "Firestore erişim hatası"}")
                                }
                        }
                    },
                    photoImageLoader = profileImageLoader,
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
                        googleStatus = null
                    },
                    googleLogin = startGoogleSignIn,
                    selectSavedGoogleAccount = selectSavedGoogleAccount,
                    savedGoogleAccounts = savedGoogleAccounts,
                    googleStatus = googleStatus,
                )
            }
        }
    }
}

@Composable
private fun AssistantScreen(
    dark: Boolean, toggleTheme: () -> Unit, signedIn: Boolean, name: String,
    email: String, phone: String, photoUrl: String, firebaseUserId: String?,
    initialPcBridgeUrl: String,
    initialPcBridgeToken: String,
    savePcBridgeSettings: (String, String) -> Unit,
    checkPcBridge: (String, String, (PcBridgeResult) -> Unit) -> Unit,
    sendPcBridgeCommand: (String, String, String, (PcBridgeResult) -> Unit) -> Unit,
    sendRemoteCommand: (String, String, Map<String, Any>, (String) -> Unit) -> Unit,
    photoImageLoader: ImageLoader, login: (String, String, (String) -> Unit) -> Unit,
    register: (String, String, String, (String) -> Unit) -> Unit, logout: () -> Unit,
    googleLogin: () -> Unit,
    selectSavedGoogleAccount: (String) -> Unit,
    savedGoogleAccounts: List<SavedGoogleAccount>,
    googleStatus: String?
) {
    var page by remember { mutableStateOf(Page.HOME) }
    var drawer by remember { mutableStateOf(false) }
    var accountListExpanded by remember { mutableStateOf(false) }
    var devices by remember(firebaseUserId) { mutableStateOf<List<PcDevice>>(emptyList()) }
    var devicesLoading by remember(firebaseUserId) { mutableStateOf(!firebaseUserId.isNullOrBlank()) }
    var devicesError by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var selectedDeviceId by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var command by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pcBridgeUrl by remember(firebaseUserId) { mutableStateOf(initialPcBridgeUrl) }
    var pcBridgeToken by remember(firebaseUserId) { mutableStateOf(initialPcBridgeToken) }
    var pcBridgeStatus by remember(firebaseUserId) { mutableStateOf("Bilgisayar bağlantısı henüz test edilmedi.") }
    var localPcConnected by remember(firebaseUserId) { mutableStateOf(false) }
    var pcBridgeBusy by remember(firebaseUserId) { mutableStateOf(false) }

    DisposableEffect(firebaseUserId) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        if (!firebaseUserId.isNullOrBlank()) {
            devicesLoading = true
            registration = FirebaseFirestore.getInstance()
                .collection("devices")
                .whereEqualTo("ownerId", firebaseUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        devicesError = error.localizedMessage ?: "Cihaz listesi alınamadı."
                        devicesLoading = false
                    } else {
                        devices = snapshot?.documents.orEmpty().mapNotNull { document ->
                            if (document.getString("ownerId") != firebaseUserId) return@mapNotNull null
                            PcDevice(
                                id = document.id,
                                name = document.getString("name")
                                    ?: document.getString("deviceName")
                                    ?: "Windows bilgisayar",
                                platform = document.getString("platform")
                                    ?: document.getString("type")
                                    ?: document.getString("os")
                                    ?: "Windows",
                                status = document.getString("status")
                                    ?: document.getString("connectionStatus")
                                    ?: "offline",
                                connectionMode = document.getString("connectionMode").orEmpty(),
                                screenJpegBase64 = document.getString("screenJpegBase64").orEmpty()
                            )
                        }
                        devicesError = null
                        devicesLoading = false
                        if (devices.none { it.id == selectedDeviceId }) selectedDeviceId = devices.firstOrNull()?.id
                    }
                }
        } else {
            devices = emptyList()
            devicesLoading = false
            devicesError = null
            selectedDeviceId = null
        }
        onDispose { registration?.remove() }
    }
    val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId } ?: devices.firstOrNull()

    // While the remote-control page is open, ask the Windows Agent to provide a short-lived desktop preview.
    // The agent only sends frames while this lease is refreshed, so it does not upload screenshots in the background.
    LaunchedEffect(page, selectedDevice?.id, selectedDevice?.connectionMode, firebaseUserId, signedIn) {
        val target = selectedDevice
        if (page == Page.REMOTE && signedIn && !firebaseUserId.isNullOrBlank() &&
            target != null && target.connectionMode == "firebase-agent") {
            val deviceRef = FirebaseFirestore.getInstance().collection("devices").document(target.id)
            try {
                while (true) {
                    deviceRef.update(
                        "screenRequested", true,
                        "screenRequestedAt", FieldValue.serverTimestamp()
                    )
                    delay(10_000)
                }
            } finally {
                deviceRef.update(
                    "screenRequested", false,
                    "screenRequestedAt", FieldValue.serverTimestamp()
                )
            }
        }
    }

    val localPcDeviceId = firebaseUserId?.let { "windows-" + it }
    val testPcBridgeNow: () -> Unit = {
        if (!signedIn) {
            feedback = "Önce Firebase hesabına giriş yap."
        } else if (pcBridgeUrl.isBlank()) {
            feedback = "Önce Ayarlar bölümüne Windows köprü adresini gir."
            page = Page.SETTINGS
        } else {
            savePcBridgeSettings(pcBridgeUrl, pcBridgeToken)
            pcBridgeBusy = true
            checkPcBridge(pcBridgeUrl, pcBridgeToken) { result ->
                pcBridgeBusy = false
                localPcConnected = result.connected
                pcBridgeStatus = result.message
                if (localPcDeviceId != null) selectedDeviceId = localPcDeviceId
                feedback = result.message
            }
        }
    }
    val sendLocalPcText: (String) -> Unit = { commandText ->
        if (!signedIn) {
            feedback = "Önce Firebase hesabına giriş yap."
        } else if (!localPcConnected) {
            feedback = "Önce Ayarlar bölümünden Windows köprüsünü bağla ve bağlantıyı test et."
        } else {
            sendPcBridgeCommand(pcBridgeUrl, pcBridgeToken, commandText) { result ->
                localPcConnected = result.connected
                pcBridgeStatus = result.message
                feedback = result.message
            }
        }
    }
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
                            googleStatus?.let { status -> Text(status, color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(false, login = login, googleLogin = googleLogin, googleStatus = googleStatus, goRegister = { page = Page.REGISTER })
                        Page.REGISTER -> if (signedIn) {
                            Text("Firebase hesabın açık.", color = MaterialTheme.colorScheme.secondary)
                            Text("E-posta: " + email.ifBlank { "Eklenmemiş" })
                            googleStatus?.let { status -> Text(status, color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
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
                            } else {
                                Text(
                                    "Windows Agent bu hesaba bağlandığında bilgisayarın burada otomatik görünecek. IP adresi veya token girmen gerekmiyor.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                )
                                if (devicesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                devicesError?.let { error ->
                                    Text("Cihazlar okunamadı: " + error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                                if (!devicesLoading && devices.isEmpty() && devicesError == null) {
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text("Henüz bilgisayar bağlanmamış", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Windows bilgisayarına MynAssistant Agent kurup aynı Google hesabıyla bir kez giriş yaptıktan sonra cihaz burada otomatik listelenir. Telefon ve bilgisayarın aynı Wi-Fi ağında olması gerekmeyecek.",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                            )
                                        }
                                    }
                                }
                                devices.forEach { device ->
                                    val deviceOnlineNow = if (device.id == localPcDeviceId) localPcConnected else device.isOnline
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f)) {
                                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                                    Text(device.platform, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = if (deviceOnlineNow) Color(0xFF183D2B) else MaterialTheme.colorScheme.background
                                                ) {
                                                    Text(
                                                        if (deviceOnlineNow) "Çevrimiçi" else "Çevrimdışı",
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        fontSize = 11.sp,
                                                        color = if (deviceOnlineNow) Color(0xFF65D68B) else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                            Text("Cihaz kimliği: " + device.id, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                                            Button(onClick = { selectedDeviceId = device.id; page = Page.REMOTE }, modifier = Modifier.fillMaxWidth()) {
                                                Text("Bağlan")
                                            }
                                        }
                                    }
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
                            HorizontalDivider()
                            Text("Otomatik bilgisayar bağlantısı", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                        Column(Modifier.weight(1f)) {
                                            Text("Windows Agent", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                when {
                                                    !signedIn -> "Önce Google hesabınla giriş yap."
                                                    devicesLoading -> "Hesabına bağlı cihazlar aranıyor..."
                                                    devices.any { it.isOnline } -> "En az bir cihaz çevrimiçi."
                                                    else -> "Henüz çevrimiçi Windows Agent yok."
                                                },
                                                fontSize = 12.sp,
                                                color = if (devices.any { it.isOnline }) Color(0xFF65D68B) else MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                            )
                                        }
                                    }
                                    Text(
                                        "Windows Agent aynı Google hesabıyla bağlandığında bilgisayarını otomatik kaydeder. Cihaz adı ve çevrimiçi durumu Firebase üzerinden güncellenir. Telefona IP adresi veya güvenlik tokeni yazman gerekmez.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f)
                                    )
                                    Text(
                                        "Not: Bilgisayarın otomatik görünmesi için Windows Agent'ın ayrıca kurulup çalışıyor olması gerekir.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    OutlinedButton(
                                        onClick = { page = Page.DEVICES },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Devices, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Cihazlarımı gör")
                                    }
                                }
                            }
                        }
                        Page.REMOTE -> {
                            val device = selectedDevice
                            val screenBitmap = remember(device?.id, device?.screenJpegBase64) {
                                val encoded = device?.screenJpegBase64.orEmpty()
                                if (encoded.isBlank()) null else try {
                                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            val localBridgeSelected = signedIn && localPcConnected &&
                                (device == null || device.id == localPcDeviceId)
                            val remoteOnline = localBridgeSelected ||
                                (device?.isOnline == true && signedIn && device.id != localPcDeviceId)
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(device?.name ?: "Windows bilgisayar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        when {
                                            !signedIn -> "Giriş yapılmamış"
                                            localBridgeSelected -> "Bağlı • Yerel MynAi köprüsü"
                                            device == null -> "Bu hesaba bağlı bilgisayar bekleniyor"
                                            device.id == localPcDeviceId -> "Çevrimdışı • Bağlantıyı yeniden test et"
                                            remoteOnline -> "Bağlı • " + device.platform
                                            else -> "Çevrimdışı • Windows Agent bekleniyor"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
                                    )
                                }
                                Surface(shape = CircleShape, color = if (remoteOnline) Color(0xFF49B86E) else Color(0xFFB85C5C)) {
                                    Box(Modifier.size(10.dp))
                                }
                            }
                            if (device?.connectionMode == "firebase-agent") {
                                Text("Windows masaüstü", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                if (screenBitmap != null) {
                                    Image(
                                        bitmap = screenBitmap,
                                        contentDescription = "Windows masaüstü önizlemesi",
                                        modifier = Modifier.fillMaxWidth()
                                            .heightIn(max = 330.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        "Ekran önizlemesi yaklaşık 1–2 saniyede bir güncellenir. Kumanda ve fare komutları ayrıca gönderilir.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
                                    )
                                } else {
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text(
                                                if (remoteOnline) "Windows ekranı bekleniyor..." else "Bilgisayar çevrimdışı",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                "Önizleme yalnızca bu sayfa açıkken istenir; Windows Agent çalışıyor olmalı.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                            )
                                            if (remoteOnline) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                            if (!remoteOnline) {
                                OutlinedButton(
                                    onClick = { page = Page.DEVICES },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (device == null) "Cihaz listesine dön" else "Cihaz durumunu gör")
                                }
                            }
                            TvRemotePanel(
                                enabled = remoteOnline,
                                mouseEnabled = remoteOnline && !localBridgeSelected,
                                onCommand = { commandName, payload ->
                                    if (!signedIn) {
                                        feedback = "Önce Firebase hesabına giriş yap."
                                    } else if (localBridgeSelected) {
                                        when (commandName) {
                                            "mute" -> sendLocalPcText("sesi kapa")
                                            "volume_up" -> sendLocalPcText("ses 10 arttir")
                                            "volume_down" -> sendLocalPcText("ses 10 azalt")
                                            "home" -> sendLocalPcText("masaustunu goster")
                                            "power" -> feedback = "Güç düğmesi bu yerel köprü sürümünde desteklenmiyor. Bilgisayar kapalıyken açmak için ayrıca Wake-on-LAN gerekir."
                                            "mouse_move", "mouse_click", "mouse_right_click" ->
                                                feedback = "Bu yerel köprü fare hareketini henüz desteklemiyor. Fare kontrolünü Windows Agent aşamasında etkinleştireceğiz."
                                            else -> feedback = "Bu kumanda işlemi bu bağlantı türünde desteklenmiyor."
                                        }
                                    } else if (device == null) {
                                        feedback = "Bu bilgisayar henüz çevrimiçi değil. Windows Agent aynı hesapla bağlandığında otomatik kullanılabilir olacak."
                                    } else if (!remoteOnline) {
                                        feedback = "Bilgisayar çevrimdışı. Windows Agent çevrimiçi olduğunda komut gönderebilirsin."
                                    } else {
                                        sendRemoteCommand(device.id, commandName, payload) { message -> feedback = message }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
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
                                Modifier.fillMaxWidth().clickable { accountListExpanded = !accountListExpanded }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (signedIn) {
                                    if (photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            imageLoader = photoImageLoader,
                                            contentDescription = "Google profil fotoğrafı",
                                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = "Hesap fotoğrafı",
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else {
                                    MynLogo(48.dp)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (signedIn) name.ifBlank { email.substringBefore("@") } else "Giriş yap", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2,
                                        color = if (signedIn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                                    Text(if (signedIn) email.ifBlank { phone } else "Google hesabınla devam et", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), maxLines = 1)
                                }
                                Text(if (accountListExpanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = accountListExpanded) {
                                Column(
                                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f), RoundedCornerShape(16.dp))
                                        .padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (savedGoogleAccounts.isEmpty()) {
                                        Text("Henüz kaydedilmiş Google hesabı yok.", Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                                    } else {
                                        savedGoogleAccounts.forEach { saved ->
                                            Row(
                                                Modifier.fillMaxWidth().clickable {
                                                    accountListExpanded = false
                                                    if (!(saved.email.equals(email, ignoreCase = true) && signedIn)) {
                                                        selectSavedGoogleAccount(saved.email)
                                                    }
                                                }.padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                if (saved.photoUrl.isNotBlank()) {
                                                    AsyncImage(model = saved.photoUrl, imageLoader = photoImageLoader,
                                                        contentDescription = "Kaydedilmiş Google hesabı",
                                                        modifier = Modifier.size(36.dp).clip(CircleShape))
                                                } else {
                                                    Icon(Icons.Default.AccountCircle, contentDescription = null,
                                                        modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                                }
                                                Column(Modifier.weight(1f)) {
                                                    Text(saved.name.ifBlank { saved.email.substringBefore("@") }, fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium, maxLines = 1)
                                                    Text(saved.email, fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f), maxLines = 1)
                                                }
                                                if (saved.email.equals(email, ignoreCase = true) && signedIn) {
                                                    Text("Etkin", fontSize = 11.sp, color = Color(0xFF4CAF50))
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                    TextButton(onClick = { accountListExpanded = false; googleLogin() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Hesap ekle")
                                    }
                                }
                            }
                            googleStatus?.let { status ->
                                Text(
                                    status,
                                    modifier = Modifier.fillMaxWidth().padding(start = 60.dp, bottom = 4.dp),
                                    fontSize = 11.sp,
                                    color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
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
            googleStatus?.let { status -> Text(status, color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
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
private fun MynLogo(size: androidx.compose.ui.unit.Dp = 58.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = "https://raw.githubusercontent.com/kralus24/MynConverter/master/app/src/main/res/drawable/splash_logo.png",
            contentDescription = "MynKralus logosu",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun TvRemotePanel(
    onCommand: (String, Map<String, Any>) -> Unit,
    enabled: Boolean,
    mouseEnabled: Boolean = enabled,
    modifier: Modifier = Modifier
) {
    val buttons = listOf(
        Triple("Aç / Kapat", Icons.Default.PowerSettingsNew, "power"),
        Triple("Sesi kapat", Icons.Default.VolumeOff, "mute"),
        Triple("...", Icons.Default.MoreHoriz, "home"),
        Triple("Sesi artır", Icons.Default.VolumeUp, "volume_up"),
        Triple("Sesi kıs", Icons.Default.VolumeDown, "volume_down"),
        Triple("...", Icons.Default.MoreHoriz, "placeholder")
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PC KUMANDASI", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            buttons.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        val buttonActive = enabled && item.third != "placeholder"
                        Column(
                            Modifier.weight(1f).height(82.dp)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = if (buttonActive) 1f else .72f), RoundedCornerShape(18.dp))
                                .clickable(enabled = buttonActive) { onCommand(item.third, emptyMap()) }
                                .padding(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(item.second, contentDescription = if (item.third == "home") "Ana ekran" else item.first,
                                modifier = Modifier.size(27.dp),
                                tint = if (buttonActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
                            Spacer(Modifier.height(5.dp))
                            Text(item.first, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1)
                        }
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().height(250.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .pointerInput(mouseEnabled) {
                    var deltaX = 0f
                    var deltaY = 0f
                    detectDragGestures(
                        onDragStart = { deltaX = 0f; deltaY = 0f },
                        onDragEnd = {
                            if (mouseEnabled && (deltaX != 0f || deltaY != 0f)) {
                                onCommand("mouse_move", mapOf("dx" to deltaX.roundToInt(), "dy" to deltaY.roundToInt()))
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            deltaX += dragAmount.x * 1.2f
                            deltaY += dragAmount.y * 1.2f
                        }
                    )
                }
                 .pointerInput(mouseEnabled) {
                    detectTapGestures(
                        onTap = { if (mouseEnabled) onCommand("mouse_click", emptyMap()) },
                        onLongPress = { if (mouseEnabled) onCommand("mouse_right_click", emptyMap()) }
                    )
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(30.dp),
                tint = if (mouseEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
            Spacer(Modifier.height(8.dp))
            Text("Dokunmatik alan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                if (mouseEnabled) "Sürükle: fare • Dokun: sol tık • Basılı tut: sağ tık"
                else if (enabled) "Ses ve masaüstü komutları bağlı. Yerel köprü fare hareketini desteklemiyor."
                else "Bilgisayar bağlandığında fare kontrolü etkinleşecek.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (mouseEnabled) "Komutlar Firebase kuyruğuna gönderilir; Windows Agent'ın bunları alıp uygulaması gerekir."
                else "Ses ve masaüstü komutları yerel Windows köprüsüne gönderilir.",
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), textAlign = TextAlign.Center
            )
        }
    }
}
