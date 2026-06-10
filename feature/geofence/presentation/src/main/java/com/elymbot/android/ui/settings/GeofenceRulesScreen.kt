package com.elymbot.android.ui.settings

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.ui.R
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceRuleValidation
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceCurrentLocationSnapshot
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMapAvailability
import com.elymbot.android.feature.geofence.domain.runtime.GeofencePermissionStatus
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiAction
import com.elymbot.android.feature.geofence.presentation.GeofenceLocationPermissionUiState
import com.elymbot.android.feature.geofence.presentation.GeofenceMapSelectorState
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleEditorDraft
import com.elymbot.android.feature.geofence.presentation.GeofenceRuleTargetContext
import com.elymbot.android.feature.geofence.presentation.buildGeofenceRuleRunUiPresentations
import com.elymbot.android.feature.geofence.presentation.buildGeofenceRulesUiPresentation
import com.elymbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.elymbot.android.ui.app.MonochromeUi
import com.elymbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.elymbot.android.model.chat.ConversationSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun GeofenceRulesScreen(onBack: () -> Unit) {
    GeofenceRulesScreenRoute(
        onBack = onBack,
        viewModel = hiltViewModel(),
    )
}

@Composable
internal fun GeofenceRulesScreenRoute(
    onBack: () -> Unit,
    viewModel: GeofenceRulesViewModel,
) {
    val rules by viewModel.rules.collectAsState()
    val botProfiles by viewModel.botProfiles.collectAsState()
    val selectedBotId by viewModel.selectedBotId.collectAsState()
    val conversationSessions by viewModel.conversationSessions.collectAsState()
    val runHistoryState by viewModel.runHistoryState
    val operationErrorState by viewModel.operationErrorState
    val permissionStatus = viewModel.currentPermissionStatus()
    val mapAvailability = viewModel.currentMapAvailability()
    val locationPermissionUiState by viewModel.locationPermissionUiState

    GeofenceSubPageScaffold(
        route = GeofenceRulesRoute,
        title = stringResource(R.string.geofence_rules_title),
        onBack = onBack,
    ) { innerPadding ->
        GeofenceRulesContent(
            rules = rules,
            defaultTargetContext = viewModel.defaultTargetContext(),
            permissionStatus = permissionStatus,
            mapAvailability = mapAvailability,
            locationPermissionUiState = locationPermissionUiState,
            botProfiles = botProfiles,
            conversationSessions = conversationSessions,
            selectedBotId = selectedBotId,
            onCreateRule = viewModel::createRule,
            onUpdateRule = viewModel::updateRule,
            onUseCurrentLocationClick = viewModel::onUseCurrentLocationClick,
            onForegroundLocationPermissionResult = viewModel::onForegroundLocationPermissionResult,
            onLoadCurrentLocation = viewModel::loadCurrentLocation,
            onDismissBackgroundPermissionGuide = viewModel::dismissBackgroundPermissionGuide,
            onBackgroundLocationSettingsOpened = viewModel::onBackgroundLocationSettingsOpened,
            onPauseRule = viewModel::pauseRule,
            onResumeRule = viewModel::resumeRule,
            onDeleteRule = viewModel::deleteRule,
            onShowRuns = viewModel::showRuns,
            runHistoryState = runHistoryState,
            onDismissRuns = viewModel::dismissRuns,
            operationErrorState = operationErrorState,
            onDismissOperationError = viewModel::dismissOperationError,
            onGeofenceMapLog = viewModel::logMapDiagnostic,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

private const val GeofenceRulesRoute = "geofence-rules"
private const val GeofenceMapDefaultZoom = 15
private const val GeofenceMapMinZoom = 4
private const val GeofenceMapMaxZoom = 19
private const val GeofenceMapCssHeightPx = 320
private const val GeofenceMapMaxRadiusMeters = 5_000f
private val GeofenceRuleEditorDialogHeight = 520.dp

private enum class GeofenceRuleEditorStep {
    Location,
    Basic,
    Action,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GeofenceRulesContent(
    rules: List<GeofenceRule>,
    defaultTargetContext: GeofenceRuleTargetContext = GeofenceRuleTargetContext(),
    permissionStatus: GeofencePermissionStatus = GeofencePermissionStatus(
        foregroundGranted = false,
        backgroundGranted = false,
    ),
    mapAvailability: GeofenceMapAvailability = GeofenceMapAvailability.MISSING_API_KEY,
    locationPermissionUiState: GeofenceLocationPermissionUiState = GeofenceLocationPermissionUiState(),
    botProfiles: List<BotProfile> = emptyList(),
    conversationSessions: List<ConversationSession> = emptyList(),
    selectedBotId: String = "",
    onCreateRule: (GeofenceRuleEditorDraft, GeofencePermissionStatus) -> Unit = { _, _ -> },
    onUpdateRule: (GeofenceRule, GeofenceRuleEditorDraft, GeofencePermissionStatus) -> Unit = { _, _, _ -> },
    onUseCurrentLocationClick: (GeofencePermissionStatus) -> GeofenceLocationPermissionUiAction = {
        GeofenceLocationPermissionUiAction.NONE
    },
    onForegroundLocationPermissionResult: (Boolean) -> GeofenceLocationPermissionUiAction = {
        GeofenceLocationPermissionUiAction.NONE
    },
    onLoadCurrentLocation: ((GeofenceCurrentLocationSnapshot) -> Unit) -> Unit = {},
    onDismissBackgroundPermissionGuide: () -> Unit = {},
    onBackgroundLocationSettingsOpened: () -> GeofenceLocationPermissionUiAction = {
        GeofenceLocationPermissionUiAction.NONE
    },
    onPauseRule: (String) -> Unit = {},
    onResumeRule: (String) -> Unit = {},
    onDeleteRule: (String) -> Unit = {},
    onShowRuns: (GeofenceRule) -> Unit = {},
    runHistoryState: GeofenceRuleRunHistoryUiState = GeofenceRuleRunHistoryUiState(),
    onDismissRuns: () -> Unit = {},
    operationErrorState: GeofenceRuleOperationErrorUiState = GeofenceRuleOperationErrorUiState(),
    onDismissOperationError: () -> Unit = {},
    onGeofenceMapLog: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var requestedPage by rememberSaveable { mutableStateOf(1) }
    var showPageJumpDialog by rememberSaveable { mutableStateOf(false) }
    var pageJumpDraft by rememberSaveable { mutableStateOf("") }
    var pageJumpHasError by rememberSaveable { mutableStateOf(false) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createDialogPendingForegroundPermission by rememberSaveable { mutableStateOf(false) }
    var createForegroundPermissionGranted by rememberSaveable { mutableStateOf(false) }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val contentPermissionStatus = permissionStatus.copy(
        foregroundGranted = permissionStatus.foregroundGranted || createForegroundPermissionGranted,
    )
    val page = buildGeofenceRulesUiPresentation(rules = rules, requestedPage = requestedPage)
    val pagerState = rememberPagerState(
        initialPage = (page.currentPage - 1).coerceAtLeast(0),
        pageCount = { page.totalPages },
    )
    val coroutineScope = rememberCoroutineScope()
    val pagerCurrentPage = (pagerState.currentPage + 1).coerceIn(1, page.totalPages)
    val editingRule = rules.firstOrNull { it.ruleId == editingRuleId }
    val pendingDeleteRule = rules.firstOrNull { it.ruleId == pendingDeleteRuleId }

    LaunchedEffect(rules.size) {
        requestedPage = buildGeofenceRulesUiPresentation(rules = rules, requestedPage = requestedPage).currentPage
    }

    LaunchedEffect(page.currentPage, page.totalPages) {
        val targetPageIndex = (page.currentPage - 1).coerceIn(0, page.totalPages - 1)
        if (pagerState.currentPage != targetPageIndex) {
            pagerState.scrollToPage(targetPageIndex)
        }
    }

    LaunchedEffect(pagerState, page.totalPages) {
        snapshotFlow { pagerState.currentPage }
            .collect { pageIndex ->
                requestedPage = (pageIndex + 1).coerceIn(1, page.totalPages)
            }
    }

    LaunchedEffect(permissionStatus.foregroundGranted) {
        if (permissionStatus.foregroundGranted) {
            createForegroundPermissionGranted = true
        }
    }

    val createForegroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            createForegroundPermissionGranted = true
        }
        createDialogPendingForegroundPermission = false
        showCreateDialog = true
    }

    fun openCreateDialogWithPermissionRequest() {
        if (contentPermissionStatus.foregroundGranted) {
            showCreateDialog = true
            return
        }
        if (createDialogPendingForegroundPermission) return
        createDialogPendingForegroundPermission = true
        createForegroundLocationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
        )
    }

    if (showCreateDialog) {
        CreateGeofenceRuleDialog(
            initialRule = null,
            initialTargetContext = defaultTargetContext,
            permissionStatus = contentPermissionStatus,
            mapAvailability = mapAvailability,
            locationPermissionUiState = locationPermissionUiState,
            botProfiles = botProfiles,
            conversationSessions = conversationSessions,
            initialSelectedBotId = selectedBotId,
            onUseCurrentLocationClick = onUseCurrentLocationClick,
            onForegroundLocationPermissionResult = onForegroundLocationPermissionResult,
            onLoadCurrentLocation = onLoadCurrentLocation,
            onDismissBackgroundPermissionGuide = onDismissBackgroundPermissionGuide,
            onBackgroundLocationSettingsOpened = onBackgroundLocationSettingsOpened,
            onGeofenceMapLog = onGeofenceMapLog,
            onDismiss = { showCreateDialog = false },
            onSave = { draft, savePermissionStatus ->
                onCreateRule(draft, savePermissionStatus)
                requestedPage = Int.MAX_VALUE
                showCreateDialog = false
            },
        )
    }

    if (editingRule != null) {
        CreateGeofenceRuleDialog(
            initialRule = editingRule,
            initialTargetContext = defaultTargetContext,
            permissionStatus = contentPermissionStatus,
            mapAvailability = mapAvailability,
            locationPermissionUiState = locationPermissionUiState,
            botProfiles = botProfiles,
            conversationSessions = conversationSessions,
            initialSelectedBotId = editingRule.targetBotId.ifBlank { selectedBotId },
            onUseCurrentLocationClick = onUseCurrentLocationClick,
            onForegroundLocationPermissionResult = onForegroundLocationPermissionResult,
            onLoadCurrentLocation = onLoadCurrentLocation,
            onDismissBackgroundPermissionGuide = onDismissBackgroundPermissionGuide,
            onBackgroundLocationSettingsOpened = onBackgroundLocationSettingsOpened,
            onGeofenceMapLog = onGeofenceMapLog,
            onDismiss = { editingRuleId = null },
            onSave = { draft, savePermissionStatus ->
                onUpdateRule(editingRule, draft, savePermissionStatus)
                editingRuleId = null
            },
        )
    }

    if (pendingDeleteRule != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteRuleId = null },
            title = { Text(stringResource(R.string.geofence_delete_confirm_title)) },
            text = { Text(stringResource(R.string.geofence_delete_confirm_message, pendingDeleteRule.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteRule(pendingDeleteRule.ruleId)
                        pendingDeleteRuleId = null
                    },
                ) {
                    Text(stringResource(R.string.geofence_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRuleId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (runHistoryState.visible) {
        GeofenceRuleRunsDialog(
            state = runHistoryState,
            onDismiss = onDismissRuns,
        )
    }

    if (showPageJumpDialog) {
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            title = { Text(stringResource(R.string.resource_list_page_jump_title)) },
            text = {
                OutlinedTextField(
                    value = pageJumpDraft,
                    onValueChange = { value ->
                        pageJumpDraft = value
                        pageJumpHasError = false
                    },
                    label = { Text(stringResource(R.string.resource_list_page_jump_label)) },
                    singleLine = true,
                    isError = pageJumpHasError,
                    modifier = Modifier.testTag("pager-jump-input"),
                    supportingText = {
                        if (pageJumpHasError) {
                            Text(stringResource(R.string.resource_list_page_jump_error, page.totalPages))
                        }
                    },
                    colors = monochromeOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetPage = pageJumpDraft.trim().toIntOrNull()
                        if (targetPage != null && targetPage in 1..page.totalPages) {
                            pageJumpHasError = false
                            showPageJumpDialog = false
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage - 1)
                            }
                        } else {
                            pageJumpHasError = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            if (operationErrorState.visible) {
                GeofenceOperationErrorBanner(
                    state = operationErrorState,
                    onDismiss = onDismissOperationError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                pageSpacing = 12.dp,
                userScrollEnabled = page.totalPages > 1,
            ) { pageIndex ->
                val pageForIndex = buildGeofenceRulesUiPresentation(
                    rules = rules,
                    requestedPage = pageIndex + 1,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pageForIndex.visibleRules.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                                color = MonochromeUi.cardBackground,
                                shape = MonochromeUi.radiusCard,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.geofence_rules_empty_hint),
                                        color = MonochromeUi.textSecondary,
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = pageForIndex.visibleRules,
                            key = { _, rule -> rule.ruleId },
                        ) { index, rule ->
                            GeofenceRuleCard(
                                rule = rule,
                                backgroundColor = if (index % 2 == 0) {
                                    MonochromeUi.cardBackground
                                } else {
                                    MonochromeUi.cardAltBackground
                                },
                                onEdit = { editingRuleId = rule.ruleId },
                                onPauseResume = {
                                    if (rule.enabled) {
                                        onPauseRule(rule.ruleId)
                                    } else {
                                        onResumeRule(rule.ruleId)
                                    }
                                },
                                onDelete = { pendingDeleteRuleId = rule.ruleId },
                                onRuns = {
                                    rules.firstOrNull { it.ruleId == rule.ruleId }?.let(onShowRuns)
                                },
                            )
                        }
                    }
                }
            }
            GeofencePagerBar(
                currentPage = pagerCurrentPage,
                totalPages = page.totalPages,
                canGoPrevious = pagerCurrentPage > 1,
                canGoNext = pagerCurrentPage < page.totalPages,
                onPrevious = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage((pagerCurrentPage - 2).coerceAtLeast(0))
                    }
                },
                onNext = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerCurrentPage.coerceAtMost(page.totalPages - 1))
                    }
                },
                onJump = {
                    pageJumpDraft = pagerCurrentPage.toString()
                    pageJumpHasError = false
                    showPageJumpDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )
        }
        FloatingActionButton(
            onClick = ::openCreateDialogWithPermissionRequest,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = FloatingBottomNavFabBottomPadding)
                .testTag("geofence-rules-add-fab"),
            containerColor = MonochromeUi.actionFabBackground,
            contentColor = MonochromeUi.actionFabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.geofence_rules_add_content_description),
            )
        }
    }
}

