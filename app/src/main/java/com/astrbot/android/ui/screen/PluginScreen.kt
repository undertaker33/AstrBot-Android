package com.astrbot.android.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.di.astrBotViewModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.screen.plugin.PluginBadgePalette
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryCardUiState
import com.astrbot.android.ui.viewmodel.PluginDetailActionState
import com.astrbot.android.ui.viewmodel.PluginDetailMetadataState
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginRepositorySourceCardUiState
import com.astrbot.android.ui.viewmodel.PluginSchemaUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.viewmodel.PluginSettingDraftValue
import com.astrbot.android.ui.viewmodel.PluginViewModel
import com.astrbot.android.ui.screen.plugin.schema.PluginSchemaRenderer
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PluginScreen(
    pluginViewModel: PluginViewModel = astrBotViewModel(),
    workspaceTab: PluginWorkspaceTab = PluginWorkspaceTab.LOCAL,
    onOpenPluginDetail: (String) -> Unit = {},
) {
    val uiState by pluginViewModel.uiState.collectAsState()
    val localPackagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pluginViewModel.submitLocalPackageUri(it.toString()) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MonochromeUi.pageBackground),
    ) {
        when (workspaceTab) {
            PluginWorkspaceTab.LOCAL -> PluginLocalWorkspace(
                uiState = uiState,
                onSearchQueryChange = pluginViewModel::updateLocalSearchQuery,
                onFilterSelected = pluginViewModel::updateSelectedLocalFilter,
                onOpenPluginDetail = onOpenPluginDetail,
                onRepositoryUrlDraftChange = pluginViewModel::updateRepositoryUrlDraft,
                onDirectPackageUrlDraftChange = pluginViewModel::updateDirectPackageUrlDraft,
                onSubmitRepositoryUrl = pluginViewModel::submitRepositoryUrl,
                onSubmitDirectPackageUrl = pluginViewModel::submitDirectPackageUrl,
                onImportLocalPackage = {
                    localPackagePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                },
            )
            PluginWorkspaceTab.MARKET -> PluginMarketWorkspace()
        }
    }
}

@Composable
private fun PluginLocalWorkspace(
    uiState: PluginScreenUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelected: (PluginLocalFilter) -> Unit,
    onOpenPluginDetail: (String) -> Unit,
    onRepositoryUrlDraftChange: (String) -> Unit,
    onDirectPackageUrlDraftChange: (String) -> Unit,
    onSubmitRepositoryUrl: () -> Unit,
    onSubmitDirectPackageUrl: () -> Unit,
    onImportLocalPackage: () -> Unit,
) {
    val presentation = buildPluginLocalWorkspacePresentation(
        uiState = uiState,
        searchQuery = uiState.localSearchQuery,
        selectedFilter = uiState.selectedLocalFilter,
    )
    var showInstallSheet by rememberSaveable { mutableStateOf(false) }
    var selectedInstallMode by rememberSaveable { mutableStateOf(PluginQuickInstallMode.LocalZip) }
    val installSheetPresentation = buildPluginLocalInstallSheetPresentation(
        showSheet = showInstallSheet,
        selectedMode = selectedInstallMode,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
                .testTag(PluginUiSpec.LocalPageTag),
            contentPadding = PaddingValues(
                top = PluginUiSpec.ScreenVerticalPadding,
                bottom = PluginUiSpec.ListContentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.localSearchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PluginUiSpec.LocalSearchTag),
                    singleLine = true,
                    shape = PluginUiSpec.SummaryShape,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MonochromeUi.textSecondary,
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.plugin_installed_library_search_placeholder),
                            color = MonochromeUi.textSecondary,
                        )
                    },
                    colors = monochromeOutlinedTextFieldColors(),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag(PluginUiSpec.LocalFilterRowTag),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presentation.filters.forEach { filter ->
                        FilterChip(
                            selected = filter.selected,
                            onClick = { onFilterSelected(filter.filter) },
                            modifier = Modifier.testTag(PluginUiSpec.LocalFilterChipTag),
                            label = {
                                Text(
                                    text = stringResource(
                                        when (filter.filter) {
                                            PluginLocalFilter.ENABLED -> R.string.plugin_installed_library_filter_enabled
                                            PluginLocalFilter.DISABLED -> R.string.plugin_local_status_disabled
                                            PluginLocalFilter.UPDATES -> R.string.plugin_installed_library_filter_updates
                                        },
                                    ),
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MonochromeUi.chipSelectedBackground,
                                selectedLabelColor = MonochromeUi.textPrimary,
                                containerColor = MonochromeUi.chipBackground,
                                labelColor = MonochromeUi.textSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = filter.selected,
                                borderColor = MonochromeUi.border,
                                selectedBorderColor = MonochromeUi.textPrimary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.2.dp,
                            ),
                        )
                    }
                }
            }
            if (presentation.cards.isEmpty()) {
                item {
                    PluginSectionEmptyState(
                        message = stringResource(R.string.plugin_installed_library_empty_message),
                    )
                }
            } else {
                items(presentation.cards, key = { it.pluginId }) { card ->
                    Box(modifier = Modifier.testTag(PluginUiSpec.LocalCardTag)) {
                        PluginLocalCard(
                            card = card,
                            onClick = { onOpenPluginDetail(card.pluginId) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showInstallSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding)
                .testTag(PluginUiSpec.LocalInstallFabTag),
            containerColor = MonochromeUi.fabBackground,
            contentColor = MonochromeUi.fabContent,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.plugin_local_add_fab_content_description),
            )
        }
    }

    if (installSheetPresentation.showSheet) {
        PluginLocalInstallDialog(
            selectedMode = installSheetPresentation.selectedMode,
            availableModes = installSheetPresentation.availableModes,
            repositoryUrlDraft = uiState.repositoryUrlDraft,
            directPackageUrlDraft = uiState.directPackageUrlDraft,
            isActionRunning = uiState.isInstallActionRunning,
            lastActionMessage = uiState.detailActionState.lastActionMessage,
            onModeSelected = { selectedInstallMode = it },
            onRepositoryUrlDraftChange = onRepositoryUrlDraftChange,
            onDirectPackageUrlDraftChange = onDirectPackageUrlDraftChange,
            onSubmitRepositoryUrl = onSubmitRepositoryUrl,
            onSubmitDirectPackageUrl = onSubmitDirectPackageUrl,
            onImportLocalPackage = onImportLocalPackage,
            onDismiss = { showInstallSheet = false },
        )
    }
}

