#!/bin/zsh
# paketler.txt'teki uygulamaları yalnızca kullanıcı 0 için kaldırır (geri alınabilir: geri_yukle.sh)
source ${0:A:h}/../cihaz.sh
LISTE=${1:-${0:h}/paketler.txt}
adb connect $DEV >/dev/null

for p in ${(f)"$(<$LISTE)"}; do
  printf " %-40s " "$p"
  adb -s $DEV shell pm uninstall -k --user 0 "$p"
done
