#!/bin/zsh
# Mi Box'ı hub olarak sürekli açık tutacak uyku/enerji ayarları.
# Kullanım: zsh enerji.sh [goster|uygula|geri_al]
source ${0:A:h}/../cihaz.sh
YEDEK=${0:h}/enerji_yedek.txt
adb connect $DEV >/dev/null

# namespace anahtar hedef açıklama
AYARLAR=(
  "global hdmi_control_auto_device_off_enabled 0 TV_kapaninca_kutu_uyumasin"
  "global low_power_standby_enabled 0 Beklemede_ag_kesilmesin"
  "global stay_on_while_plugged_in 3 Prizdeyken_uyanik_kal"
  "global wifi_sleep_policy 2 Wi-Fi_hic_uyumasin"
  "secure sleep_timeout -1 Otomatik_uyku_yok"
  "secure attentive_timeout -1 Hareketsizlikte_kapanma_yok"
)

goster() {
  printf "\n %-8s %-40s %-8s %-8s %s\n" "ALAN" "AYAR" "SIMDI" "HEDEF" "ACIKLAMA"
  printf " %s\n" "-----------------------------------------------------------------------------------------------"
  for a in $AYARLAR; do
    parts=(${=a})
    cur=$(adb -s $DEV shell settings get $parts[1] $parts[2])
    [[ $cur == $parts[3] ]] && ok="✓" || ok="✗"
    printf " %-8s %-40s %-8s %-8s %s %s\n" $parts[1] $parts[2] $cur $parts[3] $ok ${parts[4]//_/ }
  done
  print
}

case ${1:-goster} in
  goster) goster ;;
  uygula)
    if [[ ! -f $YEDEK ]]; then
      for a in $AYARLAR; do parts=(${=a}); print "$parts[1] $parts[2] $(adb -s $DEV shell settings get $parts[1] $parts[2])"; done > $YEDEK
      print " Eski degerler yedeklendi: $YEDEK"
    fi
    for a in $AYARLAR; do parts=(${=a}); adb -s $DEV shell settings put $parts[1] $parts[2] $parts[3]; done
    print " Ayarlar uygulandi."
    goster ;;
  geri_al)
    [[ -f $YEDEK ]] || { print " Yedek yok: $YEDEK"; exit 1; }
    while read ns key val; do
      if [[ $val == null ]]; then adb -s $DEV shell settings delete $ns $key; else adb -s $DEV shell settings put $ns $key $val; fi
    done < $YEDEK
    print " Eski degerlere donuldu."
    goster ;;
esac