@Composable
private fun PluginLocalCard(
    card: PluginLocalCardPresentation,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.testTag(PluginUiSpec.installedLibraryCardTag(card.pluginId)),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(MonochromeUi.mutedSurface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = card.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MonochromeUi.textPrimary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonochromeUi.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${card.author} | ${localStatusLabel(card.isEnabled)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MonochromeUi.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.testTag(PluginUiSpec.LocalCardChevronTag),
                tint = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginLocalInstallDialog(
    selectedMode: PluginQuickInstallMode,
    availableModes: List<PluginQuickInstallMode>,
    repositoryUrlDraft: String,
    directPackageUrlDraft: String,
    isActionRunning: Boolean,
    lastActionMessage: PluginActionFeedback?,
    onModeSelected: (PluginQuickInstallMode) -> Unit,
    onRepositoryUrlDraftChange: (String) -> Unit,
    onDirectPackageUrlDraftChange: (String) -> Unit,
    onSubmitRepositoryUrl: () -> Unit,
    onSubmitDirectPackageUrl: () -> Unit,
    onImportLocalPackage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PluginUiSpec.LocalInstallDialogTag),
        containerColor = MonochromeUi.cardBackground,
        titleContentColor = MonochromeUi.textPrimary,
        textContentColor = MonochromeUi.textPrimary,
        title = {
            Text(
                text = stringResource(R.string.plugin_install_entry_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.plugin_install_entry_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
                PluginInstallModeDropdown(
                    selectedMode = selectedMode,
                    availableModes = availableModes,
                    onModeSelected = onModeSelected,
                )
                PluginInstallActionFields(
                    selectedMode = selectedMode,
                    repositoryUrlDraft = repositoryUrlDraft,
                    directPackageUrlDraft = directPackageUrlDraft,
                    isActionRunning = isActionRunning,
                    onRepositoryUrlDraftChange = onRepositoryUrlDraftChange,
                    onDirectPackageUrlDraftChange = onDirectPackageUrlDraftChange,
                    onSubmitRepositoryUrl = onSubmitRepositoryUrl,
                    onSubmitDirectPackageUrl = onSubmitDirectPackageUrl,
                    onImportLocalPackage = onImportLocalPackage,
                    lastActionMessage = lastActionMessage,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MonochromeUi.textPrimary),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PluginInstallModeDropdown(
    selectedMode: PluginQuickInstallMode,
    availableModes: List<PluginQuickInstallMode>,
    onModeSelected: (PluginQuickInstallMode) -> Unit,
) {
    var expanded by remember(selectedMode, availableModes) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.plugin_install_method_label),
            style = MaterialTheme.typography.labelSmall,
            color = MonochromeUi.textSecondary,
        )
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PluginUiSpec.LocalInstallModeSelectorTag),
            shape = PluginUiSpec.SectionShape,
            color = MonochromeUi.cardAltBackground,
            border = PluginUiSpec.CardBorder,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(selectedMode.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textPrimary,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = MonochromeUi.textSecondary,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableModes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes())) },
                    modifier = Modifier.testTag(PluginUiSpec.localInstallModeOptionTag(mode.name)),
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PluginInstallActionFields(
    selectedMode: PluginQuickInstallMode,
    repositoryUrlDraft: String,
    directPackageUrlDraft: String,
    isActionRunning: Boolean,
    onRepositoryUrlDraftChange: (String) -> Unit,
    onDirectPackageUrlDraftChange: (String) -> Unit,
    onSubmitRepositoryUrl: () -> Unit,
    onSubmitDirectPackageUrl: () -> Unit,
    onImportLocalPackage: () -> Unit,
    lastActionMessage: PluginActionFeedback?,
) {
    val presentation = buildPluginQuickInstallPresentation(selectedMode)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (presentation.selectedMode) {
            PluginQuickInstallMode.LocalZip -> {
                OutlinedButton(
                    onClick = onImportLocalPackage,
                    enabled = !isActionRunning,
                ) {
                    Text(stringResource(R.string.plugin_local_package_action))
                }
            }

            PluginQuickInstallMode.RepositoryUrl -> {
                OutlinedTextField(
                    value = repositoryUrlDraft,
                    onValueChange = onRepositoryUrlDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plugin_repository_url_label)) },
                    placeholder = { Text(stringResource(R.string.plugin_repository_url_placeholder)) },
                    singleLine = true,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedButton(
                    onClick = onSubmitRepositoryUrl,
                    enabled = !isActionRunning && repositoryUrlDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.plugin_repository_url_action))
                }
            }

            PluginQuickInstallMode.DirectPackageUrl -> {
                OutlinedTextField(
                    value = directPackageUrlDraft,
                    onValueChange = onDirectPackageUrlDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plugin_direct_package_url_label)) },
                    placeholder = { Text(stringResource(R.string.plugin_direct_package_url_placeholder)) },
                    singleLine = true,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedButton(
                    onClick = onSubmitDirectPackageUrl,
                    enabled = !isActionRunning && directPackageUrlDraft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.plugin_direct_package_url_action))
                }
            }
        }

        lastActionMessage?.let { message ->
            PluginActionFeedbackCard(message = message)
        }
    }
}

