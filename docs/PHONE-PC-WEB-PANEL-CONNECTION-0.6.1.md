# Telefon, Windows ve web paneli — bağlantı kontrolü (Android 0.6.1)

## Tek bir bulut bağlantısı

- Android uygulaması ve Windows Desktop Bridge aynı Firebase Authentication kullanıcısının UID'sine bağlanır. Telefon-PC ilk eşleştirmesi Windows'ta oluşturulan tek kullanımlık kodun Android uygulamasında açıkça onaylanmasıyla yapılır; Windows'ta ayrıca Google OAuth tarayıcısı açılması beklenmez.
- Windows Agent, `myncloud-24` içindeki `devices` belgesini `connectionMode=firebase-agent`, `ownerId`, `status=online` ve `lastSeenAt` alanlarıyla günceller.
- Android cihaz listesini `ownerId` ile filtreleyerek dinler; komutlar aynı UID ve belirli Windows cihazı için `commands` kuyruğuna yazılır.
- Render web paneli Android'den bağımsız bir doğrudan kontrol protokolü değil; yönetici oturumuyla aynı Firestore kayıtlarını okur ve yalnızca çevrimiçi `firebase-agent` cihazları için izin listesindeki komutları kuyruğa alır.

## İlk kez bağlama

**Ön koşul:** Render API'sinde `/api/v1/desktop/pairing/start`, `/api/v1/desktop/pairing/approve` ve `/api/v1/desktop/pairing/poll` uç noktalarının yayımlanmış ve Firebase ayarlarının tamamlanmış olması gerekir. `/api/v1/info` yanıtındaki `desktopPairingConfigured` alanı `true` olmalıdır. 2026-10-26 tarihinde sunucu eşleştirme PR'ı hâlâ taslaktı ve canlı Render yapılandırması doğrulanmamıştı; bu koşul yayın öncesi yeniden denetlenmelidir.

1. Windows MynAssistant 0.1.5 uygulamasını aç. **Ayarlar → Hesap → Telefon-PC bağlantısı** bölümünde **Telefonla eşleştir** düğmesine bas.
2. Windows'ta gösterilen 10 karakterli kodu al. Kod beş dakika geçerlidir; eşleştirme penceresini açık bırak.
3. Android MynAssistant 0.6.1 uygulamasında bağlamak istediğin Firebase hesabıyla oturum aç. **Ayarlar → Windows bilgisayarını eşleştir** bölümüne Windows kodunu girip **Bilgisayarı bağla** düğmesine bas.
4. Windows uygulaması onayı aldıktan sonra güvenli oturumu yerel Windows kullanıcısının DPAPI korumalı oturum dosyasına kaydeder ve Desktop Bridge'i başlatır. Windows Ayarlar ekranında yalnızca süreç çalışıyor bilgisini değil, bağlantı/heartbeat durumunu da kontrol et.
5. Android'deki **Cihazlarım / PC Kumandası** bölümünde çevrimiçi Windows cihazını doğrula. Web paneli `/admin/` → **Cihazlar** bölümünde de aynı Firestore kaydı görünmelidir.

### Sunucu hazır değilse

- HTTP 404/405: eşleştirme uç noktaları canlı sunucu sürümünde yayımlanmamış olabilir.
- HTTP 503: eşleştirme Firebase yapılandırması eksik veya geçersiz olabilir.
- Render yapılandırmasında `FIREBASE_WEB_API_KEY`, `FIREBASE_PROJECT_ID` ve `FIREBASE_SERVICE_ACCOUNT_JSON` gerekir. Bunlar sunucuda kalmalı; servis hesabı JSON'u Android APK'sına, Windows kurulum paketine veya herkese açık depoya konmamalıdır.
- Hata durumunda kodu tekrar tekrar denemek yerine API dağıtımını ve Render yapılandırmasını kontrol et.

## 0.6.1 seçici düzeltmesi

Eski Android sürümleri aynı hesap için bir `windows-{uid}` belgesi (eski yerel ağ bağlantısı) ile yeni rastgele kimlikli `firebase-agent` belgesini birlikte görebiliyordu. Seçici, Firestore liste sırasına bağlı kaldığında eski/çevrimdışı LAN kaydını seçebiliyor ve bulut komutları yanlış hedefte kalabiliyordu. 0.6.1 yeni çevrimiçi `firebase-agent` kaydını önceliklendirir; çevrimiçi olarak seçilen uygun bir bulut cihazını kullanıcı seçimi varsa korur.

## Güvenlik sınırları

- Cihazların aynı Firebase UID'sine ait olması gerekir; yalnızca kodu girmek telefonun oturum anahtarlarını Windows'a kopyalamaz. Windows oturumu sunucu tarafından, telefon hesabının açık onayından sonra oluşturulur.
- Web panelinin ham terminal/PowerShell komutları değil, sunucudaki allowlist komutları kullanılır.
- Ekran karesi yalnızca PC Kumandası/önizleme isteği süreyle yenilendiği sırada gönderilir.
- Windows Agent çalışmıyorsa cihaz heartbeat'i eskir ve uzak komut seçimi reddedilmelidir.
