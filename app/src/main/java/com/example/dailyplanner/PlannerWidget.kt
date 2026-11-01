package com.example.dailyplanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row as GlanceRow
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.json.JSONObject

/** ホーム画面のウィジェット。キャラは静止画（アプリで保存した絵）、吹き出しは一言の文字。 */
class PlannerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = JSONObject(Store.load(context))
        val date = Plan.todayStr()
        val wd = Plan.weekday()
        val rows = Plan.activeItems(s, date, wd)
        val bubble = Plan.bubble(s, date, wd)
        val challenge = Plan.challengeShort(s, date)
        val test = Plan.testShort(s, date)
        val chara = loadChara(context)
        provideContent {
            WidgetBody(chara, bubble, rows.take(3), challenge, test)
        }
    }

    private fun loadChara(ctx: Context): Bitmap? {
        val f = Chara.snapshotFile(ctx)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return Bitmap.createScaledBitmap(b, 160, 160, true)
    }
}

@Composable
private fun WidgetBody(
    chara: Bitmap?,
    bubble: String,
    rows: List<Row>,
    challenge: String,
    test: String
) {
    val context = LocalContext.current
    val open = actionStartActivity(Intent(context, MainActivity::class.java))

    GlanceRow(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.White)
            .padding(8.dp)
    ) {
        Column(modifier = GlanceModifier.width(88.dp).clickable(open)) {
            if (chara != null) {
                Image(
                    provider = ImageProvider(chara),
                    contentDescription = "キャラクター",
                    modifier = GlanceModifier.size(84.dp)
                )
            } else {
                Text("アプリを開く", style = TextStyle(fontSize = 12.sp))
            }
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                bubble,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEF0FF))
                    .padding(6.dp)
                    .clickable(open),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp)
            )
            Spacer(GlanceModifier.height(4.dp))
            rows.forEach { r ->
                val mark = if (r.done >= r.target) "✅" else "⬜"
                Text(
                    "$mark ${r.name}  ${r.done}/${r.target}",
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clickable(
                            actionRunCallback<ToggleAction>(
                                actionParametersOf(ToggleAction.ITEM to r.id)
                            )
                        ),
                    style = TextStyle(fontSize = 13.sp)
                )
            }
            Text(
                challenge,
                modifier = GlanceModifier.fillMaxWidth().clickable(open),
                style = TextStyle(fontSize = 12.sp)
            )
            Text(
                test,
                modifier = GlanceModifier.fillMaxWidth().clickable(open),
                style = TextStyle(fontSize = 12.sp)
            )
        }
    }
}

/** ウィジェットの項目をタップしたときの処理（回数を1つ進める） */
class ToggleAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val id = parameters[ITEM] ?: return
        Store.save(context, Plan.toggle(Store.load(context), id, Plan.todayStr()))
        refreshWidgets(context)
    }

    companion object {
        val ITEM = ActionParameters.Key<String>("item")
    }
}

class PlannerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlannerWidget()
}

/** 置かれている全ウィジェットを描き直す */
suspend fun refreshWidgets(ctx: Context) {
    val widget = PlannerWidget()
    GlanceAppWidgetManager(ctx).getGlanceIds(PlannerWidget::class.java).forEach { id ->
        widget.update(ctx, id)
    }
}
