package com.driveguard.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.driveguard.app.ui.components.SectionCard

/**
 * Privacy page. Every claim here is enforced by the implementation:
 * no INTERNET permission exists in the manifest, so nothing can leave the
 * device even accidentally.
 */
@Composable
fun PrivacyScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("Camera", style = MaterialTheme.typography.titleMedium)
            Text(
                "DriveGuard uses the front camera only while monitoring is " +
                    "active. Frames are analysed in memory in real time and are " +
                    "never saved, uploaded or shared.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Processing", style = MaterialTheme.typography.titleMedium)
            Text(
                "Face detection and eye-state classification run entirely on " +
                    "your device. The app has no network permission — it cannot " +
                    "send data anywhere even if it wanted to.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("What is stored", style = MaterialTheme.typography.titleMedium)
            Text(
                "Only session records: start time, duration, number of alerts " +
                    "and monitoring interruptions. No images, no video, no " +
                    "location. You can delete any session or clear all history " +
                    "at any time from Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("No accounts, no tracking", style = MaterialTheme.typography.titleMedium)
            Text(
                "DriveGuard requires no login, contains no ads and no " +
                    "analytics. It works fully offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
