package com.astrbot.android.ui.config.detail

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrbot.android.R
import com.astrbot.android.ui.app.AppTopBarHeight
import com.astrbot.android.ui.app.MonochromeUi

@Composable
internal fun ConfigDetailDrawerContent(
    screenWidth: Dp,
    groups: List<ConfigNavGroup>,
    currentSection: ConfigSection,
    expandedGroupTitles: Set<Int>,
    onToggleGroup: (Int) -> Unit,
    onSelectSection: (ConfigSection) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = screenWidth * 0.5f),
        drawerContainerColor = MonochromeUi.drawerSurface,
        drawerContentColor = MonochromeUi.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.config_detail_nav_title),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            groups.forEach { group ->
                val groupExpanded = expandedGroupTitles.contains(group.titleRes)
                val groupSelected = currentSection in group.children
                Surface(
                    onClick = {
                        if (group.children.isNotEmpty()) {
                            onToggleGroup(group.titleRes)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = if (groupSelected) MonochromeUi.cardAltBackground else MonochromeUi.drawerSurface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(group.titleRes),
                            color = if (groupSelected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                            fontWeight = if (groupSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                        Text(
                            text = if (group.children.isEmpty()) "" else if (groupExpanded) "-" else "+",
                            color = MonochromeUi.textSecondary,
                        )
                    }
                }
                if (groupExpanded) {
                    group.children.forEach { section ->
                        val selected = section == currentSection
                        Surface(
                            onClick = { onSelectSection(section) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MonochromeUi.cardAltBackground else MonochromeUi.drawerSurface,
                        ) {
                            Text(
                                text = stringResource(section.titleRes),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                color = if (selected) MonochromeUi.textPrimary else MonochromeUi.textSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfigDetailTopBar(
    profileName: String,
    currentSectionTitle: String,
    onBack: () -> Unit,
    onOpenSections: () -> Unit,
) {
    Surface(color = MonochromeUi.pageBackground) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(AppTopBarHeight)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MonochromeUi.textPrimary,
                )
            }
            IconButton(onClick = onOpenSections) {
                Icon(
                    Icons.Outlined.Menu,
                    contentDescription = stringResource(R.string.config_detail_open_sections),
                    tint = MonochromeUi.textPrimary,
                )
            }
            Text(
                text = profileName,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MonochromeUi.textPrimary,
            )
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MonochromeUi.inputBackground,
            ) {
                Text(
                    text = currentSectionTitle,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonochromeUi.textSecondary,
                )
            }
        }
    }
}
