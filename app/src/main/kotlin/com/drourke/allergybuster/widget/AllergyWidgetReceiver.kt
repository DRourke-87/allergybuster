package com.drourke.allergybuster.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AllergyWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = AllergyWidget()

    companion object {
        suspend fun updateWidget(context: Context) {
            AllergyWidget().updateAll(context)
        }
    }
}
