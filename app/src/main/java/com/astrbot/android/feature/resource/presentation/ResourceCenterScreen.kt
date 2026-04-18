package com.astrbot.android.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.astrbot.android.R
import com.astrbot.android.feature.resource.presentation.ResourceCenterPresentationController
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.SkillResourceKind
import com.astrbot.android.ui.app.FloatingBottomNavFabBottomPadding
import com.astrbot.android.ui.app.MonochromeUi
import com.astrbot.android.ui.app.monochromeOutlinedTextFieldColors
import com.astrbot.android.ui.common.SubPageScaffold
import com.astrbot.android.ui.navigation.AppDestination
import com.astrbot.android.ui.plugin.PluginUiSpec
import kotlinx.coroutines.launch

@Composable
fun ResourceCenterScreen(
    onBack: () -> Unit,
    onOpenResourceList: (ResourceKind) -> Unit,
) {
    val controller: ResourceCenterPresentationController = defaultResourceCenterPresentationController()
    SubPageScaffold(
        route = AppDestination.ResourceCenter.route,
        title = stringResource(R.string.resource_center_title),
        onBack = onBack,
    ) { innerPadding ->
        EntryListPage(
            entries = buildResourceCenterPresentation(controller).entries.map { entry ->
                EntryCardState(
                    title = entry.kind.titleText(),
                    subtitle = entry.kind.subtitleText(),
                    icon = entry.kind.icon(),
                    onClick = { onOpenResourceList(entry.kind) },
                )
            },
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun ResourceListScreen(
    kind: ResourceKind,
    onBack: () -> Unit,
    resources: List<ResourceCardPresentation> = emptyList(),
    onAddResource: (ResourceCenterItem) -> Unit = {},
) {
    if (kind == ResourceKind.TOOL) {
        HostToolListScreen(onBack = onBack)
        return
    }

    var requestedPage by rememberSaveable(kind.routeSegment) { mutableStateOf(1) }
    var showPageJumpDialog by rememberSaveable(kind.routeSegment) { mutableStateOf(false) }
    var pageJumpDraft by rememberSaveable(kind.routeSegment) { mutableStateOf("") }
    var pageJumpHasError by rememberSaveable(kind.routeSegment) { mutableStateOf(false) }
    var showAddDialog by rememberSaveable(kind.routeSegment) { mutableStateOf(false) }
    val page = buildResourceListPresentation(
        kind = kind,
        resources = resources,
        requestedPage = requestedPage,
    )

    LaunchedEffect(resources.size, kind) {
        requestedPage = buildResourceListPresentation(
            kind = kind,
            resources = resources,
            requestedPage = requestedPage,
        ).currentPage
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
                            requestedPage = targetPage
                            pageJumpHasError = false
                            showPageJumpDialog = false
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

    if (showAddDialog) {
        ResourceCreateDialog(
            kind = kind,
            onDismiss = { showAddDialog = false },
            onCreate = { resource ->
                onAddResource(resource)
                showAddDialog = false
            },
        )
    }

    SubPageScaffold(
        route = AppDestination.ResourceList.route,
        title = kind.titleText(),
        onBack = onBack,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MonochromeUi.pageBackground),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding),
                contentPadding = PaddingValues(
                    top = PluginUiSpec.ScreenVerticalPadding,
                    bottom = PluginUiSpec.ListContentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
            ) {
                items(page.visibleCards, key = { card -> card.id }) { card ->
                    ResourceCard(card = card)
                }
                item {
                    SettingsPagerBar(
                        currentPage = page.currentPage,
                        totalPages = page.totalPages,
                        canGoPrevious = page.canGoPrevious,
                        canGoNext = page.canGoNext,
                        onPrevious = { requestedPage = (page.currentPage - 1).coerceAtLeast(1) },
                        onNext = { requestedPage = (page.currentPage + 1).coerceAtMost(page.totalPages) },
                        onJump = {
                            pageJumpDraft = page.currentPage.toString()
                            pageJumpHasError = false
                            showPageJumpDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = FloatingBottomNavFabBottomPadding)
                    .then(Modifier.testTag("resource-list-add-fab")),
                containerColor = MonochromeUi.actionFabBackground,
                contentColor = MonochromeUi.actionFabContent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(
                        when (kind) {
                            ResourceKind.MCP -> R.string.resource_list_add_mcp_content_description
                            ResourceKind.SKILL -> R.string.resource_list_add_skill_content_description
                            ResourceKind.TOOL -> R.string.resource_list_add_content_description
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ResourceCard(card: ResourceCardPresentation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MonochromeUi.border.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MonochromeUi.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = card.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MonochromeUi.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ResourceStatusBadge(text = stringResource(card.statusLabelRes))
            }
            Text(
                text = card.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResourceStatusBadge(text: String) {
    Surface(
        shape = PluginUiSpec.BadgeShape,
        color = MonochromeUi.mutedSurface,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MonochromeUi.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostToolListScreen(
    onBack: () -> Unit,
) {
    val presentation = buildHostToolPagerPresentation()
    val pagerState = rememberPagerState(pageCount = { presentation.totalPages })
    val coroutineScope = rememberCoroutineScope()
    var showPageJumpDialog by rememberSaveable { mutableStateOf(false) }
    var pageJumpDraft by rememberSaveable { mutableStateOf("") }
    var pageJumpHasError by rememberSaveable { mutableStateOf(false) }
    val currentPage = if (presentation.totalPages == 0) 1 else pagerState.currentPage + 1

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
                            Text(stringResource(R.string.resource_list_page_jump_error, presentation.totalPages))
                        }
                    },
                    colors = monochromeOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetPage = pageJumpDraft.trim().toIntOrNull()
                        if (targetPage != null && targetPage in 1..presentation.totalPages) {
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

    SubPageScaffold(
        route = AppDestination.ResourceList.route,
        title = ResourceKind.TOOL.titleText(),
        onBack = onBack,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MonochromeUi.pageBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PluginUiSpec.ScreenHorizontalPadding),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    pageSpacing = PluginUiSpec.SectionSpacing,
                    userScrollEnabled = presentation.totalPages > 1,
                ) { pageIndex ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = PluginUiSpec.ScreenVerticalPadding,
                            bottom = PluginUiSpec.SectionSpacing,
                        ),
                        verticalArrangement = Arrangement.spacedBy(PluginUiSpec.SectionSpacing),
                    ) {
                        items(
                            items = presentation.pages[pageIndex],
                            key = { card -> card.kind.toolId },
                        ) { card ->
                            HostToolCard(card = card)
                        }
                    }
                }
                SettingsPagerBar(
                    currentPage = currentPage,
                    totalPages = presentation.totalPages,
                    canGoPrevious = currentPage > 1,
                    canGoNext = currentPage < presentation.totalPages,
                    onPrevious = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage((currentPage - 2).coerceAtLeast(0))
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage((currentPage).coerceAtMost(presentation.totalPages - 1))
                        }
                    },
                    onJump = {
                        pageJumpDraft = currentPage.toString()
                        pageJumpHasError = false
                        showPageJumpDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = PluginUiSpec.ScreenVerticalPadding),
                )
            }
        }
    }
}

@Composable
private fun HostToolCard(card: HostToolCardPresentation) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PluginUiSpec.SectionShape,
        color = MonochromeUi.cardBackground,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MonochromeUi.border.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(card.kind.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MonochromeUi.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(card.kind.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MonochromeUi.textSecondary,
            )
        }
    }
}

@Composable
internal fun SettingsPagerBar(
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
            modifier = Modifier
                .weight(1f)
                .testTag("pager-previous"),
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
                text = stringResource(
                    R.string.resource_list_pager_label,
                    currentPage,
                    totalPages,
                ),
                color = MonochromeUi.textPrimary,
            )
        }
        OutlinedButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier
                .weight(1f)
                .testTag("pager-next"),
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
private fun ResourceCreateDialog(
    kind: ResourceKind,
    onDismiss: () -> Unit,
    onCreate: (ResourceCenterItem) -> Unit,
) {
    when (kind) {
        ResourceKind.MCP -> RemoteMcpServerDialog(onDismiss = onDismiss, onCreate = onCreate)
        ResourceKind.SKILL -> SkillResourceDialog(onDismiss = onDismiss, onCreate = onCreate)
        ResourceKind.TOOL -> Unit
    }
}

