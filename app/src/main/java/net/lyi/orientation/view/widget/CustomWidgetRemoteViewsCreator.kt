package net.lyi.orientation.view.widget

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import net.lyi.android.orientationfaker.BuildConfig
import net.lyi.android.orientationfaker.R
import net.lyi.orientation.control.Functions
import net.lyi.orientation.control.PendingIntentCreator
import net.lyi.orientation.room.WidgetSettingEntity
import net.lyi.orientation.settings.OrientationPreference
import net.lyi.orientation.util.alpha
import net.lyi.orientation.util.opaque
import net.lyi.orientation.view.widget.ViewIds.ViewId

class CustomWidgetRemoteViewsCreator(
    private val context: Context,
    width: Int,
    height: Int,
    private val setting: WidgetSettingEntity,
    private val orientation: OrientationPreference,
) {
    private val column: Int
    private val row: Int
    private val padding: Int

    init {
        column = (width / CELL_SIZE).coerceIn(1, 10)
        val size = setting.functions.size
        row = (height / CELL_SIZE).coerceIn(1, (size + column - 1) / column)
        val cellHeight = height.toFloat() / row
        val density = context.resources.displayMetrics.density
        padding = ((cellHeight - ICON_SIZE) / 2 * density).toInt().coerceAtLeast(0)
    }

    fun create(): RemoteViews = RemoteViews(context.packageName, R.layout.custom_widget).also { views ->
        views.setInt(R.id.list, "setBackgroundColor", setting.base)
        views.removeAllViews(R.id.list)
        repeat(row) { position ->
            views.addView(R.id.list, createRowView(position))
        }
    }

    private fun createRowView(position: Int): RemoteViews =
        RemoteViews(BuildConfig.APPLICATION_ID, R.layout.item_widget).also { rowViews ->
            rowViews.setViewPadding(R.id.row, 0, padding, 0, padding)
            ViewIds.widget.forEachIndexed { index, it ->
                rowViews.setUpRemoteViews(position, index, it)
            }
        }

    private fun RemoteViews.setUpRemoteViews(
        position: Int,
        index: Int,
        viewId: ViewId,
    ) {
        val helpers = RemoteViewHelpers(this, viewId)
        helpers.button.setVisible(index < column)
        if (index >= column) return
        val function = setting.functions.getOrNull(position * column + index)
        if (function != null) {
            helpers.icon.setVisible(true)
            helpers.shape.setVisible(true)
            val selected = function.orientation == orientation.orientation
            helpers.icon.setImageColor(if (selected) setting.foregroundSelected else setting.foreground)
            helpers.shape.setImageColor(if (selected) setting.backgroundSelected else setting.background)
            helpers.shape.setImageResource(setting.shape.iconId)
            helpers.button.setOnClickPendingIntent(PendingIntentCreator.function(context, function))
            Functions.find(function)?.let { helpers.icon.setImageResource(it.icon) }
        } else {
            helpers.icon.setVisible(false)
            helpers.shape.setVisible(false)
        }
    }

    private class RemoteViewHelpers(views: RemoteViews, viewId: ViewId) {
        val button = views.helper(viewId.buttonId)
        val icon = views.helper(viewId.iconId)
        val shape = views.helper(viewId.shapeId)
    }

    private class RemoteViewHelper(
        private val views: RemoteViews, @IdRes private val id: Int
    ) {
        fun setVisible(visible: Boolean) {
            views.setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
        }

        fun setImageResource(@DrawableRes resourceId: Int) {
            views.setImageViewResource(id, resourceId)
        }

        fun setImageColor(@ColorInt color: Int) {
            views.setInt(id, "setColorFilter", color.opaque())
            views.setInt(id, "setImageAlpha", color.alpha())
        }

        fun setOnClickPendingIntent(intent: PendingIntent) {
            views.setOnClickPendingIntent(id, intent)
        }
    }

    companion object {
        private const val CELL_SIZE = 50
        private const val ICON_SIZE = 48
        private fun RemoteViews.helper(@IdRes id: Int): RemoteViewHelper = RemoteViewHelper(this, id)
    }
}
