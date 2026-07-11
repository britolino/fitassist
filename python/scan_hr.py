import asyncio
from bleak import BleakScanner

HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"

async def main():
    print("Scanning for BLE devices...")
    devices = await BleakScanner.discover(timeout=30, return_adv=True)

    for device, adv in devices.values():
        service_uuids = [s.lower() for s in adv.service_uuids]

        print("-" * 60)
        print("Name:", device.name)
        print("Address:", device.address)
        print("RSSI:", adv.rssi)
        print("Local name:", adv.local_name)
        print("Service UUIDs:", service_uuids)

        if HEART_RATE_SERVICE in service_uuids:
            print(">>> POSSIBLE HEART RATE SENSOR FOUND")

asyncio.run(main())