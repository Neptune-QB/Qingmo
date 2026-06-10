package com.qingmo.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// 青墨 MVP — 黑白灰极简色板
// ============================================================

// --- 主色：黑白灰 ---
val Primary = Color(0xFF222222)
val PrimaryVariant = Color(0xFF111111)
val PrimaryLight = Color(0xFF555555)
val OnPrimary = Color(0xFFFFFFFF)
val GraphiteTeal = Color(0xFF1F3A37)
val GraphiteTealSoft = Color(0xFFEAF1ED)

// --- 辅色：浅灰 ---
val Secondary = Color(0xFFF4F4F4)
val SecondaryVariant = Color(0xFFEEEEEE)
val OnSecondary = Color(0xFF3D3D3D)

// --- 背景 ---
val Background = Color(0xFFFAFAFA)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFF4F4F4)
val SurfaceTint = Color(0xFFEEEEEE)
val SurfaceElevated = Color(0xFFFFFFFF)

// --- 文字：墨色层次 ---
val OnBackground = Color(0xFF222222)
val OnSurface = Color(0xFF222222)
val OnSurfaceVariant = Color(0xFF888888)
val OnSurfaceMuted = Color(0xFFAAAAAA)

// --- 边框 ---
val Border = Color(0xFFEEEEEE)
val BorderVariant = Color(0x1A222222)
val BorderLight = Color(0xFFF4F4F4)

// --- 功能色 ---
val Error = Color(0xFFB85C5C)
val OnError = Color(0xFFFFFFFF)
val Success = Color(0xFF444444)
val Warning = Color(0xFF666666)

// --- 播放器强调色 ---
val PlayerAccent = Color(0xFFE11D48)
val PlayerBg = Background

// --- 标签色 ---
val ConflictColor = Color(0xFF555555)
val TwistColor = Color(0xFF666666)
val SweetColor = Color(0xFF777777)
val FamousColor = Color(0xFF888888)
val FunnyColor = Color(0xFF555555)
val BranchColor = Color(0xFF666666)

// --- 渐变：封面暗部叠加 ---
val GradientCyanDark =
    Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0xB3000000)),
    )
val GradientCyanLight =
    Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0x66000000)),
    )
val GradientWarm =
    Brush.verticalGradient(
        colors = listOf(Color(0x00FFFFFF), Color(0x22EEEEEE)),
    )

// --- 渐变：灰阶横向强调 ---
val GradientPrimaryHorizontal =
    Brush.horizontalGradient(
        colors = listOf(Primary, PrimaryLight),
    )

// --- 表面层级叠加 ---
val SurfaceOverlayLight = Color(0x0A222222) // 4% 黑色叠加
val SurfaceOverlayMedium = Color(0x14222222) // 8% 黑色叠加
val SurfaceOverlayDark = Color(0x1E222222) // 12% 黑色叠加

// --- 青墨全局文本色扩展 ---
val TextPrimary = Color(0xFF222222)
val TextSecondary = Color(0xFF888888)
val TextDisabled = Color(0xFFBBBBBB)

// --- 卡片/面板专用强调色 ---
val CardIconTeal = GraphiteTeal
val CardIconPurple = Color(0xFF555555)
val DividerLight = Color(0xFFEEEEEE)
val DividerLighter = Color(0xFFF0F0F0)
