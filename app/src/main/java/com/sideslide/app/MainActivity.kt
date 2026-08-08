package com.sideslide.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        setContent {
            MaterialTheme {
                SettingsScreen(store)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized && store.enabled && Settings.canDrawOverlays(this)) {
            SideSlideService.start(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun SettingsScreen(store: SettingsStore) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(store.enabled) }
    var sensitivity by rememberSaveable { mutableStateOf(store.sensitivity) }
    var hold by rememberSaveable { mutableStateOf(store.holdMs.toFloat()) }
    var distance by rememberSaveable { mutableStateOf(store.swipeDistanceDp.toFloat()) }
    var width by rememberSaveable { mutableStateOf(store.panelWidthDp.toFloat()) }
    var height by rememberSaveable { mutableStateOf(store.panelHeightDp.toFloat()) }
    var scroll by rememberSaveable { mutableStateOf(store.scrollSensitivity) }
    var edge by rememberSaveable { mutableStateOf(store.edge) }
    var vertical by rememberSaveable { mutableStateOf(store.panelVertical) }
    var animation by rememberSaveable { mutableStateOf(store.animationMs.toFloat()) }
    var fade by rememberSaveable { mutableStateOf(store.fadeMs.toFloat()) }
    var haptics by rememberSaveable { mutableStateOf(store.haptics) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SideSlide") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SideSlide", style = MaterialTheme.typography.titleLarge)
                            Text("Swipe from an edge to reveal your panel.", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                store.enabled = it
                                if (it) {
                                    if (Settings.canDrawOverlays(context)) SideSlideService.start(context)
                                    else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                                } else SideSlideService.stop(context)
                            }
                        )
                    }
                    if (!Settings.canDrawOverlays(context)) {
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        }) { Text("Grant overlay permission") }
                    }
                }
            }

            Section("Gesture")
            SliderSetting("Sensitivity", sensitivity, 0f..1f, "%.2f") {
                sensitivity = it; store.sensitivity = it
            }
            SliderSetting("Edge hold", hold, 50f..500f, "%.0f ms") {
                hold = it; store.holdMs = it.toLong()
            }
            SliderSetting("Swipe distance", distance, 16f..120f, "%.0f dp") {
                distance = it; store.swipeDistanceDp = it.toInt()
            }

            Text("Edge", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("left" to "Left", "right" to "Right", "both" to "Both").forEach { (value, label) ->
                    Button(onClick = { edge = value; store.edge = value }) { Text(if (edge == value) "✓ $label" else label) }
                }
            }

            HorizontalDivider()
            Section("Panel")
            SliderSetting("Width", width, 200f..420f, "%.0f dp") {
                width = it; store.panelWidthDp = it.toInt()
            }
            SliderSetting("Height", height, 220f..700f, "%.0f dp") {
                height = it; store.panelHeightDp = it.toInt()
            }
            SliderSetting("Vertical position", vertical, 0.1f..0.9f, "%.0f%%") {
                vertical = it; store.panelVertical = it
            }
            SliderSetting("Scroll sensitivity", scroll, 0.5f..2f, "%.1fx") {
                scroll = it; store.scrollSensitivity = it
            }

            HorizontalDivider()
            Section("Animation")
            SliderSetting("Animation duration", animation, 60f..400f, "%.0f ms") {
                animation = it; store.animationMs = it.toLong()
            }
            SliderSetting("Fade duration", fade, 60f..400f, "%.0f ms") {
                fade = it; store.fadeMs = it.toLong()
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Haptic feedback")
                Switch(checked = haptics, onCheckedChange = { haptics = it; store.haptics = it })
            }

            Text(
                "SideSlide keeps only small invisible edge capture strips active. The visible panel is created only after an intentional gesture is detected.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@androidx.compose.runtime.Composable
private fun SliderSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(String.format(valueText, value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}