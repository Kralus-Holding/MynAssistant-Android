package com.mynkralus.mynassistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.Base64
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.provider.MediaStore
import android.provider.ContactsContract
import android.provider.Settings
import java.util.Locale
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Touchpad
import androidx.compose.material.icons.filled.TouchpadOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Send
import coil.compose.AsyncImage
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val Accent = Color(0xFF8B9CFF)
private val Mint = Color(0xFF9DD9D2)
private const val PREFS = "mynassistant_prefs"
private const val GOOGLE_ACCOUNTS_KEY = "saved_google_accounts"
private const val ANDROID_INSTALLATION_ID_KEY = "android_installation_id"
private const val ANDROID_DEVICE_HEARTBEAT_MS = 60_000L
private val COMPUTER_COMMAND_PREFIX = Regex("^(bilgisayar(?:ım|a|da|dan)?|pc|windows)(?:\\s*[:,;-]\\s*|\\s+|$)", RegexOption.IGNORE_CASE)

private data class SavedGoogleAccount(val email: String, val name: String, val photoUrl: String)

private data class PcDevice(
    val id: String,
    val name: String,
    val platform: String,
    val status: String,
    val connectionMode: String = "",
    val screenJpegBase64: String = "",
    val lastSeenAtMillis: Long? = null
) {
    val isPcTarget: Boolean
        get() = DevicePresencePolicy.isPcTarget(id, platform, connectionMode)

    fun isOnlineAt(nowMillis: Long): Boolean =
        DevicePresencePolicy.isOnline(status, lastSeenAtMillis, nowMillis)
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
        code.contains("EMAIL_ALREADY_IN_USE") -> "Bu e-posta adresiyle zaten bir hesap var. Giriş yapmayı deneyebilirsin."
        code.contains("INVALID_EMAIL") -> "Geçerli bir e-posta adresi gir."
        code.contains("WEAK_PASSWORD") -> "Daha güçlü bir şifre seç. En az 6 karakter kullan."
        code.contains("USER_NOT_FOUND") || code.contains("INVALID_CREDENTIAL") || code.contains("WRONG_PASSWORD") ->
            "E-posta adresi veya şifre hatalı."
        code.contains("OPERATION_NOT_ALLOWED") -> "Bu giriş yöntemi şu anda kullanılamıyor. Daha sonra tekrar dene."
        code.contains("TOO_MANY_REQUESTS") -> "Çok fazla deneme yapıldı. Bir süre bekleyip tekrar dene."
        code.contains("NETWORK_REQUEST_FAILED") -> "Bağlantı kurulamadı. İnternet bağlantını kontrol edip tekrar dene."
        code.contains("ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL") ->
            "Bu e-posta başka bir giriş yöntemiyle kayıtlı. Diğer giriş yöntemini dene."
        else -> "Giriş yapılamadı. Bilgilerini kontrol edip tekrar dene."
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
            onComplete("Profil kaydedilemedi.")
        }
}
private data class PcBridgeResult(
    val connected: Boolean,
    val success: Boolean,
    val message: String
)

/**
 * Executes the Android-side command subset locally on this handset.
 * Protected operations such as silently toggling Bluetooth or initiating a call
 * are deliberately routed to Android's own UI instead of bypassing user consent.
 */
private fun launchInstalledApp(context: Context, packageNames: List<String>): Boolean {
    val launchIntent = packageNames.firstNotNullOfOrNull { packageName ->
        context.packageManager.getLaunchIntentForPackage(packageName)
    } ?: return false
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
    return true
}

private fun extractPhoneNumber(rawText: String): String? {
    val candidate = Regex("""\+?\d[\d\s().-]{5,}\d""").find(rawText)?.value ?: return null
    val digits = candidate.filter { it.isDigit() }
    return digits.takeIf { it.length >= 7 }
}


private data class ContactPhoneMatch(val displayName: String, val phoneNumber: String, val score: Int)

private data class PendingContactChoice(
    val command: String,
    val aliasKey: String,
    val aliasLabel: String,
    val candidates: List<ContactPhoneMatch>,
    val saveImmediately: Boolean = false
)

private data class PendingContactConfirmation(
    val command: String,
    val aliasKey: String,
    val aliasLabel: String,
    val contact: ContactPhoneMatch
)

private fun normalizeContactText(value: String): String =
    value.lowercase(Locale.forLanguageTag("tr-TR"))
        .replace('ı', 'i')
        .replace('ş', 's')
        .replace('ğ', 'g')
        .replace('ü', 'u')
        .replace('ö', 'o')
        .replace('ç', 'c')
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")


