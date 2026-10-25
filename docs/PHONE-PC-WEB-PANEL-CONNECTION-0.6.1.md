# Telefon, Windows ve web paneli — bağlantı kontrolü (Android 0.6.1)

## Tek bir bulut bağlantısı

- Android uygulaması ve Windows Desktop Bridge aynı Google hesabıyla Firebase Authentication üzerinden aynı UID'ye bağlanır.
- Windows Agent, `myncloud-24` içindeki `devices` belgesini `connectionMode=firebase-agent`, `ownerId`, `status=online` ve `lastSeenAt` alanlarıyla günceller.
- Android cihaz listesini `ownerId` ile filtreleyerek dinler; komutlar aynı UID ve belirli Windows cihazı için `commands` kuyruğuna yazılır.
- Render web paneli Android'den bağımsız bir doğrudan kontrol protokolü değil; yönetici oturumuyla aynı Firestore kayıtlarını okur ve yalnızca çevrimiçi `firebase-agent` cihazları için izin listesindeki komutları kuyruğa alır.

## İlk kez bağlama

1. Windows MynAssistant 0.1.4 uygulamasını açıp Telefon-PC bağlantısı onayını kabul et.
2. Açılan tarayıcıda Android uygulamasında kullandığın aynı Google hesabını seç.
3. Windows Ayarlar → Telefon-PC bağlantısı bölümünde durumun yalnızca süreç çalışıyor değil, son Firestore heartbeat'i de doğruladığını kontrol et.
4. Android 0.6.1'de Cihazlarım / PC Kumandası bölümünü aç. Windows cihazı `firebase-agent` bağlantı türüyle görünmeli.
5. Web paneli `/admin/` → Cihazlar bölümünde de aynı kayıt görünmeli. Web panelinin Firebase veri durumu bağlı değilse Render `FIREBASE_SERVICE_ACCOUNT_JSON` ve gerekli Firestore izinleri denetlenmeli.

## 0.6.1 seçici düzeltmesi

Eski Android sürümleri aynı hesap için bir `windows-{uid}` belgesi (eski yerel ağ bağlantısı) ile yeni rastgele kimlikli `firebase-agent` belgesini birlikte görebiliyordu. Seçici, Firestore liste sırasına bağlı kaldığında eski/çevrimdışı LAN kaydını seçebiliyor ve bulut komutları yanlış hedefte kalabiliyordu. 0.6.1 yeni çevrimiçi `firebase-agent` kaydını önceliklendirir; çevrimiçi olarak seçilen uygun bir bulut cihazını kullanıcı seçimi varsa korur.

## Güvenlik sınırları

- Cihazların aynı Firebase UID'sine ait olması gerekir.
- Web panelinin ham terminal/PowerShell komutları değil, sunucudaki allowlist komutları kullanılır.
- Ekran karesi yalnızca PC Kumandası/önizleme isteği süreyle yenilendiği sırada gönderilir.
- Windows Agent çalışmıyorsa cihaz heartbeat'i eskir ve uzak komut seçimi reddedilmelidir.
