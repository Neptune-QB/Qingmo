package com.qingmo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingmo.app.data.auth.AuthRepository
import com.qingmo.app.data.auth.RegisterRequest
import com.qingmo.app.data.auth.TokenManager
import com.qingmo.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onGoLogin: () -> Unit,
    deviceId: String,
) {
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = "✨ 创建账户",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "加入青墨，和小墨一起看剧",
            fontSize = 14.sp,
            color = OnSurfaceMuted,
        )
        Spacer(Modifier.height(28.dp))

        // 用户名
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            label = { Text("用户名 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary,
                cursorColor = Primary,
            ),
        )
        Spacer(Modifier.height(10.dp))

        // 昵称
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("昵称（选填）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary,
                cursorColor = Primary,
            ),
        )
        Spacer(Modifier.height(10.dp))

        // 密码
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("密码 *") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                focusedLabelColor = Primary,
                cursorColor = Primary,
            ),
        )
        Spacer(Modifier.height(10.dp))

        // 确认密码
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; error = null },
            label = { Text("确认密码 *") },
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
        Spacer(Modifier.height(12.dp))

        error?.let {
            Text(text = it, fontSize = 13.sp, color = OnSurfaceMuted)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                when {
                    username.isBlank() || password.isBlank() -> error = "用户名和密码不能为空"
                    password.length < 4 -> error = "密码至少 4 个字符"
                    password != confirmPassword -> error = "两次密码不一致"
                    else -> {
                        isLoading = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                repo.register(
                                    RegisterRequest(
                                        username = username.trim(),
                                        password = password,
                                        nickname = nickname.ifBlank { username.trim() },
                                        deviceId = deviceId,
                                    ),
                                )
                            }
                            isLoading = false
                            result.onSuccess { resp ->
                                if (resp.ok) {
                                    TokenManager.saveAuth(resp.token, resp.userId, resp.username, resp.nickname)
                                    onRegisterSuccess()
                                } else {
                                    error = resp.error.ifEmpty { "注册失败" }
                                }
                            }.onFailure { e ->
                                error = "网络错误：${e.message}"
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = OnPrimary, strokeWidth = 2.dp)
            } else {
                Text("注 册", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row {
            Text("已有账户？", fontSize = 13.sp, color = OnSurfaceMuted)
            Text(
                text = "返回登录",
                fontSize = 13.sp,
                color = Primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onGoLogin() },
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
