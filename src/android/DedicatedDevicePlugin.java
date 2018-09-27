/**
 * Copyright 2018 Ayogo Health Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ayogo.cordova.dedicateddevice;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.SystemUpdatePolicy;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONObject;


// This is *heavily* inspired by Google's COSU Sample App code:
// https://codelabs.developers.google.com/codelabs/cosu/index.html
public class DedicatedDevicePlugin extends CordovaPlugin {
    private static final String TAG = "DedicatedDevicePlugin";

    private ComponentName mActivityComponentName;
    private ComponentName mAdminComponentName;
    private DevicePolicyManager mDevicePolicyManager;
    private PackageManager mPackageManager;


    @Override
    protected void pluginInitialize() {
        LOG.v(TAG, "Initializing");

        Activity _act = cordova.getActivity();

        mPackageManager = _act.getPackageManager();
        mDevicePolicyManager = (DevicePolicyManager)_act.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAdminComponentName = DeviceAdminReceiver.getComponentName(_act);
        mActivityComponentName = new ComponentName(_act.getApplicationContext(), _act.getClass());

        if (mDevicePolicyManager.isDeviceOwnerApp(_act.getApplicationContext().getPackageName())) {
            lockdownDevice();
        } else {
            LOG.w(TAG, "App is NOT a device owner!");
        }
    }


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        Activity _act = cordova.getActivity();

        if ("exitLockdown".equals(action)) {
            if (mDevicePolicyManager.isDeviceOwnerApp(_act.getApplicationContext().getPackageName())) {
                exitLockdown();

                _act.stopLockTask();
            }

            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK));
            return true;
        }

        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
        return false;
    }


    @Override
    public void onStart() {
        super.onStart();

        Activity _act = cordova.getActivity();

        if (mDevicePolicyManager.isLockTaskPermitted(_act.getApplicationContext().getPackageName())) {
            LOG.v(TAG, "Task Lock is permitted, enabling...");

            mPackageManager.setComponentEnabledSetting(mActivityComponentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

            ActivityManager am = (ActivityManager)_act.getSystemService(Context.ACTIVITY_SERVICE);

            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                _act.startLockTask();
            }
        } else {
            LOG.w(TAG, "Task Lock is NOT permitted!");
        }
    }


    private void lockdownDevice() {
        // set user restrictions
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, UserManager.DISALLOW_SAFE_BOOT);
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, UserManager.DISALLOW_FACTORY_RESET);
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, UserManager.DISALLOW_ADD_USER);
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
        mDevicePolicyManager.addUserRestriction(mAdminComponentName, UserManager.DISALLOW_ADJUST_VOLUME);

        // disable keyguard and status bar
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, true);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, true);

        // set system update policy
        mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, SystemUpdatePolicy.createWindowedInstallPolicy(60, 120));

        // set the activity as a lock task package
        mDevicePolicyManager.setLockTaskPackages(mAdminComponentName, new String[] { cordova.getActivity().getPackageName() });

        // set the activity as a home intent receiver so that it's started on reboot
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_MAIN);
        intentFilter.addCategory(Intent.CATEGORY_HOME);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        mDevicePolicyManager.addPersistentPreferredActivity(mAdminComponentName, intentFilter, mActivityComponentName);
    }


    private void exitLockdown() {
        // release user restrictions
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, UserManager.DISALLOW_SAFE_BOOT);
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, UserManager.DISALLOW_FACTORY_RESET);
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, UserManager.DISALLOW_ADD_USER);
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
        mDevicePolicyManager.clearUserRestriction(mAdminComponentName, UserManager.DISALLOW_ADJUST_VOLUME);

        // re-enable keyguard and status bar
        mDevicePolicyManager.setKeyguardDisabled(mAdminComponentName, false);
        mDevicePolicyManager.setStatusBarDisabled(mAdminComponentName, false);

        // un-set system update policy
        mDevicePolicyManager.setSystemUpdatePolicy(mAdminComponentName, null);

        // clear the activity as a home intent receiver
        mDevicePolicyManager.clearPackagePersistentPreferredActivities(mAdminComponentName, cordova.getActivity().getPackageName());
    }
}
