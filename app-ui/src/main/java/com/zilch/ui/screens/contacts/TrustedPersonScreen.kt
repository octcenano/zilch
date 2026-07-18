package com.zilch.ui.screens.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zilch.ui.theme.DarkPalette

private val BgColor = DarkPalette.background
private val SurfaceColor = DarkPalette.surface
private val SurfaceVariant = DarkPalette.surfaceVariant
private val Primary = DarkPalette.primary
private val Green = DarkPalette.secondary
private val OnBackground = DarkPalette.onBackground
private val TextMuted = DarkPalette.textMuted
private val OnSurfaceVariant = DarkPalette.onSurfaceVariant

data class TrustedPerson(
    val fingerprint: String,
    val nickname: String,
    val isTrusted: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedPersonScreen(
    contacts: List<TrustedPerson> = emptyList(),
    onFingerprintChange: (String, String) -> Unit = { _, _ -> },
    onTrustToggle: (String, Boolean) -> Unit = { _, _ -> },
    onAddContact: () -> Unit = {},
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = BgColor,
        topBar = {
            TopAppBar(
                title = { Text("Personas de Confianza") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgColor,
                    titleContentColor = OnBackground,
                ),
            )
        },
        bottomBar = {
            // Añadir contacto button
            Surface(
                color = BgColor,
                tonalElevation = 0.dp,
            ) {
                OutlinedButton(
                    onClick = onAddContact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary,
                    ),
                    border = ButtonDefaults.outlinedButtonBorder,
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Añadir contacto",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        text = "Sin contactos de confianza",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant,
                    )
                    Text(
                        text = "Añade contactos escaneando su QR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(contacts, key = { it.fingerprint }) { person ->
                    TrustedPersonCard(
                        person = person,
                        onNicknameChange = { newNick ->
                            onFingerprintChange(person.fingerprint, newNick)
                        },
                        onTrustToggle = { trusted ->
                            onTrustToggle(person.fingerprint, trusted)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedPersonCard(
    person: TrustedPerson,
    onNicknameChange: (String) -> Unit,
    onTrustToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Top row: fingerprint + trust icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = person.fingerprint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = OnBackground,
                    modifier = Modifier.weight(1f),
                )

                if (person.isTrusted) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "De confianza",
                        tint = Green,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Nickname field
            OutlinedTextField(
                value = person.nickname,
                onValueChange = onNicknameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Ponle un nombre...",
                        color = TextMuted,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = SurfaceColor,
                    cursorColor = Primary,
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground,
                ),
                shape = MaterialTheme.shapes.small,
            )

            // Trust toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Persona de confianza",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                )
                Switch(
                    checked = person.isTrusted,
                    onCheckedChange = onTrustToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Green,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceColor,
                    ),
                )
            }
        }
    }
}
