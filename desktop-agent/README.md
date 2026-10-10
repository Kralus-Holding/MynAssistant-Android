# MynAssistant Windows Agent (ilk sürüm)

Bu, Android MynAssistant uygulamasından ayrı çalışan yeni bir Windows yardımcı ajanıdır. Mevcut MynAi/Python projesinin dosyalarını değiştirmez.

## Bu sürüm ne yapar?

- İlk çalıştırmada Google hesabıyla tarayıcı üzerinden giriş yapar.
- Firebase Authentication üzerinden aynı kullanıcı hesabına bağlanır.
- Windows cihazını Firestore içindeki devices koleksiyonuna otomatik kaydeder.
- Yaklaşık 25 saniyede bir çevrimiçi durumunu günceller.
- Android uygulamasındaki PC Kumandası açıkken yaklaşık 1–2 saniyede bir küçültülmüş JPEG masaüstü karesi gönderir. Önizleme isteği süreli olduğundan sayfa kapandığında veya istek yenilenmediğinde kare gönderimi durur.
- Aynı hesaba ve bu cihaza ait commands kuyruğunu okur.
- Ses aç/kapat, ses artırma/azaltma, masaüstünü gösterme, fare hareketi ve tıklama komutlarını işler.
- Oturum yenileme anahtarını Windows DPAPI ile şifreleyerek saklar.

Telefon uygulamasına IP adresi veya token girilmez. Android uygulaması, Google hesabının UID'sine ait Firestore cihazlarını anlık dinler.

## Bir defalık Windows hazırlığı

Google OAuth kimlik bilgisi bu depoya eklenmez.

1. Google Cloud Console'da MynCloud projesinin OAuth istemcileri bölümünde türü Desktop app / Masaüstü uygulaması olan istemciyi kullan. Böyle bir istemci yoksa oluştur.
2. Bu istemcinin JSON dosyasını indir.
3. İndirdiğin dosyanın adını client_secret.json yap ve MynAssistantAgent.exe dosyasının yanına koy.
4. MynAssistantAgent.exe dosyasını çalıştır ve Android uygulamasında kullandığın aynı Google hesabıyla tarayıcıda giriş yap.
5. Agent başarılı olunca bu bilgisayar Android uygulamasındaki Cihazlarım ekranında otomatik görünür. İlk çalıştırmada Windows açılışında otomatik başlamayı seçebilirsin.

client_secret.json dosyasını GitHub'a yükleme. Bu dosya .gitignore ile dışarıda bırakılır.

## Geliştirme sırasında

    py -m pip install -r requirements.txt
    py agent.py

EXE paketi GitHub Actions'ta windows-latest üzerinde oluşturulur. Artifact ZIP'ine OAuth JSON'u eklenmez.

## Önemli sınırlar

Bu ilk sürüm otomatik cihaz keşfi, çevrimiçi durumu, temel komut/fare kontrolü ve düşük kare hızlı masaüstü önizlemesi sağlar. Önizleme Chrome Uzaktan Masaüstü gibi akıcı bir video oturumu değildir; yaklaşık 1–2 saniyede bir JPEG kare gönderir. Sonraki aşamada WebRTC tabanlı akıcı ekran aktarımı ve güvenli bağlantı pazarlığı gerekir. Bilgisayarın görünmesi için Windows Agent'ın çalışması ve aynı Firebase hesabına kaydolması gerekir.