@Composable
private fun PluginMarketWorkspace() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = PluginUiSpec.ScreenHorizontalPadding,
                vertical = PluginUiSpec.ScreenVerticalPadding,
            )
            .testTag(PluginUiSpec.MarketPageTag),
        shape = PluginUiSpec.EmptyStateShape,
        color = PluginUiSpec.EmptyStateContainerColor,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.plugin_market_placeholder_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_market_placeholder_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginHomepageWorkspace(
    uiState: PluginScreenUiState,
    onOpenPluginDetail: (String) -> Unit,
    onRepositoryUrlDraftChange: (String) -> Unit,
    onDirectPackageUrlDraftChange: (String) -> Unit,
    onSubmitRepositoryUrl: () -> Unit,
    onSubmitDirectPackageUrl: () -> Unit,
    onImportLocalPackage: () -> Unit,
) {
    var quickInstallMode by rememberSaveable { mutableStateOf(PluginQuickInstallMode.LocalZip) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding)
            .testTag(PluginUiSpec.PluginListTag),
        contentPadding = PaddingValues(
            top = PluginUiSpec.ScreenVerticalPadding,
            bottom = PluginUiSpec.ListContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
    ) {
        items(buildPluginHomepageSections(), key = { it.name }) { section ->
            when (section) {
                PluginHomepageSection.Hero -> PluginHomepageHeroSection()
                PluginHomepageSection.QuickInstall -> PluginQuickInstallSection(
                    selectedMode = quickInstallMode,
                    repositoryUrlDraft = uiState.repositoryUrlDraft,
                    directPackageUrlDraft = uiState.directPackageUrlDraft,
                    isActionRunning = uiState.isInstallActionRunning,
                    onModeSelected = { quickInstallMode = it },
                    onRepositoryUrlDraftChange = onRepositoryUrlDraftChange,
                    onDirectPackageUrlDraftChange = onDirectPackageUrlDraftChange,
                    onSubmitRepositoryUrl = onSubmitRepositoryUrl,
                    onSubmitDirectPackageUrl = onSubmitDirectPackageUrl,
                    onImportLocalPackage = onImportLocalPackage,
                    lastActionMessage = uiState.detailActionState.lastActionMessage,
                )
                PluginHomepageSection.HealthOverview -> PluginHealthOverviewSection(uiState)
                PluginHomepageSection.InstalledLibrary -> PluginInstalledLibrarySection(
                    uiState = uiState,
                    onOpenPluginDetail = onOpenPluginDetail,
                )
                PluginHomepageSection.Discover -> PluginDiscoverSection(uiState)
                PluginHomepageSection.Repositories -> PluginRepositoriesSection(uiState)
            }
        }
    }
}

@Composable
private fun PluginHomepageHeroSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.HeroSectionTag),
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_homepage_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_homepage_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginHealthOverviewSection(uiState: PluginScreenUiState) {
    val metrics = buildPluginHealthOverviewPresentation(uiState)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.HealthOverviewSectionTag),
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.InnerSpacing),
        ) {
            Text(
                text = stringResource(R.string.plugin_health_overview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_health_overview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PluginUiSpec.CardSpacing),
            ) {
                PluginMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.plugin_health_metric_installed),
                    value = metrics.installedCount.toString(),
                )
                PluginMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.plugin_health_metric_updates_available),
                    value = metrics.updatesAvailableCount.toString(),
                )
                PluginMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.plugin_health_metric_needs_review),
                    value = metrics.needsReviewCount.toString(),
                )
                PluginMetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.plugin_health_metric_sources),
                    value = metrics.sourceCount.toString(),
                )
            }
        }
    }
}

