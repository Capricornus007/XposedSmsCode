package com.github.magisk317.smscode.common.utils

import android.content.pm.ApplicationInfo
import android.os.Process
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderCallerGuardTest {
    @Test
    fun isPrivilegedUid_allowsSystemPhoneAndSelf() {
        assertTrue(ProviderCallerGuard.isPrivilegedUid(Process.SYSTEM_UID, appUid = 12345))
        assertTrue(ProviderCallerGuard.isPrivilegedUid(Process.PHONE_UID, appUid = 12345))
        assertTrue(ProviderCallerGuard.isPrivilegedUid(12345, appUid = 12345))
    }

    @Test
    fun isPrivilegedUid_rejectsOrdinaryCaller() {
        assertFalse(ProviderCallerGuard.isPrivilegedUid(20000, appUid = 12345))
        assertFalse(ProviderCallerGuard.isPrivilegedUid(20000, appUid = null))
    }

    @Test
    fun isSystemPackageFlags_acceptsSystemAndUpdatedSystemAppsOnly() {
        assertTrue(ProviderCallerGuard.isSystemPackageFlags(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(ProviderCallerGuard.isSystemPackageFlags(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertFalse(ProviderCallerGuard.isSystemPackageFlags(0))
    }
}
