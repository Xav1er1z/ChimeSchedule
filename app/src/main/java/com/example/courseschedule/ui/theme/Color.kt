package com.example.courseschedule.ui.theme

import androidx.compose.ui.graphics.Color

// —— iOS 系统色 ——
val iOSBlue = Color(0xFF007AFF)
val iOSGreen = Color(0xFF34C759)
val iOSOrange = Color(0xFFFF9500)
val iOSRed = Color(0xFFFF3B30)
val iOSPurple = Color(0xFF5856D6)
val iOSDeepPurple = Color(0xFFAF52DE)
val iOSPink = Color(0xFFFF2D55)
val iOSTeal = Color(0xFF5AC8FA)
val iOSYellow = Color(0xFFFFCC00)
val iOSBrown = Color(0xFFA2845E)

/** 课程可选颜色（ARGB Long 值，便于持久化） */
val courseColorArgb: List<Long> = listOf(
    0xFF007AFF, // 蓝
    0xFF34C759, // 绿
    0xFFFF9500, // 橙
    0xFFFF3B30, // 红
    0xFF5856D6, // 紫
    0xFFAF52DE, // 深紫
    0xFFFF2D55, // 粉
    0xFF5AC8FA, // 青
    0xFFFFCC00, // 黄
    0xFFA2845E, // 棕
)

/** 课程可选颜色（Compose Color） */
val courseColors: List<Color> = courseColorArgb.map { Color(it) }

// —— 浅色模式 ——
val LightBackground = Color(0xFFF2F2F7)
val LightCard = Color(0xFFFFFFFF)
val LightSeparator = Color(0xFFE5E5EA)
val LightSecondaryText = Color(0xFF8E8E93)

// —— 深色模式 ——
val DarkBackground = Color(0xFF000000)
val DarkCard = Color(0xFF1C1C1E)
val DarkSeparator = Color(0xFF38383A)
val DarkSecondaryText = Color(0xFF98989F)