@Composable
private fun PluginMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginQuickInstallSection(
    selectedMode: PluginQuickInstallMode,
    repositoryUrlDraft: String,
    directPackageUrlDraft: String,
    isActionRunning: Boolean,
    onModeSelected: (PluginQuickInstallMode) -> Unit,
    onRepositoryUrlDraftChange: (String) -> Unit,
    onDirectPackageUrlDraftChange: (String) -> Unit,
    onSubmitRepositoryUrl: () -> Unit,
    onSubmitDirectPackageUrl: () -> Unit,
    onImportLocalPackage: () -> Unit,
    lastActionMessage: PluginActionFeedback?,
) {
    val presentation = buildPluginQuickInstallPresentation(selectedMode)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.QuickInstallSectionTag),
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.plugin_install_entry_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MonochromeUi.textPrimary,
                )
                Text(
                    text = stringResource(R.string.plugin_install_entry_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonochromeUi.textSecondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_quick_install_mode_local_zip),
                    selected = presentation.showLocalZipAction,
                    tag = "plugin-quick-install-local-zip",
                    onClick = { onModeSelected(PluginQuickInstallMode.LocalZip) },
                )
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_quick_install_mode_repository_url),
                    selected = presentation.showRepositoryUrlForm,
                    tag = "plugin-quick-install-repository-url",
                    onClick = { onModeSelected(PluginQuickInstallMode.RepositoryUrl) },
                )
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_quick_install_mode_direct_package_url),
                    selected = presentation.showDirectPackageUrlForm,
                    tag = "plugin-quick-install-direct-package-url",
                    onClick = { onModeSelected(PluginQuickInstallMode.DirectPackageUrl) },
                )
            }
            when (presentation.selectedMode) {
                PluginQuickInstallMode.LocalZip -> {
                    OutlinedButton(
                        onClick = onImportLocalPackage,
                        enabled = !isActionRunning,
                    ) {
                        Text(stringResource(R.string.plugin_local_package_action))
                    }
                }
                PluginQuickInstallMode.RepositoryUrl -> {
                    OutlinedTextField(
                        value = repositoryUrlDraft,
                        onValueChange = onRepositoryUrlDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.plugin_repository_url_label)) },
                        placeholder = { Text(stringResource(R.string.plugin_repository_url_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = onSubmitRepositoryUrl,
                        enabled = !isActionRunning && repositoryUrlDraft.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.plugin_repository_url_action))
                    }
                }
                PluginQuickInstallMode.DirectPackageUrl -> {
                    OutlinedTextField(
                        value = directPackageUrlDraft,
                        onValueChange = onDirectPackageUrlDraftChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.plugin_direct_package_url_label)) },
                        placeholder = { Text(stringResource(R.string.plugin_direct_package_url_placeholder)) },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = onSubmitDirectPackageUrl,
                        enabled = !isActionRunning && directPackageUrlDraft.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.plugin_direct_package_url_action))
                    }
                }
            }
            lastActionMessage?.let { message ->
                PluginActionFeedbackCard(message = message)
            }
        }
    }
}

