package com.mustangos.core

import org.junit.Assert.*
import org.junit.Test

class AppSearchTest {
    private val apps = listOf(
        LaunchableApp("vendor.music", "Music", "Música"),
        LaunchableApp("vendor.nav", "Maps", "Navegación"),
        LaunchableApp("vendor.phone", "Phone", "Teléfono")
    )
    @Test fun accentsCaseAndWhitespaceDoNotPreventFindingApps() {
        assertEquals(listOf(apps[0]), AppSearch.filter(apps, " MUSICA "))
        assertEquals(listOf(apps[1]), AppSearch.filter(apps, "navega"))
        assertEquals(listOf(apps[2]), AppSearch.filter(apps, "telefono"))
    }
    @Test fun clearingSearchRestoresAllAppsInOriginalOrder() {
        assertSame(apps, AppSearch.filter(apps, "  "))
        assertTrue(AppSearch.filter(apps, "not-installed").isEmpty())
    }
    @Test fun searchUsesAppLabelsAndDoesNotMatchHiddenPackageNames() {
        assertTrue(AppSearch.filter(apps, "vendor").isEmpty())
    }
}
