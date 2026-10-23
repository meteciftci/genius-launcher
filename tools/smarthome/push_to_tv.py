"""
secrets/devices.json'dan launcher'ın ihtiyaç duyduğu kadarını (anahtar, IP, sürüm) seçip TV'ye gönderir.
Dosya uygulamanın harici klasörüne konur; uygulama açılınca özel klasörüne taşıyıp kopyayı siler.

Kullanım: python tools/smarthome/push_to_tv.py
"""
import json
import os
import socket
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SECRETS = os.path.join(ROOT, "secrets", "devices.json")
TARGET = "/sdcard/Android/data/dev.evim.tv/files/home.json"


def device_address():
    address = os.environ.get("DEV")
    if not address:
        with open(os.path.join(ROOT, "tools", ".tv")) as f:
            address = f.read().strip()
    return address


def main():
    with open(SECRETS) as f:
        data = json.load(f)

    tuya = [
        {k: d[k] for k in ("id", "name", "local_key", "ip", "version", "category")}
        for d in data.get("tuya", [])
        if d.get("ip") and d.get("local_key")
    ]
    dyson = []
    for d in data.get("dyson", []):
        # Dyson kendini mDNS'te seri numarasıyla duyuruyor
        try:
            ip = socket.gethostbyname(d["serial"] + ".local")
        except OSError:
            print(f"  {d['name']}: ağda bulunamadı, atlandı")
            continue
        dyson.append({k: d[k] for k in ("serial", "name", "credential", "product_type")} | {"ip": ip})

    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as tmp:
        json.dump({"tuya": tuya, "dyson": dyson}, tmp, ensure_ascii=False)
    try:
        dev = device_address()
        subprocess.run(["adb", "-s", dev, "shell", "mkdir", "-p", os.path.dirname(TARGET)], check=True)
        subprocess.run(["adb", "-s", dev, "push", tmp.name, TARGET], check=True, stdout=subprocess.DEVNULL)
    finally:
        os.remove(tmp.name)

    for d in tuya:
        print(f"  {d['name']:<24} {d['ip']}")
    for d in dyson:
        print(f"  {d['name']:<24} {d['ip']}")
    print(f"{len(tuya) + len(dyson)} cihaz TV'ye gönderildi; launcher açılınca içeri alınır.")


if __name__ == "__main__":
    main()
