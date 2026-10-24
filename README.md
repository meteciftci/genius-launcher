# Genius Launcher

Android TV / Google TV için reklamsız, sade bir ana ekran (launcher) ve evdeki akıllı cihazları tek yerden yöneten bir hub.

> Durum: erken geliştirme. İlk hedef Xiaomi Mi Box S (2. nesil, Android 14).

## Neler var

- **Vitrin:** Odaktaki uygulamanın simgesinden çıkarılan renkle boyanan büyük bir karşılama alanı.
- **Uygulamalar:** Kumandada OK'a basılı tutunca açılan menüden sıralama, gizleme ve renk seçme. Yeni yüklenen uygulamaların kartı kendiliğinden gelir.
- **Evim widget'ı:** Tuya (priz, röle, mama kabı), Dyson hava temizleyiciler ve Eufy kameralar, buluta gitmeden yerel ağdan. Kartlar sıralanabilir ve yeniden adlandırılabilir; kaç kart görüneceği ayarlardan seçilir (3–6), fazlası "Diğer" panelinde.
- **Kamera:** Eufy kameranın karta basınca tam ekran canlı görüntüsü (RTSP, 1080p, sesli). Pan & Tilt kameralar yön tuşlarıyla döndürülür (yerel P2P, hesap gerekmez).
- **Kurulum sihirbazı:** Tam ekran, görselli adımlarla ADB izni, Home tuşu, gereksiz uygulama temizliği ve "uyanık tut"; hepsi geri alınabilir.
- **Üst şerit:** Saat ve hava durumu (Open-Meteo, şehir seçilebilir, saatlik ve 7 günlük panel), Evim kutusu, Wi-Fi/Bluetooth/ayar kısayolları, Genius ayarları, RAM kullanımı ve "Boşalt" düğmesi (TV'nin kendi ADB'siyle).
- **Dil:** Sistem dilini izler; İngilizce ve Türkçe.

## Derleme

Gerekenler: JDK 17 ve Android SDK (platform 35, build-tools 35).

```bash
brew install openjdk@17
brew install --cask android-commandlinetools android-platform-tools
sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

`local.properties` dosyasına SDK yolunu yazın (bu dosya repoya girmez):

```
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

Tuya cihazlarını TV'den eklemek için QR girişinin istemci kimliğini de aynı dosyaya yazın. Home Assistant'ın
Tuya entegrasyonunun herkese açık kimliği kullanılabilir; yoksa panelde Tuya seçeneği çalışmaz:

```
tuya.clientId=HA_3y9q4ak7g4ephrvke
tuya.schema=haauthorize
```

TV'nin adb adresini `tools/.tv` dosyasına yazın (bu dosya da repoya girmez):

```bash
echo 192.168.1.50:5555 > tools/.tv
```

Release derleyip TV'ye yükleyin. Betik uygulamayı cihazda önceden derler (ilk açılışlardaki takılmayı önler) ve açar:

```bash
zsh tools/install.sh
```

Akıcılığı ölçmek için `zsh tools/perf.sh`. Mi Box temizliği ve uyku ayarları için yardımcı betikler `tools/mibox/` altında.

## Akıllı ev kurulumu

Ana ekranın üstündeki **Evim** kutusuna basın → **Cihaz ekle** → **Tuya · Smart Life**. Uygulamadaki kullanıcı
kodunu yazıp TV'de çıkan QR kodu Smart Life uygulamasıyla okutun; hesaptaki cihazlardan eklemek istediklerinizi
seçin. TV cihazları ev ağında kendisi bulur, adresleri değişirse yeniden arar. Hesap şifresi ya da bulut jetonu
saklanmaz; cihaz yeniden kurulursa paneldeki **Yeniden bağlan** anahtarını yeniler.

Dyson için **Cihaz ekle** → **Dyson**: MyDyson hesabıyla (e-postaya gelen kod ve şifre) ya da hesap olmadan
cihazın üstündeki Wi-Fi etiketinden. TV hava temizleyiciyi ev ağında mDNS ile bulur.

Eufy kamera için önce Eufy uygulamasında **Kamera Ayarları → Genel → Depolama → NAS (RTSP)**'yi açıp bir kullanıcı
adı ve parola belirleyin. Sonra **Cihaz ekle** → **Kamera**: kullanıcı adı ve parolayı yazın; TV kamerayı ev ağında
Eufy'nin P2P yerel aramasıyla kendisi bulur, bulamazsa IP adresini sorar. Adresi değişen kamera da yeniden bulunur.

