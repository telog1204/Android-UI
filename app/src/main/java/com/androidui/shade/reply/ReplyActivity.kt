package com.androidui.shade.reply

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.androidui.shade.notification.NotificationActions
import com.androidui.shade.notification.ShadeAction
import com.androidui.shade.ui.theme.ShadeTheme

/**
 * 通知への返信を入力する画面。
 * パネルはキーボードより手前に表示されるため、返信入力だけは通常の画面で行う。
 */
class ReplyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pendingIntent = IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, PendingIntent::class.java)
        val inputs = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_INPUTS, RemoteInput::class.java)
        if (pendingIntent == null || inputs.isNullOrEmpty()) {
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val hint = inputs.first().label?.toString()?.takeIf { it.isNotBlank() } ?: "返信"

        setContent {
            ShadeTheme {
                ReplySheet(
                    title = title,
                    hint = hint,
                    onSend = { text ->
                        NotificationActions.reply(this, pendingIntent, inputs, text)
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_INTENT = "com.androidui.shade.extra.INTENT"
        private const val EXTRA_INPUTS = "com.androidui.shade.extra.INPUTS"
        private const val EXTRA_TITLE = "com.androidui.shade.extra.TITLE"

        fun createIntent(context: Context, action: ShadeAction, title: String): Intent =
            Intent(context, ReplyActivity::class.java)
                .putExtra(EXTRA_INTENT, action.intent)
                .putParcelableArrayListExtra(EXTRA_INPUTS, ArrayList(action.remoteInputs))
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun ReplySheet(
    title: String,
    hint: String,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // 入力欄の外側をタップしたら閉じる
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { onCancel() } },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        placeholder = { Text(hint) },
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { onSend(text.trim()) }, enabled = text.isNotBlank()) {
                        Text("送信")
                    }
                }
            }
        }
    }
}