@Composable
private fun PluginInstalledLibrarySection(
    uiState: PluginScreenUiState,
    onOpenPluginDetail: (String) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(PluginInstalledLibraryFilter.All) }
    val presentation = buildPluginInstalledLibraryPresentation(
        uiState = uiState,
        selectedFilter = selectedFilter,
    )
    Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
        PluginSectionHeader(
            title = stringResource(R.string.plugin_installed_library_title),
            subtitle = stringResource(R.string.plugin_installed_library_subtitle),
            modifier = Modifier.testTag(PluginUiSpec.InstalledLibrarySectionTag),
        )
        PluginInstalledLibraryControls(
            filters = presentation.filters,
            onFilterSelected = { selectedFilter = it },
        )
        if (presentation.cards.isEmpty()) {
            PluginSectionEmptyState(message = stringResource(R.string.plugin_installed_library_empty_message))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
                presentation.cards.forEach { card ->
                    PluginInstalledLibraryCard(
                        card = card,
                        onOpenPluginDetail = onOpenPluginDetail,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginInstalledLibraryControls(
    filters: List<PluginInstalledLibraryFilterPresentation>,
    onFilterSelected: (PluginInstalledLibraryFilter) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.InstalledLibraryControlsTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PluginUiSpec.InstalledLibrarySearchTag),
                label = { Text(stringResource(R.string.plugin_installed_library_search_label)) },
                placeholder = { Text(stringResource(R.string.plugin_installed_library_search_placeholder)) },
                singleLine = true,
                readOnly = true,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = filter.selected,
                        onClick = { onFilterSelected(filter.filter) },
                        modifier = Modifier.testTag(PluginUiSpec.installedLibraryFilterTag(filter.filter.name)),
                        label = {
                            Text(text = installedLibraryFilterLabel(filter.filter))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MonochromeUi.cardBackground,
                            labelColor = MonochromeUi.textSecondary,
                            selectedContainerColor = MonochromeUi.cardAltBackground,
                            selectedLabelColor = MonochromeUi.textPrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginDiscoverSection(uiState: PluginScreenUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
        PluginSectionHeader(
            title = stringResource(R.string.plugin_discover_title),
            subtitle = stringResource(R.string.plugin_discover_subtitle),
            modifier = Modifier.testTag(PluginUiSpec.DiscoverSectionTag),
        )
        if (uiState.catalogEntries.isEmpty()) {
            PluginSectionEmptyState(message = stringResource(R.string.plugin_workspace_discoverable_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
                uiState.catalogEntries.forEach { entry ->
                    PluginCatalogEntryCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun PluginRepositoriesSection(uiState: PluginScreenUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
        PluginSectionHeader(
            title = stringResource(R.string.plugin_repositories_title),
            subtitle = stringResource(R.string.plugin_repositories_subtitle),
            modifier = Modifier.testTag(PluginUiSpec.RepositorySectionTag),
        )
        if (uiState.repositorySources.isEmpty()) {
            PluginSectionEmptyState(message = stringResource(R.string.plugin_workspace_repositories_empty))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing)) {
                uiState.repositorySources.forEach { source ->
                    PluginRepositorySourceCard(source = source)
                }
            }
        }
    }
}

@Composable
private fun PluginInstalledLibraryCard(
    card: PluginInstalledLibraryCardPresentation,
    onOpenPluginDetail: (String) -> Unit,
) {
    val priorityPalette = installedLibraryBadgePalette(card.priority)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.installedLibraryCardTag(card.pluginId))
            .clickable(onClick = { onOpenPluginDetail(card.pluginId) }),
        shape = PluginUiSpec.SectionShape,
        color = if (card.priority == PluginInstalledLibraryPriority.Critical) {
            MonochromeUi.cardAltBackground
        } else {
            MonochromeUi.cardBackground
        },
        tonalElevation = 2.dp,
        border = if (card.priority == PluginInstalledLibraryPriority.Critical) {
            BorderStroke(1.5.dp, MonochromeUi.fabBackground.copy(alpha = 0.45f))
        } else {
            PluginUiSpec.CardBorder
        },
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .testTag(PluginUiSpec.InstalledLibraryCardTag),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MonochromeUi.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.plugin_installed_library_identity_subtitle,
                            card.versionLabel,
                            card.sourceLabel,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
                PluginInstalledLibraryStatusBadge(
                    card = card,
                    palette = priorityPalette,
                )
            }
            Text(
                text = installedLibraryInsightLabel(card.insight),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { onOpenPluginDetail(card.pluginId) },
                    modifier = Modifier.testTag(PluginUiSpec.installedLibraryPrimaryActionTag(card.pluginId)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = when (card.primaryAction) {
                            PluginInstalledLibraryPrimaryAction.Open -> MonochromeUi.cardBackground
                            PluginInstalledLibraryPrimaryAction.Review -> MonochromeUi.cardAltBackground
                            PluginInstalledLibraryPrimaryAction.Update -> MonochromeUi.fabBackground.copy(alpha = 0.14f)
                        },
                        contentColor = MonochromeUi.textPrimary,
                    ),
                ) {
                    Text(text = installedLibraryPrimaryActionLabel(card.primaryAction))
                }
            }
        }
    }
}

@Composable
private fun PluginInstalledLibraryStatusBadge(
    card: PluginInstalledLibraryCardPresentation,
    palette: PluginBadgePalette,
) {
    PluginBadge(
        label = installedLibraryStatusLabel(card.status),
        palette = palette,
        modifier = Modifier.testTag(PluginUiSpec.installedLibraryStatusTag(card.pluginId)),
    )
}

@Composable
private fun installedLibraryFilterLabel(filter: PluginInstalledLibraryFilter): String {
    return when (filter) {
        PluginInstalledLibraryFilter.All -> stringResource(R.string.plugin_installed_library_filter_all)
        PluginInstalledLibraryFilter.Enabled -> stringResource(R.string.plugin_installed_library_filter_enabled)
        PluginInstalledLibraryFilter.Updates -> stringResource(R.string.plugin_installed_library_filter_updates)
        PluginInstalledLibraryFilter.Issues -> stringResource(R.string.plugin_installed_library_filter_issues)
        PluginInstalledLibraryFilter.PermissionChanges -> stringResource(R.string.plugin_installed_library_filter_permission_changes)
    }
}

@Composable
private fun installedLibraryStatusLabel(status: PluginInstalledLibraryStatus): String {
    return when (status) {
        PluginInstalledLibraryStatus.Enabled -> stringResource(R.string.plugin_installed_library_status_enabled)
        PluginInstalledLibraryStatus.Disabled -> stringResource(R.string.plugin_installed_library_status_disabled)
        PluginInstalledLibraryStatus.UpdateAvailable -> stringResource(R.string.plugin_installed_library_status_update_available)
        PluginInstalledLibraryStatus.CompatibilityUnknown -> stringResource(R.string.plugin_installed_library_status_compatibility_unknown)
        PluginInstalledLibraryStatus.Incompatible -> stringResource(R.string.plugin_installed_library_status_incompatible)
        PluginInstalledLibraryStatus.PermissionChanges -> stringResource(R.string.plugin_installed_library_status_permission_changes)
        PluginInstalledLibraryStatus.Suspended -> stringResource(R.string.plugin_installed_library_status_suspended)
    }
}

@Composable
private fun installedLibraryInsightLabel(insight: PluginInstalledLibraryInsight): String {
    return when (insight) {
        PluginInstalledLibraryInsight.UpToDate -> stringResource(R.string.plugin_installed_library_insight_up_to_date)
        PluginInstalledLibraryInsight.DisabledReady -> stringResource(R.string.plugin_installed_library_insight_disabled_ready)
        PluginInstalledLibraryInsight.UpdateAvailable -> stringResource(R.string.plugin_installed_library_insight_update_available)
        PluginInstalledLibraryInsight.CompatibilityUnknown -> stringResource(R.string.plugin_installed_library_insight_compatibility_unknown)
        PluginInstalledLibraryInsight.Incompatible -> stringResource(R.string.plugin_installed_library_insight_incompatible)
        PluginInstalledLibraryInsight.PermissionChanges -> stringResource(R.string.plugin_installed_library_insight_permission_changes)
        PluginInstalledLibraryInsight.Suspended -> stringResource(R.string.plugin_installed_library_insight_suspended)
    }
}

@Composable
private fun installedLibraryPrimaryActionLabel(action: PluginInstalledLibraryPrimaryAction): String {
    return when (action) {
        PluginInstalledLibraryPrimaryAction.Open -> stringResource(R.string.plugin_action_open)
        PluginInstalledLibraryPrimaryAction.Review -> stringResource(R.string.plugin_action_review)
        PluginInstalledLibraryPrimaryAction.Update -> stringResource(R.string.plugin_action_update)
    }
}

@Composable
private fun PluginDetailHero(record: PluginInstallRecord) {
    val presentation = buildPluginRecordPresentation(record)
    Surface(
        shape = PluginUiSpec.SummaryShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 2.dp,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.InnerSpacing),
        ) {
            Text(
                text = record.manifestSnapshot.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_detail_subtitle, record.installedVersion, sourceTypeLabel(record.source.sourceType), installStatusLabel(record)),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presentation.badges.forEach { badge ->
                    val palette = if (badge == compatibilityLabel(record.compatibilityState.status)) {
                        PluginUiSpec.compatibilityBadgePalette(record.compatibilityState.status)
                    } else {
                        PluginBadgePalette(
                            containerColor = MonochromeUi.cardAltBackground,
                            contentColor = MonochromeUi.textPrimary,
                        )
                    }
                    PluginBadge(badge, palette)
                }
            }
            Text(
                text = record.manifestSnapshot.entrySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textPrimary,
            )
        }
    }
}

