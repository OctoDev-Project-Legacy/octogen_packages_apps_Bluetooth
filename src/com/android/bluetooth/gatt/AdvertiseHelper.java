/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import java.io.ByteArrayOutputStream;

class AdvertiseHelper {

  private static final String TAG = "AdvertiseHelper";

  // Advertise interval for different modes.
  private static final int ADVERTISING_INTERVAL_HIGH_MILLS = 1000;
  private static final int ADVERTISING_INTERVAL_MEDIUM_MILLS = 250;
  private static final int ADVERTISING_INTERVAL_LOW_MILLS = 100;

  private static final int ADVERTISING_TX_POWER_MIN = 0;
  private static final int ADVERTISING_TX_POWER_LOW = 1;
  private static final int ADVERTISING_TX_POWER_MID = 2;
  private static final int ADVERTISING_TX_POWER_UPPER = 3;
  // Note this is not exposed to the Java API.
  private static final int ADVERTISING_TX_POWER_MAX = 4;

  // Note we don't expose connectable directed advertising to API.
  private static final int ADVERTISING_EVENT_TYPE_LEGACY_CONNECTABLE = 0x13;
  private static final int ADVERTISING_EVENT_TYPE_LEGACY_SCANNABLE = 0x12;
  private static final int ADVERTISING_EVENT_TYPE_LEGACY_NON_CONNECTABLE = 0x10;

  private static final int DEVICE_NAME_MAX = 18;

  private static final int COMPLETE_LIST_16_BIT_SERVICE_UUIDS = 0X03;
  private static final int COMPLETE_LIST_32_BIT_SERVICE_UUIDS = 0X05;
  private static final int COMPLETE_LIST_128_BIT_SERVICE_UUIDS = 0X07;
  private static final int SHORTENED_LOCAL_NAME = 0X08;
  private static final int COMPLETE_LOCAL_NAME = 0X09;
  private static final int TX_POWER_LEVEL = 0x0A;
  private static final int SERVICE_DATA_16_BIT_UUID = 0X16;
  private static final int SERVICE_DATA_32_BIT_UUID = 0X20;
  private static final int SERVICE_DATA_128_BIT_UUID = 0X21;
  private static final int MANUFACTURER_SPECIFIC_DATA = 0XFF;

