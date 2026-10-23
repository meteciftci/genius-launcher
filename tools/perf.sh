#!/bin/zsh
# Launcher'da kartlar arasında gezinip kare sürelerini ölçer.
# Kullanım: zsh tools/perf.sh [tekrar_sayisi]
source ${0:A:h}/cihaz.sh
N=${1:-12}
PKG=dev.evim.tv
adb connect $DEV >/dev/null

adb -s $DEV shell am start -n $PKG/.MainActivity >/dev/null
sleep 2
adb -s $DEV shell dumpsys gfxinfo $PKG reset >/dev/null
for i in $(seq 1 $N); do adb -s $DEV shell input keyevent DPAD_RIGHT; sleep 0.35; done
for i in $(seq 1 $N); do adb -s $DEV shell input keyevent DPAD_LEFT; sleep 0.35; done
adb -s $DEV shell dumpsys gfxinfo $PKG | grep -E 'Total frames|Janky frames:'
adb -s $DEV shell dumpsys gfxinfo $PKG framestats | python3 -I ${0:h}/framestats.py
