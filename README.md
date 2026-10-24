# Genius Launcher

Android TV / Google TV için reklamsız, sade bir ana ekran (launcher) ve evdeki akıllı cihazları tek yerden yöneten bir hub.

> Durum: erken geliştirme. İlk hedef Xiaomi Mi Box S (2. nesil, Android 14).

## Neler var

- **Vitrin:** Odaktaki uygulamanın simgesinden çıkarılan renkle boyanan büyük bir karşılama alanı; yanında hava durumu (Open-Meteo, şehir seçilebilir, saatlik ve 7 günlük panel).
- **Uygulamalar:** Kumandada OK'a basılı tutunca açılan menüden sıralama, gizleme ve renk seçme.
- **Evim widget'ı:** Tuya (röle, mama kabı) ve Dyson cihazları buluta gitmeden, yerel ağdan. Kartlar sıralanabilir ve yeniden adlandırılabilir; dördü aşkın cihaz "Diğer" panelinde.
- **Üst şerit:** Saat, Wi-Fi/Bluetooth/ayar kısayolları, RAM kullanımı ve "Boşalt" düğmesi (TV'nin kendi ADB'siyle).
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
saklanmaz; cihaz yeniden kurulursa paneldeki **Yeniden bağlan** anahtarını yeniler. Dyson için TV'den ekleme
sonraki sürümde; o zamana kadar Mac'teki eski yol:

Anahtarlar bir kez Mac'ten alınır ve TV'ye gönderilir (Python 3, `pip install -r tools/smarthome/requirements.txt`):

```bash
python tools/smarthome/tuya_keys.py    # Tuya Smart / Smart Life uygulamasıyla QR okutarak
python tools/smarthome/dyson_keys.py   # MyDyson hesabıyla (e-postaya kod gelir)
python tools/smarthome/push_to_tv.py   # TV'ye gönderir; launcher açılınca içeri alır
```

## Ana ekran (Home tuşu)

Google TV kendi ana ekranı açıkken başka launcher'ları yok sayar. Bu betik önce kurulum sihirbazını, sonra Google TV ana ekranını kapatır (kaldırmaz) ve Home tuşunu Genius Launcher'a bağlar; bağlı olunan Wi-Fi ağını kaydeden uygulama kapatılacaksa hiçbir şey yapmaz:

```bash
zsh tools/mibox/ana_ekran.sh genius   # Genius Launcher
zsh tools/mibox/ana_ekran.sh google   # Google TV'ye geri dön
```

Launcher 5 dakikada 3 kez çökerse Google TV ana ekranını kendiliğinden geri açar. Ekran koruyucu Google'ınki olarak kalır; Google TV'deki gibi ana ekranda Geri tuşuna art arda iki kez basınca hemen başlar.

## Gizli bilgiler

Bu repoda **hiçbir cihaz anahtarı, hesap bilgisi ya da IP adresi bulunmaz.** Tuya `local_key`, Dyson MQTT şifresi gibi bilgiler çalışma zamanında cihazda (uygulamanın özel depolamasında) saklanır. `secrets/`, `devices.json` ve `*.secrets.json` `.gitignore` içindedir.

## Teşekkür

Google TV'de ana ekranı değiştirme yaklaşımı için [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT) projesinden ilham alındı.

## Lisans

Henüz seçilmedi.
