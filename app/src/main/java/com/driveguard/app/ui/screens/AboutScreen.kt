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

/** About: identity, model transparency, limitations. */
@Composable
fun AboutScreen(appVersion: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("About", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        SectionCard {
            Text("DriveGuard", style = MaterialTheme.typography.titleLarge)
            Text(
                "Version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Real-time driver drowsiness detection, entirely on your phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Detection model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "A compact convolutional neural network (683,329 parameters, " +
                    "2.6 MB) classifies each eye crop as open or closed, 64×64 " +
                    "RGB, running fully on-device via LiteRT. Output is the " +
                    "probability that the eye is closed. See the project " +
                    "documentation for the full model contract.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Acknowledgements", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Built with Jetpack Compose, CameraX, ML Kit and LiteRT.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("Limitations", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "• Not a medical or certified safety device.\n" +
                    "• Performance depends on lighting, glasses, camera angle " +
                    "and face visibility.\n" +
                    "• Alerts can be missed or false; never rely on it as your " +
                    "only safeguard against fatigue.\n" +
                    "• No accuracy metric is claimed for the supplied model; " +
                    "validate behaviour in your own conditions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
