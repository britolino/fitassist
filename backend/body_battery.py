from datetime import date, timedelta
import os
import json

from garminconnect import Garmin

email = os.getenv("GARMIN_EMAIL")
password = os.getenv("GARMIN_PASSWORD")

if not email or not password:
    raise RuntimeError("Defina GARMIN_EMAIL e GARMIN_PASSWORD no terminal.")

client = Garmin(email, password)
client.login()

today = date.today()
start = today - timedelta(days=7)

data = client.get_body_battery(
    start.isoformat(),
    today.isoformat()
)

print(json.dumps(data, indent=2, ensure_ascii=False))

import requests

url = "http://localhost:1880/garmin/body-battery"

response = requests.post(url, json=data)

print(response.status_code)

print(response.text)