@Composable
private fun GeofencePagerBar(
    currentPage: Int,
    totalPages: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = canGoPrevious,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MonochromeUi.cardBackground,
                contentColor = MonochromeUi.textPrimary,
                disabledContentColor = MonochromeUi.textSecondary,
            ),
        ) {
            Text(stringResource(R.string.resource_list_pager_previous))
        }
        TextButton(
            onClick = onJump,
            modifier = Modifier
                .weight(1f)
                .testTag("pager-page"),
        ) {
            Text(
                text = stringResource(R.string.resource_list_pager_label, currentPage, totalPages),
                color = MonochromeUi.textPrimary,
            )
        }
        OutlinedButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MonochromeUi.cardBackground,
                contentColor = MonochromeUi.textPrimary,
                disabledContentColor = MonochromeUi.textSecondary,
            ),
        ) {
            Text(stringResource(R.string.resource_list_pager_next))
        }
    }
}

@Composable
private fun GeofenceOperationErrorBanner(
    state: GeofenceRuleOperationErrorUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MonochromeUi.inputBackground,
        shape = MonochromeUi.radiusCard,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.geofence_operation_error_title),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = state.message,
                    color = MonochromeUi.textPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}

@Composable
private fun CreateGeofenceRuleDialog(
    initialRule: GeofenceRule?,
    initialTargetContext: GeofenceRuleTargetContext,
    permissionStatus: GeofencePermissionStatus,
    mapAvailability: GeofenceMapAvailability,
    locationPermissionUiState: GeofenceLocationPermissionUiState,
    botProfiles: List<BotProfile>,
    conversationSessions: List<ConversationSession>,
    initialSelectedBotId: String,
    onUseCurrentLocationClick: (GeofencePermissionStatus) -> GeofenceLocationPermissionUiAction,
    onForegroundLocationPermissionResult: (Boolean) -> GeofenceLocationPermissionUiAction,
    onLoadCurrentLocation: ((GeofenceCurrentLocationSnapshot) -> Unit) -> Unit,
    onDismissBackgroundPermissionGuide: () -> Unit,
    onBackgroundLocationSettingsOpened: () -> GeofenceLocationPermissionUiAction,
    onGeofenceMapLog: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (GeofenceRuleEditorDraft, GeofencePermissionStatus) -> Unit,
) {
    val initialDraft = initialRule?.let(GeofenceRuleEditorDraft::fromRule)
        ?: GeofenceRuleEditorDraft.fromTargetContext(initialTargetContext)
    var editorStep by rememberSaveable(initialRule?.ruleId) { mutableStateOf(GeofenceRuleEditorStep.Location) }
    var name by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.name) }
    var description by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.description) }
    var latitude by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.latitude) }
    var longitude by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.longitude) }
    var radiusMeters by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.radiusMeters) }
    var addressLabel by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.addressLabel) }
    var triggerEnter by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.triggerEnter) }
    var triggerExit by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.triggerExit) }
    var triggerDwell by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.triggerDwell) }
    var dwellDelayMillis by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.dwellDelayMillis) }
    var actionType by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.actionType) }
    var actionPrompt by rememberSaveable(initialRule?.ruleId) { mutableStateOf(initialDraft.actionPrompt) }
    var conversationId by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(
            initialDraft.conversationId
                .ifBlank { initialTargetContext.conversationId }
                .ifBlank { conversationSessions.firstOrNull()?.id.orEmpty() },
        )
    }
    var selectedBotId by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(
            initialDraft.selectedBotId
                .ifBlank { initialSelectedBotId }
                .ifBlank { botProfiles.firstOrNull()?.id.orEmpty() },
        )
    }
    var minimumTriggerIntervalMillis by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(initialDraft.minimumTriggerIntervalMillis)
    }
    var foregroundGranted by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(permissionStatus.foregroundGranted)
    }
    var backgroundGranted by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(permissionStatus.backgroundGranted)
    }
    var runtimeMapAvailability by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(mapAvailability)
    }
    var runtimeMapLoaded by rememberSaveable(initialRule?.ruleId) {
        mutableStateOf(mapAvailability != GeofenceMapAvailability.AVAILABLE)
    }
    val effectivePermissionStatus = GeofencePermissionStatus(
        foregroundGranted = foregroundGranted,
        backgroundGranted = backgroundGranted,
    )
    val mapSelectorState = GeofenceMapSelectorState(
        latitudeText = latitude,
        longitudeText = longitude,
        radiusText = radiusMeters,
        addressLabel = addressLabel,
        availability = runtimeMapAvailability,
        mapLoaded = runtimeMapLoaded,
    )
    val inferredPlatform = conversationSessions
        .firstOrNull { it.id == conversationId }
        ?.targetRuntimePlatform()
        ?: initialDraft.platform.ifBlank {
            initialTargetContext.platform.ifBlank { RuntimePlatform.APP_CHAT.wireValue }
        }

    val draft = GeofenceRuleEditorDraft(
        name = name,
        description = description,
        enabled = initialDraft.enabled,
        regionLabel = "",
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        addressLabel = addressLabel,
        triggerEnter = triggerEnter,
        triggerExit = triggerExit,
        triggerDwell = triggerDwell,
        dwellDelayMillis = dwellDelayMillis,
        actionType = actionType,
        actionPrompt = actionPrompt,
        platform = inferredPlatform,
        conversationId = conversationId,
        selectedBotId = selectedBotId,
        configProfileId = "",
        personaId = "",
        providerId = "",
        minimumTriggerIntervalMillis = minimumTriggerIntervalMillis,
    )
    val selectedBot = selectedBotForDraft(draft, botProfiles)
    val targetErrorMessage = when {
        draft.enabled && selectedBotId.isBlank() -> stringResource(R.string.geofence_target_bot_required)
        draft.enabled && botProfiles.none { it.id == selectedBotId } -> {
            stringResource(R.string.geofence_target_bot_missing, selectedBotId)
        }
        draft.enabled && conversationSessions.none { it.id == conversationId } -> {
            stringResource(R.string.geofence_target_conversation_required)
        }
        else -> ""
    }

    LaunchedEffect(botProfiles) {
        if (selectedBotId.isBlank()) {
            val firstBot = botProfiles.firstOrNull()
            if (firstBot != null) {
                selectedBotId = firstBot.id
            }
        }
    }

    LaunchedEffect(conversationSessions) {
        if (conversationSessions.isNotEmpty() && conversationSessions.none { it.id == conversationId }) {
            conversationId = conversationSessions.first().id
        } else if (conversationId.isBlank()) {
            conversationId = conversationSessions.firstOrNull()?.id ?: initialTargetContext.conversationId
        }
    }

    LaunchedEffect(permissionStatus.foregroundGranted, permissionStatus.backgroundGranted) {
        if (permissionStatus.foregroundGranted) foregroundGranted = true
        if (permissionStatus.backgroundGranted) backgroundGranted = true
    }

    LaunchedEffect(mapAvailability) {
        runtimeMapAvailability = mapAvailability
        runtimeMapLoaded = mapAvailability != GeofenceMapAvailability.AVAILABLE
    }

    fun applyMapSelectorState(nextState: GeofenceMapSelectorState) {
        latitude = nextState.latitudeText
        longitude = nextState.longitudeText
        radiusMeters = nextState.radiusText
        addressLabel = nextState.addressLabel
        runtimeMapAvailability = nextState.availability
        runtimeMapLoaded = nextState.mapLoaded
    }

    fun loadCurrentLocationIntoDraft() {
        onLoadCurrentLocation { location ->
            applyMapSelectorState(mapSelectorState.applyCurrentLocation(location))
        }
    }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            foregroundGranted = true
        }
        when (onForegroundLocationPermissionResult(granted)) {
            GeofenceLocationPermissionUiAction.FETCH_CURRENT_LOCATION -> loadCurrentLocationIntoDraft()
            else -> Unit
        }
    }
    val context = LocalContext.current

    if (locationPermissionUiState.backgroundPermissionGuideVisible) {
        AlertDialog(
            onDismissRequest = onDismissBackgroundPermissionGuide,
            title = { Text(stringResource(R.string.geofence_background_location_title)) },
            text = {
                Text(
                    text = stringResource(R.string.geofence_background_location_message),
                    color = MonochromeUi.textPrimary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = onBackgroundLocationSettingsOpened()
                        if (action == GeofenceLocationPermissionUiAction.OPEN_BACKGROUND_LOCATION_SETTINGS) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.geofence_background_location_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBackgroundPermissionGuide) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initialRule == null) R.string.geofence_create_title else R.string.geofence_edit_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .height(GeofenceRuleEditorDialogHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 2.dp)
                    .testTag(
                        when (editorStep) {
                            GeofenceRuleEditorStep.Location -> "geofence-step-location"
                            GeofenceRuleEditorStep.Basic -> "geofence-step-basic"
                            GeofenceRuleEditorStep.Action -> "geofence-step-action"
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (editorStep) {
                    GeofenceRuleEditorStep.Location -> {
                        GeofenceDialogSectionTitle(R.string.geofence_section_location)
                        GeofenceMapSelector(
                            state = mapSelectorState,
                            currentLocationLoading = locationPermissionUiState.currentLocationLoading,
                            onStateChange = ::applyMapSelectorState,
                            onUseCurrentLocation = {
                                when (onUseCurrentLocationClick(effectivePermissionStatus)) {
                                    GeofenceLocationPermissionUiAction.REQUEST_FOREGROUND_LOCATION -> {
                                        foregroundLocationLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                            ),
                                        )
                                    }
                                    GeofenceLocationPermissionUiAction.FETCH_CURRENT_LOCATION -> loadCurrentLocationIntoDraft()
                                    else -> Unit
                                }
                            },
                            onGeofenceMapLog = onGeofenceMapLog,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GeofenceRuleEditorStep.Basic -> {
                        GeofenceDialogSectionTitle(R.string.geofence_section_basic)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.geofence_field_name)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("geofence-create-name"),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(R.string.geofence_field_description)) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        GeofenceBotSelectionField(
                            bots = botProfiles,
                            selectedBotId = selectedBotId,
                            onSelect = { bot -> selectedBotId = bot.id },
                        )
                        GeofenceConversationSelectionField(
                            sessions = conversationSessions,
                            selectedConversationId = conversationId,
                            onSelect = { conversationId = it },
                        )
                        if (targetErrorMessage.isNotBlank()) {
                            Text(
                                text = targetErrorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    GeofenceRuleEditorStep.Action -> {
                        GeofenceDialogSectionTitle(R.string.geofence_section_triggers)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = triggerEnter,
                                onClick = { triggerEnter = !triggerEnter },
                                label = { Text(stringResource(R.string.geofence_trigger_enter)) },
                            )
                            FilterChip(
                                selected = triggerExit,
                                onClick = { triggerExit = !triggerExit },
                                label = { Text(stringResource(R.string.geofence_trigger_exit)) },
                            )
                            FilterChip(
                                selected = triggerDwell,
                                onClick = { triggerDwell = !triggerDwell },
                                label = { Text(stringResource(R.string.geofence_trigger_dwell)) },
                            )
                        }
                        OutlinedTextField(
                            value = dwellDelayMillis,
                            onValueChange = { dwellDelayMillis = it },
                            label = { Text(stringResource(R.string.geofence_field_dwell_delay)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        GeofenceDialogSectionTitle(R.string.geofence_section_action)
                        GeofenceActionTypeField(
                            selectedActionType = actionType,
                            onSelect = { actionType = it },
                        )
                        OutlinedTextField(
                            value = actionPrompt,
                            onValueChange = { actionPrompt = it },
                            label = { Text(stringResource(R.string.geofence_field_action_prompt)) },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("geofence-create-action-prompt"),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = minimumTriggerIntervalMillis,
                            onValueChange = { minimumTriggerIntervalMillis = it },
                            label = { Text(stringResource(R.string.geofence_field_minimum_interval)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = monochromeOutlinedTextFieldColors(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        editorStep = when (editorStep) {
                            GeofenceRuleEditorStep.Location -> GeofenceRuleEditorStep.Location
                            GeofenceRuleEditorStep.Basic -> GeofenceRuleEditorStep.Location
                            GeofenceRuleEditorStep.Action -> GeofenceRuleEditorStep.Basic
                        }
                    },
                    enabled = editorStep != GeofenceRuleEditorStep.Location,
                    modifier = Modifier.testTag("geofence-dialog-previous"),
                ) {
                    Text(stringResource(R.string.geofence_dialog_previous))
                }
                if (editorStep == GeofenceRuleEditorStep.Action) {
                    TextButton(
                        onClick = {
                            if (selectedBot == null) return@TextButton
                            onSave(draft, effectivePermissionStatus)
                        },
                        enabled = draft.canSubmit() && selectedBot != null && targetErrorMessage.isBlank(),
                        modifier = Modifier.testTag("geofence-dialog-save"),
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                } else {
                    TextButton(
                        onClick = {
                            editorStep = when (editorStep) {
                                GeofenceRuleEditorStep.Location -> GeofenceRuleEditorStep.Basic
                                GeofenceRuleEditorStep.Basic -> GeofenceRuleEditorStep.Action
                                GeofenceRuleEditorStep.Action -> GeofenceRuleEditorStep.Action
                            }
                        },
                        modifier = Modifier.testTag("geofence-dialog-next"),
                    ) {
                        Text(stringResource(R.string.geofence_dialog_next))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun GeofenceMapSelector(
    state: GeofenceMapSelectorState,
    currentLocationLoading: Boolean,
    onStateChange: (GeofenceMapSelectorState) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onGeofenceMapLog: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("geofence-map-selector"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val selection = state.selection
        Text(
            text = if (selection == null) {
                stringResource(R.string.geofence_map_select_center_hint)
            } else {
                stringResource(R.string.geofence_map_selected_center_hint)
            },
            color = MonochromeUi.textSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        NetworkGeofenceMap(
            state = state,
            onGeofenceMapLog = onGeofenceMapLog,
            onMapSelect = { latitude, longitude, address ->
                val nextState = state.onMapClick(latitude = latitude, longitude = longitude)
                onStateChange(
                    if (address.isBlank()) {
                        nextState
                    } else {
                        nextState.onAddressLabelChange(address)
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        )
        OutlinedButton(
            onClick = onUseCurrentLocation,
            enabled = !currentLocationLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("geofence-use-current-location"),
        ) {
            Text(stringResource(R.string.geofence_use_current_location))
        }
        val sliderValue = state.selection?.radiusMeters
            ?.coerceIn(GeofenceRuleValidation.MIN_RADIUS_METERS, GeofenceMapMaxRadiusMeters)
            ?: GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("geofence-map-radius-control"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.geofence_field_radius),
                    color = MonochromeUi.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${sliderValue.toInt()} m",
                    color = MonochromeUi.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("geofence-map-radius-value"),
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { value -> onStateChange(state.onRadiusSliderChange(value)) },
                valueRange = GeofenceRuleValidation.MIN_RADIUS_METERS..GeofenceMapMaxRadiusMeters,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("geofence-map-radius-slider"),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun NetworkGeofenceMap(
    state: GeofenceMapSelectorState,
    onGeofenceMapLog: (String) -> Unit,
    onMapSelect: (Double, Double, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestSelectHandler = rememberUpdatedState(onMapSelect)
    val latestState = rememberUpdatedState(state)
    val latestLogHandler = rememberUpdatedState(onGeofenceMapLog)
    AndroidView(
        modifier = modifier.testTag("geofence-network-map"),
        factory = { context ->
            GeofenceMapHostView(
                context = context,
                onSelect = { latitude, longitude, address ->
                    latestSelectHandler.value(latitude, longitude, address)
                },
                onLog = { message -> latestLogHandler.value(message) },
                onStateReady = { host -> host.updateGeofenceMap(latestState.value, latestLogHandler.value) },
            ).apply {
                latestLogHandler.value(
                    "MapHost create availability=${state.availability} hasSelection=${state.selection != null}",
                )
                loadMap()
            }
        },
        update = { host ->
            host.updateGeofenceMap(state, onGeofenceMapLog)
        },
    )
}

private class GeofenceMapHostView(
    context: Context,
    private val onSelect: (Double, Double, String) -> Unit,
    private val onLog: (String) -> Unit,
    private val onStateReady: (GeofenceMapHostView) -> Unit,
) : FrameLayout(context) {
    private val density: Float = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
    private val webView: WebView = WebView(context)
    private val zoomControls: LinearLayout = LinearLayout(context)
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var lastDx: Float = 0f
    private var lastDy: Float = 0f
    private var dragMoved: Boolean = false
    private var pinchDistance: Float = 0f

    init {
        clipChildren = true
        clipToPadding = true
        isClickable = true
        minimumHeight = (GeofenceMapCssHeightPx * density).roundToInt()
        webView.apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            minimumHeight = this@GeofenceMapHostView.minimumHeight
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isClickable = false
            isFocusable = false
            webViewClient = GeofenceMapWebViewClient(
                onLog = onLog,
                onPageReady = { onStateReady(this@GeofenceMapHostView) },
            )
            webChromeClient = GeofenceMapWebChromeClient(onLog)
            addJavascriptInterface(
                GeofenceMapBridge(
                    onSelect = { latitude, longitude, address ->
                        post { onSelect(latitude, longitude, address) }
                    },
                    onLog = onLog,
                ),
                "ElymBotMap",
            )
        }
        addView(webView)
        addNativeZoomControls()
    }

    fun loadMap() {
        webView.loadDataWithBaseURL(
            "https://map.elymbot.invalid/",
            geofenceMapHtml(),
            "text/html",
            "UTF-8",
            null,
        )
    }

    fun updateGeofenceMap(
        state: GeofenceMapSelectorState,
        updateLog: (String) -> Unit = onLog,
    ) {
        resetNativePan()
        webView.updateGeofenceMap(state, updateLog)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isInsideZoomControls(event.x, event.y)) {
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> true
            else -> super.onInterceptTouchEvent(event)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInsideZoomControls(event.x, event.y)) {
            return super.onTouchEvent(event)
        }
        parent?.requestDisallowInterceptTouchEvent(true)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastDx = 0f
                lastDy = 0f
                dragMoved = false
                pinchDistance = 0f
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    dragMoved = true
                    pinchDistance = event.pointerDistance()
                    resetNativePan()
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    handleNativePinch(event)
                    true
                } else {
                    handleNativeDrag(event)
                    true
                }
            }
            MotionEvent.ACTION_UP -> {
                finishNativeGesture(event, allowTap = true)
                parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                finishNativeGesture(event, allowTap = false)
                parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> true
        }
    }

    private fun addNativeZoomControls() {
        zoomControls.orientation = LinearLayout.VERTICAL
        zoomControls.setBackgroundColor(Color.argb(238, 255, 255, 255))
        val buttonSize = (36f * density).roundToInt()
        val zoomIn = nativeZoomButton("+")
        val zoomOut = nativeZoomButton("-")
        zoomIn.setOnClickListener { zoomNativeMapBy(1) }
        zoomOut.setOnClickListener { zoomNativeMapBy(-1) }
        zoomControls.addView(zoomIn, LinearLayout.LayoutParams(buttonSize, buttonSize))
        zoomControls.addView(zoomOut, LinearLayout.LayoutParams(buttonSize, buttonSize))
        val margin = (10f * density).roundToInt()
        addView(
            zoomControls,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = margin
                rightMargin = margin
            },
        )
    }

    private fun nativeZoomButton(label: String): Button {
        return Button(context).apply {
            text = label
            textSize = 20f
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
        }
    }

    private fun handleNativeDrag(event: MotionEvent) {
        val index = event.findPointerIndex(activePointerId)
        if (index < 0) {
            return
        }
        val dx = event.getX(index) - downX
        val dy = event.getY(index) - downY
        if (!dragMoved && kotlin.math.abs(dx) + kotlin.math.abs(dy) > touchSlop) {
            dragMoved = true
        }
        if (dragMoved) {
            lastDx = dx
            lastDy = dy
            applyNativePan(dx, dy)
        }
    }

    private fun handleNativePinch(event: MotionEvent) {
        val nextDistance = event.pointerDistance()
        if (pinchDistance <= 0f) {
            pinchDistance = nextDistance
            return
        }
        when {
            nextDistance > pinchDistance * 1.28f -> {
                zoomNativeMapBy(1)
                pinchDistance = nextDistance
            }
            nextDistance < pinchDistance * 0.78f -> {
                zoomNativeMapBy(-1)
                pinchDistance = nextDistance
            }
        }
    }

    private fun finishNativeGesture(event: MotionEvent, allowTap: Boolean) {
        if (dragMoved) {
            commitNativePan(lastDx, lastDy)
        } else if (allowTap) {
            tapNativeMap(event.x, event.y)
        } else {
            resetNativePan()
        }
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pinchDistance = 0f
        dragMoved = false
        lastDx = 0f
        lastDy = 0f
    }

    private fun applyNativePan(dx: Float, dy: Float) {
        webView.translationX = dx
        webView.translationY = dy
    }

    private fun resetNativePan() {
        webView.translationX = 0f
        webView.translationY = 0f
    }

    private fun commitNativePan(dx: Float, dy: Float) {
        resetNativePan()
        webView.evaluateJavascript(
            "window.elymbotNativeCommitPan && window.elymbotNativeCommitPan(${dx / density}, ${dy / density});",
            null,
        )
    }

    private fun tapNativeMap(x: Float, y: Float) {
        val clientX = x / density
        val clientY = y / density
        webView.evaluateJavascript(
            "window.elymbotNativeTap && window.elymbotNativeTap($clientX, $clientY);",
            null,
        )
    }

    private fun zoomNativeMapBy(delta: Int) {
        resetNativePan()
        webView.evaluateJavascript(
            "window.elymbotNativeZoomBy && window.elymbotNativeZoomBy($delta);",
            null,
        )
    }

    private fun isInsideZoomControls(x: Float, y: Float): Boolean {
        return x >= zoomControls.left &&
            x <= zoomControls.right &&
            y >= zoomControls.top &&
            y <= zoomControls.bottom
    }

    private fun MotionEvent.pointerDistance(): Float {
        if (pointerCount < 2) {
            return 0f
        }
        val dx = getX(0) - getX(1)
        val dy = getY(0) - getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

private class GeofenceMapBridge(
    private val onSelect: (Double, Double, String) -> Unit,
    private val onLog: (String) -> Unit,
) {
    @JavascriptInterface
    fun select(latitude: Double, longitude: Double, addressLabel: String) {
        onSelect(latitude, longitude, addressLabel)
    }

    @JavascriptInterface
    fun log(message: String) {
        onLog(message.take(240))
    }
}

private class GeofenceMapWebViewClient(
    private val onLog: (String) -> Unit,
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    private var resourceErrorLogCount = 0

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onLog("WebView page started url=${url.orEmpty()}")
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        onLog("WebView page finished url=${url.orEmpty()}")
        if (view != null) {
            onPageReady(view)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        val isMainFrame = request?.isForMainFrame == true
        if (isMainFrame || resourceErrorLogCount < 6) {
            resourceErrorLogCount += 1
            onLog(
                "WebView resource error main=$isMainFrame code=${error?.errorCode} " +
                    "description=${error?.description} url=${request?.url}",
            )
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        val isMainFrame = request?.isForMainFrame == true
        if (isMainFrame || resourceErrorLogCount < 6) {
            resourceErrorLogCount += 1
            onLog(
                "WebView HTTP error main=$isMainFrame status=${errorResponse?.statusCode} " +
                    "reason=${errorResponse?.reasonPhrase} url=${request?.url}",
            )
        }
    }
}

private class GeofenceMapWebChromeClient(
    private val onLog: (String) -> Unit,
) : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage != null) {
            onLog(
                "JS console ${consoleMessage.messageLevel()}: ${consoleMessage.message()} " +
                    "@${consoleMessage.lineNumber()}",
            )
        }
        return true
    }
}

private fun WebView.updateGeofenceMap(
    state: GeofenceMapSelectorState,
    onLog: (String) -> Unit = {},
) {
    val selection = state.selection
    val cameraCenter = state.cameraCenter
    val radius = selection?.radiusMeters ?: GeofenceRuleValidation.RECOMMENDED_MIN_RADIUS_METERS
    val hasSelection = selection != null
    val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
    val cssWidth = (width / density).roundToInt().coerceAtLeast(1)
    val cssHeight = (height / density).roundToInt().coerceAtLeast(GeofenceMapCssHeightPx)
    evaluateJavascript(
        """
        (function() {
          if (window.elymbotForceMapViewport) {
            window.elymbotForceMapViewport($cssWidth, $cssHeight);
          }
          if (window.elymbotUpdateMap) {
            window.elymbotUpdateMap(${cameraCenter.latitude}, ${cameraCenter.longitude}, $radius, $hasSelection);
            return true;
          }
          return false;
        })();
        """.trimIndent(),
        { result ->
            if (result != "true") {
                onLog("updateMap skipped result=$result url=$url native=${width}x${height} css=${cssWidth}x${cssHeight}")
            }
        },
    )
}

private fun geofenceMapHtml(): String =
    """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <link rel="icon" href="data:,">
        <style>
          html, body {
            width: 100%;
            height: 100%;
            min-height: 100vh;
            min-height: ${GeofenceMapCssHeightPx}px;
            margin: 0;
            padding: 0;
            background-color: #eeeeee;
          }
          body {
            position: fixed;
            inset: 0;
            overflow: hidden;
          }
          #map {
            position: absolute;
            inset: 0;
            width: 100vw;
            height: 100vh;
            height: ${GeofenceMapCssHeightPx}px;
            min-height: 100vh;
            min-height: ${GeofenceMapCssHeightPx}px;
            overflow: hidden;
            touch-action: none;
            background-color: #eeeeee;
            background-image:
              linear-gradient(rgba(0, 0, 0, 0.06) 1px, transparent 1px),
              linear-gradient(90deg, rgba(0, 0, 0, 0.06) 1px, transparent 1px);
            background-size: 48px 48px;
            font-family: sans-serif;
          }
          #tileLayer {
            position: absolute;
            inset: 0;
            will-change: transform;
            transform: translate3d(0, 0, 0);
          }
          .tile {
            position: absolute;
            width: 256px;
            height: 256px;
            user-select: none;
            -webkit-user-drag: none;
            background: transparent;
          }
          .tile-error {
            opacity: 0;
          }
          .zoom-controls {
            position: absolute;
            top: 10px;
            right: 10px;
            z-index: 8;
            display: none;
            flex-direction: column;
            overflow: hidden;
            border: 1px solid rgba(17, 17, 17, 0.16);
            border-radius: 8px;
            background: rgba(255, 255, 255, 0.94);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
          }
          .zoom-button {
            width: 36px;
            height: 36px;
            border: 0;
            border-bottom: 1px solid rgba(17, 17, 17, 0.12);
            background: transparent;
            color: #111111;
            font-size: 24px;
            line-height: 36px;
            font-family: sans-serif;
            font-weight: 700;
            text-align: center;
          }
          .zoom-button:last-child {
            border-bottom: 0;
          }
          .zoom-button:disabled {
            color: rgba(17, 17, 17, 0.32);
          }
          #circle {
            position: absolute;
            display: none;
            border: 2px solid #111111;
            border-radius: 9999px;
            background: rgba(17, 17, 17, 0.12);
            box-sizing: border-box;
            pointer-events: none;
            will-change: transform;
            transform: translate3d(0, 0, 0);
          }
          #marker {
            position: absolute;
            display: none;
            width: 18px;
            height: 18px;
            margin-left: -9px;
            margin-top: -9px;
            border: 3px solid #ffffff;
            border-radius: 9999px;
            background: #111111;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.32);
            box-sizing: border-box;
            pointer-events: none;
            will-change: transform;
            transform: translate3d(0, 0, 0);
          }
        </style>
      </head>
      <body>
        <div id="map">
          <div id="tileLayer"></div>
          <div id="circle"></div>
          <div id="marker"></div>
          <div class="zoom-controls" aria-label="Map zoom controls">
            <button id="zoomInButton" class="zoom-button" type="button" aria-label="Zoom in" data-testid="geofence-map-zoom-in">+</button>
            <button id="zoomOutButton" class="zoom-button" type="button" aria-label="Zoom out" data-testid="geofence-map-zoom-out">-</button>
          </div>
        </div>
        <script>
          const defaultLat = 31.2304;
          const defaultLng = 121.4737;
          const defaultZoom = $GeofenceMapDefaultZoom;
          const minZoom = $GeofenceMapMinZoom;
          const maxZoom = $GeofenceMapMaxZoom;
          const tileSize = 256;
          const mapElement = document.getElementById('map');
          const tileLayer = document.getElementById('tileLayer');
          const markerElement = document.getElementById('marker');
          const circleElement = document.getElementById('circle');
          const zoomInButton = document.getElementById('zoomInButton');
          const zoomOutButton = document.getElementById('zoomOutButton');
          let centerLat = defaultLat;
          let centerLng = defaultLng;
          let zoom = clampZoom(defaultZoom);
          let currentRadius = 150;
          let selectedLat = defaultLat;
          let selectedLng = defaultLng;
          let hasCurrentSelection = false;
          let renderRetryCount = 0;
          let didReportRenderReady = false;
          let didReportForcedViewport = false;
          let tileFailureLogCount = 0;
          let currentPanOffset = { x: 0, y: 0 };

          function report(message) {
            const text = String(message || '');
            console.log('Geofence map ' + text);
            try {
              if (window.ElymBotMap && window.ElymBotMap.log) {
                window.ElymBotMap.log(text);
              }
            } catch (error) {
              console.log('Geofence map log bridge failed', error);
            }
          }

          function clampLatitude(lat) {
            return Math.max(-85.05112878, Math.min(85.05112878, lat));
          }

          function worldSize() {
            return tileSize * Math.pow(2, zoom);
          }

          function latLngToWorld(lat, lng) {
            const sinLat = Math.sin(clampLatitude(lat) * Math.PI / 180);
            const size = worldSize();
            return {
              x: (lng + 180) / 360 * size,
              y: (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * size
            };
          }

          function worldToLatLng(x, y) {
            const size = worldSize();
            const lng = x / size * 360 - 180;
            const n = Math.PI - 2 * Math.PI * y / size;
            const lat = 180 / Math.PI * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)));
            return { lat: clampLatitude(lat), lng: lng };
          }

          function outOfChina(lat, lng) {
            return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
          }

          function transformLat(x, y) {
            let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y +
              0.2 * Math.sqrt(Math.abs(x));
            ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
            ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
            ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
            return ret;
          }

          function transformLng(x, y) {
            let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y +
              0.1 * Math.sqrt(Math.abs(x));
            ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
            ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
            ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
            return ret;
          }

          function wgsToGcj(lat, lng) {
            if (outOfChina(lat, lng)) {
              return { lat: lat, lng: lng };
            }
            const a = 6378245.0;
            const ee = 0.00669342162296594323;
            let dLat = transformLat(lng - 105.0, lat - 35.0);
            let dLng = transformLng(lng - 105.0, lat - 35.0);
            const radLat = lat / 180.0 * Math.PI;
            let magic = Math.sin(radLat);
            magic = 1 - ee * magic * magic;
            const sqrtMagic = Math.sqrt(magic);
            dLat = (dLat * 180.0) / ((a * (1 - ee)) / (magic * sqrtMagic) * Math.PI);
            dLng = (dLng * 180.0) / (a / sqrtMagic * Math.cos(radLat) * Math.PI);
            return { lat: lat + dLat, lng: lng + dLng };
          }

          function gcjToWgs(lat, lng) {
            if (outOfChina(lat, lng)) {
              return { lat: lat, lng: lng };
            }
            const gcj = wgsToGcj(lat, lng);
            return { lat: lat * 2 - gcj.lat, lng: lng * 2 - gcj.lng };
          }

          function latLngToDisplayWorld(lat, lng) {
            const display = wgsToGcj(lat, lng);
            return latLngToWorld(display.lat, display.lng);
          }

          function displayWorldToLatLng(x, y) {
            const display = worldToLatLng(x, y);
            return gcjToWgs(display.lat, display.lng);
          }

          function clampZoom(value) {
            const numeric = Number(value);
            const rounded = Math.round(Number.isFinite(numeric) ? numeric : defaultZoom);
            return Math.max(minZoom, Math.min(maxZoom, rounded));
          }

          function updateZoomControls() {
            if (zoomInButton) {
              zoomInButton.disabled = zoom >= maxZoom;
            }
            if (zoomOutButton) {
              zoomOutButton.disabled = zoom <= minZoom;
            }
          }

          function setPanOffset(dx, dy) {
            currentPanOffset = { x: dx, y: dy };
            const transform = 'translate3d(' + Math.round(dx) + 'px, ' + Math.round(dy) + 'px, 0)';
            tileLayer.style.transform = transform;
            markerElement.style.transform = transform;
            circleElement.style.transform = transform;
          }

          function resetPanOffset() {
            setPanOffset(0, 0);
          }

          function zoomAroundClientPoint(nextZoom, clientX, clientY, reason) {
            resetPanOffset();
            const targetZoom = clampZoom(nextZoom);
            if (targetZoom === zoom) {
              updateZoomControls();
              return;
            }
            const rect = mapElement.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) {
              zoom = targetZoom;
              renderTiles();
              renderSelectionOverlay();
              updateZoomControls();
              report('zoom changed zoom=' + zoom + ' reason=' + reason + ' without-anchor');
              return;
            }
            const anchor = latLngForPoint(clientX, clientY);
            const offsetX = clientX - rect.left;
            const offsetY = clientY - rect.top;
            zoom = targetZoom;
            const anchorWorld = latLngToDisplayWorld(anchor.lat, anchor.lng);
            const centerWorldX = anchorWorld.x - (offsetX - rect.width / 2);
            const centerWorldY = anchorWorld.y - (offsetY - rect.height / 2);
            const nextCenter = displayWorldToLatLng(centerWorldX, centerWorldY);
            centerLat = nextCenter.lat;
            centerLng = nextCenter.lng;
            renderTiles();
            renderSelectionOverlay();
            updateZoomControls();
            report('zoom changed zoom=' + zoom + ' reason=' + reason);
          }

          function zoomBy(delta, reason) {
            const rect = mapElement.getBoundingClientRect();
            zoomAroundClientPoint(
              zoom + delta,
              rect.left + rect.width / 2,
              rect.top + rect.height / 2,
              reason
            );
          }

          const mainlandTileSources = [
            {
              name: 'amap-webrd01',
              region: 'mainland',
              url: function(z, x, y) {
                return 'https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=' +
                  x + '&y=' + y + '&z=' + z;
              }
            },
            {
              name: 'amap-webrd02',
              region: 'mainland',
              url: function(z, x, y) {
                return 'https://webrd02.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=' +
                  x + '&y=' + y + '&z=' + z;
              }
            },
            {
              name: 'amap-webrd03',
              region: 'mainland',
              url: function(z, x, y) {
                return 'https://webrd03.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x=' +
                  x + '&y=' + y + '&z=' + z;
              }
            }
          ];
          const overseasTileSources = [
            {
              name: 'osm-standard',
              region: 'overseas',
              url: function(z, x, y) {
                return 'https://tile.openstreetmap.org/' + z + '/' + x + '/' + y + '.png';
              }
            },
            {
              name: 'osm-hot',
              region: 'overseas',
              url: function(z, x, y) {
                return 'https://a.tile.openstreetmap.fr/hot/' + z + '/' + x + '/' + y + '.png';
              }
            }
          ];
          const tileSources = mainlandTileSources.concat(overseasTileSources);

          function tileUrl(sourceIndex, z, x, y) {
            const source = tileSources[Math.max(0, Math.min(tileSources.length - 1, sourceIndex))];
            return source.url(z, x, y);
          }

          function wrapTileX(tileX, maxTile) {
            return ((tileX % maxTile) + maxTile) % maxTile;
          }

          window.elymbotForceMapViewport = function(widthCssPx, heightCssPx) {
            const nextWidth = Math.max(1, Number(widthCssPx) || mapElement.clientWidth || 1);
            const nextHeight = Math.max($GeofenceMapCssHeightPx, Number(heightCssPx) || mapElement.clientHeight || 0);
            document.documentElement.style.height = nextHeight + 'px';
            document.body.style.height = nextHeight + 'px';
            document.body.style.minHeight = nextHeight + 'px';
            mapElement.style.width = nextWidth + 'px';
            mapElement.style.height = nextHeight + 'px';
            mapElement.style.minHeight = nextHeight + 'px';
            if (!didReportForcedViewport) {
              didReportForcedViewport = true;
              report('forced viewport css=' + Math.round(nextWidth) + 'x' + Math.round(nextHeight));
            }
          };

          function scheduleRenderRetry(reason) {
            if (renderRetryCount >= 8) {
              report('render retry limit reached reason=' + reason);
              return;
            }
            renderRetryCount += 1;
            requestAnimationFrame(function() {
              renderTiles();
              renderSelectionOverlay();
            });
            window.setTimeout(function() {
              renderTiles();
              renderSelectionOverlay();
            }, 120);
          }

          function renderTiles() {
            const rect = mapElement.getBoundingClientRect();
            if (rect.width <= 0 || rect.height <= 0) {
              report('render skipped rect=' + Math.round(rect.width) + 'x' + Math.round(rect.height));
              scheduleRenderRetry('zero-rect');
              return;
            }
            renderRetryCount = 0;
            if (!didReportRenderReady) {
              didReportRenderReady = true;
              report('render ready rect=' + Math.round(rect.width) + 'x' + Math.round(rect.height) + ' zoom=' + zoom);
            }
            const center = latLngToDisplayWorld(centerLat, centerLng);
            const originX = center.x - rect.width / 2;
            const originY = center.y - rect.height / 2;
            const firstTileX = Math.floor(originX / tileSize);
            const firstTileY = Math.floor(originY / tileSize);
            const lastTileX = Math.floor((originX + rect.width) / tileSize);
            const lastTileY = Math.floor((originY + rect.height) / tileSize);
            const maxTile = Math.pow(2, zoom);
            let html = '';
            for (let tileX = firstTileX; tileX <= lastTileX; tileX += 1) {
              for (let tileY = firstTileY; tileY <= lastTileY; tileY += 1) {
                if (tileY < 0 || tileY >= maxTile) {
                  continue;
                }
                const wrappedX = wrapTileX(tileX, maxTile);
                const left = Math.round(tileX * tileSize - originX);
                const top = Math.round(tileY * tileSize - originY);
                const url = tileUrl(0, zoom, wrappedX, tileY);
                html += '<img class="tile" alt="" src="' + url + '" style="left:' +
                  left + 'px;top:' + top + 'px" data-source-index="0" data-z="' + zoom +
                  '" data-x="' + wrappedX + '" data-y="' + tileY + '">';
              }
            }
            tileLayer.innerHTML = html;
            const tiles = tileLayer.querySelectorAll('img');
            for (let index = 0; index < tiles.length; index += 1) {
              tiles[index].onerror = function() {
                const nextSourceIndex = Number(this.dataset.sourceIndex || '0') + 1;
                if (nextSourceIndex < tileSources.length) {
                  this.dataset.sourceIndex = String(nextSourceIndex);
                  const nextSource = tileSources[nextSourceIndex];
                  if (tileFailureLogCount < 6) {
                    tileFailureLogCount += 1;
                    report('tile fallback source=' + nextSource.name + ' region=' + nextSource.region);
                  }
                  this.src = tileUrl(
                    nextSourceIndex,
                    Number(this.dataset.z),
                    Number(this.dataset.x),
                    Number(this.dataset.y)
                  );
                } else {
                  if (tileFailureLogCount < 6) {
                    tileFailureLogCount += 1;
                    report('tile failed all sources z=' + this.dataset.z + ' x=' + this.dataset.x + ' y=' + this.dataset.y);
                  }
                  this.classList.add('tile-error');
                }
              };
            }
          }

          function pointForLatLng(lat, lng) {
            const rect = mapElement.getBoundingClientRect();
            const center = latLngToDisplayWorld(centerLat, centerLng);
            const point = latLngToDisplayWorld(lat, lng);
            return {
              x: rect.width / 2 + (point.x - center.x),
              y: rect.height / 2 + (point.y - center.y)
            };
          }

          function latLngForPoint(clientX, clientY) {
            const rect = mapElement.getBoundingClientRect();
            const center = latLngToDisplayWorld(centerLat, centerLng);
            const worldX = center.x - rect.width / 2 + (clientX - rect.left);
            const worldY = center.y - rect.height / 2 + (clientY - rect.top);
            return displayWorldToLatLng(worldX, worldY);
          }

          function radiusMetersToPixels(radiusMeters, lat) {
            const metersPerPixel = 156543.03392 * Math.cos(clampLatitude(lat) * Math.PI / 180) / Math.pow(2, zoom);
            return Math.max(12, radiusMeters / Math.max(1, metersPerPixel));
          }

          function renderSelectionOverlay() {
            if (!hasCurrentSelection) {
              markerElement.style.display = 'none';
              circleElement.style.display = 'none';
              return;
            }
            const point = pointForLatLng(selectedLat, selectedLng);
            const radiusPixels = radiusMetersToPixels(currentRadius, selectedLat);
            markerElement.style.display = 'block';
            markerElement.style.left = point.x + 'px';
            markerElement.style.top = point.y + 'px';
            circleElement.style.display = 'block';
            circleElement.style.left = (point.x - radiusPixels) + 'px';
            circleElement.style.top = (point.y - radiusPixels) + 'px';
            circleElement.style.width = (radiusPixels * 2) + 'px';
            circleElement.style.height = (radiusPixels * 2) + 'px';
          }

          function renderSelection(lat, lng, radius, hasSelection, moveCamera) {
            resetPanOffset();
            if (moveCamera) {
              centerLat = lat;
              centerLng = lng;
            }
            currentRadius = radius;
            hasCurrentSelection = hasSelection;
            if (hasSelection) {
              selectedLat = lat;
              selectedLng = lng;
            }
            renderTiles();
            renderSelectionOverlay();
          }

          window.elymbotUpdateMap = function(lat, lng, radius, hasSelection) {
            renderSelection(lat, lng, radius, hasSelection, true);
          };
          window.elymbotRenderTiles = renderTiles;
          window.elymbotNativeCommitPan = function(dx, dy) {
            const center = latLngToDisplayWorld(centerLat, centerLng);
            const nextCenter = displayWorldToLatLng(center.x - Number(dx || 0), center.y - Number(dy || 0));
            centerLat = nextCenter.lat;
            centerLng = nextCenter.lng;
            resetPanOffset();
            renderTiles();
            renderSelectionOverlay();
            return true;
          };
          window.elymbotNativeTap = function(clientX, clientY) {
            const selected = latLngForPoint(Number(clientX || 0), Number(clientY || 0));
            if (window.ElymBotMap && window.ElymBotMap.select) {
              window.ElymBotMap.select(selected.lat, selected.lng, '');
            }
            renderSelection(selected.lat, selected.lng, currentRadius, true, false);
            return true;
          };
          window.elymbotNativeZoomBy = function(delta) {
            zoomBy(Number(delta || 0), 'native-button');
            return true;
          };

          function isZoomControlEvent(event) {
            return event.target && event.target.closest && event.target.closest('.zoom-controls');
          }

          if (zoomInButton && zoomOutButton) {
            [zoomInButton, zoomOutButton].forEach(function(button) {
              ['pointerdown', 'pointermove', 'pointerup', 'click'].forEach(function(type) {
                button.addEventListener(type, function(event) {
                  event.stopPropagation();
                });
              });
            });
            zoomInButton.addEventListener('click', function(event) {
              event.preventDefault();
              zoomBy(1, 'button-in');
            });
            zoomOutButton.addEventListener('click', function(event) {
              event.preventDefault();
              zoomBy(-1, 'button-out');
            });
          }

          mapElement.addEventListener('wheel', function(event) {
            if (isZoomControlEvent(event)) {
              return;
            }
            event.preventDefault();
            zoomAroundClientPoint(
              zoom + (event.deltaY < 0 ? 1 : -1),
              event.clientX,
              event.clientY,
              'wheel'
            );
          }, { passive: false });

          report('Geofence map HTML ready');
          updateZoomControls();
          requestAnimationFrame(function() {
            renderTiles();
            renderSelectionOverlay();
          });
          window.setTimeout(function() {
            renderTiles();
            renderSelectionOverlay();
          }, 120);
          window.addEventListener('resize', function() {
            report('resize');
            renderTiles();
            renderSelectionOverlay();
          });
        </script>
      </body>
    </html>
    """.trimIndent()

@Composable
private fun GeofenceRuleCard(
    rule: com.elymbot.android.feature.geofence.presentation.GeofenceRuleUiListItemPresentation,
    backgroundColor: androidx.compose.ui.graphics.Color,
    onEdit: () -> Unit,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    onRuns: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = backgroundColor,
        shape = MonochromeUi.radiusCard,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = rule.name,
                    modifier = Modifier.weight(1f),
                    color = MonochromeUi.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                GeofenceStatusPill(rule.statusSummary)
            }
            GeofenceInfoRow(
                label = stringResource(R.string.geofence_card_region),
                value = rule.regionSummary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GeofenceInfoRow(
                    label = stringResource(R.string.geofence_card_triggers),
                    value = rule.triggerSummary,
                    modifier = Modifier.weight(1f),
                )
                GeofenceInfoRow(
                    label = stringResource(R.string.geofence_card_last_triggered),
                    value = formatTimestampOrUnavailable(rule.lastTriggeredAt),
                    modifier = Modifier.weight(1f),
                )
            }
            GeofenceInfoRow(
                label = stringResource(R.string.geofence_card_action),
                value = rule.actionSummary,
            )
            if (rule.lastError.isNotBlank()) {
                GeofenceInfoRow(
                    label = stringResource(R.string.geofence_card_error),
                    value = rule.lastError,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRuns) {
                    Text(stringResource(R.string.geofence_action_runs))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.geofence_card_enabled),
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onPauseResume() },
                        modifier = Modifier.testTag("geofence-rule-enabled-switch-${rule.ruleId}"),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.geofence_action_edit),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.geofence_action_delete),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeofenceStatusPill(status: String) {
    Surface(
        color = MonochromeUi.inputBackground,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MonochromeUi.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun GeofenceRuleRunsDialog(
    state: GeofenceRuleRunHistoryUiState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("geofence-runs-dialog"),
        title = { Text(stringResource(R.string.geofence_runs_title, state.ruleName)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    state.loading -> Text(
                        text = stringResource(R.string.geofence_runs_loading),
                        color = MonochromeUi.textSecondary,
                    )
                    state.errorMessage.isNotBlank() -> Text(
                        text = stringResource(R.string.geofence_runs_error, state.errorMessage),
                        color = MonochromeUi.textSecondary,
                    )
                    state.runs.isEmpty() -> Text(
                        text = stringResource(R.string.geofence_runs_empty),
                        color = MonochromeUi.textSecondary,
                    )
                    else -> buildGeofenceRuleRunUiPresentations(state.runs).forEach { run ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.geofence_runs_status_line, run.transition, run.status),
                                color = MonochromeUi.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.geofence_runs_time_line,
                                    formatTimestampOrUnavailable(run.startedAt),
                                    formatTimestampOrUnavailable(run.completedAt),
                                ),
                                color = MonochromeUi.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (run.errorCode.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.geofence_runs_error_code, run.errorCode),
                                    color = MonochromeUi.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (run.summary.isNotBlank()) {
                                Text(
                                    text = run.summary,
                                    color = MonochromeUi.textPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}

@Composable
private fun GeofenceDialogSectionTitle(stringId: Int) {
    Text(
        text = stringResource(stringId),
        color = MonochromeUi.textPrimary,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun GeofenceSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, color = MonochromeUi.textPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GeofenceActionTypeField(
    selectedActionType: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedActionType) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.geofence_field_action_type),
            color = MonochromeUi.textPrimary,
            style = MaterialTheme.typography.bodySmall,
        )
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedActionType,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GeofenceActionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.persistedValue) },
                    onClick = {
                        onSelect(type.persistedValue)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun GeofenceBotSelectionField(
    bots: List<BotProfile>,
    selectedBotId: String,
    onSelect: (BotProfile) -> Unit,
) {
    var expanded by remember(selectedBotId, bots) { mutableStateOf(false) }
    val selectedBot = bots.firstOrNull { it.id == selectedBotId }
    val summary = selectedBot?.displayName ?: selectedBotId.ifBlank { stringResource(R.string.common_not_selected) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.geofence_field_bot),
            color = MonochromeUi.textPrimary,
        )
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (bots.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.geofence_field_bot_no_options)) },
                    onClick = { expanded = false },
                )
            } else {
                bots.forEach { bot ->
                    DropdownMenuItem(
                        text = { Text(bot.displayName) },
                        onClick = {
                            onSelect(bot)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeofenceConversationSelectionField(
    sessions: List<ConversationSession>,
    selectedConversationId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember(selectedConversationId, sessions) { mutableStateOf(false) }
    val selectedSession = sessions.firstOrNull { it.id == selectedConversationId }
    val summary = selectedSession?.displayLabel()
        ?: selectedConversationId.ifBlank { stringResource(R.string.common_not_selected) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.geofence_field_conversation_id),
            color = MonochromeUi.textPrimary,
        )
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MonochromeUi.inputBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (sessions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_not_available)) },
                    onClick = { expanded = false },
                )
            } else {
                sessions.forEach { session ->
                    DropdownMenuItem(
                        text = { Text(session.displayLabel()) },
                        onClick = {
                            onSelect(session.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun ConversationSession.displayLabel(): String =
    title.ifBlank { originSessionId }.ifBlank { id }

private fun ConversationSession.targetRuntimePlatform(): String =
    if (platformId == "qq") RuntimePlatform.QQ_ONEBOT.wireValue else RuntimePlatform.APP_CHAT.wireValue

@Composable
private fun GeofenceInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = label,
            color = MonochromeUi.textSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value.ifBlank { stringResource(R.string.common_not_available) },
            color = MonochromeUi.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun selectedBotForDraft(
    draft: GeofenceRuleEditorDraft,
    botProfiles: List<BotProfile>,
): BotProfile? {
    return botProfiles.firstOrNull { it.id == draft.selectedBotId }
        ?: if (!draft.enabled) {
            BotProfile(
                id = draft.selectedBotId.ifBlank { "manual-geofence-bot" },
                displayName = draft.selectedBotId.ifBlank { "Manual geofence target" },
                configProfileId = draft.configProfileId,
                defaultPersonaId = draft.personaId,
                defaultProviderId = draft.providerId,
            )
        } else {
            null
        }
}

private val geofenceTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatTimestampOrUnavailable(timestamp: Long): String {
    return if (timestamp > 0L) {
        geofenceTimeFormatter.format(Instant.ofEpochMilli(timestamp))
    } else {
        "-"
    }
}
