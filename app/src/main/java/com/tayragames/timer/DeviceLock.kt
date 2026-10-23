package com.tayragames.timer

import android.app.KeyguardManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class KidTimerAdminReceiver : DeviceAdminReceiver()

object DeviceLock {
    private fun admin(context: Context) = ComponentName(context, KidTimerAdminReceiver::class.java)

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)

    fun isEnabled(context: Context): Boolean = dpm(context).isAdminActive(admin(context))

    fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun enableIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.admin_explanation),
            )

    fun disable(context: Context) {
        if (isEnabled(context)) dpm(context).removeActiveAdmin(admin(context))
    }

    /** Returns true when the screen was locked. */
    fun lock(context: Context): Boolean {
        if (!isEnabled(context)) return false
        return runCatching { dpm(context).lockNow() }.isSuccess
    }
}
