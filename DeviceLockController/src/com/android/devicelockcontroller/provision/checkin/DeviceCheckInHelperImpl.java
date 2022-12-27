/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.devicelockcontroller.provision.checkin;

import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_IMEI;
import static com.android.devicelockcontroller.common.DeviceLockConstants.DEVICE_ID_TYPE_MEID;
import static com.android.devicelockcontroller.common.DeviceLockConstants.TOTAL_DEVICE_ID_TYPES;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.util.LogUtil;

/**
 * Helper class to perform the device check in process with device lock backend server
 */
public final class DeviceCheckInHelperImpl implements DeviceCheckInHelper {
    private static final String TAG = "DeviceCheckInHelper";
    private final Context mContext;

    public DeviceCheckInHelperImpl(Context context) {
        mContext = context;
    }

    /**
     * Get the check-in status of this device for device lock program.
     *
     * @param isExpedited if true, the work request should be expedited;
     */
    @Override
    public void enqueueDeviceCheckInWork(boolean isExpedited) {
        final OneTimeWorkRequest.Builder builder =
                new OneTimeWorkRequest.Builder(DeviceCheckInWorker.class)
                        .setConstraints(
                                new Constraints.Builder().setRequiredNetworkType(
                                        NetworkType.CONNECTED).build());
        if (!isExpedited) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST);
        WorkManager.getInstance(mContext).enqueue(builder.build());
    }


    @NonNull
    ArraySet<Pair<Integer, String>> getDeviceUniqueIds() {
        final int deviceIdTypeBitmap = mContext.getResources().getInteger(
                R.integer.device_id_type_bitmap);
        if (deviceIdTypeBitmap < 0) {
            LogUtil.e(TAG, "getDeviceId: Cannot get device_id_type_bitmap");
        }

        return getDeviceAvailableUniqueIds(deviceIdTypeBitmap);
    }

    @VisibleForTesting
    ArraySet<Pair<Integer, String>> getDeviceAvailableUniqueIds(
            int deviceIdTypeBitmap) {

        final TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        final int totalSlotCount =
                telephonyManager != null ? telephonyManager.getActiveModemCount() : 0;
        final int maximumIdCount = TOTAL_DEVICE_ID_TYPES * totalSlotCount;
        final ArraySet<Pair<Integer, String>> deviceIds = new ArraySet<>(maximumIdCount);
        if (maximumIdCount == 0) return deviceIds;

        for (int i = 0; i < totalSlotCount; i++) {
            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_IMEI)) != 0) {
                final String imei = telephonyManager.getImei(i);

                if (imei != null) {
                    deviceIds.add(new Pair<>(DEVICE_ID_TYPE_IMEI, imei));
                }
            }

            if ((deviceIdTypeBitmap & (1 << DEVICE_ID_TYPE_MEID)) != 0) {
                final String meid = telephonyManager.getMeid(i);

                if (meid != null) {
                    deviceIds.add(new Pair<>(DEVICE_ID_TYPE_MEID, meid));
                }
            }
        }

        return deviceIds;
    }
}