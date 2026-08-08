package com.sideslide.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("sideslide", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var sensitivity: Float
        get() = prefs.getFloat("sensitivity", 0.65f)
        set(value) = prefs.edit().putFloat("sensitivity", value).apply()

    var holdMs: Long
        get() = prefs.getLong("hold_ms", 150L)
        set(value) = prefs.edit().putLong("hold_ms", value).apply()

    var swipeDistanceDp: Int
        get() = prefs.getInt("swipe_distance_dp", 42)
        set(value) = prefs.edit().putInt("swipe_distance_dp", value).apply()

    var panelWidthDp: Int
        get() = prefs.getInt("panel_width_dp", 280)
        set(value) = prefs.edit().putInt("panel_width_dp", value).apply()

    var panelHeightDp: Int
        get() = prefs.getInt("panel_height_dp", 360)
        set(value) = prefs.edit().putInt("panel_height_dp", value).apply()

    var scrollSensitivity: Float
        get() = prefs.getFloat("scroll_sensitivity", 1f)
        set(value) = prefs.edit().putFloat("scroll_sensitivity", value).apply()

    var edge: String
        get() = prefs.getString("edge", "both") ?: "both"
        set(value) = prefs.edit().putString("edge", value).apply()

    var panelVertical: Float
        get() = prefs.getFloat("panel_vertical", 0.5f)
        set(value) = prefs.edit().putFloat("panel_vertical", value).apply()

    var animationMs: Long
        get() = prefs.getLong("animation_ms", 180L)
        set(value) = prefs.edit().putLong("animation_ms", value).apply()

    var fadeMs: Long
        get() = prefs.getLong("fade_ms", 160L)
        set(value) = prefs.edit().putLong("fade_ms", value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()
}