package com.zhongbai233.net_music_can_play_bili.editor.host.controlconsole.media;


import com.zhongbai233.net_music_can_play_bili.util.MathCompat;

/** 合成 hardRange 空间增益与进入/退出时间包络，供视频、字幕和音频保持一致。 */
public final class ControlConsoleConsumerGain {
    private ControlConsoleConsumerGain() {
    }

    public static float combine(float rangeGain, float envelopeGain) {
        if (!Float.isFinite(rangeGain) || !Float.isFinite(envelopeGain)) {
            return 0.0F;
        }
        return MathCompat.clamp(rangeGain, 0.0F, 1.0F) * MathCompat.clamp(envelopeGain, 0.0F, 1.0F);
    }
}