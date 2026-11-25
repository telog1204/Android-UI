package com.androidui.shade.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.androidui.shade.overlay.ShadeAccessibilityService
import com.androidui.shade.settings.Permissions
import com.androidui.shade.settings.ShadePrefs

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val prefs = remember { ShadePrefs(context) }
    var listenerEnabled by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var writeSettingsEnabled by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var leftHalfOnly by remember { mutableStateOf(prefs.leftHalfOnly) }
    var volumePanel by remember { mutableStateOf(prefs.volumePanelEnabled) }
    var headsUp by remember { mutableStateOf(prefs.headsUpEnabled) }
    val serviceRunning by ShadeAccessibilityService.running.collectAsState()

    // 設定画面から戻ってきたときに許可の状態を確認し直す
    LifecycleResumeEffect(Unit) {
        listenerEnabled = Permissions.isNotificationListenerEnabled(context)
        accessibilityEnabled = Permissions.isAccessibilityEnabled(context)
        writeSettingsEnabled = Permissions.canWriteSettings(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Android 15風 通知パネル", style = MaterialTheme.typography.headlineSmall)
        Text(
            "次の順に設定してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (Build.VERSION.SDK_INT >= 33) {
            StepCard(
                number = 1,
                title = "制限付き設定を許可",
                description = "APKを手動でインストールした場合、2と3の前に必要です。" +
                    "アプリ情報を開き、右上の︙メニューから「制限付き設定を許可」を選んでください。" +
                    "メニューに項目がなければ不要です。",
                done = null,
                buttonLabel = "アプリ情報を開く",
                onClick = { Permissions.openAppDetails(context) },
            )
        }
        StepCard(
            number = 2,
            title = "通知へのアクセス",
            description = "一覧から「AndroidUI 通知パネル」をオンにしてください。通知の読み取りに使います。",
            done = listenerEnabled,
            buttonLabel = "設定を開く",
            onClick = { Permissions.openNotificationListenerSettings(context) },
        )
        StepCard(
            number = 3,
            title = "ユーザー補助",
            description = "「ダウンロードしたアプリ」などから「AndroidUI 通知パネル」をオンにしてください。" +
                "画面上端のスワイプ検知とパネル表示に使います。",
            done = accessibilityEnabled,
            buttonLabel = "設定を開く",
            onClick = { Permissions.openAccessibilitySettings(context) },
        )
        StepCard(
            number = 4,
            title = "システム設定の変更",
            description = "「AndroidUI 通知パネル」を許可してください。明るさスライダーと自動回転の切り替えに使います。",
            done = writeSettingsEnabled,
            buttonLabel = "設定を開く",
            onClick = { Permissions.openWriteSettings(context) },
        )
        StepCard(
            number = 5,
            title = "自動起動を許可（HyperOS）",
            description = "一覧で「AndroidUI 通知パネル」をオンにしてください。HyperOSにアプリを停止されないようにします。",
            done = null,
            buttonLabel = "設定を開く",
            onClick = { Permissions.openAutoStart(context) },
        )
        StepCard(
            number = 6,
            title = "バッテリー制限を解除（HyperOS）",
            description = "アプリ情報の「バッテリーセーバー」を「制限なし」にしてください。",
            done = null,
            buttonLabel = "アプリ情報を開く",
            onClick = { Permissions.openAppDetails(context) },
        )

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        SettingSwitch(
            title = "パネルを有効にする",
            description = null,
            checked = enabled,
            onCheckedChange = {
                enabled = it
                prefs.enabled = it
            },
        )
        SettingSwitch(
            title = "画面上端の左半分だけで開く",
            description = "右半分はHyperOSのコントロールセンターをそのまま使えます。",
            checked = leftHalfOnly,
            onCheckedChange = {
                leftHalfOnly = it
                prefs.leftHalfOnly = it
            },
        )
        SettingSwitch(
            title = "音量パネルを置き換える",
            description = "音量ボタンを押したときに、Android 15風の音量パネルを表示します。" +
                "表示されない場合は、ユーザー補助を一度オフにしてからオンにし直してください。",
            checked = volumePanel,
            onCheckedChange = {
                volumePanel = it
                prefs.volumePanelEnabled = it
            },
        )
        SettingSwitch(
            title = "ポップアップ通知を表示する",
            description = "新しい通知を画面上部にAndroid 15風のカードで表示します。" +
                "HyperOSのポップアップと二重に出る場合は、通知を出すアプリごとに" +
                "「フローティング通知」をオフにしてください。",
            checked = headsUp,
            onCheckedChange = {
                headsUp = it
                prefs.headsUpEnabled = it
            },
        )
        FilledTonalButton(onClick = { Permissions.openNotificationSettings(context) }) {
            Text("HyperOSの通知設定を開く")
        }
        Button(
            onClick = { ShadeAccessibilityService.instance?.openPanel() },
            enabled = serviceRunning && enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("パネルを開いてみる")
        }
        if (!serviceRunning) {
            Text(
                "ユーザー補助をオンにすると使えます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    description: String,
    done: Boolean?,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number. $title", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done != null) {
                    Text(
                        text = if (done) "許可済み" else "未許可",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (done) scheme.primary else scheme.error,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
