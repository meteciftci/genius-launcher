#!/bin/zsh
# Kaldırılan sistem uygulamasını geri getirir. Kullanım: zsh geri_yukle.sh paket.adi  (argümansız: paketler.txt'tekilerin hepsi)
source ${0:A:h}/../cihaz.sh
adb connect $DEV >/dev/null

if (( $# )); then pkgs=($@); else pkgs=(${(f)"$(<${0:h}/paketler.txt)"}); fi
for p in $pkgs; do
  printf " %-40s " "$p"
  adb -s $DEV shell cmd package install-existing "$p"
done