  public static byte[] advertiseDataToBytes(AdvertiseData data, String name) {

    if (data == null) return new byte[0];

    // Flags are added by lower layers of the stack, only if needed;
    // no need to add them here.

    ByteArrayOutputStream ret = new ByteArrayOutputStream();

    if (data.getIncludeDeviceName()) {
      try {
        byte[] nameBytes = name.getBytes("UTF-8");

        int nameLength = nameBytes.length;
        byte type;

        // TODO(jpawlowski) put a better limit on device name!
        if (nameLength > DEVICE_NAME_MAX) {
          nameLength = DEVICE_NAME_MAX;
          type = SHORTENED_LOCAL_NAME;
        } else {
          type = COMPLETE_LOCAL_NAME;
        }

        ret.write(nameLength + 1);
        ret.write(type);
        ret.write(nameBytes, 0, nameLength);
      } catch (java.io.UnsupportedEncodingException e) {
        Log.e(TAG, "Can't include name - encoding error!", e);
      }
    }

    for (int i = 0; i < data.getManufacturerSpecificData().size(); i++) {
      int manufacturerId = data.getManufacturerSpecificData().keyAt(i);

      byte[] manufacturerData = data.getManufacturerSpecificData().get(manufacturerId);
      int dataLen = 2 + (manufacturerData == null ? 0 : manufacturerData.length);
      byte[] concated = new byte[dataLen];
      // First two bytes are manufacturer id in little-endian.
      concated[0] = (byte) (manufacturerId & 0xFF);
      concated[1] = (byte) ((manufacturerId >> 8) & 0xFF);
      if (manufacturerData != null) {
        System.arraycopy(manufacturerData, 0, concated, 2, manufacturerData.length);
      }

      ret.write(concated.length + 1);
      ret.write(MANUFACTURER_SPECIFIC_DATA);
      ret.write(concated, 0, concated.length);
    }

    if (data.getIncludeTxPowerLevel()) {
      ret.write(2 /* Length */);
      ret.write(TX_POWER_LEVEL);
      ret.write(0); // lower layers will fill this value.
    }

    if (data.getServiceUuids() != null) {
      ByteArrayOutputStream serviceUuids16 = new ByteArrayOutputStream();
      ByteArrayOutputStream serviceUuids32 = new ByteArrayOutputStream();
      ByteArrayOutputStream serviceUuids128 = new ByteArrayOutputStream();

      for (ParcelUuid parcelUuid : data.getServiceUuids()) {
        byte[] uuid = BluetoothUuid.uuidToBytes(parcelUuid);

        if (uuid.length == BluetoothUuid.UUID_BYTES_16_BIT) {
          serviceUuids16.write(uuid, 0, uuid.length);
        } else if (uuid.length == BluetoothUuid.UUID_BYTES_32_BIT) {
          serviceUuids32.write(uuid, 0, uuid.length);
        } else /*if (uuid.length == BluetoothUuid.UUID_BYTES_128_BIT)*/ {
          serviceUuids128.write(uuid, 0, uuid.length);
        }
      }

      if (serviceUuids16.size() != 0) {
        ret.write(serviceUuids16.size() + 1);
        ret.write(COMPLETE_LIST_16_BIT_SERVICE_UUIDS);
        ret.write(serviceUuids16.toByteArray(), 0, serviceUuids16.size());
      }

      if (serviceUuids32.size() != 0) {
        ret.write(serviceUuids32.size() + 1);
        ret.write(COMPLETE_LIST_32_BIT_SERVICE_UUIDS);
        ret.write(serviceUuids32.toByteArray(), 0, serviceUuids32.size());
      }

      if (serviceUuids128.size() != 0) {
        ret.write(serviceUuids128.size() + 1);
        ret.write(COMPLETE_LIST_128_BIT_SERVICE_UUIDS);
        ret.write(serviceUuids128.toByteArray(), 0, serviceUuids128.size());
      }
    }

    if (!data.getServiceData().isEmpty()) {
      for (ParcelUuid parcelUuid : data.getServiceData().keySet()) {
        byte[] serviceData = data.getServiceData().get(parcelUuid);

        byte[] uuid = BluetoothUuid.uuidToBytes(parcelUuid);
        int uuidLen = uuid.length;

        int dataLen = uuidLen + (serviceData == null ? 0 : serviceData.length);
        byte[] concated = new byte[dataLen];

        System.arraycopy(uuid, 0, concated, 0, uuidLen);

        if (serviceData != null) {
          System.arraycopy(serviceData, 0, concated, uuidLen, serviceData.length);
        }

        if (uuid.length == BluetoothUuid.UUID_BYTES_16_BIT) {
          ret.write(concated.length + 1);
          ret.write(SERVICE_DATA_16_BIT_UUID);
          ret.write(concated, 0, concated.length);
        } else if (uuid.length == BluetoothUuid.UUID_BYTES_32_BIT) {
          ret.write(concated.length + 1);
          ret.write(SERVICE_DATA_32_BIT_UUID);
          ret.write(concated, 0, concated.length);
        } else /*if (uuid.length == BluetoothUuid.UUID_BYTES_128_BIT)*/ {
          ret.write(concated.length + 1);
          ret.write(SERVICE_DATA_128_BIT_UUID);
          ret.write(concated, 0, concated.length);
        }
      }
    }

    return ret.toByteArray();
  }

  // Convert settings tx power level to stack tx power level.
  public static int getTxPowerLevel(AdvertiseSettings settings) {
    switch (settings.getTxPowerLevel()) {
      case AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW:
        return ADVERTISING_TX_POWER_MIN;
      case AdvertiseSettings.ADVERTISE_TX_POWER_LOW:
        return ADVERTISING_TX_POWER_LOW;
      case AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM:
        return ADVERTISING_TX_POWER_MID;
      case AdvertiseSettings.ADVERTISE_TX_POWER_HIGH:
        return ADVERTISING_TX_POWER_UPPER;
      default:
        // Shouldn't happen, just in case.
        return ADVERTISING_TX_POWER_MID;
    }
  }

  // Convert advertising event type to stack values.
  public static int getAdvertisingEventProperties(AdvertiseClient client) {
    AdvertiseSettings settings = client.settings;
    if (settings.isConnectable()) {
      return ADVERTISING_EVENT_TYPE_LEGACY_CONNECTABLE;
    }
    return client.scanResponse == null
        ? ADVERTISING_EVENT_TYPE_LEGACY_NON_CONNECTABLE
        : ADVERTISING_EVENT_TYPE_LEGACY_SCANNABLE;
  }

  // Convert advertising milliseconds to advertising units(one unit is 0.625 millisecond).
  public static long getAdvertisingIntervalUnit(AdvertiseSettings settings) {
    switch (settings.getMode()) {
      case AdvertiseSettings.ADVERTISE_MODE_LOW_POWER:
        return Utils.millsToUnit(ADVERTISING_INTERVAL_HIGH_MILLS);
      case AdvertiseSettings.ADVERTISE_MODE_BALANCED:
        return Utils.millsToUnit(ADVERTISING_INTERVAL_MEDIUM_MILLS);
      case AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY:
        return Utils.millsToUnit(ADVERTISING_INTERVAL_LOW_MILLS);
      default:
        // Shouldn't happen, just in case.
        return Utils.millsToUnit(ADVERTISING_INTERVAL_HIGH_MILLS);
    }
  }
}