private fun parseRelationshipAliasAssignment(rawCommand: String): Pair<String, String>? {
    val match = Regex(
        """^\s*(babam(?:ın|in)?|baba|annem(?:in|in)?|anne|abim|abi|ablam|abla|eşim|esim)\s*(?:=|:|\s+(?:adı|adi|ismi))\s*(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    ).find(rawCommand) ?: return null
    val relation = normalizeContactText(match.groupValues[1])
    val contactName = match.groupValues[2].trim()
    if (contactName.length < 2) return null
    val key = when {
        relation.startsWith("babam") || relation == "baba" -> "father"
        relation.startsWith("annem") || relation == "anne" -> "mother"
        relation.startsWith("abim") || relation == "abi" -> "brother"
        relation.startsWith("ablam") || relation == "abla" -> "sister"
        relation.startsWith("esim") -> "spouse"
        else -> return null
    }
    return key to contactName
}

private fun relationshipPreferenceKey(rawCommand: String): String? {
    val tokens = normalizeContactText(rawCommand).split(" ").toSet()
    return when {
        tokens.any { it.startsWith("baba") || it.startsWith("babam") || it in setOf("peder", "dad", "daddy", "father") } -> "father"
        tokens.any { it.startsWith("anne") || it.startsWith("annem") || it in setOf("anam", "mom", "mommy", "mum", "mother") } -> "mother"
        tokens.any { it.startsWith("abi") || it.startsWith("abim") || it.startsWith("kardes") || it == "agabey" } -> "brother"
        tokens.any { it.startsWith("abla") || it.startsWith("kizkardes") } -> "sister"
        tokens.any { it.startsWith("esim") || it.startsWith("karim") || it.startsWith("kocam") || it == "partnerim" } -> "spouse"
        else -> null
    }
}

private fun relationshipAliasesForCommand(rawCommand: String): Set<String>? {
    val tokens = normalizeContactText(rawCommand).split(" ").toSet()
    val fatherWords = setOf("babam", "babami", "babamin", "babamla", "babamdan", "babaya", "baba", "babacigim", "peder", "dad", "daddy", "father")
    val motherWords = setOf("annem", "annemi", "annemin", "anneme", "annemle", "anne", "annecigim", "anam", "mom", "mommy", "mum", "mother")
    val brotherWords = setOf("abim", "abimi", "abime", "abi", "agabey", "kardesim", "kardesimi", "kardese")
    val sisterWords = setOf("ablam", "ablami", "ablama", "abla", "kizkardesim", "kizkardesimi")
    val spouseWords = setOf("esim", "esimi", "esime", "karim", "karimi", "kocam", "kocami", "partnerim")
    return when {
        tokens.any { token -> token in fatherWords || token.startsWith("baba") || token.startsWith("babam") } ->
            setOf("baba", "babam", "babacigim", "peder", "dad", "daddy", "father")
        tokens.any { token -> token in motherWords || token.startsWith("anne") || token.startsWith("annem") } ->
            setOf("anne", "annem", "annecigim", "anam", "mom", "mommy", "mum", "mother")
        tokens.any { token -> token in brotherWords || token.startsWith("abim") || token.startsWith("kardes") } ->
            setOf("abi", "abim", "agabey", "kardesim")
        tokens.any { token -> token in sisterWords || token.startsWith("abla") || token.startsWith("kizkardes") } ->
            setOf("abla", "ablam", "kizkardesim")
        tokens.any { token -> token in spouseWords || token.startsWith("esim") || token.startsWith("karim") || token.startsWith("kocam") } ->
            setOf("esim", "karim", "kocam", "partnerim")
        else -> null
    }
}

private fun normalizeContactSearchToken(rawToken: String): String {
    val token = normalizeContactText(rawToken)
    val suffixes = listOf(
        "lardan", "lerden", "larla", "lerle", "dan", "den", "tan", "ten",
        "nin", "nın", "nun", "nün", "yla", "yle", "yi", "yı", "yu", "yü",
        "in", "ın", "un", "ün", "la", "le", "da", "de", "a", "e", "i", "ı", "u", "ü"
    )
    for (suffix in suffixes) {
        if (token.endsWith(suffix) && token.length - suffix.length >= 4) {
            return token.dropLast(suffix.length)
        }
    }
    return token
}

private fun contactLookupAlias(rawCommand: String, selectedContactName: String? = null): String? {
    val withoutQuoted = rawCommand.replace(Regex("""["“].*?["”]"""), " ")
    val messageIntent = Regex(
        """(?iu)\s+diye\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?(?:\s+(?:tan|ten|dan|den))?\s+)?mesaj\s+(?:at|gönder|gonder|yolla|yaz)\b"""
    ).find(withoutQuoted) ?: Regex(
        """(?iu)\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?\s+)?mesaj(?:ı|i|ini|ını)?\s+(?:at|gönder|gonder|yolla|yaz)\b"""
    ).find(withoutQuoted) ?: Regex(
        """(?iu)\s+(?:at|yaz|gönder|gonder|yolla|ilet)\s*[.!?]*$"""
    ).find(withoutQuoted)
    val phraseBeforeMessage = if (messageIntent != null) {
        withoutQuoted.substring(0, messageIntent.range.first)
    } else withoutQuoted

    if (messageIntent != null && !selectedContactName.isNullOrBlank()) {
        val phraseTokens = phraseBeforeMessage.trim().split(Regex("""\s+"""))
            .filter { it.isNotBlank() }.toMutableList()
        while (phraseTokens.isNotEmpty()) {
            val first = normalizeContactText(phraseTokens.first())
            if (first.startsWith("whatsapp") || first in setOf("tan", "ten", "dan", "den")) {
                phraseTokens.removeAt(0)
            } else break
        }
        val queryTokens = phraseTokens.map(::normalizeContactSearchToken)
        val nameTokens = normalizeContactText(selectedContactName).split(" ").map(::normalizeContactSearchToken)
        val matchedCount = minOf(queryTokens.size, nameTokens.size).downTo(1).firstOrNull { count ->
            (0 until count).all { index ->
                queryTokens[index] == nameTokens[index] ||
                    queryTokens[index].startsWith(nameTokens[index]) ||
                    nameTokens[index].startsWith(queryTokens[index])
            }
        }
        if (matchedCount != null) {
            return nameTokens.take(matchedCount).joinToString(" ").trim().takeIf { it.isNotBlank() }
        }
    }

    var normalized = normalizeContactText(phraseBeforeMessage)
    normalized = normalized.replace(Regex("""^(?:whatsapp(?:tan|ten|dan|den)?\s+)+"""), "")
    val stopWords = setOf(
        "beni", "bana", "icin", "telefonla", "telefon", "ara", "aramak", "arayin", "arayalim", "call",
        "whatsapp", "whatsapptan", "tan", "ten", "dan", "den", "ile", "yoluyla",
        "mesaj", "mesaji", "mesajini", "mesajina", "at", "yaz", "gonder", "gondermeyi",
        "lutfen", "ac", "diye", "yolla", "ilet", "sms"
    )
    val tokens = normalized.split(" ")
        .filter { it.isNotBlank() && it !in stopWords }
        .map(::normalizeContactSearchToken)
        .filter { it.length >= 2 }
    return tokens.joinToString(" ").trim().takeIf { it.isNotBlank() }
}

private fun contactAliasSpec(
    rawCommand: String,
    selectedContactName: String? = null
): Pair<String, String>? {
    val relationKey = relationshipPreferenceKey(rawCommand)
    if (relationKey != null) {
        val label = when (relationKey) {
            "father" -> "babam"
            "mother" -> "annem"
            "brother" -> "abim"
            "sister" -> "ablam"
            else -> "eşim"
        }
        return "contact_alias_$relationKey" to label
    }
    val alias = contactLookupAlias(rawCommand, selectedContactName) ?: return null
    val safeAlias = normalizeContactText(alias)
        .split(" ")
        .map(::normalizeContactSearchToken)
        .filter { it.isNotBlank() }
        .joinToString("_")
    if (safeAlias.isBlank()) return null
    return "contact_alias_name_$safeAlias" to alias
}

private fun findContactPhonesForCommand(
    context: Context,
    rawCommand: String,
    resolveSavedAlias: Boolean = true
): List<ContactPhoneMatch> {
    if (androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return emptyList()

    val withoutQuotedMessage = rawCommand.replace(Regex("""["“].*?["”]"""), " ")
    val normalizedCommand = normalizeContactText(withoutQuotedMessage)
    val relationAliases = relationshipAliasesForCommand(withoutQuotedMessage)
    val relationKey = relationshipPreferenceKey(withoutQuotedMessage)
    val naturalMessageCommand = contactActionKind(rawCommand) == "WHATSAPP_MESSAGE" || Regex(
        """(?iu)\s+diye\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?(?:\s+(?:tan|ten|dan|den))?\s+)?mesaj\s+(?:at|gönder|gonder|yolla|yaz)\b"""
    ).containsMatchIn(rawCommand)
    val aliasSpec = if (resolveSavedAlias && !naturalMessageCommand) contactAliasSpec(rawCommand) else null
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val savedNumber = aliasSpec?.first?.let { prefs.getString(it + "_number", null) }.orEmpty()
    val savedDisplayName = aliasSpec?.first?.let { prefs.getString(it, null) }.orEmpty()
    if (savedNumber.isNotBlank()) {
        return listOf(ContactPhoneMatch(savedDisplayName.ifBlank { aliasSpec?.second.orEmpty() }, savedNumber, 200))
    }
    val savedRelationshipName = relationKey?.let {
        prefs.getString("contact_alias_$it", null)
    }?.let { normalizeContactText(it) }.orEmpty()
    val stopWords = setOf(
        "beni", "bana", "icin", "telefonla", "telefon", "ara", "aramak", "arayin", "arayalim", "call",
        "whatsapp", "whatsapptan", "tan", "ten", "dan", "den", "ile", "yoluyla",
        "mesaj", "mesaji", "mesajini", "mesajina", "yaz", "gonder", "gönder", "gondermeyi",
        "lutfen", "a", "ac", "diye", "at", "yolla", "ilet", "sms"
    )
    val alias = contactLookupAlias(rawCommand)
    val queryTokens = (alias?.split(" ") ?: normalizedCommand.split(" "))
        .map(::normalizeContactSearchToken)
        .filter { it.isNotBlank() && it !in stopWords && it !in setOf("i", "yi", "yı", "yu", "yü", "ı", "u", "ü") }
    val queryText = queryTokens.joinToString(" ")

    if (resolveSavedAlias && naturalMessageCommand && queryTokens.isNotEmpty()) {
        val savedAliases = prefs.all.keys
            .filter { it.startsWith("contact_alias_name_") && !it.endsWith("_number") }
            .mapNotNull { key ->
                val number = prefs.getString(key + "_number", null).orEmpty()
                val displayName = prefs.getString(key, null).orEmpty()
                if (number.isBlank() || displayName.isBlank()) return@mapNotNull null
                val keyTokens = key.removePrefix("contact_alias_name_").split("_").filter { it.isNotBlank() }
                if (keyTokens.isEmpty() || keyTokens.size > queryTokens.size) return@mapNotNull null
                if (!keyTokens.indices.all { index -> queryTokens[index] == keyTokens[index] }) return@mapNotNull null
                Triple(keyTokens.size, displayName, number)
            }
            .maxByOrNull { it.first }
        if (savedAliases != null) {
            return listOf(ContactPhoneMatch(savedAliases.second, savedAliases.third, 200))
        }
    }
    if (relationAliases == null && queryText.isBlank()) return emptyList()

    val contacts = mutableListOf<ContactPhoneMatch>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        if (nameIndex >= 0 && numberIndex >= 0) {
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)?.trim().orEmpty()
                val number = cursor.getString(numberIndex)?.trim().orEmpty()
                if (displayName.isBlank() || number.isBlank()) continue
                val normalizedName = normalizeContactText(displayName)
                val nameWords = normalizedName.split(" ").map(::normalizeContactSearchToken)
                val prefixMatchCount = minOf(nameWords.size, queryTokens.size)
                    .let { count ->
                        (0 until count).takeWhile { index ->
                            val query = queryTokens[index]
                            val name = nameWords[index]
                            query == name || query.startsWith(name) || name.startsWith(query)
                        }.size
                    }
                val score = if (relationAliases != null) {
                    when {
                        savedRelationshipName.isNotBlank() && normalizedName == savedRelationshipName -> 150
                        savedRelationshipName.isNotBlank() && normalizedName.contains(savedRelationshipName) -> 135
                        normalizedName in relationAliases -> 120
                        nameWords.any { it in relationAliases } -> 100
                        relationAliases.any { normalizedName.startsWith("$it ") } -> 90
                        relationAliases.any { normalizedName.contains(it) } -> 70
                        else -> 0
                    }
                } else {
                    when {
                        normalizedName == queryText -> 130
                        naturalMessageCommand && prefixMatchCount > 0 -> 100 + prefixMatchCount * 5
                        queryTokens.isNotEmpty() && queryTokens.all { query ->
                            nameWords.any { it == query || (query.length >= 3 && it.startsWith(query)) }
                        } -> 100
                        queryText.isNotBlank() && normalizedName.contains(queryText) -> 90
                        queryTokens.size == 1 && queryTokens.first() in nameWords -> 80
                        else -> 0
                    }
                }
                if (score > 0) contacts += ContactPhoneMatch(displayName, number, score)
            }
        }
    }

    val unique = contacts.distinctBy {
        normalizeContactText(it.displayName) + "|" + it.phoneNumber.filter { c -> c.isDigit() }
    }.sortedWith(
        compareByDescending<ContactPhoneMatch> { it.score }
            .thenBy { normalizeContactText(it.displayName) }
            .thenBy { it.phoneNumber }
    )
    if (savedNumber.isBlank() && savedRelationshipName.isNotBlank()) {
        val mapped = unique.filter { normalizeContactText(it.displayName) == savedRelationshipName }
        if (mapped.isNotEmpty()) return mapped
    }
    return unique.take(50)
}

private fun requiredRuntimePermissionList(): List<String> = buildList {
    add(android.Manifest.permission.READ_CONTACTS)
    add(android.Manifest.permission.CALL_PHONE)
    add(android.Manifest.permission.CAMERA)
    add(android.Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) {
        add(android.Manifest.permission.POST_NOTIFICATIONS)
        add(android.Manifest.permission.READ_MEDIA_IMAGES)
        add(android.Manifest.permission.READ_MEDIA_VIDEO)
        add(android.Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= 28) add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= 34) add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
}

private fun contactActionKind(rawText: String): String? {
    val normalized = normalizeContactText(rawText)
    val words = normalized.split(" ").filter { it.isNotBlank() }
    val hasCallVerb = words.any {
        it in setOf("ara", "aramak", "arayin", "arayalim", "call") ||
            it.startsWith("aray") || it.startsWith("ara")
    }
    val messageNouns = setOf("mesaj", "mesaji", "mesajini", "mesajina", "message", "text")
    val messageVerbs = setOf("at", "yaz", "gonder", "yolla", "ilet")
    val hasMessageNoun = words.any { it in messageNouns }
    val hasMessageVerb = words.any {
        it in messageVerbs || it.startsWith("at") || it.startsWith("yaz") ||
            it.startsWith("gonder") || it.startsWith("yolla") || it.startsWith("ilet")
    }
    val containsWhatsApp = normalized.contains("whatsapp")
    val containsOtherDeliveryTarget = listOf("sms", "gmail", "mail", "e posta", "e-posta", "email")
        .any { normalized.contains(it) }

    if (containsWhatsApp && hasCallVerb) return "WHATSAPP_CALL"
    if (containsWhatsApp && (hasMessageNoun || hasMessageVerb)) return "WHATSAPP_MESSAGE"
    if (!containsOtherDeliveryTarget && hasMessageNoun && hasMessageVerb) return "WHATSAPP_MESSAGE"
    if (!containsWhatsApp && !containsOtherDeliveryTarget && hasCallVerb) return "DIAL"
    return null
}

private fun extractWhatsAppMessage(rawText: String, recipientName: String? = null): String? {
    val quoted = Regex("""["“](.+?)["”]""").find(rawText)?.groupValues?.getOrNull(1)?.trim()
    if (!quoted.isNullOrBlank()) return quoted

    val withDiye = Regex(
        """(?iu)\s+diye\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?(?:\s+(?:tan|ten|dan|den))?\s+)?mesaj\s+(?:at|gönder|gonder|yolla|yaz)\b"""
    ).find(rawText)
    val messageEnding = Regex(
        """(?iu)\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?\s+)?mesaj(?:ı|i|ini|ını)?\s+(?:at|gönder|gonder|yolla|yaz)\b"""
    ).find(rawText)
    val actionEnding = Regex("""(?iu)\s+(?:at|yaz|gönder|gonder|yolla|ilet)\s*[.!?]*$""").find(rawText)
    val marker = withDiye ?: messageEnding ?: actionEnding ?: return null
    val phrase = rawText.substring(0, marker.range.first).trim()
    val trailingText = if (withDiye == null && messageEnding != null) {
        rawText.substring(marker.range.last + 1).trim().trim(':', '-', ' ')
    } else ""
    if (trailingText.isNotBlank()) return trailingText

    val recipient = recipientName?.takeIf { it.isNotBlank() } ?: run {
        if (relationshipPreferenceKey(phrase) == null) return null
        normalizeContactText(phrase).split(" ").firstOrNull() ?: return null
    }
    val phraseTokens = phrase.split(Regex("""\s+""")).filter { it.isNotBlank() }.toMutableList()
    while (phraseTokens.isNotEmpty()) {
        val first = normalizeContactText(phraseTokens.first())
        if (first.startsWith("whatsapp") || first in setOf("tan", "ten", "dan", "den")) {
            phraseTokens.removeAt(0)
        } else break
    }
    val queryTokens = phraseTokens.map(::normalizeContactSearchToken)
    val recipientTokens = normalizeContactText(recipient).split(" ").map(::normalizeContactSearchToken)
    if (queryTokens.isEmpty() || recipientTokens.isEmpty()) return null
    val matchedCount = minOf(queryTokens.size, recipientTokens.size).downTo(1).firstOrNull { count ->
        (0 until count).all { index ->
            queryTokens[index] == recipientTokens[index] ||
                queryTokens[index].startsWith(recipientTokens[index]) ||
                recipientTokens[index].startsWith(queryTokens[index])
        }
    } ?: return null
    val message = phraseTokens.drop(matchedCount).joinToString(" ")
        .trim().trim(',', ':', '-', ' ', '\'', '"', '“', '”')
    return message.takeIf { it.isNotBlank() }
}

private fun isMynAssistantAccessibilityEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val expected = ComponentName(context, MynAssistantAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun normalizeWhatsAppNumber(rawNumber: String): String {
    val digits = rawNumber.filter { it.isDigit() }
    return when {
        digits.length == 11 && digits.startsWith("0") -> "90" + digits.drop(1)
        digits.length == 10 && Locale.getDefault().country.equals("TR", ignoreCase = true) -> "90$digits"
        else -> digits
    }
}

private fun openWhatsAppChat(
    context: Context,
    rawNumber: String,
    autoAction: String? = null,
    message: String? = null
): String {
    val number = normalizeWhatsAppNumber(rawNumber)
    if (number.length < 7) return "WhatsApp için geçerli bir telefon numarası bulunamadı."
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (autoAction != null && !isMynAssistantAccessibilityEnabled(context)) {
        prefs.edit()
            .putString("pending_whatsapp_action", autoAction)
            .putLong("pending_whatsapp_action_at", System.currentTimeMillis())
            .putString("pending_whatsapp_phone", number)
            .putString("pending_whatsapp_message", message.orEmpty())
            .putBoolean("pending_whatsapp_message_ready", false)
            .putBoolean("pending_whatsapp_waiting_accessibility", true)
            .putString("whatsapp_auto_call_status", "waiting")
            .apply()
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "WhatsApp işlemi beklemede. MynAssistant hizmetini Erişilebilirlik ayarlarında açınca WhatsApp otomatik açılacak."
    }
    if (autoAction != null) {
        prefs.edit()
            .putString("pending_whatsapp_action", autoAction)
            .putLong("pending_whatsapp_action_at", System.currentTimeMillis())
            .putString("pending_whatsapp_phone", number)
            .putString("pending_whatsapp_message", message.orEmpty())
            .putBoolean("pending_whatsapp_message_ready", false)
            .putBoolean("pending_whatsapp_waiting_accessibility", false)
            .putString("whatsapp_auto_call_status", "waiting")
            .apply()
    } else {
        prefs.edit()
            .remove("pending_whatsapp_action")
            .remove("pending_whatsapp_action_at")
            .remove("pending_whatsapp_message")
            .remove("pending_whatsapp_message_ready")
            .remove("pending_whatsapp_phone")
            .remove("pending_whatsapp_waiting_accessibility")
            .apply()
    }
    val url = Uri.parse("https://wa.me/$number")
    val appIntent = Intent(Intent.ACTION_VIEW, url).setPackage("com.whatsapp")
    val intent = if (appIntent.resolveActivity(context.packageManager) != null) appIntent else Intent(Intent.ACTION_VIEW, url)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return when (autoAction) {
        "CALL" -> "WhatsApp sohbeti açıldı; MynAssistant sesli arama düğmesine otomatik basmayı deneyecek."
        "MESSAGE" -> "WhatsApp sohbeti açıldı; mesaj otomatik gönderilmeye hazırlanıyor."
        else -> "WhatsApp sohbeti açıldı."
    }
}

/**
 * Runs supported phone commands locally and hands app-specific actions to Android apps.
 * Calls and WhatsApp conversations still require the user to confirm the final action.
 */
private fun executeAndroidTextCommand(context: Context, rawText: String): String {
    val command = rawText.trim()
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace(Regex("\\s+"), " ")
    if (command.isBlank()) return "Önce bir telefon komutu yaz."

    return try {
        val contactAction = contactActionKind(rawText)
        val number = extractPhoneNumber(rawText)
        if (contactAction == "WHATSAPP_CALL" && number != null) {
            return openWhatsAppChat(context, number, autoAction = "CALL")
        }
        if (contactAction == "WHATSAPP_MESSAGE" && number != null) {
            val message = extractWhatsAppMessage(rawText)
                ?: return "Gönderilecek mesajı tırnak içinde komuta ekle. Örn. WhatsApp'tan \"Eve geç kalacağım\" mesajını gönder."
            return openWhatsAppChat(context, number, autoAction = "MESSAGE", message = message)
        }
        if (contactAction == "DIAL" && number != null) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CALL_PHONE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return "Telefon araması için Arama iznini vermen gerekiyor."
            context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}")))
            return "Arama başlatılıyor."
        }

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when {
            command in setOf(
                "ses artır", "sesi artır", "sesini artır", "ses yükselt", "sesi yükselt", "sesini yükselt",
                "ses arttır", "sesi arttır", "sesini arttır", "volume up"
            ) -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Telefonun medya sesi artırıldı."
            }
            command in setOf(
                "ses azalt", "sesi azalt", "sesini azalt", "ses kıs", "sesi kıs", "sesini kıs",
                "volume down"
            ) -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Telefonun medya sesi azaltıldı."
            }
            command in setOf("sessize al", "sesi kapat", "sesini kapat", "medya sesini kapat", "medya sesini sessize al") -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "Telefonun medya sesi kapatıldı."
            }
            command in setOf("medya sesini aç", "sessizden çıkar") -> {
                audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                "Telefonun medya sesi açıldı."
            }
            command in setOf("bluetooth ayarlarını aç", "bluetooth aç", "bluetooth'u aç", "bluetoothu aç") -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "Bluetooth kontrol paneli açıldı. Durumu Android panelinden değiştirebilirsin."
            }
            command in setOf("wifi ayarlarını aç", "wi-fi ayarlarını aç", "wifi aç", "wi-fi aç", "kablosuz ağ ayarlarını aç") -> {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
                context.startActivity(intent)
                "Wi-Fi kontrol paneli açıldı. Bağlantı durumunu Android panelinden değiştirebilirsin."
            }
            command in setOf("ayarları aç", "telefon ayarlarını aç", "android ayarlarını aç") -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                "Telefon ayarları açıldı."
            }
            command in setOf("kamera aç", "kamerayı aç", "fotoğraf çek", "kamera uygulamasını aç") -> {
                val launched = launchInstalledApp(
                    context,
                    listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera2")
                )
                if (launched) {
                    "Kamera uygulaması açıldı."
                } else {
                    val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    when {
                        cameraIntent.resolveActivity(context.packageManager) != null -> {
                            context.startActivity(cameraIntent)
                            "Kamera uygulaması açıldı."
                        }
                        fallbackIntent.resolveActivity(context.packageManager) != null -> {
                            context.startActivity(fallbackIntent)
                            "Fotoğraf çekmek için kamera uygulaması açıldı."
                        }
                        else -> "Bu telefonda kullanılabilir bir kamera uygulaması bulunamadı."
                    }
                }
            }
            command in setOf("video kamera aç", "video çek", "kamerayı videoya al", "video kamerasını aç") -> {
                val videoIntent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
                if (videoIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(videoIntent)
                    "Video kamera uygulaması açıldı."
                } else {
                    "Bu telefonda kullanılabilir bir video kamera uygulaması bulunamadı."
                }
            }
            command in setOf("arama ekranını aç", "telefon uygulamasını aç", "arama aç", "telefonu aç") -> {
                context.startActivity(Intent(Intent.ACTION_DIAL))
                "Telefon arama ekranı açıldı. Aramayı sen onaylayabilirsin."
            }
            command in setOf("chrome aç", "tarayıcı aç", "internet aç", "google aç") -> {
                if (!launchInstalledApp(context, listOf("com.android.chrome"))) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
                }
                "Tarayıcı açıldı."
            }
            command in setOf("youtube aç", "youtube'u aç", "youtubeu aç") -> {
                if (!launchInstalledApp(context, listOf("com.google.android.youtube"))) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                }
                "YouTube açıldı."
            }
            command in setOf("whatsapp aç", "whatsapp'ı aç", "whatsappı aç") -> {
                if (launchInstalledApp(context, listOf("com.whatsapp", "com.whatsapp.w4b"))) {
                    "WhatsApp uygulaması açıldı."
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.whatsapp.com/android")))
                    "WhatsApp indirme sayfası açıldı."
                }
            }
            command in setOf("instagram aç", "instagramı aç") -> {
                if (launchInstalledApp(context, listOf("com.instagram.android"))) "Instagram açıldı."
                else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com")))
                    "Instagram web sayfası açıldı."
                }
            }
            command in setOf("telegram aç", "telegramı aç") -> {
                if (launchInstalledApp(context, listOf("org.telegram.messenger"))) "Telegram açıldı."
                else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://telegram.org")))
                    "Telegram web sayfası açıldı."
                }
            }
            command in setOf("gmail aç", "gmail'i aç", "gmaili aç") -> {
                if (launchInstalledApp(context, listOf("com.google.android.gm"))) "Gmail açıldı."
                else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")))
                    "Gmail web sayfası açıldı."
                }
            }
            command in setOf("galeri aç", "fotoğrafları aç", "fotoğraflarımı aç") -> {
                if (launchInstalledApp(context, listOf("com.miui.gallery", "com.google.android.apps.photos", "com.sec.android.gallery3d"))) {
                    "Galeri uygulaması açıldı."
                } else {
                    "Bu telefonda tanınan bir galeri uygulaması bulunamadı."
                }
            }
            command in setOf("haritalar aç", "google haritalar aç") -> {
                if (launchInstalledApp(context, listOf("com.google.android.apps.maps"))) "Haritalar açıldı."
                else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")))
                    "Haritalar açıldı."
                }
            }
            command in setOf("play store aç", "play store'u aç", "play storeu aç") -> {
                if (launchInstalledApp(context, listOf("com.android.vending"))) "Play Store açıldı."
                else "Bu telefonda Play Store bulunamadı."
            }
            else -> "Bu telefon komutu henüz desteklenmiyor. Kamera, video kamera, arama ekranı, ses, Bluetooth/Wi-Fi ayarları, WhatsApp, Instagram, Telegram, Gmail, galeri, tarayıcı, Haritalar ve YouTube komutlarını deneyebilirsin."
        }
    } catch (_: Exception) {
        "Telefon komutu açılamadı. Gerekli uygulamanın veya Android ayarının bu cihazda kullanılabildiğini kontrol et."
    }
}

private fun callMynAiBridge(baseUrl: String, token: String, command: String? = null): PcBridgeResult {
    var base = baseUrl.trim().trimEnd('/')
    if (base.endsWith("/health")) base = base.removeSuffix("/health")
    if (base.endsWith("/command")) base = base.removeSuffix("/command")
    base = base.trimEnd('/')
    if (base.isBlank() || !(base.startsWith("http://") || base.startsWith("https://"))) {
        return PcBridgeResult(false, false, "Bilgisayar bağlantı adresini kontrol et.")
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
            return PcBridgeResult(false, false, "Bilgisayara ulaşılamadı. Bağlantı ayarlarını kontrol edip tekrar dene.")
        }

        val response = try { JSONObject(bodyText) } catch (_: Exception) {
            return PcBridgeResult(true, false, "Bilgisayardan beklenen yanıt alınamadı. Yardımcı uygulamayı yeniden başlatıp tekrar dene.")
        }
        if (command == null) {
            val ok = response.optBoolean("ok", true)
            val message = response.optString("message").takeIf { it.isNotBlank() }
                ?: if (ok) "Bilgisayar bağlantısı hazır." else "Bilgisayar bağlantısı hazır değil."
            PcBridgeResult(ok, ok, message)
        } else {
            val result = response.optJSONObject("result")
            val ok = response.optBoolean("ok", result?.optBoolean("ok", false) ?: false)
            val message = result?.optString("message").orEmpty()
            val actionText = result?.optJSONObject("data")?.optString("action_text").orEmpty()
            val displayText = when {
                message.isBlank() -> actionText.ifBlank { "Komut yanıtlandı." }
                actionText.isBlank() || actionText == message -> message
                else -> message + "\n" + actionText
            }
            PcBridgeResult(true, ok, displayText)
        }
    } catch (error: Exception) {
        PcBridgeResult(
            false, false,
            "Bilgisayara ulaşılamadı. Telefon ve bilgisayarın aynı Wi-Fi ağına bağlı olduğunu ve bilgisayar uygulamasının açık olduğunu kontrol et."
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
    val deviceRef = FirebaseFirestore.getInstance()
        .collection("devices")
        .document("windows-" + user.uid)
    deviceRef.get()
        .addOnSuccessListener { snapshot ->
            val existingName = snapshot.getString("name")?.trim().orEmpty()
            data["name"] = existingName.ifBlank { "Windows bilgisayarım" }
            deviceRef.set(data, SetOptions.merge())
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { onComplete("Bilgisayar bağlantısı kaydedilemedi.") }
        }
        .addOnFailureListener { onComplete("Bilgisayar bağlantısı kaydedilemedi.") }
}

private const val WEB_CLIENT_ID = "994290744643-em1j9c951okq1flhq9n00jcuduqpvjls.apps.googleusercontent.com"
private enum class Page { HOME, LOGIN, REGISTER, DEVICES, SETTINGS, REMOTE, FILES, DIAGNOSTICS }

/**
 * Returns true only when Android accepted the start request. Listening becomes active
 * only after MynWakeWordService reports the LISTENING phase through shared preferences.
 */
private fun requestMynWakeWordServiceStart(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("wake_word_enabled", true)
        .putString("wake_word_phase", MynWakeWordPhase.STARTING.persistedValue)
        .putString("wake_word_status", "Servis başlatma isteği gönderildi…")
        .putLong("wake_word_status_updated_at", System.currentTimeMillis())
        .remove("wake_word_error_code")
        .remove("wake_word_error_details")
        .remove("wake_word_error_at")
        .putInt("wake_word_retry_count", 0)
        .apply()

    try {
        androidx.core.content.ContextCompat.startForegroundService(
            context, Intent(context, MynWakeWordService::class.java)
        )
    } catch (error: Exception) {
        val code = "MYN-WAKE-8009"
        val detail = error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
        prefs.edit()
            .putBoolean("wake_word_enabled", false)
            .putString("wake_word_phase", MynWakeWordPhase.ERROR.persistedValue)
            .putString("wake_word_status", "Hata $code · Ön plan mikrofon servisi başlatılamadı. MynAssistant'ı açıp tekrar dene.")
            .putString("wake_word_error_code", code)
            .putString("wake_word_error_details", detail.take(1200))
            .putLong("wake_word_error_at", System.currentTimeMillis())
            .apply()
        throw error
    }
}

private fun stopMynWakeWordService(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("wake_word_enabled", false)
        .putString("wake_word_phase", MynWakeWordPhase.OFF.persistedValue)
        .putString("wake_word_status", "Kapalı")
        .putLong("wake_word_status_updated_at", System.currentTimeMillis())
        .remove("wake_word_heartbeat_at")
        .apply()
    context.stopService(Intent(context, MynWakeWordService::class.java))
}

private fun persistWakeWordPermissionState(
    context: Context,
    phase: MynWakeWordPhase,
    status: String,
    errorCode: String? = null,
    details: String = ""
) {
    val now = System.currentTimeMillis()
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val previousPhase = prefs.getString("wake_word_phase", MynWakeWordPhase.OFF.persistedValue)
        ?: MynWakeWordPhase.OFF.persistedValue
    val editor = prefs.edit()
        .putBoolean("wake_word_enabled", false)
        .putString("wake_word_phase", phase.persistedValue)
        .putString("wake_word_status", status)
        .putLong("wake_word_status_updated_at", now)

    if (phase == MynWakeWordPhase.ERROR && !errorCode.isNullOrBlank()) {
        editor.putString(
            "wake_word_last_phase",
            previousPhase.takeUnless { it == MynWakeWordPhase.ERROR.persistedValue }
                ?: prefs.getString("wake_word_last_phase", "UNKNOWN")
        )
        editor.putString("wake_word_error_code", errorCode)
            .putString("wake_word_error_details", details.take(1200))
            .putLong("wake_word_error_at", now)
    } else {
        editor.remove("wake_word_error_code")
            .remove("wake_word_error_details")
            .remove("wake_word_error_at")
    }
    editor.apply()

    if (phase == MynWakeWordPhase.ERROR && !errorCode.isNullOrBlank()) {
        MynAssistantDiagnostics.record(
            context, errorCode, "Hey Meyene", "", "USER_MESSAGE=$status; DETAILS=$details"
        )
    }
}

/**
 * A service process can be killed without a reliable onDestroy callback. Reconcile the
 * persisted status when the UI returns so old "Listening" state is not shown as live.
 */
private fun reconcileStaleWakeWordState(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean("wake_word_enabled", false)) return

    val phase = MynWakeWordPhase.fromPersisted(prefs.getString("wake_word_phase", null))
    val now = System.currentTimeMillis()
    val heartbeatAt = prefs.getLong("wake_word_heartbeat_at", 0L)
    val statusUpdatedAt = prefs.getLong("wake_word_status_updated_at", 0L)
    if (!MynWakeWordLivenessPolicy.isStale(
            enabled = true,
            phase = phase,
            lastHeartbeatAt = heartbeatAt,
            lastStatusUpdatedAt = statusUpdatedAt,
            now = now
        )
    ) return

    val listeningWasExpected = phase == MynWakeWordPhase.LISTENING
    val code = if (listeningWasExpected) "MYN-WAKE-8030" else "MYN-WAKE-8031"
    val userMessage = if (listeningWasExpected) {
        "Dinleme servisi canlılık sinyalini kaybetti. Sesli asistan durmuş olabilir; dinlemeyi kapatıp tekrar etkinleştir."
    } else {
        "Ses sistemi hazırlama aşamasında uzun süre yanıt vermedi. Hata ayrıntılarını kontrol edip tekrar dene."
    }
    val details = "Persisted phase=" + phase.persistedValue + "; heartbeatAt=" + heartbeatAt +
        "; statusUpdatedAt=" + statusUpdatedAt + "; detectedAt=" + now

    prefs.edit()
        .putBoolean("wake_word_enabled", false)
        .putString("wake_word_last_phase", phase.persistedValue)
        .putString("wake_word_phase", MynWakeWordPhase.ERROR.persistedValue)
        .putString("wake_word_status", "Hata " + code + " · " + userMessage)
        .putString("wake_word_error_code", code)
        .putString("wake_word_error_details", details)
        .putLong("wake_word_error_at", now)
        .putLong("wake_word_status_updated_at", now)
        .apply()

    android.util.Log.w("MynWakeWordService", code + ": " + details)
    MynAssistantDiagnostics.record(context, code, "Hey Meyene", "", userMessage + "; " + details)
}

class MainActivity : ComponentActivity() {
    private val deviceHeartbeatHandler = Handler(Looper.getMainLooper())
    private var activityResumed = false
    private var deviceHeartbeatRunnable: Runnable? = null

    private val firebaseAuthStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (activityResumed) startAndroidDeviceHeartbeat(auth.currentUser)
        else stopAndroidDeviceHeartbeat()
    }

    private val incomingWakeCommand = mutableStateOf<String?>(null)
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        incomingWakeCommand.value = intent.getStringExtra(EXTRA_MYN_WAKE_COMMAND)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        reconcileStaleWakeWordState(this)
        // Remove old local-only demo account data. Firebase now owns auth sessions.
        prefs.edit().remove("signed_in").remove("demo_password").remove("auth_provider")
            .remove("name").remove("email").remove("phone").apply()
        val auth = FirebaseAuth.getInstance()
        auth.addAuthStateListener(firebaseAuthStateListener)
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
                        googleStatus = "Google ile giriş şu anda tamamlanamıyor. Lütfen tekrar dene."
                    } else {
                        val credential = GoogleAuthProvider.getCredential(idToken, null)
                        auth.signInWithCredential(credential).addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                googleStatus = firebaseAuthErrorMessage(task.exception)
                            } else {
                                val user = task.result?.user ?: auth.currentUser
                                if (user == null) {
                                    googleStatus = "Giriş tamamlandı ancak hesap bilgileri alınamadı. Lütfen tekrar dene."
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
                                            "Giriş yapıldı ancak hesap bilgileri kaydedilemedi. Lütfen tekrar dene."
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (error: ApiException) {
                    googleStatus = when (error.statusCode) {
                        10 -> "Google ile giriş şu anda kullanılamıyor. Lütfen daha sonra tekrar dene."
                        12501 -> "Giriş iptal edildi."
                        else -> "Google ile giriş tamamlanamadı. Lütfen tekrar dene."
                    }
                } catch (error: Exception) {
                    googleStatus = "Bir sorun oluştu. Lütfen tekrar dene."
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
                                            message = "Bağlantı kontrol edildi ancak bilgisayar bilgileri kaydedilemedi."
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
                            report("Devam etmek için önce giriş yap.", null)
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
                                .addOnSuccessListener { document ->
                                    report(
                                        "Komut bilgisayara iletildi; işlem sonucu bekleniyor.",
                                        document.id
                                    )
                                }
                                .addOnFailureListener {
                                    report("Komut gönderilemedi. İnternet bağlantını kontrol edip tekrar dene.", null)
                                }
                        }
                    },
                    photoImageLoader = profileImageLoader,
                    login = { identity, password, report ->
                        val cleanEmail = identity.trim()
                        if (!cleanEmail.contains("@")) {
                            val message = "E-posta adresinle giriş yapmak için e-posta adresini gir."
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
                                                    "Giriş yapıldı."
                                                } else {
                                                    "Giriş yapıldı ancak hesap bilgileri kaydedilemedi. Lütfen tekrar dene."
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
                                            val message = "Hesap oluşturulamadı. Lütfen tekrar dene."
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
                                                            " Hesap bilgileri kaydedilemedi. Lütfen tekrar dene."
                                                        }
                                                        val message = "Hesabın oluşturuldu." + verificationMessage + profileMessage
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
                    wakeCommandFromOverlay = incomingWakeCommand.value,
                    onWakeCommandConsumed = { incomingWakeCommand.value = null }
                )
            }
        }
    }


    override fun onPause() {
        activityResumed = false
        stopAndroidDeviceHeartbeat()
        FirebaseAuth.getInstance().currentUser?.let { updateAndroidDevicePresence(it, online = false) }
        super.onPause()
    }

    override fun onDestroy() {
        stopAndroidDeviceHeartbeat()
        FirebaseAuth.getInstance().removeAuthStateListener(firebaseAuthStateListener)
        super.onDestroy()
    }

    private fun stopAndroidDeviceHeartbeat() {
        deviceHeartbeatRunnable?.let(deviceHeartbeatHandler::removeCallbacks)
        deviceHeartbeatRunnable = null
    }

    private fun startAndroidDeviceHeartbeat(user: FirebaseUser?) {
        stopAndroidDeviceHeartbeat()
        if (!activityResumed || user == null) return

        val expectedUid = user.uid
        val heartbeat = object : Runnable {
            override fun run() {
                val activeUser = FirebaseAuth.getInstance().currentUser
                if (!activityResumed || activeUser?.uid != expectedUid) {
                    deviceHeartbeatRunnable = null
                    return
                }
                updateAndroidDevicePresence(activeUser, online = true)
                deviceHeartbeatHandler.postDelayed(this, ANDROID_DEVICE_HEARTBEAT_MS)
            }
        }
        deviceHeartbeatRunnable = heartbeat
        deviceHeartbeatHandler.post(heartbeat)
    }

    private fun androidDeviceDocumentId(uid: String): String {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val installationId = prefs.getString(ANDROID_INSTALLATION_ID_KEY, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also {
                prefs.edit().putString(ANDROID_INSTALLATION_ID_KEY, it).apply()
            }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$uid:$installationId".toByteArray(Charsets.UTF_8))
        val suffix = digest.take(12).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return "android-$suffix"
    }

    private fun updateAndroidDevicePresence(user: FirebaseUser, online: Boolean) {
        val documentId = androidDeviceDocumentId(user.uid)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val registeredKey = "android_device_registered_$documentId"
        val model = Build.MODEL?.trim().orEmpty().ifBlank { "Android telefon" }
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val deviceName = if (manufacturer.isNotBlank() &&
            !model.startsWith(manufacturer, ignoreCase = true)) "$manufacturer $model" else model
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")

        val fields = hashMapOf<String, Any>(
            "ownerId" to user.uid,
            "name" to deviceName,
            "deviceName" to deviceName,
            "platform" to "Android",
            "type" to "Android",
            "status" to if (online) "online" else "offline",
            "connectionMode" to "android-app",
            "agentVersion" to versionName,
            "lastCheckedAt" to FieldValue.serverTimestamp()
        )
        if (online) fields["lastSeenAt"] = FieldValue.serverTimestamp()
        if (!prefs.getBoolean(registeredKey, false)) {
            fields["createdAt"] = FieldValue.serverTimestamp()
        }

        FirebaseFirestore.getInstance()
            .collection("devices")
            .document(documentId)
            .set(fields, SetOptions.merge())
            .addOnSuccessListener {
                if (!prefs.getBoolean(registeredKey, false)) {
                    prefs.edit().putBoolean(registeredKey, true).apply()
                }
            }
            .addOnFailureListener {
                Log.w("MynAssistantDevice", "Device presence update failed")
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingWakeCommand.value = intent.getStringExtra(EXTRA_MYN_WAKE_COMMAND)
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        startAndroidDeviceHeartbeat(FirebaseAuth.getInstance().currentUser)
        reconcileStaleWakeWordState(this)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("pending_whatsapp_waiting_accessibility", false)) return
        val requestedAt = prefs.getLong("pending_whatsapp_action_at", 0L)
        if (requestedAt <= 0L || System.currentTimeMillis() - requestedAt > 120_000L) {
            prefs.edit()
                .remove("pending_whatsapp_action")
                .remove("pending_whatsapp_phone")
                .remove("pending_whatsapp_message")
                .remove("pending_whatsapp_action_at")
                .putBoolean("pending_whatsapp_waiting_accessibility", false)
                .putString("whatsapp_auto_call_status", "expired")
                .apply()
            return
        }
        if (!isMynAssistantAccessibilityEnabled(this)) return
        val action = prefs.getString("pending_whatsapp_action", null) ?: return
        val phoneNumber = prefs.getString("pending_whatsapp_phone", null) ?: return
        val message = prefs.getString("pending_whatsapp_message", null)
        prefs.edit().putBoolean("pending_whatsapp_waiting_accessibility", false).apply()
        window.decorView.post {
            try {
                openWhatsAppChat(this, phoneNumber, action, message)
            } catch (_: Exception) {
                prefs.edit().putString("whatsapp_auto_call_status", "expired").apply()
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
    sendRemoteCommand: (String, String, Map<String, Any>, (String, String?) -> Unit) -> Unit,
    photoImageLoader: ImageLoader, login: (String, String, (String) -> Unit) -> Unit,
    register: (String, String, String, (String) -> Unit) -> Unit, logout: () -> Unit,
    googleLogin: () -> Unit,
    selectSavedGoogleAccount: (String) -> Unit,
    savedGoogleAccounts: List<SavedGoogleAccount>,
    googleStatus: String?,
    wakeCommandFromOverlay: String? = null,
    onWakeCommandConsumed: () -> Unit = {}
) {
    var page by remember { mutableStateOf(Page.HOME) }
    val pageHistory = remember { mutableStateListOf<Page>() }
    var drawer by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var accountListExpanded by remember { mutableStateOf(false) }
    var devices by remember(firebaseUserId) { mutableStateOf<List<PcDevice>>(emptyList()) }
    var deviceNameDrafts by remember(firebaseUserId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingDeviceNameId by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var deviceNameEditError by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var deviceNameSaveBusyId by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var devicesLoading by remember(firebaseUserId) { mutableStateOf(!firebaseUserId.isNullOrBlank()) }
    var devicesError by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var selectedDeviceId by remember(firebaseUserId) { mutableStateOf<String?>(null) }
    var presenceNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            presenceNowMillis = System.currentTimeMillis()
        }
    }
    var command by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(wakeCommandFromOverlay) {
        val incoming = wakeCommandFromOverlay?.trim().orEmpty()
        if (incoming.isNotBlank()) {
            command = incoming
            feedback = "Sesli komut hazır. Kontrol edip gönder simgesine dokun."
            onWakeCommandConsumed()
        }
    }

    var commandTarget by remember { mutableStateOf("AUTO") }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var diagnosticsRevision by remember { mutableIntStateOf(0) }
    var pendingContactChoice by remember { mutableStateOf<PendingContactChoice?>(null) }
    var pendingContactConfirmation by remember { mutableStateOf<PendingContactConfirmation?>(null) }
    val commandContext = LocalContext.current
    var wakeWordEnabled by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("wake_word_enabled", false))
    }
    var wakeWordStatus by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wake_word_status", "Kapalı").orEmpty())
    }
    var wakeWordPhase by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wake_word_phase", MynWakeWordPhase.OFF.persistedValue).orEmpty())
    }
    var wakeWordErrorCode by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wake_word_error_code", "").orEmpty())
    }
    var wakeWordErrorDetails by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wake_word_error_details", "").orEmpty())
    }
    var wakeWordErrorAt by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("wake_word_error_at", 0L))
    }
    var wakeWordLastPhase by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("wake_word_last_phase", "").orEmpty())
    }
    var wakeWordRetryCount by remember {
        mutableIntStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("wake_word_retry_count", 0))
    }
    var wakeWordLastDetectedAt by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("wake_word_last_detected_at", 0L))
    }
    var wakeWordDetectionCount by remember {
        mutableStateOf(commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt("wake_word_detection_count", 0))
    }
    var wakeWordActivationPending by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val navigateToPage: (Page) -> Unit = { targetPage ->
        if (targetPage == Page.HOME) {
            pageHistory.clear()
            page = Page.HOME
        } else if (targetPage != page) {
            pageHistory.add(page)
            page = targetPage
        }
        drawer = false
    }
    BackHandler(enabled = !keyboardVisible) {
        when {
            showExitConfirmation -> showExitConfirmation = false
            drawer -> drawer = false
            pageHistory.isNotEmpty() -> page = pageHistory.removeAt(pageHistory.lastIndex)
            page != Page.HOME -> page = Page.HOME
            else -> showExitConfirmation = true
        }
    }
    val recordIssue: (String, String, String, String, String) -> String = {
            codePrefix, category, userMessage, commandText, technicalDetails ->
        val issueId = MynAssistantDiagnostics.record(
            commandContext,
            codePrefix,
            category,
            commandText,
            technicalDetails
        )
        diagnosticsRevision += 1
        userMessage + "\nSorun kodu: " + issueId
    }
    val refreshWakeWordUi: () -> Unit = {
        val wakePrefs = commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        wakeWordEnabled = wakePrefs.getBoolean("wake_word_enabled", false)
        wakeWordStatus = wakePrefs.getString("wake_word_status", "Kapalı").orEmpty()
        wakeWordPhase = wakePrefs.getString("wake_word_phase", MynWakeWordPhase.OFF.persistedValue).orEmpty()
        wakeWordErrorCode = wakePrefs.getString("wake_word_error_code", "").orEmpty()
        wakeWordErrorDetails = wakePrefs.getString("wake_word_error_details", "").orEmpty()
        wakeWordErrorAt = wakePrefs.getLong("wake_word_error_at", 0L)
        wakeWordLastPhase = wakePrefs.getString("wake_word_last_phase", "").orEmpty()
        wakeWordRetryCount = wakePrefs.getInt("wake_word_retry_count", 0)
        wakeWordLastDetectedAt = wakePrefs.getLong("wake_word_last_detected_at", 0L)
        wakeWordDetectionCount = wakePrefs.getInt("wake_word_detection_count", 0)
    }
    var pendingCommandAfterPermission by remember { mutableStateOf<String?>(null) }
    var pendingPermissionResultTick by remember { mutableIntStateOf(0) }
    var permissionResultTick by remember { mutableIntStateOf(0) }
    var startupPermissionRequestPending by remember { mutableStateOf(false) }
    var showAccessibilitySetupDialog by remember { mutableStateOf(false) }
    val runtimePermissions = remember { requiredRuntimePermissionList() }
    var startSpeechAfterPermission by remember { mutableStateOf(false) }
    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra("android.speech.extra.RESULTS")
            ?.firstOrNull()
            ?.trim()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spokenText.isNullOrBlank()) {
            command = spokenText
            feedback = null
        } else if (result.resultCode == android.app.Activity.RESULT_OK) {
            feedback = "Ses algılanamadı. Tekrar deneyebilirsin."
        }
    }
    val launchSpeechInput: () -> Unit = {
        try {
            speechLauncher.launch(
                Intent("android.speech.action.RECOGNIZE_SPEECH").apply {
                    putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form")
                    putExtra("android.speech.extra.LANGUAGE", "tr-TR")
                    putExtra("android.speech.extra.PROMPT", "Komutunu söyle")
                    putExtra("android.speech.extra.MAX_RESULTS", 1)
                }
            )
        } catch (error: Exception) {
            feedback = recordIssue(
                "MYN-VOICE-7001", "Sesli komut",
                "Sesli giriş açılamadı. Konuşma tanıma hizmetini ve mikrofon iznini kontrol et.",
                command,
                error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
            )
        }
    }
    val overlayPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(commandContext)) {
            wakeWordActivationPending = false
            try {
                requestMynWakeWordServiceStart(commandContext)
                refreshWakeWordUi()
            } catch (error: Exception) {
                wakeWordEnabled = false
                refreshWakeWordUi()
                feedback = recordIssue(
                    "MYN-WAKE-8009", "Hey Meyene",
                    "Uyandırma dinlemesi başlatılamadı. Telefon ayarlarını kontrol et.",
                    "", error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                )
            }
        } else {
            wakeWordActivationPending = false
            persistWakeWordPermissionState(
                commandContext,
                MynWakeWordPhase.WAITING_FOR_PERMISSION,
                "Ekran üstü izni verilmedi. Yüzen panel için bu izni açmalısın."
            )
            refreshWakeWordUi()
            feedback = "Hey Meyene paneli için ekran üstünde gösterme izni gerekli."
        }
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionResultTick += 1
        val denied = results.filterValues { granted -> !granted }.keys
        feedback = if (denied.isEmpty()) {
            "İzinler güncellendi."
        } else {
            recordIssue(
                "MYN-PERM-4001", "İzinler",
                "Bazı izinler verilmedi. İlgili özelliği kullanırken izinleri açman gerekebilir.",
                command,
                "DENIED_PERMISSIONS=" + denied.joinToString(",")
            )
        }
        if (startupPermissionRequestPending) {
            startupPermissionRequestPending = false
            val prefs = commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!isMynAssistantAccessibilityEnabled(commandContext) &&
                !prefs.getBoolean("whatsapp_accessibility_setup_seen", false)
            ) {
                showAccessibilitySetupDialog = true
            }
        }
        if (startSpeechAfterPermission) {
            startSpeechAfterPermission = false
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    commandContext, android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launchSpeechInput()
            } else {
                feedback = "Sesli komut için mikrofon izni gerekiyor."
            }
        }
        if (wakeWordActivationPending) {
            val requiredWakePermissions = listOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            val missingWakePermissions = requiredWakePermissions.filter { permission ->
                androidx.core.content.ContextCompat.checkSelfPermission(commandContext, permission) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missingWakePermissions.isNotEmpty()) {
                wakeWordActivationPending = false
                val microphoneMissing = android.Manifest.permission.RECORD_AUDIO in missingWakePermissions
                val code = if (microphoneMissing) "MYN-PERM-4004" else "MYN-PERM-4006"
                val userMessage = if (microphoneMissing) {
                    "Hey Meyene için mikrofon izni gerekiyor. Uygulama izinlerinden mikrofon erişimini aç."
                } else {
                    "Arka planda dinlemenin görünür servis bildirimi için bildirim iznini aç."
                }
                persistWakeWordPermissionState(
                    commandContext,
                    MynWakeWordPhase.ERROR,
                    "Hata $code · $userMessage",
                    code,
                    "Denied required wake permissions: " + missingWakePermissions.joinToString(",")
                )
                refreshWakeWordUi()
                feedback = userMessage
            } else if (!Settings.canDrawOverlays(commandContext)) {
                persistWakeWordPermissionState(
                    commandContext,
                    MynWakeWordPhase.WAITING_FOR_PERMISSION,
                    "Ekran üstü izni bekleniyor…"
                )
                refreshWakeWordUi()
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + commandContext.packageName)
                    )
                )
            } else {
                wakeWordActivationPending = false
                try {
                    requestMynWakeWordServiceStart(commandContext)
                    refreshWakeWordUi()
                } catch (error: Exception) {
                    wakeWordEnabled = false
                    refreshWakeWordUi()
                    feedback = recordIssue(
                        "MYN-WAKE-8009", "Hey Meyene",
                        "Uyandırma dinlemesi başlatılamadı. Telefon ayarlarını kontrol et.",
                        "", error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                    )
                }
            }
        }
    }
    val requestWakeWordActivation: () -> Unit = {
        if (wakeWordEnabled) {
            stopMynWakeWordService(commandContext)
            wakeWordEnabled = false
            wakeWordStatus = "Kapalı"
        } else {
            val missing = listOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.POST_NOTIFICATIONS
            ).filter { permission ->
                androidx.core.content.ContextCompat.checkSelfPermission(commandContext, permission) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                wakeWordActivationPending = true
                persistWakeWordPermissionState(
                    commandContext,
                    MynWakeWordPhase.WAITING_FOR_PERMISSION,
                    "Mikrofon ve bildirim izinleri bekleniyor…"
                )
                refreshWakeWordUi()
                permissionLauncher.launch(missing.toTypedArray())
            } else if (!Settings.canDrawOverlays(commandContext)) {
                wakeWordActivationPending = true
                persistWakeWordPermissionState(
                    commandContext,
                    MynWakeWordPhase.WAITING_FOR_PERMISSION,
                    "Ekran üstü izni bekleniyor…"
                )
                refreshWakeWordUi()
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + commandContext.packageName)
                    )
                )
            } else {
                try {
                    requestMynWakeWordServiceStart(commandContext)
                    refreshWakeWordUi()
                } catch (error: Exception) {
                    wakeWordEnabled = false
                    refreshWakeWordUi()
                    feedback = recordIssue(
                        "MYN-WAKE-8009", "Hey Meyene",
                        "Uyandırma dinlemesi başlatılamadı. Telefon ayarlarını kontrol et.",
                        "", error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        val missing = runtimePermissions.filter { permission ->
            androidx.core.content.ContextCompat.checkSelfPermission(commandContext, permission) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        val prefs = commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (missing.isNotEmpty()) {
            startupPermissionRequestPending = true
            permissionLauncher.launch(missing.toTypedArray())
        } else if (!isMynAssistantAccessibilityEnabled(commandContext) &&
            !prefs.getBoolean("whatsapp_accessibility_setup_seen", false)
        ) {
            showAccessibilitySetupDialog = true
        }
    }
    var pcBridgeUrl by remember(firebaseUserId) { mutableStateOf(initialPcBridgeUrl) }
    var pcBridgeToken by remember(firebaseUserId) { mutableStateOf(initialPcBridgeToken) }
    var pcBridgeStatus by remember(firebaseUserId) { mutableStateOf("Bilgisayar bağlantısı henüz test edilmedi.") }
    var localPcConnected by remember(firebaseUserId) { mutableStateOf(false) }
    var pcBridgeBusy by remember(firebaseUserId) { mutableStateOf(false) }
    var activeCommandListener by remember {
        mutableStateOf<com.google.firebase.firestore.ListenerRegistration?>(null)
    }

    // Keep the most recently submitted Firebase command observed until it reaches a terminal state.
    DisposableEffect(firebaseUserId, activeCommandListener) {
        val listenerToDispose = activeCommandListener
        onDispose { listenerToDispose?.remove() }
    }

    val sendTrackedRemoteCommand: (String, String, Map<String, Any>) -> Unit = {
            targetDeviceId, targetCommandName, targetPayload ->
        activeCommandListener?.remove()
        activeCommandListener = null
        feedback = "Komut bilgisayara gönderiliyor..."
        sendRemoteCommand(targetDeviceId, targetCommandName, targetPayload) { message, commandId ->
            feedback = message
            if (commandId != null) {
                val registration = FirebaseFirestore.getInstance()
                    .collection("commands")
                    .document(commandId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            feedback = recordIssue(
                                "MYN-PC-6001", "Bilgisayar komutu",
                                "Bilgisayardan gelen işlem durumunu okuyamadım. Bağlantını kontrol edip tekrar dene.",
                                targetPayload["text"]?.toString().orEmpty(),
                                error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                            )
                            activeCommandListener = null
                        } else if (snapshot == null || !snapshot.exists()) {
                            feedback = recordIssue(
                                "MYN-PC-6002", "Bilgisayar komutu",
                                "Bilgisayar komutunun sonucunu bulamadım. Bilgisayar bağlantısını kontrol et.",
                                targetPayload["text"]?.toString().orEmpty(),
                                "COMMAND_DOCUMENT_MISSING; id=" + commandId
                            )
                            activeCommandListener = null
                        } else {
                            val status = snapshot.getString("status").orEmpty()
                            val result = snapshot.getString("result").orEmpty()
                            when (status) {
                                "pending" -> feedback = "Komut bilgisayara ulaştı; işlemesi bekleniyor."
                                "running" -> feedback = "Bilgisayar komutu uyguluyor..."
                                "completed" -> {
                                    feedback = result.ifBlank { "Komut tamamlandı." }
                                    activeCommandListener = null
                                }
                                "failed" -> {
                                    feedback = recordIssue(
                                        "MYN-PC-6003", "Bilgisayar komutu",
                                        result.ifBlank { "Bilgisayar komutu tamamlayamadı. Sorun kodunu paylaşabilirsin." },
                                        targetPayload["text"]?.toString().orEmpty(),
                                        "REMOTE_COMMAND_FAILED; result=" + result
                                    )
                                    activeCommandListener = null
                                }
                                else -> {
                                    feedback = recordIssue(
                                        "MYN-PC-6005", "Bilgisayar komutu",
                                        "Bilgisayar beklenmeyen bir işlem durumu bildirdi.",
                                        targetPayload["text"]?.toString().orEmpty(),
                                        "UNKNOWN_COMMAND_STATUS=" + status
                                    )
                                    activeCommandListener = null
                                }
                            }
                        }
                    }
                activeCommandListener = registration
            } else if (
                message.contains("hata", ignoreCase = true) ||
                message.contains("gönderilemedi", ignoreCase = true) ||
                message.contains("başarısız", ignoreCase = true)
            ) {
                feedback = recordIssue(
                    "MYN-PC-6006", "Bilgisayar bağlantısı",
                    message,
                    targetPayload["text"]?.toString().orEmpty(),
                    "REMOTE_COMMAND_REJECTED; message=" + message
                )
            }
        }
    }

    DisposableEffect(commandContext) {
        val wakePrefs = commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wakeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key in setOf(
                    "wake_word_enabled", "wake_word_status", "wake_word_phase",
                    "wake_word_error_code", "wake_word_error_details", "wake_word_error_at", "wake_word_last_phase",
                    "wake_word_retry_count", "wake_word_last_detected_at", "wake_word_detection_count"
                )
            ) {
                wakeWordEnabled = shared?.getBoolean("wake_word_enabled", false) ?: false
                wakeWordStatus = shared?.getString("wake_word_status", "Kapalı").orEmpty()
                wakeWordPhase = shared?.getString("wake_word_phase", MynWakeWordPhase.OFF.persistedValue).orEmpty()
                wakeWordErrorCode = shared?.getString("wake_word_error_code", "").orEmpty()
                wakeWordErrorDetails = shared?.getString("wake_word_error_details", "").orEmpty()
                wakeWordErrorAt = shared?.getLong("wake_word_error_at", 0L) ?: 0L
                wakeWordLastPhase = shared?.getString("wake_word_last_phase", "").orEmpty()
                wakeWordRetryCount = shared?.getInt("wake_word_retry_count", 0) ?: 0
                wakeWordLastDetectedAt = shared?.getLong("wake_word_last_detected_at", 0L) ?: 0L
                wakeWordDetectionCount = shared?.getInt("wake_word_detection_count", 0) ?: 0
            }
        }
        wakePrefs.registerOnSharedPreferenceChangeListener(wakeListener)
        onDispose { wakePrefs.unregisterOnSharedPreferenceChangeListener(wakeListener) }
    }
    LaunchedEffect(commandContext) {
        while (true) {
            kotlinx.coroutines.delay(15_000L)
            reconcileStaleWakeWordState(commandContext)
            refreshWakeWordUi()
        }
    }
    DisposableEffect(commandContext) {
        val prefs = commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == "whatsapp_auto_call_status") {
                val status = shared?.getString(key, "").orEmpty()
                if (status == "started" || status == "sent" || status == "expired") {
                    (commandContext as? ComponentActivity)?.runOnUiThread {
                        feedback = when (status) {
                            "started" -> "WhatsApp sesli araması başlatıldı."
                            "sent" -> "WhatsApp mesajı gönderildi."
                            else -> recordIssue(
                                "MYN-WA-3002", "WhatsApp",
                                "WhatsApp işlemi tamamlanamadı. WhatsApp'ın açık ve gerekli iznin etkin olduğundan emin ol.",
                                command,
                                "ACCESSIBILITY_ACTION_TIMEOUT"
                            )
                        }
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(firebaseUserId) {
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        if (!firebaseUserId.isNullOrBlank()) {
            devicesLoading = true
            registration = FirebaseFirestore.getInstance()
                .collection("devices")
                .whereEqualTo("ownerId", firebaseUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        devicesError = "Cihaz listesi şu anda yüklenemedi. İnternet bağlantını kontrol edip tekrar dene."
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
                                    ?: "Unknown",
                                status = document.getString("status")
                                    ?: document.getString("connectionStatus")
                                    ?: "offline",
                                connectionMode = document.getString("connectionMode").orEmpty(),
                                screenJpegBase64 = document.getString("screenJpegBase64").orEmpty(),
                                lastSeenAtMillis = document.getTimestamp("lastSeenAt")?.toDate()?.time
                            )
                        }
                        devicesError = null
                        devicesLoading = false
                        if (devices.none { it.id == selectedDeviceId && it.isPcTarget }) {
                            selectedDeviceId = devices.firstOrNull { it.isPcTarget }?.id
                        }
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
    val pcDevices = devices.filter { it.isPcTarget }
    val selectedDevice = pcDevices.firstOrNull { it.id == selectedDeviceId }
        ?: pcDevices.firstOrNull { it.isOnlineAt(presenceNowMillis) }
        ?: pcDevices.firstOrNull()

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
    val connectedDevices = pcDevices.filter { device ->
        if (device.id == localPcDeviceId) localPcConnected else device.isOnlineAt(presenceNowMillis)
    }
    val hasConnectedPc = signedIn && connectedDevices.isNotEmpty()
    val testPcBridgeNow: () -> Unit = {
        if (!signedIn) {
            feedback = "Devam etmek için önce giriş yap."
        } else if (pcBridgeUrl.isBlank()) {
            feedback = "Önce bilgisayar bağlantı ayarlarını tamamla."
            navigateToPage(Page.SETTINGS)
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
            feedback = "Devam etmek için önce giriş yap."
        } else if (!localPcConnected) {
            feedback = "Önce bilgisayar bağlantı ayarlarını tamamla."
        } else {
            sendPcBridgeCommand(pcBridgeUrl, pcBridgeToken, commandText) { result ->
                localPcConnected = result.connected
                pcBridgeStatus = result.message
                feedback = result.message
            }
        }
    }

    val executeResolvedContactAction: (String, ContactPhoneMatch) -> Unit = { commandText, contact ->
        val action = contactActionKind(commandText)
        feedback = try {
            when (action) {
                "DIAL" -> {
                    commandContext.startActivity(
                        Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(contact.phoneNumber)))
                    )
                    contact.displayName + " aranıyor."
                }
                "WHATSAPP_CALL" -> openWhatsAppChat(
                    commandContext, contact.phoneNumber, autoAction = "CALL"
                )
                "WHATSAPP_MESSAGE" -> {
                    val message = extractWhatsAppMessage(commandText, contact.displayName)
                    if (message.isNullOrBlank()) {
                        recordIssue(
                            "MYN-WA-3001", "WhatsApp", 
                            "Mesajın içeriğini ayıramadım. Örneğin: Fatih'e naber diye WhatsApp'tan mesaj at.",
                            commandText,
                            "MESSAGE_PARSE_EMPTY; resolvedContact=" + contact.displayName
                        )
                    } else {
                        openWhatsAppChat(
                            commandContext,
                            contact.phoneNumber,
                            autoAction = "MESSAGE",
                            message = message
                        )
                    }
                }
                else -> executeAndroidTextCommand(commandContext, commandText)
            }
        } catch (error: Exception) {
            recordIssue(
                "MYN-ACT-5001", "Komut yürütme",
                "İşlemi başlatamadım. İzinleri ve ilgili uygulamayı kontrol et.",
                commandText,
                error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
            )
        }
    }

    val chooseContactCandidate: (PendingContactChoice, ContactPhoneMatch) -> Unit = { choice, contact ->
        pendingContactChoice = null
        if (choice.saveImmediately) {
            commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(choice.aliasKey, contact.displayName)
                .putString(choice.aliasKey + "_number", contact.phoneNumber)
                .apply()
            feedback = choice.aliasLabel + " artık " + contact.displayName + " olarak kaydedildi."
        } else {
            val selectedAlias = contactAliasSpec(choice.command, contact.displayName)
            pendingContactConfirmation = PendingContactConfirmation(
                command = choice.command,
                aliasKey = selectedAlias?.first ?: choice.aliasKey,
                aliasLabel = selectedAlias?.second ?: choice.aliasLabel,
                contact = contact
            )
        }
    }

    val runAssistantCommand: (String) -> Unit = { rawCommand ->
        val textCommand = rawCommand.trim()
        val computerPrefix = COMPUTER_COMMAND_PREFIX.find(textCommand)
        val target = when (commandTarget) {
            "PHONE" -> "PHONE"
            "PC" -> "PC"
            else -> if (computerPrefix != null) "PC" else "PHONE"
        }
        val routedText = if (target == "PC" && computerPrefix != null) {
            textCommand.substring(computerPrefix.range.last + 1).trim()
        } else {
            textCommand
        }
        val contactAction = contactActionKind(textCommand)
        val phoneNumber = extractPhoneNumber(textCommand)
        val relationshipAssignment = parseRelationshipAliasAssignment(textCommand)

        when {
            textCommand.isBlank() -> feedback = "Önce bir komut yaz."
            target == "PHONE" && relationshipAssignment != null -> {
                val missing = if (
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        commandContext, android.Manifest.permission.READ_CONTACTS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) listOf(android.Manifest.permission.READ_CONTACTS) else emptyList()
                if (missing.isNotEmpty()) {
                    pendingCommandAfterPermission = textCommand
                    pendingPermissionResultTick = permissionResultTick + 1
                    feedback = "Rehber izni isteniyor; izin verince kişi eşleştirmesi otomatik devam edecek."
                    permissionLauncher.launch(missing.toTypedArray())
                } else {
                    val (relationKey, requestedName) = relationshipAssignment
                    val matches = findContactPhonesForCommand(commandContext, requestedName, resolveSavedAlias = false)
                    if (matches.isEmpty()) {
                        feedback = recordIssue(
                            "MYN-CON-2001", "Kişi eşleştirme",
                            "Bu isimle kayıtlı bir telefon numarası bulamadım. Rehberdeki adı kontrol edip tekrar dene.",
                            textCommand, "NO_CONTACT_MATCH; requestedName=" + requestedName
                        )
                    } else if (matches.size > 1) {
                        val relationLabel = when (relationKey) {
                            "father" -> "babam"
                            "mother" -> "annem"
                            "brother" -> "abim"
                            "sister" -> "ablam"
                            else -> "eşim"
                        }
                        pendingContactChoice = PendingContactChoice(
                            command = textCommand,
                            aliasKey = "contact_alias_" + relationKey,
                            aliasLabel = relationLabel,
                            candidates = matches,
                            saveImmediately = true
                        )
                        feedback = null
                    } else {
                        val matchedContact = matches.first()
                        commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString("contact_alias_" + relationKey, normalizeContactText(matchedContact.displayName))
                            .putString("contact_alias_" + relationKey + "_number", matchedContact.phoneNumber)
                            .apply()
                        val relationshipName = when (relationKey) {
                            "father" -> "Baban"
                            "mother" -> "Annen"
                            "brother" -> "Ağabeyin/kardeşin"
                            "sister" -> "Ablan/kız kardeşin"
                            else -> "Eşin"
                        }
                        feedback = relationshipName + " artık " + matchedContact.displayName + " olarak kaydedildi. Bir sonraki komutta otomatik eşleştireceğim."
                    }
                }
            }
            target == "PHONE" && contactAction != null -> {
                val missingContactPermissions = buildList {
                    if (phoneNumber == null &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            commandContext,
                            android.Manifest.permission.READ_CONTACTS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) add(android.Manifest.permission.READ_CONTACTS)
                    if (contactAction == "DIAL" &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            commandContext,
                            android.Manifest.permission.CALL_PHONE
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) add(android.Manifest.permission.CALL_PHONE)
                }
                if (missingContactPermissions.isNotEmpty()) {
                    pendingCommandAfterPermission = textCommand
                    pendingPermissionResultTick = permissionResultTick + 1
                    feedback = "MynAssistant rehber/arama izinlerini istiyor; izin verince komut otomatik devam edecek."
                    permissionLauncher.launch(missingContactPermissions.toTypedArray())
                } else {
                    val contacts = if (phoneNumber != null) {
                        listOf(ContactPhoneMatch("Telefon numarası", phoneNumber, 120))
                    } else {
                        findContactPhonesForCommand(commandContext, textCommand)
                    }
                    when {
                        contacts.isEmpty() -> {
                            feedback = recordIssue(
                                "MYN-CON-2001", "Kişi eşleştirme",
                                "Bu kişiyle eşleşen telefon numarası bulamadım. Kişinin adını rehberdeki gibi yazmayı deneyebilirsin.",
                                textCommand, "NO_CONTACT_MATCH; contactsPermission=" +
                                    (androidx.core.content.ContextCompat.checkSelfPermission(
                                        commandContext, android.Manifest.permission.READ_CONTACTS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                            )
                        }
                        contacts.size > 1 -> {
                            val aliasSpec = contactAliasSpec(textCommand, contacts.first().displayName)
                            val includesNaturalMessage = Regex(
                                """(?iu)\s+diye\s+(?:(?:whatsapp)(?:['’]?(?:tan|ten|dan|den))?\s+)?mesaj\s+(?:at|gönder|gonder|yolla|yaz)\b"""
                            ).containsMatchIn(textCommand)
                            val aliasLabel = aliasSpec?.second
                                ?: if (includesNaturalMessage) "bu isim" else contactLookupAlias(textCommand)
                                ?: contacts.first().displayName
                            val aliasKey = aliasSpec?.first
                                ?: ("contact_alias_name_" + normalizeContactText(aliasLabel).replace(" ", "_"))
                            pendingContactChoice = PendingContactChoice(
                                command = textCommand,
                                aliasKey = aliasKey,
                                aliasLabel = aliasLabel,
                                candidates = contacts,
                                saveImmediately = false
                            )
                            feedback = null
                        }
                        else -> executeResolvedContactAction(textCommand, contacts.first())
                    }
                }
            }
            target == "PHONE" -> {
                val result = executeAndroidTextCommand(commandContext, textCommand)
                feedback = if (result.contains("henüz desteklenmiyor", ignoreCase = true) ||
                    result.contains("desteklenmiyor", ignoreCase = true)
                ) {
                    recordIssue(
                        "MYN-CMD-1001", "Komut tanıma",
                        "Bu komut henüz desteklenmiyor. Farklı bir ifade deneyebilir veya sorun kodunu paylaşabilirsin.",
                        textCommand, "UNSUPPORTED_COMMAND; result=" + result
                    )
                } else result
            }
            routedText.isBlank() -> feedback = "Bilgisayar için komutu da yaz. Örn. bilgisayar Chrome aç."
            !signedIn -> feedback = "Bilgisayara komut göndermek için önce giriş yap."
            selectedDevice?.isOnlineAt(presenceNowMillis) == true && selectedDevice.connectionMode == "firebase-agent" -> {
                feedback = "Komut bilgisayarına gönderiliyor..."
                sendTrackedRemoteCommand(selectedDevice.id, "text_command", mapOf("text" to routedText))
            }
            localPcConnected -> {
                feedback = "Komut bilgisayarına gönderiliyor..."
                sendPcBridgeCommand(pcBridgeUrl, pcBridgeToken, routedText) { result ->
                    localPcConnected = result.connected
                    pcBridgeStatus = result.message
                    feedback = if (result.connected) {
                        result.message
                    } else {
                        recordIssue(
                            "MYN-PC-6004", "Bilgisayar bağlantısı",
                            "Bilgisayara komut gönderemedim. Windows yardımcı uygulamasının açık ve bağlantı ayarlarının doğru olduğunu kontrol et.",
                            routedText,
                            "LOCAL_BRIDGE_COMMAND_FAILED; message=" + result.message
                        )
                    }
                }
            }
            else -> feedback = "Şu anda bağlı bilgisayar yok. Windows uygulaması çalıştığında burada görünür."
        }
    }
    LaunchedEffect(permissionResultTick, pendingCommandAfterPermission) {
        val pending = pendingCommandAfterPermission ?: return@LaunchedEffect
        if (permissionResultTick < pendingPermissionResultTick) return@LaunchedEffect
        val action = contactActionKind(pending)
        val hasContacts = androidx.core.content.ContextCompat.checkSelfPermission(
            commandContext, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCall = androidx.core.content.ContextCompat.checkSelfPermission(
            commandContext, android.Manifest.permission.CALL_PHONE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val canContinue = (extractPhoneNumber(pending) != null || hasContacts) &&
            (action != "DIAL" || hasCall)
        pendingCommandAfterPermission = null
        if (canContinue) {
            runAssistantCommand(pending)
        } else {
            feedback = "Gerekli izin verilmediği için komut çalıştırılmadı. İzni verip komutu yeniden deneyebilirsin."
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                // This toolbar stays fixed while the page content below it scrolls.
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { drawer = true }) { Icon(Icons.Default.Menu, contentDescription = "Menüyü aç", modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(42.dp).clickable { navigateToPage(Page.REMOTE) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SettingsRemote, contentDescription = "PC kumandası", modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = toggleTheme, shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp)); Text(if (dark) "Açık" else "Koyu", fontSize = 14.sp) }
                    }
                }
                androidx.compose.runtime.SideEffect {
                    val activity = commandContext as? ComponentActivity
                    activity?.window?.let { systemWindow ->
                        val systemBarColor = if (dark) android.graphics.Color.rgb(11, 13, 20) else android.graphics.Color.rgb(245, 246, 250)
                        systemWindow.statusBarColor = systemBarColor
                        systemWindow.navigationBarColor = systemBarColor
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            systemWindow.isNavigationBarContrastEnforced = false
                            systemWindow.isStatusBarContrastEnforced = false
                        }
                        val controller = androidx.core.view.WindowCompat.getInsetsController(systemWindow, systemWindow.decorView)
                        controller.isAppearanceLightStatusBars = !dark
                        controller.isAppearanceLightNavigationBars = !dark
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                if (page == Page.HOME) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.padding(top = 3.dp)) { MynLogo(56.dp) }
                        Column(Modifier.weight(1f)) {
                            Text("MynAssistant", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            Text("Kişisel asistanın için yeni başlangıç.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .68f))
                        }
                    }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("KOMUT MERKEZİ", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Sana nasıl yardımcı olabilirim?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                            Text("Telefonuna veya bilgisayarına komut ver; hedefi sen seç veya otomatik yönlendirmeyi kullan.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                        }
                    }
                } else {
                    Text(when (page) {
                        Page.LOGIN -> if (signedIn) "Hesabım" else "Giriş yap"
                        Page.REGISTER -> "Kayıt ol"
                        Page.DEVICES -> "Cihazlarım"
                        Page.SETTINGS -> "Ayarlar"
                        Page.REMOTE -> "PC Kumandası"
                        Page.FILES -> "PcFiles"
                        Page.DIAGNOSTICS -> "Hata kayıtları"
                        else -> "Ana sayfa"
                    }, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    when (page) {
                        Page.LOGIN -> if (signedIn) {
                            Text("Oturum açık", color = MaterialTheme.colorScheme.secondary)
                            Text("Ad: " + name)
                            Text("E-posta: " + email.ifBlank { "Eklenmemiş" })
                            Text("Telefon: " + phone.ifBlank { "Eklenmemiş" })
                            googleStatus?.let { status -> Text(status, color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) || status.contains("giriş yapıldı", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(false, login = login, googleLogin = googleLogin, googleStatus = googleStatus, goRegister = { navigateToPage(Page.REGISTER) })
                        Page.REGISTER -> if (signedIn) {
                            Text("Oturum açık.", color = MaterialTheme.colorScheme.secondary)
                            Text("E-posta: " + email.ifBlank { "Eklenmemiş" })
                            googleStatus?.let { status -> Text(status, color = when {
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) || status.contains("giriş yapıldı", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
                            OutlinedButton(onClick = logout) { Text("Çıkış yap") }
                        } else AuthForm(true, register = register, googleLogin = googleLogin, googleStatus = googleStatus, goRegister = { navigateToPage(Page.LOGIN) })
                        Page.DEVICES -> {
                            Text(
                                if (signedIn) "Yalnızca bu hesaba ait cihazlar burada görünür."
                                else "Cihazlarını görmek için önce hesabına giriş yap.",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f),
                                fontSize = 14.sp
                            )
                            if (!signedIn) {
                                Button(onClick = { navigateToPage(Page.LOGIN) }, modifier = Modifier.fillMaxWidth()) { Text("Giriş yap") }
                            } else {
                                Text(
                                    "Windows uygulaması aynı hesapla giriş yaptığında bilgisayarın burada otomatik görünür. Ek bağlantı bilgisi girmen gerekmez.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                )
                                if (devicesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                devicesError?.let { error ->
                                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                                if (!devicesLoading && pcDevices.isEmpty() && devicesError == null) {
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                            Text("Henüz bilgisayar bağlanmamış", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Bilgisayarında MynAssistant Windows uygulamasını kurup bu hesapla giriş yaptığında cihazın burada otomatik görünür.",
                                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                            )
                                        }
                                    }
                                }
                                devices.forEach { device ->
                                    val deviceOnlineNow = if (device.id == localPcDeviceId) localPcConnected else device.isOnlineAt(presenceNowMillis)
                                    val isEditingName = editingDeviceNameId == device.id
                                    var editedDeviceName by remember(device.id, device.name) {
                                        mutableStateOf(deviceNameDrafts[device.id] ?: device.name)
                                    }
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(
                                                    if (device.isPcTarget) Icons.Default.Computer else Icons.Default.Devices,
                                                    contentDescription = if (device.isPcTarget) "Windows bilgisayar" else "Android telefon",
                                                    modifier = Modifier.size(30.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                                                IconButton(
                                                    onClick = {
                                                        deviceNameEditError = null
                                                        if (isEditingName) {
                                                            editingDeviceNameId = null
                                                            deviceNameDrafts = deviceNameDrafts - device.id
                                                        } else {
                                                            editingDeviceNameId = device.id
                                                            deviceNameDrafts = deviceNameDrafts + (device.id to device.name)
                                                            editedDeviceName = device.name
                                                        }
                                                        feedback = null
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        if (isEditingName) Icons.Default.Close else Icons.Default.Edit,
                                                        contentDescription = if (isEditingName) "Ad düzenlemeyi kapat" else "Cihaz adını düzenle",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            if (isEditingName) {
                                                OutlinedTextField(
                                                    value = editedDeviceName,
                                                    onValueChange = {
                                                        editedDeviceName = it.take(80)
                                                        deviceNameDrafts = deviceNameDrafts + (device.id to it.take(80))
                                                        deviceNameEditError = null
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    label = { Text(if (device.isPcTarget) "Bilgisayar adı" else "Cihaz adı") },
                                                    singleLine = true,
                                                    enabled = deviceNameSaveBusyId != device.id
                                                )
                                                deviceNameEditError?.let { error ->
                                                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                                }
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            editingDeviceNameId = null
                                                            deviceNameDrafts = deviceNameDrafts - device.id
                                                            deviceNameEditError = null
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        enabled = deviceNameSaveBusyId != device.id
                                                    ) {
                                                        Text("İptal")
                                                    }
                                                    Button(
                                                        onClick = {
                                                            val cleanedName = editedDeviceName.trim()
                                                            if (cleanedName.isBlank()) {
                                                                deviceNameEditError = if (device.isPcTarget) "Bilgisayar adı boş olamaz." else "Cihaz adı boş olamaz."
                                                            } else if (cleanedName != device.name) {
                                                                deviceNameSaveBusyId = device.id
                                                                FirebaseFirestore.getInstance().collection("devices").document(device.id)
                                                                    .update("name", cleanedName)
                                                                    .addOnSuccessListener {
                                                                        deviceNameDrafts = deviceNameDrafts - device.id
                                                                        editingDeviceNameId = null
                                                                        deviceNameEditError = null
                                                                        deviceNameSaveBusyId = null
                                                                        feedback = null
                                                                    }
                                                                    .addOnFailureListener {
                                                                        deviceNameSaveBusyId = null
                                                                        deviceNameEditError = "Ad kaydedilemedi. Bağlantıyı ve hesap izinlerini kontrol edip tekrar dene."
                                                                    }
                                                            } else {
                                                                editingDeviceNameId = null
                                                                deviceNameDrafts = deviceNameDrafts - device.id
                                                                deviceNameEditError = null
                                                                feedback = null
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        enabled = deviceNameSaveBusyId != device.id
                                                    ) {
                                                        if (deviceNameSaveBusyId == device.id) {
                                                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                                        } else {
                                                            Icon(Icons.Default.Edit, contentDescription = null)
                                                            Spacer(Modifier.width(6.dp))
                                                            Text("Kaydet")
                                                        }
                                                    }
                                                }
                                            }
                                            if (device.isPcTarget) {
                                                Button(
                                                    onClick = {
                                                        selectedDeviceId = device.id
                                                        feedback = null
                                                        navigateToPage(Page.REMOTE)
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("PC kumandasına bağlan")
                                                }
                                            } else {
                                                Text(
                                                    "Android cihaz • PC kumandası yalnızca Windows bilgisayarlarda kullanılabilir.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Page.DIAGNOSTICS -> {
                            val entries = remember(diagnosticsRevision, page) {
                                MynAssistantDiagnostics.read(commandContext)
                            }
                            Text(
                                "Sorun kodları, nedenleri ve komut kayıtları yalnızca bu telefonda tutulur. İstersen kayıtları temizleyebilirsin.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)
                            )
                            if (entries.isEmpty()) {
                                Card(Modifier.fillMaxWidth()) {
                                    Text("Henüz hata kaydı yok.", Modifier.padding(16.dp))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        MynAssistantDiagnostics.clear(commandContext)
                                        diagnosticsRevision += 1
                                        feedback = "Hata kayıtları temizlendi."
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Kayıtları temizle")
                                }
                                entries.forEach { entry ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(entry.id, fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary)
                                            Text(entry.category, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text(
                                                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                                                    .format(java.util.Date(entry.createdAt)),
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)
                                            )
                                            if (entry.command.isNotBlank()) Text("Komut: " + entry.command, fontSize = 12.sp)
                                            if (entry.details.isNotBlank()) Text("Ayrıntı: " + entry.details, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Page.FILES -> {
                            Text(
                                "Yalnızca şu anda aktif ve bağlı bilgisayarlar burada görünür.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)
                            )
                            if (!signedIn) {
                                Button(onClick = { navigateToPage(Page.LOGIN) }, modifier = Modifier.fillMaxWidth()) { Text("Giriş yap") }
                            } else if (devicesLoading) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            } else if (connectedDevices.isEmpty()) {
                                Text("Şu anda aktif ve bağlı bilgisayar yok.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                            } else {
                                connectedDevices.forEach { device ->
                                    val deviceOnlineNow = true
                                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                                Column(Modifier.weight(1f)) {
                                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                                    Text(if (deviceOnlineNow) "Çevrimiçi" else "Çevrimdışı", fontSize = 12.sp,
                                                        color = if (deviceOnlineNow) Color(0xFF65D68B) else MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                                                }
                                            }
                                            Button(onClick = {
                                                selectedDeviceId = device.id
                                                if (!deviceOnlineNow) {
                                                    feedback = "Bu bilgisayar çevrimdışı. Windows uygulamasını açıp yeniden dene."
                                                } else if (device.connectionMode == "firebase-agent") {
                                                    feedback = "Dosya listesi bilgisayardan isteniyor..."
                                                    sendTrackedRemoteCommand(device.id, "text_command", mapOf("text" to "bilgisayar dosyalarını listele"))
                                                } else if (device.id == localPcDeviceId && localPcConnected) {
                                                    feedback = "Dosya listesi bilgisayardan isteniyor..."
                                                    sendPcBridgeCommand(pcBridgeUrl, pcBridgeToken, "dosyaları listele") { result ->
                                                        localPcConnected = result.connected
                                                        pcBridgeStatus = result.message
                                                        feedback = result.message
                                                    }
                                                } else {
                                                    feedback = "Bu bağlantı türünde dosya listeleme henüz desteklenmiyor."
                                                }
                                            }, modifier = Modifier.fillMaxWidth()) {
                                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Dosya listesini iste")
                                            }
                                            Text(
                                                "Bilgisayardaki klasörleri burada görüntülemek için Windows yardımcı uygulamasının dosya listesini göndermesi gerekir.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
                                            )
                                        }
                                    }
                                }
                            }
                            feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                        }
                        Page.SETTINGS -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (dark) "Koyu tema" else "Açık tema", Modifier.weight(1f))
                                Switch(checked = dark, onCheckedChange = { toggleTheme() })
                            }
                            Text("Tema tercihi uygulama yeniden açıldığında korunur.", fontSize = 13.sp)
                            Text(
                                "Rehber izni verildiğinde, bu telefona senkronize edilmiş Google, SIM ve diğer kişi hesaplarındaki kayıtlar aranır.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f)
                            )
                            Text(if (signedIn) "Oturum bu cihazda hatırlanıyor." else "Şu anda giriş yapılmamış.")
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            Modifier.size(58.dp).background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = .16f), CircleShape
                                            ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Mic, contentDescription = null,
                                                modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Hey Meyene", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                            Text("Çevrimdışı uyandırma", fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = if (wakeWordPhase == MynWakeWordPhase.LISTENING.persistedValue) Color(0xFF49B86E) else MaterialTheme.colorScheme.outline.copy(alpha = .5f)
                                        ) { Box(Modifier.size(10.dp)) }
                                    }
                                    Text(
                                        when (wakeWordPhase) {
                                            MynWakeWordPhase.LISTENING.persistedValue ->
                                                "“Hey Meyene” dediğinde ekran üstü komut paneli açılmalı."
                                            MynWakeWordPhase.ERROR.persistedValue ->
                                                "Dinleme başlatılamadı veya durdu. Hata ayrıntılarını aşağıdan kontrol edebilirsin."
                                            MynWakeWordPhase.DOWNLOADING_MODEL.persistedValue,
                                            MynWakeWordPhase.EXTRACTING_MODEL.persistedValue,
                                            MynWakeWordPhase.VALIDATING_MODEL.persistedValue,
                                            MynWakeWordPhase.LOADING_ENGINE.persistedValue,
                                            MynWakeWordPhase.STARTING_MICROPHONE.persistedValue ->
                                                "Ses sistemi hazırlanıyor. Gerçek dinleme durumu aşağıdaki aşamada gösterilir."
                                            else ->
                                                "Etkinleştirdikten sonra desteklenen koşullarda “Hey Meyene” diyerek diğer uygulamaların üzerinde yüzen paneli çağırabilirsin."
                                        },
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f)
                                    )
                                    Text(
                                        wakeWordStatus,
                                        fontSize = 12.sp,
                                        color = when {
                                            wakeWordPhase == MynWakeWordPhase.ERROR.persistedValue -> MaterialTheme.colorScheme.error
                                            wakeWordPhase == MynWakeWordPhase.LISTENING.persistedValue -> Color(0xFF65D68B)
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .72f)
                                        }
                                    )
                                    if (wakeWordErrorCode.isNotBlank() &&
                                        wakeWordPhase == MynWakeWordPhase.ERROR.persistedValue
                                    ) {
                                        Text(
                                            "Hata kodu: $wakeWordErrorCode · Son aşama: " +
                                                wakeWordLastPhase.ifBlank { "bilinmiyor" } +
                                                " · yeniden deneme: $wakeWordRetryCount" +
                                                (wakeWordErrorAt.takeIf { it > 0L }?.let {
                                                    " · " + android.text.format.DateFormat.format("dd.MM HH:mm", it)
                                                } ?: ""),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        if (wakeWordErrorDetails.isNotBlank()) {
                                            Text(
                                                wakeWordErrorDetails,
                                                fontSize = 11.sp,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f)
                                            )
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("Arka planda dinle", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "Açıkken kalıcı bildirim ve mikrofon gizlilik göstergesi görünür. İstediğinde buradan kapatabilirsin.",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f)
                                            )
                                        }
                                        Switch(
                                            checked = wakeWordEnabled,
                                            onCheckedChange = { requestWakeWordActivation() }
                                        )
                                    }
                                    Text(
                                        "İlk kurulumda Türkçe komut modeli ve ayrı düşük güç uyandırma modeli indirilir. Kurulduktan sonra uyandırma ve komut tanıma cihaz üzerinde çevrimdışı çalışır. Dinleme etkin olduğu sürece mikrofon gizlilik göstergesi ve kalıcı bildirim açık kalır.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f)
                                    )
                                    Text(
                                        "Önemli: Dinlemeyi MynAssistant açıkken Ayarlar'dan başlat. Android 14 ve üzeri, uygulama arka plandayken mikrofonlu ön plan servisi başlatılmasını genellikle engeller. Zorla durdurma veya telefonun pil yönetimi dinlemeyi kesebilir; böyle bir durumda uygulamayı açıp yeniden etkinleştir.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f)
                                    )
                                    Text(
                                        "Son algılama: " +
                                            (wakeWordLastDetectedAt.takeIf { it > 0L }?.let {
                                                android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", it).toString()
                                            } ?: "Henüz algılanmadı") +
                                            " · toplam $wakeWordDetectionCount",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .74f)
                                    )
                                    Text(
                                        "Uyandırma algılaması artık düşük hesaplama gücü kullanan bir anahtar-kelime motoruyla yapılır. Türkçe komut tanıma yalnızca uyandırma algılandıktan sonra başlar.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                commandContext.startActivity(
                                                    Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + commandContext.packageName)
                                                    )
                                                )
                                            } catch (error: Exception) {
                                                feedback = recordIssue(
                                                    "MYN-PERM-4002", "Ekran üstü izin",
                                                    "Ekran üstü izin ayarları açılamadı.", "",
                                                    error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (Settings.canDrawOverlays(commandContext))
                                                "Ekran üstü iznini yönet"
                                            else
                                                "Ekran üstü iznini ver"
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                commandContext.startActivity(
                                                    Intent(
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.parse("package:" + commandContext.packageName)
                                                    )
                                                )
                                            } catch (error: Exception) {
                                                feedback = recordIssue(
                                                    "MYN-PERM-4003", "Pil ve arka plan",
                                                    "Uygulama pil ayarları açılamadı.", "",
                                                    error.javaClass.name + ": " + (error.message ?: "ayrıntı yok")
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pil ve arka plan ayarları")
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { navigateToPage(Page.DIAGNOSTICS) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Hata kayıtları")
                            }
                            OutlinedButton(
                                onClick = {
                                    val missing = requiredRuntimePermissionList().filter { permission ->
                                        androidx.core.content.ContextCompat.checkSelfPermission(commandContext, permission) !=
                                            android.content.pm.PackageManager.PERMISSION_GRANTED
                                    }
                                    if (missing.isEmpty()) {
                                        feedback = "Tanımlı çalışma zamanı izinleri zaten açık."
                                    } else {
                                        permissionLauncher.launch(missing.toTypedArray())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Eksik izinleri iste")
                            }
                            feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                            HorizontalDivider()
                            Text("WhatsApp otomatik işlemleri", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Bu izin açıkken, yalnızca sen WhatsApp arama veya mesaj komutu verdiğinde MynAssistant ilgili sohbeti açıp sesli arama ya da gönder düğmesine basmayı dener. Hizmet yalnızca WhatsApp ekranında çalışır; içeriği kaydetmez veya sunucuya göndermez.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            commandContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Erişilebilirlik ayarlarını aç")
                                    }
                                }
                            }
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
                                            Text("Windows uygulaması", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                when {
                                                    !signedIn -> "Önce hesabına giriş yap."
                                                    devicesLoading -> "Hesabına bağlı cihazlar aranıyor..."
                                                    pcDevices.any { it.isOnlineAt(presenceNowMillis) } -> "En az bir cihaz çevrimiçi."
                                                    else -> "Henüz çevrimiçi bilgisayar yok."
                                                },
                                                fontSize = 12.sp,
                                                color = if (pcDevices.any { it.isOnlineAt(presenceNowMillis) }) Color(0xFF65D68B) else MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                            )
                                        }
                                    }
                                    Text(
                                        "Windows yardımcı uygulamasında aynı hesapla giriş yaptığında bilgisayarın burada otomatik görünür. Telefona adres veya güvenlik kodu girmen gerekmez.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f)
                                    )
                                    Text(
                                        "Bilgisayarının görünmesi için Windows yardımcı uygulamasının kurulu ve açık olması gerekir.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    OutlinedButton(
                                        onClick = { navigateToPage(Page.DEVICES) },
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
                                (device?.isOnlineAt(presenceNowMillis) == true && signedIn && device.id != localPcDeviceId)
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
                                            localBridgeSelected -> "Bağlı • Yerel bağlantı"
                                            device == null -> "Bu hesaba bağlı bilgisayar bekleniyor"
                                            device.id == localPcDeviceId -> "Çevrimdışı • Bağlantıyı yeniden test et"
                                            remoteOnline -> "Bağlı • " + device.platform
                                            else -> "Çevrimdışı • Bilgisayar bekleniyor"
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
                                                "Önizleme yalnızca bu sayfa açıkken alınır. Bilgisayarında MynAssistant Windows uygulaması çalışıyor olmalı.",
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
                                    onClick = { navigateToPage(Page.DEVICES) },
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
                                        feedback = "Devam etmek için önce giriş yap."
                                    } else if (localBridgeSelected) {
                                        when (commandName) {
                                            "mute" -> sendLocalPcText("sesi kapa")
                                            "volume_up" -> sendLocalPcText("ses 10 arttir")
                                            "volume_down" -> sendLocalPcText("ses 10 azalt")
                                            "home" -> sendLocalPcText("masaustunu goster")
                                            "power" -> feedback = "Bilgisayarı uzaktan açma özelliği henüz desteklenmiyor."
                                            "mouse_move", "mouse_click", "mouse_right_click" ->
                                                feedback = "Fare kontrolü bu bağlantıda kullanılamıyor. Bilgisayar bağlantısını kontrol edip tekrar dene."
                                            else -> feedback = "Klavye ve fare girişleri bu yerel bağlantı türünde henüz desteklenmiyor."
                                        }
                                    } else if (device == null) {
                                        feedback = "Bilgisayar henüz çevrimiçi değil. Windows uygulaması açıldığında otomatik bağlanacak."
                                    } else if (!remoteOnline) {
                                        feedback = "Bilgisayar çevrimdışı. Bağlantı kurulduğunda komut gönderebilirsin."
                                    } else {
                                        sendTrackedRemoteCommand(device.id, commandName, payload)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
                        }
                        else -> Unit
                    }
                }
            }
                if (page == Page.HOME && !feedback.isNullOrBlank()) {
                    Text(
                        feedback.orEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (page == Page.HOME) {
                    val keyboardController = LocalSoftwareKeyboardController.current
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = if (keyboardVisible) 0.dp else 8.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box {
                                    Column(
                                        modifier = Modifier.width(52.dp).clip(RoundedCornerShape(18.dp))
                                            .clickable { targetMenuExpanded = true }
                                            .padding(vertical = 3.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            when (commandTarget) {
                                                "PHONE" -> Icons.Default.Devices
                                                "PC" -> Icons.Default.Computer
                                                else -> Icons.Default.SmartToy
                                            },
                                            contentDescription = when (commandTarget) {
                                                "PHONE" -> "Hedef: bu Android telefonu"
                                                "PC" -> "Hedef: Windows bilgisayar"
                                                else -> "Hedef: otomatik yönlendirme"
                                            },
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            when (commandTarget) {
                                                "PHONE" -> "Telefon"
                                                "PC" -> "PC"
                                                else -> "Oto"
                                            },
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .75f),
                                            maxLines = 1
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = targetMenuExpanded,
                                        onDismissRequest = { targetMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Otomatik — komuta göre seç") },
                                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                            onClick = { commandTarget = "AUTO"; targetMenuExpanded = false; feedback = null }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Bu telefon (Android)") },
                                            leadingIcon = { Icon(Icons.Default.Devices, contentDescription = null) },
                                            onClick = { commandTarget = "PHONE"; targetMenuExpanded = false; feedback = null }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Bilgisayarım (Windows)") },
                                            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                                            onClick = { commandTarget = "PC"; targetMenuExpanded = false; feedback = null }
                                        )
                                    }
                                }
                                BasicTextField(
                                    value = command,
                                    onValueChange = { command = it; feedback = null },
                                    modifier = Modifier.weight(1f).height(58.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 4,
                                    keyboardOptions = KeyboardOptions.Default,
                                    decorationBox = { innerTextField ->
                                        Box(
                                            Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (command.isBlank()) {
                                                Text(
                                                    "Komut yaz…",
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                                                    fontSize = 16.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                IconButton(
                                    onClick = {
                                        val canRecord = androidx.core.content.ContextCompat.checkSelfPermission(
                                            commandContext, android.Manifest.permission.RECORD_AUDIO
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (canRecord) {
                                            launchSpeechInput()
                                        } else {
                                            startSpeechAfterPermission = true
                                            permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                                        }
                                    },
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Sesli komut",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (command.isNotBlank()) {
                                            keyboardController?.hide()
                                            runAssistantCommand(command)
                                        }
                                    },
                                    enabled = command.isNotBlank(),
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(
                                        if (command.isNotBlank()) Accent else MaterialTheme.colorScheme.outline.copy(alpha = .25f)
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "Komutu gönder",
                                        tint = if (command.isNotBlank()) Color(0xFF10121A) else MaterialTheme.colorScheme.onSurface.copy(alpha = .4f),
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val contactChoiceForDialog = pendingContactChoice
            if (contactChoiceForDialog != null) {
                AlertDialog(
                    onDismissRequest = { pendingContactChoice = null },
                    title = { Text("Hangi kişiyi kastediyorsun?") },
                    text = {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "“" + contactChoiceForDialog.aliasLabel + "” için rehberde birden fazla eşleşme buldum. Doğru kişiye dokun.",
                                fontSize = 14.sp
                            )
                            contactChoiceForDialog.candidates.forEach { candidate ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        chooseContactCandidate(contactChoiceForDialog, candidate)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.AccountCircle, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(candidate.displayName, fontWeight = FontWeight.SemiBold)
                                            Text(candidate.phoneNumber, fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Bu kişiyi seç")
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingContactChoice = null }) { Text("İptal") }
                    }
                )
            }
            val contactConfirmationForDialog = pendingContactConfirmation
            if (contactConfirmationForDialog != null) {
                AlertDialog(
                    onDismissRequest = { pendingContactConfirmation = null },
                    title = { Text("Bu kişiyi hatırlayayım mı?") },
                    text = {
                        Text(
                            "Bundan sonra “" + contactConfirmationForDialog.aliasLabel + "” dediğinde “" +
                                contactConfirmationForDialog.contact.displayName + "” kişisini kullanayım mı?"
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString(contactConfirmationForDialog.aliasKey,
                                    contactConfirmationForDialog.contact.displayName)
                                .putString(contactConfirmationForDialog.aliasKey + "_number",
                                    contactConfirmationForDialog.contact.phoneNumber)
                                .apply()
                            pendingContactConfirmation = null
                            executeResolvedContactAction(
                                contactConfirmationForDialog.command,
                                contactConfirmationForDialog.contact
                            )
                        }) { Text("Evet, hatırla") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            val oneTime = contactConfirmationForDialog
                            pendingContactConfirmation = null
                            executeResolvedContactAction(oneTime.command, oneTime.contact)
                        }) { Text("Sadece bu sefer") }
                    }
                )
            }
            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = { Text("MynAssistant açık kalsın mı?") },
                    text = { Text("Geri tuşuna tekrar basarsan uygulamadan çıkabilirsin. Açık sayfalar arasında geri dönmek için geri tuşunu kullan.") },
                    confirmButton = {
                        TextButton(onClick = { showExitConfirmation = false }) { Text("Uygulamada kal") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showExitConfirmation = false
                            (commandContext as? ComponentActivity)?.finish()
                        }) { Text("Çıkış yap") }
                    }
                )
            }
            if (showAccessibilitySetupDialog) {
                AlertDialog(
                    onDismissRequest = {
                        commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putBoolean("whatsapp_accessibility_setup_seen", true).apply()
                        showAccessibilitySetupDialog = false
                    },
                    title = { Text("WhatsApp otomasyonunu etkinleştir") },
                    text = {
                        Text(
                            "WhatsApp'ta babanı aramak veya mesaj göndermek için MynAssistant'a bir kez Erişilebilirlik izni vermelisin. Android bu özel izni ayrı ayardan onaylatır."
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putBoolean("whatsapp_accessibility_setup_seen", true).apply()
                            showAccessibilitySetupDialog = false
                            commandContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("İzin ayarlarını aç") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            commandContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putBoolean("whatsapp_accessibility_setup_seen", true).apply()
                            showAccessibilitySetupDialog = false
                        }) { Text("Daha sonra") }
                    }
                )
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
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
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
                                    Text(if (signedIn) name.ifBlank { email.substringBefore("@") } else "MynAssistant", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurface)
                                    Text(if (signedIn) email.ifBlank { phone } else "Giriş yap veya kayıt ol", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f), maxLines = 1)
                                }
                                Text(if (accountListExpanded) "▲" else "▼", color = MaterialTheme.colorScheme.primary)
                            }
                            androidx.compose.animation.AnimatedVisibility(visible = accountListExpanded) {
                                Column(
                                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 8.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .98f))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .10f), RoundedCornerShape(16.dp))
                                        .padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val otherGoogleAccounts = savedGoogleAccounts.filterNot { saved ->
                                        signedIn && saved.email.equals(email, ignoreCase = true)
                                    }
                                    if (otherGoogleAccounts.isEmpty()) {
                                        Text(
                                            if (signedIn) "Başka kayıtlı hesap yok." else "Henüz kaydedilmiş Google hesabı yok.",
                                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
                                        )
                                    } else {
                                        otherGoogleAccounts.forEach { saved ->
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
                                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
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
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) || status.contains("giriş yapıldı", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        DrawerItem("Ana sayfa") { navigateToPage(Page.HOME) }
                        DrawerItem(if (signedIn) "Hesabım" else "Giriş yap") { navigateToPage(Page.LOGIN); drawer = false }
                        if (!signedIn) DrawerItem("Kayıt ol") { navigateToPage(Page.REGISTER); drawer = false }
                        DrawerItem("Cihazlarım") { navigateToPage(Page.DEVICES); drawer = false }
                        DrawerItem("Ayarlar") { navigateToPage(Page.SETTINGS); drawer = false }
                        DrawerItem("PC Kumandası") { navigateToPage(Page.REMOTE); drawer = false }
                        DrawerItem("PcFiles") { navigateToPage(Page.FILES) }
                        Spacer(Modifier.weight(1f))
                        if (signedIn) OutlinedButton(onClick = { logout(); navigateToPage(Page.HOME) }, Modifier.fillMaxWidth()) { Text("Çıkış yap") }
                        Text("MynAssistant • Android 0.4.4", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
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
        "PcFiles" -> Icons.Default.FolderOpen
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
                                        status.contains("başarılı", ignoreCase = true) || status.contains("oluşturuldu", ignoreCase = true) || status.contains("giriş yapıldı", ignoreCase = true) -> Color(0xFF65D68B)
                                        status.contains("başarısız", ignoreCase = true) || status.contains("hata", ignoreCase = true) || status.contains("kaydedilemedi", ignoreCase = true) -> Color(0xFFFF7D85)
                                        else -> MaterialTheme.colorScheme.secondary
                                    }, fontSize = 12.sp) }
            TextButton(onClick = goRegister, Modifier.fillMaxWidth()) { Text(if (registerMode) "Zaten hesabın var mı? Giriş yap" else "Hesabın yok mu? Kayıt ol") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
            Text("Hesabını tamamlamak için e-posta kutuna gönderilen doğrulama bağlantısını aç.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
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
        Triple("...", Icons.Default.MoreHoriz, "placeholder"),
        Triple("Sesi artır", Icons.Default.VolumeUp, "volume_up"),
        // The Home control uses a real home icon and sends the desktop Home command.
        Triple("Ana sayfa", Icons.Default.Home, "home"),
        Triple("Sesi kıs", Icons.Default.VolumeDown, "volume_down")
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
        var touchpadEnabled by remember { mutableStateOf(true) }
        var keyboardText by remember { mutableStateOf("") }

        fun submitRemoteKeyboardText() {
            if (!mouseEnabled) return
            val text = keyboardText
            if (text.isBlank()) {
                onCommand("key_enter", emptyMap())
            } else {
                onCommand("type_text", mapOf("text" to text, "pressEnter" to true))
                keyboardText = ""
            }
        }

        val touchpadGestureModifier = if (mouseEnabled && touchpadEnabled) {
            Modifier
                .pointerInput(mouseEnabled, touchpadEnabled) {
                    var deltaX = 0f
                    var deltaY = 0f
                    detectDragGestures(
                        onDragStart = { deltaX = 0f; deltaY = 0f },
                        onDragEnd = {
                            if (deltaX != 0f || deltaY != 0f) {
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
                .pointerInput(mouseEnabled, touchpadEnabled) {
                    detectTapGestures(
                        onTap = { onCommand("mouse_click", emptyMap()) },
                        onLongPress = { onCommand("mouse_right_click", emptyMap()) }
                    )
                }
        } else {
            Modifier
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dokunmatik alan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        when {
                            !mouseEnabled -> "Bilgisayar bağlantısı bekleniyor"
                            touchpadEnabled -> "Kaydır: fare • Dokun: tık"
                            else -> "Dokunmatik kapalı • Sayfayı kaydırabilirsin"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (touchpadEnabled && mouseEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    IconButton(
                        onClick = { touchpadEnabled = !touchpadEnabled },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            if (touchpadEnabled) Icons.Default.Touchpad else Icons.Default.TouchpadOff,
                            contentDescription = if (touchpadEnabled) "Dokunmatiği kapat" else "Dokunmatiği aç",
                            tint = if (touchpadEnabled && mouseEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .then(if (mouseEnabled && touchpadEnabled) touchpadGestureModifier else Modifier)
                    .then(if (!touchpadEnabled) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color(0xFF3D3D3D))
                    val spacing = 29.dp.toPx()
                    var y = spacing * .65f
                    var rowIndex = 0
                    while (y < size.height) {
                        val stagger = (rowIndex % 2) * spacing * .5f
                        var x = spacing * .35f + stagger
                        while (x < size.width) {
                            val diagonal = kotlin.math.sin((x / spacing + rowIndex * .7f) * .32f)
                            val radius = (1.2f + (diagonal + 1f) * .75f).dp.toPx()
                            val alpha = (0.12f + (diagonal + 1f) * .055f).coerceIn(.1f, .24f)
                            drawCircle(
                                color = Color(0xFFD4D4D4).copy(alpha = alpha),
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(x, y)
                            )
                            x += spacing
                        }
                        y += spacing
                        rowIndex++
                    }
                }
                if (!mouseEnabled || !touchpadEnabled) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (touchpadEnabled) Icons.Default.SettingsRemote else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = Color.White.copy(alpha = .82f)
                            )
                            Text(
                                if (!mouseEnabled) {
                                    if (enabled) "Fare kontrolü bu bağlantıda desteklenmiyor" else "Bilgisayar bağlantısı bekleniyor"
                                } else "Dokunmatik kapalı",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                if (!mouseEnabled) "Windows Agent bağlantısı kurulduğunda fare kontrolü açılır."
                                else "Bu alandaki dokunuşlar artık fareye gönderilmez; sayfayı kaydırabilirsin.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = .78f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SettingsRemote, contentDescription = null, modifier = Modifier.size(30.dp), tint = Color.White.copy(alpha = .85f))
                        Text(
                            "Sürükle: fareyi hareket ettir • Dokun: sol tık • Basılı tut: sağ tık",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = .85f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OutlinedTextField(
                    value = keyboardText,
                    onValueChange = { keyboardText = it.take(1000) },
                    modifier = Modifier.weight(1f),
                    enabled = mouseEnabled,
                    singleLine = true,
                    label = { Text("Metin") },
                    placeholder = { Text("Yaz…") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitRemoteKeyboardText() })
                )
                FilledTonalButton(
                    onClick = { keyboardText = keyboardText + " " },
                    enabled = mouseEnabled,
                    modifier = Modifier.height(54.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text("Boşluk")
                }
                FilledTonalIconButton(
                    onClick = {
                        if (keyboardText.isNotEmpty()) {
                            keyboardText = keyboardText.dropLast(1)
                        } else {
                            onCommand("key_backspace", emptyMap())
                        }
                    },
                    enabled = mouseEnabled,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Yazıdan bir karakter sil; alan boşsa bilgisayarda Backspace")
                }
                FilledIconButton(
                    onClick = { submitRemoteKeyboardText() },
                    enabled = mouseEnabled,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(Icons.Default.KeyboardReturn, contentDescription = "Metni yaz ve Enter gönder")
                }
            }
            Text(
                if (mouseEnabled) "Boşluk metne boşluk ekler. Silme önce yazı alanındaki son karakteri siler; alan boşsa bilgisayarda Backspace gönderir. Enter metni gönderir."
                else "Klavye girişi Windows Agent bağlantısı aktifken kullanılabilir.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
            )
        }
    }
}