@Composable
private fun RemoteMcpServerDialog(
    onDismiss: () -> Unit,
    onCreate: (ResourceCenterItem) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var timeoutSeconds by rememberSaveable { mutableStateOf("30") }
    var active by rememberSaveable { mutableStateOf(true) }

    val canSave = name.isNotBlank() && serverUrl.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.resource_add_mcp_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.resource_add_mcp_hint_clean),
                    color = MonochromeUi.textSecondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.resource_add_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-name"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.resource_add_description_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-description"),
                    minLines = 2,
                    maxLines = 3,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.resource_add_mcp_server_url_label)) },
                    placeholder = { Text(stringResource(R.string.resource_add_mcp_server_url_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-server-url"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.resource_add_mcp_transport_label),
                        color = MonochromeUi.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(R.string.resource_add_mcp_transport_fixed_value),
                        color = MonochromeUi.textPrimary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = timeoutSeconds,
                    onValueChange = { timeoutSeconds = it },
                    label = { Text(stringResource(R.string.resource_add_mcp_timeout_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-timeout"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.resource_add_active_label),
                        color = MonochromeUi.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.material3.Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        RemoteMcpServerDraft(
                            name = name,
                            description = description,
                            serverUrl = serverUrl,
                            timeoutSeconds = timeoutSeconds,
                            active = active,
                        ).toResourceItem(),
                    )
                },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.common_save))
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
private fun SkillResourceDialog(
    onDismiss: () -> Unit,
    onCreate: (ResourceCenterItem) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var skillKind by rememberSaveable { mutableStateOf(SkillResourceKind.PROMPT.name) }

    val canSave = name.isNotBlank() && content.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.resource_add_skill_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 540.dp)
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.resource_add_skill_hint),
                    color = MonochromeUi.textSecondary,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.resource_add_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-name"),
                    colors = monochromeOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.resource_add_description_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-description"),
                    minLines = 2,
                    maxLines = 3,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Text(
                    text = stringResource(R.string.resource_add_skill_kind_label),
                    color = MonochromeUi.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = skillKind == SkillResourceKind.PROMPT.name,
                        onClick = { skillKind = SkillResourceKind.PROMPT.name },
                        label = { Text(stringResource(R.string.resource_add_prompt_skill_option)) },
                    )
                    FilterChip(
                        selected = skillKind == SkillResourceKind.TOOL.name,
                        onClick = { skillKind = SkillResourceKind.TOOL.name },
                        label = { Text(stringResource(R.string.resource_add_tool_skill_option)) },
                    )
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = {
                        Text(
                            stringResource(
                                if (skillKind == SkillResourceKind.PROMPT.name) {
                                    R.string.resource_add_skill_content_label
                                } else {
                                    R.string.resource_add_tool_skill_template_label
                                },
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resource-add-content"),
                    minLines = 4,
                    maxLines = 8,
                    colors = monochromeOutlinedTextFieldColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.resource_add_active_label),
                        color = MonochromeUi.textPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.material3.Switch(
                        checked = active,
                        onCheckedChange = { active = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        SkillResourceDraft(
                            name = name,
                            description = description,
                            content = content,
                            skillKind = SkillResourceKind.valueOf(skillKind),
                            active = active,
                        ).toResourceItem(),
                    )
                },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.common_save))
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
private fun ResourceKind.titleText(): String {
    return stringResource(
        when (this) {
            ResourceKind.MCP -> R.string.resource_kind_mcp_title
            ResourceKind.SKILL -> R.string.resource_kind_skill_title
            ResourceKind.TOOL -> R.string.resource_kind_tool_title
        },
    )
}

@Composable
private fun ResourceKind.subtitleText(): String {
    return stringResource(
        when (this) {
            ResourceKind.MCP -> R.string.resource_kind_mcp_subtitle
            ResourceKind.SKILL -> R.string.resource_kind_skill_subtitle
            ResourceKind.TOOL -> R.string.resource_kind_tool_subtitle
        },
    )
}

private fun ResourceKind.icon(): ImageVector {
    return when (this) {
        ResourceKind.MCP -> Icons.Outlined.Memory
        ResourceKind.SKILL -> Icons.Outlined.Settings
        ResourceKind.TOOL -> Icons.Outlined.CloudDownload
    }
}
