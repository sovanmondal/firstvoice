package com.firstvoice.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firstvoice.app.ui.theme.*

/**
 * Full-screen "Show to Survivor" mode.
 * Displays translated text in large font (32sp+) with high contrast,
 * plus universal visual icons for deaf/illiterate survivors.
 */
@Composable
fun SurvivorViewScreen(
    text: String = "",
    language: String = "",
    onExit: () -> Unit = {},
    onIconTap: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (text.isNotEmpty()) {
                // Large translated text for survivor to read
                Text(
                    text = text,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 44.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                if (language.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = language,
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Universal icons grid when no text to show
                Text(
                    "Tap an icon to communicate",
                    fontSize = 18.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Row 1: Medical, Water, Shelter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UniversalIconButton(
                        icon = Icons.Default.LocalHospital,
                        label = "Medical",
                        color = CriticalRed,
                        onClick = { onIconTap("medical") }
                    )
                    UniversalIconButton(
                        icon = Icons.Default.WaterDrop,
                        label = "Water",
                        color = SafeBlue,
                        onClick = { onIconTap("water") }
                    )
                    UniversalIconButton(
                        icon = Icons.Default.Home,
                        label = "Shelter",
                        color = LowGreen,
                        onClick = { onIconTap("shelter") }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Row 2: Danger, Safe
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UniversalIconButton(
                        icon = Icons.Default.Warning,
                        label = "Danger",
                        color = HighOrange,
                        onClick = { onIconTap("danger") }
                    )
                    UniversalIconButton(
                        icon = Icons.Default.CheckCircle,
                        label = "Safe",
                        color = LowGreen,
                        onClick = { onIconTap("safe") }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Row 3: Yes, No
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    UniversalIconButton(
                        icon = Icons.Default.ThumbUp,
                        label = "Yes",
                        color = LowGreen,
                        onClick = { onIconTap("yes") }
                    )
                    UniversalIconButton(
                        icon = Icons.Default.ThumbDown,
                        label = "No",
                        color = CriticalRed,
                        onClick = { onIconTap("no") }
                    )
                }
            }
        }

        // Exit button (small, top-right corner — for responder only)
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Exit survivor view",
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun UniversalIconButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}
