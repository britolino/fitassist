import asyncio
import struct
from bleak import BleakClient

TRAINER_ADDRESS = "EACD0B7F-AFFB-897F-481C-0889BC9386FB"

CYCLING_POWER_MEASUREMENT = "00002a63-0000-1000-8000-00805f9b34fb"
CSC_MEASUREMENT = "00002a5b-0000-1000-8000-00805f9b34fb"


def handle_power(sender, data: bytearray):
    # Cycling Power Measurement:
    # bytes 0-1: flags
    # bytes 2-3: instantaneous power, signed int16, watts
    flags = int.from_bytes(data[0:2], byteorder="little")
    power = int.from_bytes(data[2:4], byteorder="little", signed=True)

    print(f"Power: {power} W | raw: {data.hex(' ')}")


def handle_csc(sender, data: bytearray):
    # CSC Measurement:
    # byte 0: flags
    # bit 0 = wheel revolution data present
    # bit 1 = crank revolution data present

    flags = data[0]
    offset = 1

    wheel_present = flags & 0x01
    crank_present = flags & 0x02

    wheel_revs = None
    wheel_time = None
    crank_revs = None
    crank_time = None

    if wheel_present:
        wheel_revs = int.from_bytes(data[offset:offset+4], "little")
        offset += 4
        wheel_time = int.from_bytes(data[offset:offset+2], "little")
        offset += 2

    if crank_present:
        crank_revs = int.from_bytes(data[offset:offset+2], "little")
        offset += 2
        crank_time = int.from_bytes(data[offset:offset+2], "little")
        offset += 2

    print(
        f"CSC | wheel_revs={wheel_revs} wheel_time={wheel_time} "
        f"crank_revs={crank_revs} crank_time={crank_time} | raw: {data.hex(' ')}"
    )


async def main():
    async with BleakClient(TRAINER_ADDRESS) as client:
        print("Connected:", client.is_connected)

        await client.start_notify(CYCLING_POWER_MEASUREMENT, handle_power)
        await client.start_notify(CSC_MEASUREMENT, handle_csc)

        print("Listening... start pedalling.")
        await asyncio.sleep(120)

        await client.stop_notify(CYCLING_POWER_MEASUREMENT)
        await client.stop_notify(CSC_MEASUREMENT)


asyncio.run(main())