@Composable
private fun PluginFailureSummaryInline(
    pluginId: String,
    failureState: PluginFailureUiState,
) {
    val palette = PluginUiSpec.failureBadgePalette(failureState.isSuspended)
    Column(verticalArrangement = Arrangement.spacedBy(PluginUiSpec.InnerSpacing)) {
        PluginBadge(
            label = failureState.statusMessage.asText(),
            palette = palette,
            modifier = Modifier.testTag(PluginUiSpec.pluginFailureChipTag(pluginId)),
        )
        Text(
            text = failureState.summaryMessage.asText(),
            modifier = Modifier.testTag(PluginUiSpec.DetailFailureSummaryTag),
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
        Text(
            text = failureState.recoveryMessage.asText(),
            modifier = Modifier.testTag(PluginUiSpec.DetailFailureRecoveryTag),
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
private fun PluginFailureBanner(failureState: PluginFailureUiState) {
    val palette = PluginUiSpec.failureBannerPalette(failureState.isSuspended)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = palette.containerColor,
        border = PluginUiSpec.CardBorder,
        modifier = Modifier.testTag(PluginUiSpec.DetailFailureBannerTag),
    ) {
        Column(
            modifier = Modifier.padding(PluginUiSpec.FailureBannerPadding),
            verticalArrangement = Arrangement.spacedBy(PluginUiSpec.FailureBannerSpacing),
        ) {
            PluginBadge(
                label = failureState.statusMessage.asText(),
                palette = PluginUiSpec.failureBadgePalette(failureState.isSuspended),
            )
            Text(
                text = failureState.summaryMessage.asText(),
                modifier = Modifier.testTag(PluginUiSpec.DetailFailureSummaryTag),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.contentColor,
            )
            Text(
                text = failureState.recoveryMessage.asText(),
                modifier = Modifier.testTag(PluginUiSpec.DetailFailureRecoveryTag),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.contentColor,
            )
        }
    }
}

@Composable
private fun PluginDetailSection(title: String, body: String) {
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MonochromeUi.textSecondary)
        }
    }
}

@Composable
private fun PluginKeyValueSection(
    title: String,
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MonochromeUi.textPrimary)
            items.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MonochromeUi.textSecondary)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MonochromeUi.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun PluginCompatibilitySection(record: PluginInstallRecord) {
    PluginKeyValueSection(
        title = stringResource(R.string.plugin_detail_compatibility_title),
        items = listOf(
            stringResource(R.string.plugin_field_compatibility_status) to compatibilityLabel(record.compatibilityState.status),
            stringResource(R.string.plugin_field_compatibility_notes) to record.compatibilityState.notes.ifBlank {
                stringResource(R.string.plugin_value_no_notes)
            },
        ),
    )
}

@Composable
private fun PluginSourceDetailSection(
    metadata: PluginDetailMetadataState,
) {
    val items = buildList {
        metadata.sourceBadge?.label?.takeIf { it.isNotBlank() }?.let { label ->
            add(stringResource(R.string.plugin_field_source_label) to label)
        }
        add(
            stringResource(R.string.plugin_field_repository_name_or_host) to metadata.repositoryNameOrHost.ifBlank {
                stringResource(R.string.plugin_value_not_available)
            },
        )
        metadata.repositoryHost.takeIf { it.isNotBlank() }?.let { host ->
            add(stringResource(R.string.plugin_field_repository_host) to host)
        }
        add(
            stringResource(R.string.plugin_field_last_sync) to (
                metadata.lastSyncAtEpochMillis?.let(::formatPluginTimestamp)
                    ?: stringResource(R.string.plugin_value_not_synced)
                ),
        )
        add(
            stringResource(R.string.plugin_field_last_updated) to (
                metadata.lastUpdatedAtEpochMillis?.let(::formatPluginTimestamp)
                    ?: stringResource(R.string.plugin_value_not_available)
                ),
        )
        add(
            stringResource(R.string.plugin_field_version_history) to metadata.versionHistorySummary.ifBlank {
                stringResource(R.string.plugin_value_not_available)
            },
        )
        add(
            stringResource(R.string.plugin_field_changelog_summary) to metadata.changelogSummary.ifBlank {
                stringResource(R.string.plugin_value_no_changelog)
            },
        )
    }
    PluginKeyValueSection(
        title = stringResource(R.string.plugin_detail_source_title),
        items = items,
    )
}

