package com.qingmo.app.ui.screens

import androidx.compose.runtime.Composable

@Suppress("ktlint:standard:function-naming")
@Composable
fun PlayerScreen(
    dramaId: Int,
    episodeId: Long,
    onBack: () -> Unit,
    onNavigate: (Int, Long) -> Unit = { _, _ -> },
) {
    DramaPagerScreen(
        dramaId = dramaId,
        episodeId = episodeId,
        onBack = onBack,
        onNextDrama = { id -> onNavigate(id, -1L) },
    )
}
