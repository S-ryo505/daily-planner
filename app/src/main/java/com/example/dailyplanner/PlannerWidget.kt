package com.example.dailyplanner

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.json.JSONObject

/** ホーム画面のウィジェット本体。表示は静止（3Dは本体アプリ側）。 */
class PlannerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = JSONObject(Store.load(context))
        val date = Plan.todayStr()
        val rows = Plan.activeItems(s, date, Plan.weekday())
        val challenge = Plan.challengeShort(s, date)
        val test = Plan.testShort(s, date)
        provideContent {
            WidgetBody(rows, challenge, test)
        }
    }
}

@Composable
private fun WidgetBody(rows: List<Row>, challenge: String, test: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.White)
            .padding(10.dp)
    ) {
        Text(
            "今日",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)
        )
        if (rows.isEmpty()) {
            Text("今日の予定はありません", style = TextStyle(fontSize = 13.sp))
        }
        rows.take(5).forEach { r ->
            val mark = if (r.done >= r.target) "✅" else "⬜"
            Text(
                "$mark ${r.name}  ${r.done}/${r.target}",
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable(
                        actionRunCallback<ToggleAction>(
                            actionParametersOf(ToggleAction.ITEM to r.id)
                        )
                    ),
                style = TextStyle(fontSize = 14.sp)
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            challenge,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(fontSize = 13.sp)
        )
        Text(
            test,
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity<MainActivity>()),
            style = TextStyle(fontSize = 13.sp)
        )
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
        PlannerWidget().updateAll(context)
    }

    companion object {
        val ITEM = ActionParameters.Key<String>("item")
    }
}

class PlannerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlannerWidget()
}
