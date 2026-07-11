import os
from datetime import date
from garminconnect import Garmin

# First run: logs in and saves tokens to ~/.garminconnect
# Subsequent runs: loads saved tokens and auto-refreshes
client = Garmin(
    os.getenv("GARMIN_EMAIL"),
    os.getenv("GARMIN_PASSWORD"),
    prompt_mfa=lambda: input("MFA code: "),
)
client.login("~/.garminconnect")

# Get today's stats
today = date.today().isoformat()
stats = client.get_stats(today)

# Get heart rate data
hr_data = client.get_heart_rates(today)
print(f"Resting HR: {hr_data.get('restingHeartRate', 'n/a')}")
#
#