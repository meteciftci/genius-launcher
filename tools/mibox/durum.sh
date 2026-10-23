#!/bin/zsh
# Mi Box paket durumu: hiçbir şeyi değiştirmez, yalnızca okur.
source ${0:A:h}/../cihaz.sh
adb connect $DEV >/dev/null

typeset -A ACIKLAMA
ACIKLAMA=(
  com.google.android.youtube.tvmusic   "YouTube Music"
  com.amazon.amazonvideo.livingroom    "Amazon Prime Video"
  com.netflix.ninja                    "Netflix"
  com.netflix.tokenmanager             "Netflix yardimci servis"
  com.wbd.stream                       "Max (HBO) - sizin yuklediginiz"
  com.xiaomi.statistic                 "Xiaomi istatistik/telemetri"
  com.miui.tv.analytics                "Xiaomi analitik"
  com.xm.webcontent                    "Xiaomi web icerik/reklam"
  com.mitv.tvhome.oemtab               "Ana ekrandaki Mi sekmesi"
  com.mitv.tvhome.atv                  "Mi TV ana ekran"
  com.mitv.tvhome.michannel            "Mi kanallari"
  com.mitv.tvhome.mitvplus             "Mi TV+ (Turkiye'de calismaz)"
  com.xiaomi.mitv.smartshare           "Mi ekran paylasimi"
  com.xiaomi.wfdsinkhelperservice      "Miracast yardimcisi"
  com.xiaomi.mimusic2                  "Mi Muzik"
  com.mitv.gallery                     "Mi Galeri"
  com.mitv.videoplayer                 "Mi Video oynatici"
  com.xiaomi.mitv.mediaexplorer        "Mi Medya gezgini"
  com.xiaomo.tv.milegal                "Xiaomi yasal metinler"
  com.xiaomi.tvqs.overseas             "Xiaomi hizli ayarlar paneli"
  com.google.android.play.games        "Google Play Oyunlar"
  com.xiaomi.mitv.updateservice        "Sistem guncelleme (KALSIN)"
)

installed=$(adb -s $DEV shell pm list packages | sed 's/package://')
disabled=$(adb -s $DEV shell pm list packages -d | sed 's/package://')

print "\n Mi Box ($(adb -s $DEV shell getprop ro.product.model), Android $(adb -s $DEV shell getprop ro.build.version.release))\n"
printf " %-12s %-38s %s\n" "DURUM" "PAKET" "ACIKLAMA"
printf " %s\n" "--------------------------------------------------------------------------------------"
for p in ${(ko)ACIKLAMA}; do
  if print -r -- "$installed" | grep -qx "$p"; then
    if print -r -- "$disabled" | grep -qx "$p"; then st="KAPALI"; else st="YUKLU"; fi
  else
    st="kaldirilmis"
  fi
  printf " %-12s %-38s %s\n" "$st" "$p" "${ACIKLAMA[$p]}"
done
print "\n Ana ekran: $(adb -s $DEV shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
adb -s $DEV shell df -h /data | tail -1 | awk '{print " Depolama: " $3 " dolu / " $2 " (bos " $4 ")"}'
adb -s $DEV shell cat /proc/meminfo | awk '/MemTotal|MemAvailable/{printf " %s %d MB\n", $1, $2/1024}'
