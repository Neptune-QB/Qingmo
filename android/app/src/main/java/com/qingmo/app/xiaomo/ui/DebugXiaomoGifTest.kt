package com.qingmo.app.xiaomo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.xiaomo.StableXiaomoGif

@Composable
fun DebugXiaomoGifTest(modifier: Modifier = Modifier) {
    var currentCode by remember { mutableStateOf("idle") }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "小墨 GIF 隔离测试",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "当前: $currentCode",
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA),
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color(0xFF16213E), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            StableXiaomoGif(code = currentCode, modifier = Modifier.size(180.dp))
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GifTestButton("idle") { currentCode = "idle" }
            GifTestButton("slapback") { currentCode = "slapback" }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GifTestButton("heartbreak") { currentCode = "heartbreak" }
            GifTestButton("sweet_moment") { currentCode = "sweet_moment" }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GifTestButton("reversal") { currentCode = "reversal" }
            GifTestButton("cliffhanger") { currentCode = "cliffhanger" }
        }
    }
}

@Composable
private fun GifTestButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A6FA5)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Color.White)
    }
}
