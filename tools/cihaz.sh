# Betiklerin ortak TV adresi. Önce DEV ortam değişkenine, yoksa tools/.tv dosyasına bakar.
# tools/.tv repoya girmez; içine yalnızca "192.168.x.x:5555" yazılır.
if [[ -z $DEV ]]; then
  _tv_file=${${(%):-%x}:A:h}/.tv
  [[ -f $_tv_file ]] && DEV=$(<$_tv_file)
fi
if [[ -z $DEV ]]; then
  print -u2 "TV adresi bulunamadi. Ornek: echo 192.168.1.50:5555 > tools/.tv"
  exit 1
fi