### Bilinen sınırlar

- Tuya: yerel protokolün 3.3 ve 3.4 sürümleri desteklenir; 3.5 henüz desteklenmez. Tam kontrol yalnızca denenmiş
  tiplerde (priz/röle, Xenon X8605 mama kabı); bilinmeyen cihazlarda aç/kapa ya da yalnızca durum gösterilir.
- Dyson: ısıtmalı hava temizleyici (527K) ile denendi; diğer modellerde bazı değerler eksik görünebilir.
- Eufy: yalnızca RTSP veren kameralar; döndürme Indoor Cam Pan & Tilt (T8410) ile denendi. RTSP açıkken Eufy görüntüyü 1080p'ye düşürür.

## İlk kurulum sihirbazı

Launcher ilk açıldığında bir sihirbaz çıkar; her adım atlanabilir ve Genius ayarlarından yeniden çalıştırılabilir:

1. **TV'nin kendi ADB'si:** USB hata ayıklamanın nasıl açılacağını anlatır ve TV'nin izin penceresini çıkarır.
   Home tuşu, uygulama kaldırma ve bellek boşaltma bununla yapılır; bağlantı TV'nin içinde kalır.
2. **Home tuşu:** Genius Launcher'ı ana ekran yapar (aşağıya bakın).
3. **Temizlik:** Markaya göre bilinen gereksiz uygulamaları listeler (önce Xiaomi Mi Box); önerilenler seçili gelir.
   Uygulamalar silinmez, kullanıcı için kaldırılır. Bağlı olunan Wi-Fi ağını kaydetmiş bir uygulama seçilemez.
4. **Uyanık tut (isteğe bağlı):** TV kapanınca kutu uyumaz, ev ağında kalır.

Sihirbaz neyi değiştirdiğini (ayarların eski değerleriyle) kaydeder. Genius ayarlarındaki **Sihirbazın
değişikliklerini geri al** yalnızca bunları eski hâline getirir; **Kaldır** da önce bunları geri getirmeyi sorar.

## Ana ekran (Home tuşu)

Google TV kendi ana ekranı açıkken başka launcher'ları yok sayar. Bu yüzden önce Google TV'nin kurulum sihirbazı, sonra
ana ekranı kapatılır (kaldırılmaz) ve Home tuşu Genius Launcher'a bağlanır; bağlı olunan Wi-Fi ağını kaydeden uygulama
kapatılacaksa hiçbir şey yapılmaz. Bunu kurulum sihirbazı ya da Genius ayarları yapar; geliştirme için aynısı Mac'ten:

```bash
zsh tools/mibox/ana_ekran.sh genius   # Genius Launcher
zsh tools/mibox/ana_ekran.sh google   # Google TV'ye geri dön
```

Launcher 5 dakikada 3 kez çökerse Google TV ana ekranını kendiliğinden geri açar. Ekran koruyucu Google'ınki olarak kalır; Google TV'deki gibi ana ekranda Geri tuşuna art arda iki kez basınca hemen başlar.

## Gizli bilgiler

Bu repoda **hiçbir cihaz anahtarı, hesap bilgisi ya da IP adresi bulunmaz.** Tuya `local_key`, Dyson MQTT şifresi ve Eufy RTSP parolası gibi bilgiler çalışma zamanında TV'de, uygulamanın özel depolamasında saklanır. Tuya, Dyson ve Eufy hesaplarının şifreleri ve bulut jetonları hiç saklanmaz; yalnızca giriş sırasında kullanılır. `secrets/`, `devices.json`, `*.secrets.json` ve `local.properties` `.gitignore` içindedir.

## Teşekkür

- Google TV'de ana ekranı değiştirme yaklaşımı için [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT) projesinden ilham alındı.
- Tuya QR girişi [tuya-device-sharing-sdk](https://github.com/tuya/tuya-device-sharing-sdk)'nın, Dyson bulut girişi
  [libdyson-neon](https://github.com/libdyson-wg/libdyson-neon)'un Kotlin'e taşınmış hâlidir.
- Eufy P2P yerel arama ve döndürme komutu [eufy-security-client](https://github.com/bropat/eufy-security-client)'tan (MIT) alındı.
- Kullanılan kütüphaneler: [dadb](https://github.com/mobile-dev-inc/dadb), [ZXing](https://github.com/zxing/zxing),
  [Media3](https://github.com/androidx/media) (hepsi Apache-2.0).

## Lisans

[MIT](LICENSE)
