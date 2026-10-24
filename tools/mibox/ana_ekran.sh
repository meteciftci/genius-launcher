#!/bin/zsh
# Home tuşunu Genius Launcher'a ya da Google TV'ye bağlar.
#   zsh tools/mibox/ana_ekran.sh genius   Genius Launcher ana ekran olur
#   zsh tools/mibox/ana_ekran.sh google   Google TV ana ekranı geri gelir
#
# Google TV kendi ana ekranı açıkken başka launcher'ları yok sayar; bu yüzden kapatılır (pm disable-user,
# kaldırılmaz). Sıra Free My TV'den: önce kurulum sihirbazı, sonra ana ekran. Tersinde sihirbazın kurtarma
# ekranı Home'u alır ve orada yeniden başlatmak bir Xiaomi'yi fabrika ayarlarına döndürmüş.
# Android kapatılan uygulamanın kaydettiği Wi-Fi ağlarını siler; bağlı olunan ağ onlardansa hiçbir şey yapılmaz.
source ${0:A:h}/../cihaz.sh
adb connect $DEV >/dev/null

GENIUS=dev.evim.tv/.MainActivity
GOOGLE_HOME=com.google.android.apps.tv.launcherx/.home.HomeActivity
GOOGLE_PKG=com.google.android.apps.tv.launcherx
SETUP_PKG=com.google.android.tungsten.setupwraith

sh() { adb -s $DEV shell "$@" | tr -d '\r' }

case $1 in
  genius)
    # Bağlı olunan ağı kaydeden uygulama kapatılırsa TV internetsiz kalır
    current=$(sh "dumpsys wifi | grep -A0 'mostRecentlyConnected=true' | grep -o 'cname=[^ ]*' | head -1")
    for p in $SETUP_PKG $GOOGLE_PKG; do
      if [[ $current == "cname=$p" ]]; then
        print -u2 "Bağlı olunan Wi-Fi ağını $p kaydetmiş; kapatılırsa ağ silinir. Ağı unutup ayarlardan yeniden bağlanın."
        exit 1
      fi
    done
    for p in $SETUP_PKG $GOOGLE_PKG; do
      owned=$(sh "dumpsys wifi | grep -c 'cname=$p '")
      # Döküm ağ listesini birkaç kez yazdığı için sayı değil, var/yok bakılır
      (( owned > 0 )) && print "Uyarı: $p uygulamasının kaydettiği Wi-Fi ağları silinecek (bağlı olunan ağ değil)."
    done
    sh cmd package set-home-activity $GENIUS
    print -n "Kurulum sihirbazı: "; sh pm disable-user --user 0 $SETUP_PKG
    print -n "Google TV ana ekranı: "; sh pm disable-user --user 0 $GOOGLE_PKG
    sh cmd package set-home-activity $GENIUS
    ;;
  google)
    # Önce ana ekran açılır ki sihirbazın kurtarma ekranı Home'u almasın
    print -n "Google TV ana ekranı: "; sh pm enable $GOOGLE_PKG
    sh cmd package set-home-activity $GOOGLE_HOME
    print -n "Kurulum sihirbazı: "; sh pm enable $SETUP_PKG
    ;;
  *)
    print -u2 "Kullanım: zsh tools/mibox/ana_ekran.sh genius|google"
    exit 1
    ;;
esac

print -n "Home tuşu şimdi: "
sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1
