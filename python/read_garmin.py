import asyncio
from bleak import BleakClient

GARMIN_ADDRESS = "84D54953-5C1E-BB44-5789-6246148012A1"

HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"


def parse_heart_rate(data: bytearray):
    flags = data[0]

    # Bit 0:
    # 0 = HR em uint8
    # 1 = HR em uint16
    hr_16bit = flags & 0x01

    if hr_16bit:
        heart_rate = int.from_bytes(data[1:3], byteorder="little")
    else:
        heart_rate = data[1]

    return heart_rate


def handle_hr(sender, data: bytearray):
    hr = parse_heart_rate(data)
    print(f"Heart rate: {hr} bpm | raw: {data.hex(' ')}")


async def main():
    async with BleakClient(GARMIN_ADDRESS) as client:
        print("Connected:", client.is_connected)

        await client.start_notify(HEART_RATE_MEASUREMENT, handle_hr)

        print("Listening for heart rate... Press Ctrl+C to stop.")

        try:
            while True:
                await asyncio.sleep(1)
        finally:
            await client.stop_notify(HEART_RATE_MEASUREMENT)


asyncio.run(main())