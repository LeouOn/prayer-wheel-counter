package com.prayerwheel.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Hosts [PrayerWheelWidget] so it appears in the Android widget picker and can
 * be placed on the home screen. Registered in AndroidManifest.xml with the
 * APPWIDGET_UPDATE intent-filter and the appwidget-provider metadata.
 */
class PrayerWheelWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PrayerWheelWidget()
}
