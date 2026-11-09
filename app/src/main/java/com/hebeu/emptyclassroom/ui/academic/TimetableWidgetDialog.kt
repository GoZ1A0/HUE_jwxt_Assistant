package com.hebeu.emptyclassroom.ui.academic

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.hebeu.emptyclassroom.data.widget.TimetableWidgetProvider

@Composable
fun TimetableWidgetDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    AcademicDialog("桌面小组件", onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text("关闭") }
        if (manager.isRequestPinAppWidgetSupported) TextButton(onClick = {
            val requested = manager.requestPinAppWidget(ComponentName(context, TimetableWidgetProvider::class.java), null, null)
            Toast.makeText(context, if (requested) "请在桌面确认添加“今日课表”" else "请通过桌面的小组件列表添加", Toast.LENGTH_LONG).show()
        }) { Text("添加到桌面") }
    }) {
        Text("今日课表显示当前所选课表的当天课程与已确认考试，不需要开启通知权限。点击卡片可打开课表，点击刷新只读取本机保存的数据。")
        Text("添加方法：长按手机桌面空白处 → 小组件（或添加部件）→ HUE教务助手 → 今日课表。部分桌面需要先进入“安卓小组件”。")
        Text("可拖动边缘调整大小。切换课表或编辑安排后会更新；退出账号后清空展示。移除小组件不会删除课表。系统自动刷新可能延迟，可点击刷新。")
        Text("若列表暂未出现，先打开应用一次，再重新打开桌面的小组件列表。")
    }
}
