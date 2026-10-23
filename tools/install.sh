#!/bin/zsh
# Release derleyip TV'ye yükler, ART ile önceden derler (ilk açılışlardaki takılmayı önler) ve açar.
# Kullanım: zsh tools/install.sh
set -e
source ${0:A:h}/cihaz.sh
PKG=dev.evim.tv
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}

cd ${0:h}/..
./gradlew :tv:assembleRelease --console=plain -q
adb connect $DEV >/dev/null
adb -s $DEV install -r tv/build/outputs/apk/release/tv-release.apk
adb -s $DEV shell cmd package compile -m speed -f $PKG
adb -s $DEV shell am start -n $PKG/.MainActivity
