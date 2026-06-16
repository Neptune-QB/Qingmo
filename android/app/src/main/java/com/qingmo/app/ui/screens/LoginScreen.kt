package com.qingmo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.qingmo.app.data.auth.AuthRepository
import com.qingmo.app.data.auth.LoginRequest
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onGoRegister: () -> Unit,
    deviceId: String,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 标题
        Image(
            painter = painterResource(com.qingmo.app.R.drawable.xiaomo_login),
            contentDescription = "青墨",
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "青墨短剧",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = GraphiteTeal,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "登录你的账户",
            fontSize = 14.sp,
            color = OnSurfaceMuted,
        )
        Spacer(Modifier.height(32.dp))

        // 用户名
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary,
                cursorColor = Primary,
            ),
        )
        Spacer(Modifier.height(12.dp))

        // 密码
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary,
                cursorColor = Primary,
            ),
        )
        Spacer(Modifier.height(8.dp))

        // 错误提示
        error?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = Color(0xFFE53935),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
            )
            Spacer(Modifier.height(8.dp))
        }

        // 登录按钮
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    error = "请输入用户名和密码"
                    return@Button
                }
                isLoading = true
                error = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repo.login(LoginRequest(username.trim(), password, deviceId))
                    }
                    isLoading = false
                    result.onSuccess { resp ->
                        if (resp.ok) {
                            TokenManager.saveAuth(resp.token, resp.userId, resp.username, resp.nickname, resp.avatar)
                            onLoginSuccess()
                        } else {
                            error = resp.error.ifEmpty { "登录失败" }
                        }
                    }.onFailure { e ->
                        error = "网络错误：${e.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = OnPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("登 录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 注册入口
        Row {
            Text("还没有账户？", fontSize = 13.sp, color = OnSurfaceMuted)
            Text(
                text = "立即注册",
                fontSize = 13.sp,
                color = Primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onGoRegister() },
            )
        }
    }
}
