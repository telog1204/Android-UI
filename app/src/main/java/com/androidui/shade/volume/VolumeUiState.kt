package com.androidui.shade.volume

import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 音量パネルに表示する内容 */
class VolumeUiState {
    var level by mutableIntStateOf(0)
    var min by mutableIntStateOf(0)
    var max by mutableIntStateOf(15)
    var ringerMode by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)

    /** 表示中か（false にすると消えるアニメーションのあとでウィンドウを外す） */
    var shown by mutableStateOf(false)

    /** 音量の割合（0〜1） */
    val fraction: Float
        get() = if (max > min) (level - min).toFloat() / (max - min) else 0f
}
