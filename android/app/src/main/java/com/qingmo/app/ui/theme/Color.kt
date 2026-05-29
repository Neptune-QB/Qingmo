package com.qingmo.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================
// 青墨 — 东方雅韵 × 现代极简 青白主题色板
// ============================================================

// --- 主色：石青 ---
val Primary = Color(0xFF5B8C85)
val PrimaryVariant = Color(0xFF3D6B7A)
val PrimaryLight = Color(0xFF7BA8A0)
val OnPrimary = Color(0xFFFFFFFF)

// --- 辅色：宣纸暖白 ---
val Secondary = Color(0xFFE8E3D7)
val SecondaryVariant = Color(0xFFD5CFC0)
val OnSecondary = Color(0xFF3D3D3D)

// --- 背景：宣纸层次 ---
val Background = Color(0xFFF7F3EC)
val Surface = Color(0xFFFCFAF6)
val SurfaceVariant = Color(0xFFF0EBE2)
val SurfaceTint = Color(0xFFEDE7DC)
val SurfaceElevated = Color(0xFFFFFFFF)

// --- 文字：墨色层次 ---
val OnBackground = Color(0xFF2C2C2C)
val OnSurface = Color(0xFF3D3D3D)
val OnSurfaceVariant = Color(0xFF7A7A7A)
val OnSurfaceMuted = Color(0xFFAAAAAA)

// --- 边框 ---
val Border = Color(0xFFD5CFC0)
val BorderVariant = Color(0x1A3D3D3D)
val BorderLight = Color(0xFFE8E3D7)

// --- 功能色 ---
val Error = Color(0xFFB85C5C)
val OnError = Color(0xFFFFFFFF)
val Success = Color(0xFF5B8C5B)
val Warning = Color(0xFFB89B5C)

// --- 播放器强调色 ---
val PlayerAccent = Color(0xFFE11D48)
val PlayerBg = Background

// --- 标签色 ---
val ConflictColor = Color(0xFFB85C5C)
val TwistColor = Color(0xFF6B5B8C)
val SweetColor = Color(0xFFB87B8C)
val FamousColor = Color(0xFFB8A85C)
val FunnyColor = Color(0xFF5B8C5B)
val BranchColor = Color(0xFF5B7B8C)

// --- 渐变：石青渐变（卡片封面叠加） ---
val GradientCyanDark =
    Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0xCC1A3A35)),
    )
val GradientCyanLight =
    Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0x995B8C85)),
    )
val GradientWarm =
    Brush.verticalGradient(
        colors = listOf(Color(0x00F7F3EC), Color(0x33E8E3D7)),
    )

// --- 渐变：石青横向强调 ---
val GradientPrimaryHorizontal =
    Brush.horizontalGradient(
        colors = listOf(Primary, PrimaryLight),
    )

// --- 表面层级叠加 ---
val SurfaceOverlayLight = Color(0x0A5B8C85) // 4% 石青叠加
val SurfaceOverlayMedium = Color(0x145B8C85) // 8% 石青叠加
val SurfaceOverlayDark = Color(0x1E3D3D3D) // 12% 黑色叠加
