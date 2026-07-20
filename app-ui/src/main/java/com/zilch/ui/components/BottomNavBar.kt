package com.zilch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * BottomNavBar — Barra de navegación inferior estilo WhatsApp/Signal.
 *
 * Pestañas:
 * - Chats (lista de conversaciones)
 * - Bandeja (correo P2P cifrado)
 * - Ajustes (configuración)
 *
 * El botón central de QR se muestra como un FAB flotante
 * encima de la barra, no como pestaña.
 */
private val NavBarColor = Color(0xFF161B22)
private val ActiveColor = Color(0xFF58A6FF)
private val InactiveColor = Color(0xFF8B949E)
private val DividerColor = Color(0xFF21262D)

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    NavItem("chats", "Chats", Icons.AutoMirrored.Filled.Chat),
    NavItem("groups", "Grupos", Icons.Default.People),
    NavItem("settings", "Ajustes", Icons.Default.Settings),
)

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dividerStroke = with(LocalDensity.current) { 0.5.dp.toPx() }

    NavigationBar(
        containerColor = NavBarColor,
        contentColor = ActiveColor,
        tonalElevation = 0.dp,
        modifier = modifier
            .drawBehind {
                drawLine(
                    color = DividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = dividerStroke
                )
            }
            .navigationBarsPadding()
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp
                    )
                },
                selected = selected,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ActiveColor,
                    selectedTextColor = ActiveColor,
                    unselectedIconColor = InactiveColor,
                    unselectedTextColor = InactiveColor,
                    indicatorColor = ActiveColor.copy(alpha = 0.12f)
                )
            )
        }
    }
}
