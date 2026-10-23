"""
Tuya cihazlarının yerel anahtarlarını (local_key) alır ve secrets/devices.json'a yazar.

Home Assistant'ın Tuya entegrasyonunun kullandığı QR girişiyle çalışır; geliştirici hesabı gerekmez.
Kullanıcı kodu Tuya Smart / Smart Life uygulamasında: Ben → Ayarlar → Hesap ve Güvenlik → Kullanıcı Kodu.

Kullanım: python tools/smarthome/tuya_keys.py
"""
import json
import os
import sys
import time

import qrcode
import tinytuya
from tuya_sharing import LoginControl, Manager

# Home Assistant'ın herkese açık istemci bilgileri
CLIENT_ID = "HA_3y9q4ak7g4ephrvke"
SCHEMA = "haauthorize"

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SECRETS = os.path.join(ROOT, "secrets", "devices.json")


def login():
    user_code = input("Kullanıcı kodu: ").strip()
    control = LoginControl()
    response = control.qr_code(CLIENT_ID, SCHEMA, user_code)
    if not response.get("success"):
        sys.exit(f"QR alınamadı: {response.get('msg') or response}")

    qr = qrcode.QRCode(border=1)
    qr.add_data("tuyaSmart--qrLogin?token=" + response["result"]["qrcode"])
    qr.print_ascii(invert=True)
    print("Uygulamada sağ üstteki tarama simgesiyle bu kodu okutun ve girişi onaylayın…")

    token = response["result"]["qrcode"]
    for _ in range(90):
        time.sleep(2)
        ok, info = control.login_result(token, CLIENT_ID, user_code)
        if ok:
            return user_code, info
    sys.exit("Süre doldu, tekrar deneyin.")


def main():
    user_code, info = login()
    manager = Manager(
        CLIENT_ID,
        user_code,
        info["terminal_id"],
        info["endpoint"],
        {
            "t": info["t"],
            "uid": info["uid"],
            "expire_time": info["expire_time"],
            "access_token": info["access_token"],
            "refresh_token": info["refresh_token"],
        },
    )
    manager.update_device_cache()

    print("Yerel ağ taranıyor…")
    local = tinytuya.deviceScan(verbose=False, maxretry=8)
    by_id = {d["gwId"]: d for d in local.values()}

    devices = []
    for d in manager.device_map.values():
        lan = by_id.get(d.id, {})
        devices.append({
            "id": d.id,
            "name": d.name,
            "local_key": d.local_key,
            "ip": lan.get("ip"),
            "version": lan.get("version"),
            "category": d.category,
            "product_id": d.product_id,
            "product_name": d.product_name,
            # DP kodları ve değer aralıkları: uygulamadaki istemciyi yazarken lazım
            "status": d.status,
            "functions": {k: vars(v) for k, v in d.function.items()},
            "status_range": {k: vars(v) for k, v in d.status_range.items()},
            "local_strategy": d.local_strategy,
        })

    os.makedirs(os.path.dirname(SECRETS), exist_ok=True)
    data = {}
    if os.path.exists(SECRETS):
        with open(SECRETS) as f:
            data = json.load(f)
    data["tuya"] = devices
    with open(SECRETS, "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, default=str)
    os.chmod(SECRETS, 0o600)

    for d in devices:
        print(f"  {d['name']:<24} {d['ip'] or 'ağda yok':<15} v{d['version'] or '?'}  anahtar {'var' if d['local_key'] else 'YOK'}")
    print(f"{len(devices)} cihaz {os.path.relpath(SECRETS, ROOT)} dosyasına yazıldı.")


if __name__ == "__main__":
    main()
