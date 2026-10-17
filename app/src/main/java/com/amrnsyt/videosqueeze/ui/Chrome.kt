@file:OptIn(ExperimentalMaterial3Api::class)

package com.amrnsyt.videosqueeze.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ---------- top bar ----------

@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            append("WM")
            withStyle(SpanStyle(color = accent)) { append("/") }
            append("CMPRSS")
        },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        modifier = modifier
    )
}

@Composable
fun EditorTopBar(actions: @Composable RowScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    TopAppBar(
        title = { BrandTitle() },
        actions = {
            actions()
            Spacer(Modifier.width(Spacing.m))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = bg, scrolledContainerColor = bg)
    )
}

@Composable
fun PrimaryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.s)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---------- bottom sheet panel ----------

@Composable
fun EditorPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.l)
                .padding(top = Spacing.l),
            content = content
        )
    }
}

@Composable
fun PanelTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant,
                label = "tabBg"
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabFg"
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
            ) {
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------- empty state ----------

@Composable
fun PreviewEmptyState(
    title: String,
    subtitle: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            PrimaryPill(primaryLabel, onPrimary, modifier = Modifier.padding(top = Spacing.s))
            if (secondaryLabel != null) GhostPill(secondaryLabel, onSecondary)
        }
    }
}

// ---------- bottom navigation ----------

private data class NavItem(val label: String, val icon: ImageVector)

private val NavItems = listOf(
    NavItem("Compress", AppIcons.Compress),
    NavItem("Watermark", AppIcons.Watermark)
)

@Composable
fun AppNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface) {
        NavItems.forEachIndexed { i, item ->
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.surface,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant
                )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ChromePreview() {
    VideoSqueezeTheme {
        Column {
            EditorTopBar { PrimaryPill("Save", {}) }
            EditorPanel {
                PanelTabs(listOf("Badge", "Style", "Export"), 0, {})
                Text("Panel content", modifier = Modifier.padding(vertical = Spacing.xl))
            }
            AppNavBar(0, {})
        }
    }
}
