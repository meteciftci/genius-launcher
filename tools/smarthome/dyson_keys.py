"""
Dyson cihazlarının yerel MQTT bilgilerini (seri no, parola, ürün tipi) alır ve secrets/devices.json'a yazar.

İki yol var:
  python tools/smarthome/dyson_keys.py            MyDyson hesabıyla giriş (e-postaya kod gelir)
  python tools/smarthome/dyson_keys.py --sticker  Cihaz üzerindeki Wi-Fi etiketinden, buluta gitmeden
"""
import getpass
import json
import os
import sys

from libdyson.cloud import DysonAccount
from libdyson.utils import get_mqtt_info_from_wifi_info

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SECRETS = os.path.join(ROOT, "secrets", "devices.json")


def from_account():
    email = input("MyDyson e-posta: ").strip()
    country = (input("Ülke kodu [TR]: ").strip() or "TR").upper()
    account = DysonAccount()
    verify = account.login_email_otp(email, country)
    otp = input("E-postaya gelen kod: ").strip()
    verify(otp, getpass.getpass("MyDyson parolası (görünmez): "))
    return [
        {"serial": d.serial, "name": d.name, "credential": d.credential, "product_type": d.product_type}
        for d in account.devices()
    ]


def from_sticker():
    ssid = input("Etiketteki Wi-Fi adı (DYSON-…): ").strip()
    password = getpass.getpass("Etiketteki Wi-Fi parolası (görünmez): ").strip()
    serial, credential, product_type = get_mqtt_info_from_wifi_info(ssid, password)
    return [{"serial": serial, "name": "Dyson", "credential": credential, "product_type": product_type}]


def main():
    devices = from_sticker() if "--sticker" in sys.argv else from_account()

    os.makedirs(os.path.dirname(SECRETS), exist_ok=True)
    data = {}
    if os.path.exists(SECRETS):
        with open(SECRETS) as f:
            data = json.load(f)
    data["dyson"] = devices
    with open(SECRETS, "w") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.chmod(SECRETS, 0o600)

    for d in devices:
        print(f"  {d['name']:<24} {d['serial']}  tip {d['product_type']}")
    print(f"{len(devices)} cihaz {os.path.relpath(SECRETS, ROOT)} dosyasına yazıldı.")


if __name__ == "__main__":
    main()
