# Genius Launcher

Android TV / Google TV için reklamsız, sade bir ana ekran (launcher) ve evdeki akıllı cihazları tek yerden yöneten bir hub.

> Durum: erken geliştirme. İlk hedef Xiaomi Mi Box S (2. nesil, Android 14).

## Neler var

- **Vitrin:** Odaktaki uygulamanın simgesinden çıkarılan renkle boyanan büyük bir karşılama alanı.
- **Uygulamalar:** Kumandada OK'a basılı tutunca açılan menüden sıralama, gizleme ve renk seçme.
- **Üst şerit:** Saat, tarih, hava durumu (Open-Meteo, varsayılan şehir Muğla), RAM kullanımı ve "Boşalt" düğmesi.
- **Evim şeridi:** Akıllı cihazlar (Tuya, Dyson, eufy…). Şimdilik yer tutucu; protokoller sonraki aşamada eklenecek.

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

TV'nin adb adresini `tools/.tv` dosyasına yazın (bu dosya da repoya girmez):

```bash
echo 192.168.1.50:5555 > tools/.tv
```

Release derleyip TV'ye yükleyin. Betik uygulamayı cihazda önceden derler (ilk açılışlardaki takılmayı önler) ve açar:

```bash
zsh tools/install.sh
```

Akıcılığı ölçmek için `zsh tools/perf.sh`. Mi Box temizliği ve uyku ayarları için yardımcı betikler `tools/mibox/` altında.

## Gizli bilgiler

Bu repoda **hiçbir cihaz anahtarı, hesap bilgisi ya da IP adresi bulunmaz.** Tuya `local_key`, Dyson MQTT şifresi gibi bilgiler çalışma zamanında cihazda (uygulamanın özel depolamasında) saklanır. `secrets/`, `devices.json` ve `*.secrets.json` `.gitignore` içindedir.

## Teşekkür

Google TV'de ana ekranı değiştirme yaklaşımı için [Free My TV](https://github.com/simbasjungles/free-my-tv) (MIT) projesinden ilham alındı.

## Lisans

Henüz seçilmedi.
