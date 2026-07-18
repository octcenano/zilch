package com.zilch.ui.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BgColor = Color(0xFF0D1117)
private val SurfaceColor = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF21262D)
private val Primary = Color(0xFF58A6FF)
private val Secondary = Color(0xFF3FB950)
private val OnBackground = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)
private val Emergency = Color(0xFFDA3633)

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val accentColor: Color,
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Bienvenido a Zilch",
        subtitle = "Comunicación sin internet",
        description = "Zilch funciona SIN conexión a internet, sin datos móviles y sin Wi-Fi.\n\n" +
                "Tus mensajes viajan directamente entre dispositivos cercanos a través de\n" +
                "una red descentralizada y anónima. No hay servidores, no hay rastro,\n" +
                "no hay vigilancia posible.",
        icon = Icons.Filled.Shield,
        gradientColors = listOf(Color(0xFF0D1117), Color(0xFF112240), Color(0xFF0D2137)),
        accentColor = Primary,
    ),
    OnboardingPage(
        title = "Comunicación Cercana",
        subtitle = "Red mesh por Bluetooth",
        description = "Los mensajes saltan de dispositivo en dispositivo usando Bluetooth\n" +
                "de baja energía (BLE). Cada teléfono cercano puede retransmitir\n" +
                "tus mensajes, creando una malla invisible y autónoma.\n\n" +
                "A más personas usando Zilch, más fuerte y más lejos llega la red.",
        icon = Icons.Filled.Hub,
        gradientColors = listOf(Color(0xFF0D1117), Color(0xFF0D2B1F), Color(0xFF0D1117)),
        accentColor = Secondary,
    ),
    OnboardingPage(
        title = "Cifrado Total",
        subtitle = "Cifrado de extremo a extremo",
        description = "Cada mensaje se cifra en tu dispositivo y solo se descifra en el\n" +
                "dispositivo del destinatario. Nadie en el camino puede leerlo.\n\n" +
                "Usa huellas digitales criptográficas para verificar la identidad\n" +
                "de tus contactos en persona y evitar suplantaciones.",
        icon = Icons.Filled.Lock,
        gradientColors = listOf(Color(0xFF0D1117), Color(0xFF1A1040), Color(0xFF0D1117)),
        accentColor = Color(0xFFBC8CFF),
    ),
    OnboardingPage(
        title = "Tu Identidad",
        subtitle = "Efímera y bajo tu control",
        description = "Tu identidad es efímera: se regenera automáticamente y no está\n" +
                "vinculada a tu nombre, teléfono ni dispositivo.\n\n" +
                "Panic button integrado: borra instantáneamente todos tus datos\n" +
                "con un solo toque. No almacenamos nada — ni metadatos, ni\n" +
                "historial, ni información de uso.",
        icon = Icons.Filled.PersonOff,
        gradientColors = listOf(Color(0xFF0D1117), Color(0xFF2D1215), Color(0xFF1A0D0D)),
        accentColor = Emergency,
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // Animated background gradient overlay
        val currentPage = pagerState.currentPage
        val page = onboardingPages[currentPage]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.accentColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        radius = 800f,
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar with skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Zilch logo text
                Text(
                    text = "zilch",
                    color = OnBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )

                // Skip button
                AnimatedVisibility(
                    visible = currentPage < onboardingPages.lastIndex,
                    enter = fadeIn() + slideInHorizontally(),
                    exit = fadeOut() + slideOutHorizontally(),
                ) {
                    TextButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = TextMuted,
                        ),
                    ) {
                        Text(
                            text = "Omitir",
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 32.dp),
                pageSpacing = 16.dp,
            ) { pageIndex ->
                OnboardingPageContent(
                    page = onboardingPages[pageIndex],
                    isCurrentPage = pagerState.currentPage == pageIndex,
                )
            }

            // Bottom section: indicators + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 32.dp),
                ) {
                    onboardingPages.forEachIndexed { index, page ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isSelected) page.accentColor else SurfaceVariant,
                                )
                                .then(
                                    Modifier.then(
                                        if (isSelected) {
                                            Modifier.width(32.dp)
                                        } else {
                                            Modifier.width(12.dp)
                                        }
                                    )
                                )
                                .animateContentSize(),
                        )
                    }
                }

                // EMPEZAR button (show on last page) or Next arrow
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        fadeIn() + slideInVertically { it / 2 } togetherWith
                                fadeOut() + slideOutVertically { -it / 2 }
                    },
                    label = "button_transition",
                ) { pageIndex ->
                    if (pageIndex == onboardingPages.lastIndex) {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = Color.White,
                            ),
                            shape = MaterialTheme.shapes.large,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp,
                            ),
                        ) {
                            Text(
                                text = "EMPEZAR",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val target = (pagerState.currentPage + 1)
                                        .coerceAtMost(onboardingPages.lastIndex)
                                    pagerState.animateScrollToPage(target)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = onboardingPages[pageIndex].accentColor,
                            ),
                            border = BorderStroke(
                                1.dp,
                                onboardingPages[pageIndex].accentColor.copy(alpha = 0.4f),
                            ),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "SIGUIENTE",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = "Siguiente",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    isCurrentPage: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon with glowing background
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            page.accentColor.copy(alpha = 0.15f),
                            page.accentColor.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        radius = 120f,
                    )
                ),
        ) {
            // Outer ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(SurfaceColor.copy(alpha = 0.8f))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                page.accentColor.copy(alpha = 0.5f),
                                page.accentColor.copy(alpha = 0.1f),
                            ),
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = page.accentColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title
        Text(
            text = page.title,
            color = OnBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtitle
        Text(
            text = page.subtitle,
            color = page.accentColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Description
        Text(
            text = page.description,
            color = TextMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Decorative line
        Spacer(modifier = Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .clip(MaterialTheme.shapes.small)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            page.accentColor.copy(alpha = 0.6f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}