@Composable
private fun PluginPermissionsSection(record: PluginInstallRecord) {
    val permissions = buildPluginPermissionPresentation(record)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_detail_permissions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            permissions.forEach { permission ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = permission.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MonochromeUi.textPrimary,
                            )
                        }
                        Text(permission.description, style = MaterialTheme.typography.bodySmall, color = MonochromeUi.textSecondary)
                        Text(
                            text = permission.requirementLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginActionSection(
    metadata: PluginDetailMetadataState,
    actionState: PluginDetailActionState,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onRequestUpgrade: () -> Unit,
    onSelectPolicy: (PluginUninstallPolicy) -> Unit,
    onUninstall: () -> Unit,
) {
    val incompatiblePalette = PluginUiSpec.compatibilityBadgePalette(PluginCompatibilityStatus.INCOMPATIBLE)
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.plugin_detail_actions_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            actionState.enableBlockedReason?.let { blockedReason ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = incompatiblePalette.containerColor,
                ) {
                    Text(
                        text = blockedReason.asText(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = incompatiblePalette.contentColor,
                    )
                }
            }
            actionState.updateBlockedReason?.let { blockedReason ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = incompatiblePalette.containerColor,
                    modifier = Modifier.testTag(PluginUiSpec.DetailUpgradeBlockedReasonTag),
                ) {
                    Text(
                        text = blockedReason.asText(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = incompatiblePalette.contentColor,
                    )
                }
            }
            Text(
                text = stringResource(R.string.plugin_field_compatibility_notes),
                style = MaterialTheme.typography.labelMedium,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = actionState.compatibilityNotes.ifBlank {
                    stringResource(R.string.plugin_value_no_notes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = stringResource(R.string.plugin_action_uninstall_policy_title),
                style = MaterialTheme.typography.labelMedium,
                color = MonochromeUi.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_action_uninstall_policy_keep_data),
                    selected = actionState.uninstallPolicy == PluginUninstallPolicy.KEEP_DATA,
                    tag = PluginUiSpec.DetailKeepDataPolicyTag,
                    onClick = { onSelectPolicy(PluginUninstallPolicy.KEEP_DATA) },
                )
                PolicyToggleButton(
                    label = stringResource(R.string.plugin_action_uninstall_policy_remove_data),
                    selected = actionState.uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA,
                    tag = PluginUiSpec.DetailRemoveDataPolicyTag,
                    onClick = { onSelectPolicy(PluginUninstallPolicy.REMOVE_DATA) },
                )
            }
            actionState.lastActionMessage?.let { message ->
                PluginActionFeedbackCard(message = message)
            }
            metadata.updateHint?.let { updateHint ->
                Surface(
                    modifier = Modifier.testTag(PluginUiSpec.DetailUpdateHintTag),
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.plugin_update_available_summary,
                                actionState.updateAvailability?.installedVersion ?: "",
                                updateHint.latestVersion,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textPrimary,
                        )
                        updateHint.changelogSummary.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                        updateHint.blockedReason.takeIf { it.isNotBlank() }?.let { reason ->
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = incompatiblePalette.contentColor,
                            )
                        }
                    }
                }
            }
            metadata.permissionDiffHint?.let { diff ->
                Surface(
                    modifier = Modifier.testTag(PluginUiSpec.DetailPermissionDiffTag),
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        diff.addedPermissions.takeIf { it.isNotEmpty() }?.let { added ->
                            Text(
                                text = stringResource(
                                    R.string.plugin_permission_diff_added,
                                    added.joinToString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                        diff.removedPermissions.takeIf { it.isNotEmpty() }?.let { removed ->
                            Text(
                                text = stringResource(
                                    R.string.plugin_permission_diff_removed,
                                    removed.joinToString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                        diff.changedPermissions.takeIf { it.isNotEmpty() }?.let { changed ->
                            Text(
                                text = stringResource(
                                    R.string.plugin_permission_diff_changed,
                                    changed.joinToString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                        diff.upgradedPermissions.takeIf { it.isNotEmpty() }?.let { upgraded ->
                            Text(
                                text = stringResource(
                                    R.string.plugin_permission_diff_upgraded,
                                    upgraded.joinToString(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }
            }
            actionState.updateAvailability?.let { update ->
                Surface(
                    shape = PluginUiSpec.SectionShape,
                    color = MonochromeUi.cardAltBackground,
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.plugin_update_available_summary,
                                update.installedVersion,
                                update.latestVersion,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MonochromeUi.textPrimary,
                        )
                        update.changelogSummary.takeIf { it.isNotBlank() }?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MonochromeUi.textSecondary,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actionState.updateAvailability?.let {
                    OutlinedButton(
                        onClick = onRequestUpgrade,
                        enabled = actionState.isUpgradeActionEnabled,
                        modifier = Modifier.testTag(PluginUiSpec.DetailUpgradeActionTag),
                    ) {
                        Text(stringResource(R.string.plugin_action_upgrade))
                    }
                }
                OutlinedButton(
                    onClick = onEnable,
                    enabled = actionState.isEnableActionEnabled,
                    modifier = Modifier.testTag(PluginUiSpec.DetailEnableActionTag),
                ) {
                    Text(stringResource(R.string.plugin_action_enable))
                }
                OutlinedButton(
                    onClick = onDisable,
                    enabled = actionState.isDisableActionEnabled,
                    modifier = Modifier.testTag(PluginUiSpec.DetailDisableActionTag),
                ) {
                    Text(stringResource(R.string.plugin_action_disable))
                }
                OutlinedButton(
                    onClick = onUninstall,
                    modifier = Modifier.testTag(PluginUiSpec.DetailUninstallActionTag),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        }
    }
}

@Composable
private fun PolicyToggleButton(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag),
        border = if (selected) BorderStroke(1.5.dp, MonochromeUi.textPrimary) else PluginUiSpec.CardBorder,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.cardBackground,
            contentColor = MonochromeUi.textPrimary,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun PluginActionFeedbackCard(message: PluginActionFeedback) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.DetailActionMessageTag),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardAltBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Text(
            text = message.asText(),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textPrimary,
        )
    }
}

@Composable
private fun PluginActionFeedback.asText(): String {
    return when (this) {
        is PluginActionFeedback.Resource -> stringResource(resId, *formatArgs.toTypedArray())
        is PluginActionFeedback.Text -> value
    }
}

@Composable
private fun PluginBadge(
    label: String,
    palette: PluginBadgePalette,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text = label, color = palette.contentColor) },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = palette.containerColor,
            disabledLabelColor = palette.contentColor,
            disabledLeadingIconContentColor = palette.contentColor,
        ),
        border = null,
    )
}

@Composable
private fun PluginEmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PluginUiSpec.EmptyStateShape,
        color = PluginUiSpec.EmptyStateContainerColor,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(PluginUiSpec.EmptyStateAccentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = MonochromeUi.textPrimary)
            }
            Text(
                text = stringResource(R.string.plugin_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(R.string.plugin_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = PluginUiSpec.EmptyStateBodyColor,
            )
        }
    }
}

@Composable
private fun PluginSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MonochromeUi.textPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
private fun PluginSectionEmptyState(message: String) {
    Surface(
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MonochromeUi.textSecondary,
        )
    }
}

@Composable
private fun PluginRepositorySourceCard(source: PluginRepositorySourceCardUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.repositoryCardTag(source.sourceId)),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = source.catalogUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = stringResource(R.string.plugin_repository_card_summary, source.pluginCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = stringResource(
                    R.string.plugin_repository_card_last_sync,
                    source.lastSyncAtEpochMillis?.let(::formatPluginTimestamp)
                        ?: stringResource(R.string.plugin_value_not_synced),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginCatalogEntryCard(entry: PluginCatalogEntryCardUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PluginUiSpec.discoverableCardTag(entry.pluginId)),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        border = PluginUiSpec.CardBorder,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MonochromeUi.textSecondary,
            )
            Text(
                text = stringResource(
                    R.string.plugin_discoverable_card_summary,
                    entry.latestVersion.ifBlank { stringResource(R.string.plugin_value_not_available) },
                    entry.sourceName,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
private fun PluginUpgradeDialog(
    state: com.astrbot.android.ui.viewmodel.PluginUpgradeDialogState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val permissionDiff = state.availability.permissionDiff
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (state.isSecondaryConfirmationStep) {
                    stringResource(R.string.plugin_upgrade_confirm_secondary_title)
                } else {
                    stringResource(R.string.plugin_upgrade_confirm_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.plugin_update_available_summary,
                        state.availability.installedVersion,
                        state.availability.latestVersion,
                    ),
                )
                state.availability.publishedAt?.let { publishedAt ->
                    Text(
                        text = stringResource(R.string.plugin_upgrade_published_at, publishedAt.toString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.availability.changelogSummary.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
                if (permissionDiff.added.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.plugin_upgrade_added_permissions,
                            permissionDiff.added.joinToString { permission -> permission.title },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (permissionDiff.riskUpgraded.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.plugin_upgrade_upgraded_permissions,
                            permissionDiff.riskUpgraded.joinToString { upgrade -> upgrade.to.title },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.message?.let { message ->
                    Text(
                        text = message.asText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MonochromeUi.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.isInstalling,
            ) {
                Text(
                    text = if (state.requiresSecondaryConfirmation && !state.isSecondaryConfirmationStep) {
                        stringResource(R.string.plugin_upgrade_continue)
                    } else {
                        stringResource(R.string.plugin_action_upgrade)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isInstalling,
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun sourceTypeLabel(sourceType: PluginSourceType): String {
    return when (sourceType) {
        PluginSourceType.LOCAL_FILE -> stringResource(R.string.plugin_source_local_file)
        PluginSourceType.MANUAL_IMPORT -> stringResource(R.string.plugin_source_manual_import)
        PluginSourceType.REPOSITORY -> stringResource(R.string.plugin_source_repository)
        PluginSourceType.DIRECT_LINK -> stringResource(R.string.plugin_source_direct_link)
    }
}

@Composable
private fun compatibilityLabel(status: PluginCompatibilityStatus): String {
    return when (status) {
        PluginCompatibilityStatus.COMPATIBLE -> stringResource(R.string.plugin_compatibility_compatible)
        PluginCompatibilityStatus.INCOMPATIBLE -> stringResource(R.string.plugin_compatibility_incompatible)
        PluginCompatibilityStatus.UNKNOWN -> stringResource(R.string.plugin_compatibility_unknown)
    }
}

@Composable
private fun installStatusLabel(record: PluginInstallRecord): String {
    return if (record.enabled) {
        stringResource(R.string.common_enabled)
    } else {
        stringResource(R.string.plugin_status_installed)
    }
}

@Composable
private fun localStatusLabel(enabled: Boolean): String {
    return if (enabled) {
        stringResource(R.string.plugin_local_status_enabled)
    } else {
        stringResource(R.string.plugin_local_status_disabled)
    }
}

private fun PluginQuickInstallMode.labelRes(): Int {
    return when (this) {
        PluginQuickInstallMode.LocalZip -> R.string.plugin_quick_install_mode_local_zip
        PluginQuickInstallMode.RepositoryUrl -> R.string.plugin_quick_install_mode_repository_url
        PluginQuickInstallMode.DirectPackageUrl -> R.string.plugin_quick_install_mode_direct_package_url
    }
}

private fun formatPluginTimestamp(epochMillis: Long): String {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

internal fun shouldRenderSchemaWorkspace(schemaUiState: PluginSchemaUiState): Boolean {
    return schemaUiState !is PluginSchemaUiState.None
}
