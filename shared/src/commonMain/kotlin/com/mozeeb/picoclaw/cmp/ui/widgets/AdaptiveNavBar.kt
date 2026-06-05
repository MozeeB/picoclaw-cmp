package com.mozeeb.picoclaw.cmp.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mozeeb.picoclaw.cmp.core.UiConstants
import com.mozeeb.picoclaw.cmp.ui.NavDestination

/**
 * Adaptive navigation:
 * - Compact (< 600 dp) → [NavigationBar] at the bottom
 * - Wide (≥ 600 dp)    → custom [NavigationRail] with pill indicator + logo
 */
@Composable
fun AdaptiveNavBar(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= UiConstants.compactBreakpoint) {
            WideLayout(
                destinations = destinations,
                selectedIndex = selectedIndex,
                onDestinationSelected = onDestinationSelected,
                content = content,
            )
        } else {
            CompactLayout(
                destinations = destinations,
                selectedIndex = selectedIndex,
                onDestinationSelected = onDestinationSelected,
                content = content,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Wide layout — custom navigation rail with pill indicator
// ---------------------------------------------------------------------------

@Composable
private fun WideLayout(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxSize()) {
        // Rail
        Surface(
            modifier = Modifier
                .width(UiConstants.navRailWidth)
                .fillMaxHeight(),
            color = cs.primary,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Logo mark
                Box(
                    modifier = Modifier
                        .size(UiConstants.navLogoSize)
                        .clip(CircleShape)
                        .background(cs.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "P",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                        ),
                        color = cs.secondary,
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Nav items
                destinations.forEachIndexed { index, destination ->
                    RailNavItem(
                        destination = destination,
                        selected = index == selectedIndex,
                        onClick = { onDestinationSelected(index) },
                    )
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.weight(1f))
            }
        }

        // Content
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = cs.primaryContainer,
        ) {
            content()
        }
    }
}

@Composable
private fun RailNavItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val bgAlpha by animateColorAsState(
        targetValue = if (selected) cs.secondary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rail_bg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) cs.secondary else cs.onPrimary.copy(alpha = 0.55f),
        label = "rail_icon",
    )
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 48.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rail_pill",
    )

    Box(
        modifier = Modifier
            .size(UiConstants.minTouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .background(bgAlpha)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        // Selected pill bar on left edge
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(pillWidth)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(cs.secondary),
            )
        }
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = destination.label,
            tint = iconTint,
            modifier = Modifier.size(UiConstants.navIconSize),
        )
    }
}

// ---------------------------------------------------------------------------
// Compact layout — bottom NavigationBar
// ---------------------------------------------------------------------------

@Composable
private fun CompactLayout(
    destinations: List<NavDestination>,
    selectedIndex: Int,
    onDestinationSelected: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = cs.primaryContainer,
        ) {
            content()
        }

        NavigationBar(
            containerColor = cs.primary,
            contentColor = cs.onPrimary,
            tonalElevation = 0.dp,
        ) {
            destinations.forEachIndexed { index, destination ->
                val selected = index == selectedIndex
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationSelected(index) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = destination.label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = cs.secondary,
                        selectedTextColor = cs.secondary,
                        unselectedIconColor = cs.onPrimary.copy(alpha = 0.5f),
                        unselectedTextColor = cs.onPrimary.copy(alpha = 0.5f),
                        indicatorColor = cs.secondary.copy(alpha = 0.15f),
                    ),
                )
            }
        }
    }
}
