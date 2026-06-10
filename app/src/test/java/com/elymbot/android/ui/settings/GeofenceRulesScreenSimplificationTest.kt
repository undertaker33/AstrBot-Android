package com.elymbot.android.ui.settings

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRulesScreenSimplificationTest {
    private val projectRoot: Path = detectProjectRoot()
    private val screenSource = projectRoot.resolve(
        "feature/geofence/presentation/src/main/java/com/elymbot/android/ui/settings/GeofenceRulesScreen.kt",
    )

    @Test
    fun editor_uses_map_selector_without_manual_coordinate_or_internal_id_fields() {
        val source = screenSource.readText()

        assertTrue(source.contains("GeofenceMapSelector("))
        assertTrue(source.contains("geofence-step-location"))
        assertTrue(source.contains("geofence-step-basic"))
        assertTrue(source.contains("geofence-step-action"))
        assertTrue(source.contains("geofence-dialog-previous"))
        assertTrue(source.contains("geofence-dialog-next"))
        assertTrue(source.contains("geofence-dialog-save"))
        assertTrue(source.contains("geofence-map-zoom-in"))
        assertTrue(source.contains("geofence-map-zoom-out"))
        assertTrue(source.contains("geofence-map-radius-control"))
        assertTrue(source.contains("geofence-map-radius-value"))
        assertTrue(source.contains("zoomAroundClientPoint"))
        assertTrue(source.contains("handleNativePinch"))
        assertTrue(source.contains("wheel"))
        assertTrue(source.contains("clampZoom"))
        assertTrue(source.contains("mainlandTileSources"))
        assertTrue(source.contains("overseasTileSources"))
        assertTrue(source.contains("webrd0"))
        assertTrue(source.contains("elymbotRenderTiles"))
        assertTrue(source.contains("tileLayer"))
        assertTrue(source.contains("tile-error"))
        assertTrue(source.contains("openstreetmap.fr/hot"))
        assertTrue(source.contains("Geofence map HTML ready"))
        assertTrue(source.contains("WebViewClient"))
        assertTrue(source.contains("WebChromeClient"))
        assertTrue(source.contains("ElymBotMap.log"))
        assertTrue(source.contains("100vh"))
        assertTrue(source.contains("requestAnimationFrame"))
        assertTrue(source.contains("GeofenceMapCssHeightPx"))
        assertTrue(source.contains("private const val GeofenceMapCssHeightPx = 320"))
        assertTrue(source.contains(".height(320.dp)"))
        assertTrue(source.contains("elymbotForceMapViewport"))
        assertTrue(source.contains("targetRuntimePlatform()"))
        assertTrue(source.contains("geofence-rule-enabled-switch-"))
        assertFalse(source.contains("unpkg.com/leaflet"))
        assertFalse(source.contains("L.map("))
        assertFalse(source.contains("L.tileLayer("))
        assertFalse(source.contains("geofence-map-fallback"))
        assertFalse(source.contains("geofence-map-search-query"))
        assertFalse(source.contains("geofence-map-search-action"))
        assertFalse(source.contains("geofence-map-search-suggestions"))
        assertFalse(source.contains("GeofenceMapSearchSuggestion"))
        assertFalse(source.contains("searchLocation"))
        assertFalse(source.contains("searchSuggestions"))
        assertFalse(source.contains("mainlandSearchProviders"))
        assertFalse(source.contains("overseasSearchProviders"))
        assertFalse(source.contains("geofenceSearchProxyUrl"))
        assertFalse(source.contains("shouldInterceptRequest"))
        assertFalse(source.contains("GeofenceSearchProxyUserAgent"))
        assertFalse(source.contains("/geofence-search"))
        assertFalse(source.contains("restapi.amap.com"))
        assertFalse(source.contains("nominatim.openstreetmap.org/search"))
        assertFalse(source.contains("geofence-create-latitude"))
        assertFalse(source.contains("geofence-create-longitude"))
        assertFalse(source.contains("geofence_field_enabled"))
        assertFalse(source.contains("geofence_field_region_label"))
        assertFalse(source.contains("geofence_field_platform"))
        assertFalse(source.contains("geofence_platform_app_chat"))
        assertFalse(source.contains("geofence_platform_qq_onebot"))
        assertFalse(source.contains("geofence_field_config_profile_id"))
        assertFalse(source.contains("geofence_field_persona_id"))
        assertFalse(source.contains("geofence_field_provider_id"))
        assertFalse(source.contains("PermissionStatusLine("))
    }

    @Test
    fun radius_control_stays_in_first_location_step() {
        val source = screenSource.readText()
        val testTagBranch = source.indexOf("when (editorStep) {")
        val contentBranch = source.indexOf("when (editorStep) {", startIndex = testTagBranch + 1)
        val locationStep = source.indexOf("GeofenceRuleEditorStep.Location ->", startIndex = contentBranch)
        val basicStep = source.indexOf("GeofenceRuleEditorStep.Basic ->", startIndex = contentBranch)
        val actionStep = source.indexOf("GeofenceRuleEditorStep.Action ->", startIndex = contentBranch)

        assertTrue(testTagBranch >= 0)
        assertTrue(contentBranch >= 0)
        assertTrue(locationStep >= 0)
        assertTrue(basicStep > locationStep)
        assertTrue(actionStep > basicStep)
        assertTrue(source.substring(locationStep, basicStep).contains("GeofenceMapSelector("))
        assertFalse(source.substring(basicStep, actionStep).contains("geofence-map-radius-slider"))
    }

    @Test
    fun create_entry_requests_foreground_permission_before_opening_editor_when_missing() {
        val source = screenSource.readText()

        assertTrue(source.contains("createDialogPendingForegroundPermission"))
        assertTrue(source.contains("createForegroundPermissionGranted"))
        assertTrue(source.contains("createForegroundLocationLauncher"))
        assertTrue(source.contains("fun openCreateDialogWithPermissionRequest()"))
        assertTrue(source.contains("if (contentPermissionStatus.foregroundGranted)"))
        assertTrue(source.contains("createForegroundLocationLauncher.launch"))
        assertTrue(source.contains("Manifest.permission.ACCESS_COARSE_LOCATION"))
        assertTrue(source.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
        assertTrue(source.contains("showCreateDialog = true"))
        assertTrue(source.contains("onClick = ::openCreateDialogWithPermissionRequest"))
        assertFalse(source.contains("onClick = { showCreateDialog = true }"))
    }

    @Test
    fun map_drag_moves_existing_layers_without_rebuilding_tiles_on_every_pointer_move() {
        val source = screenSource.readText()

        assertTrue(source.contains("class GeofenceMapHostView"))
        assertTrue(source.contains("override fun onInterceptTouchEvent"))
        assertTrue(source.contains("override fun onTouchEvent"))
        assertTrue(source.contains("commitNativePan"))
        assertTrue(source.contains("tapNativeMap"))
        assertTrue(source.contains("function setPanOffset(dx, dy)"))
        assertTrue(source.contains("window.elymbotNativeCommitPan"))
        assertTrue(source.contains("window.elymbotNativeTap"))
        assertTrue(source.contains("translate3d("))
        assertTrue(source.contains("webView.translationX = 0f"))
        assertTrue(source.contains("webView.translationY = 0f"))
        assertFalse(source.contains("panOverdrawPx"))
        assertFalse(source.contains("mapElement.addEventListener('pointermove'"))
        assertFalse(source.contains("mapElement.setPointerCapture"))
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}
