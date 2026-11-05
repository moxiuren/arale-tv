package com.cycitv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycitv.App
import com.cycitv.AuthStore
import com.cycitv.data.PhoneInputServer
import com.cycitv.ui.components.QrCode
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.cycButtonShape
import com.cycitv.ui.theme.BrandGlowColor
import com.cycitv.ui.theme.OnMintText
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 设置页内的登录子页模式: 主页 / 手机扫码 / 电视键盘直接输入 */
private enum class LoginPage { NONE, QR, KEYBOARD }

@Composable
fun SettingsScreen() {
    var page by remember { mutableStateOf(LoginPage.NONE) }
    val app = LocalContext.current.applicationContext as App
    // 登录态(用户名):随退出/登录变化,remember 不够,用 observable 值
    var username by remember { mutableStateOf(app.authUsername) }

    // 监听登录结果:手机扫码登录成功后更新登录态
    LaunchedEffect(Unit) {
        PhoneInputServer.loginResult.collect { r ->
            if (r.ok) {
                // 注意用 r.username(用户名),r.msg 只是"登录成功"这类提示文案
                app.authUsername = r.username.ifBlank { r.msg }
                username = app.authUsername
                page = LoginPage.NONE
            }
        }
    }

    when (page) {
        LoginPage.QR -> LoginSubPage(
            onBack = { page = LoginPage.NONE },
            onLoggedIn = { u ->
                app.authUsername = u
                username = u
                page = LoginPage.NONE
            },
        )
        LoginPage.KEYBOARD -> TvKeyboardLoginPage(
            onBack = { page = LoginPage.NONE },
            onLoggedIn = { u ->
                app.authUsername = u
                username = u
                page = LoginPage.NONE
            },
        )
        LoginPage.NONE -> SettingsHome(
            app = app,
            username = username,
            onUsernameChange = { username = it },
            onOpenQr = { page = LoginPage.QR },
            onOpenKeyboard = { page = LoginPage.KEYBOARD },
        )
    }
}

@Composable
private fun SettingsHome(
    app: App,
    username: String?,
    onUsernameChange: (String?) -> Unit,
    onOpenQr: () -> Unit,
    onOpenKeyboard: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(start = 36.dp, top = 20.dp)) {
        Text("设置", fontSize = 28.sp, color = TextMain)
        // 账号行
        Row(
            Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val expired = app.authExpired
            Text(
                when {
                    username == null -> "未登录"
                    expired -> "登录已失效: " + username
                    else -> "已登录: " + username
                },
                fontSize = 20.sp,
                color = when {
                    username == null -> TextDim
                    expired -> Color(0xFFFF8080)
                    else -> Primary
                },
            )
            if (username != null) {
                androidx.tv.material3.Button(
                    shape = cycButtonShape(),
                    onClick = {
                        app.scope.launch {
                            // clear 会连同加密保存的凭据一起删除,之后不再自动续登
                            AuthStore.clear(app)
                            app.api.token = null
                            app.authUsername = null
                        }
                        app.authExpired = false
                        onUsernameChange(null)
                    },
                    modifier = Modifier.padding(start = 24.dp),
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Surface,
                        contentColor = TextMain,
                        focusedContainerColor = Primary,
                        focusedContentColor = OnMintText,
                    ),
                ) {
                    Text("退出登录", color = TextMain, fontSize = 16.sp)
                }
            }
        }

        // 显示模式: 白天 / 黑夜 (扁平粗线条两套色板)
        val darkNow by app.darkTheme.collectAsState()
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("显示模式", fontSize = 18.sp, color = TextDim, modifier = Modifier.width(96.dp))
            androidx.tv.material3.Button(
                shape = cycButtonShape(),
                onClick = { app.setDarkTheme(true) },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = if (darkNow) Primary else Surface,
                    contentColor = if (darkNow) OnMintText else TextMain,
                    focusedContainerColor = Primary,
                    focusedContentColor = OnMintText,
                ),
            ) {
                Text("黑夜", color = if (darkNow) OnMintText else TextMain, fontSize = 16.sp)
            }
            androidx.tv.material3.Button(
                shape = cycButtonShape(),
                onClick = { app.setDarkTheme(false) },
                modifier = Modifier.padding(start = 14.dp),
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = if (!darkNow) Primary else Surface,
                    contentColor = if (!darkNow) OnMintText else TextMain,
                    focusedContainerColor = Primary,
                    focusedContentColor = OnMintText,
                ),
            ) {
                Text("白天", color = if (!darkNow) OnMintText else TextMain, fontSize = 16.sp)
            }
        }

        if (username == null) {
            // 两种登录入口: 遥控器直接输账号密码 / 手机扫码
            Row(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.tv.material3.Button(
                    shape = cycButtonShape(),
                    onClick = onOpenKeyboard,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Primary,
                        contentColor = OnMintText,
                        focusedContainerColor = Primary,
                        focusedContentColor = OnMintText,
                    ),
                ) {
                    Text("遥控器输入登录", color = OnMintText, fontSize = 16.sp)
                }
                androidx.tv.material3.Button(
                    shape = cycButtonShape(),
                    onClick = onOpenQr,
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Surface,
                        contentColor = TextMain,
                        focusedContainerColor = Primary,
                        focusedContentColor = OnMintText,
                    ),
                ) {
                    Text("手机扫码登录", color = TextMain, fontSize = 16.sp)
                }
            }
        }

        if (username != null && !app.authExpired) {
            Text(
                "已保存登录凭据, token 到期自动续登(无需手动退出再登录)",
                fontSize = 14.sp,
                color = TextDim,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Text(
            "阿拉蕾TV v" + com.cycitv.BuildConfig.VERSION_NAME + "\n\n" +
                "操作说明\n" +
                "· OK 确认 / 播放暂停\n" +
                "· 左右键 快进快退 10 秒\n" +
                "· 菜单键 播放页呼出选集\n" +
                "· 数字键 播放页直达指定集\n" +
                "· 返回键 逐级返回, 长按回首页\n\n" +
                "数据源: 公开索引\n播放: Media3 (ExoPlayer)\n" +
                "需登录账号后正常播放(遥控器输入或扫码登录)\n仅供个人学习使用",
            fontSize = 18.sp,
            color = TextDim,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

/**
 * 电视端键盘登录页: 遥控器直接在电视上输入账号密码。
 * 登录成功后除了 token, 还会把账号密码经 Keystore 加密留档, 供 token 过期时静默续登。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvKeyboardLoginPage(onBack: () -> Unit, onLoggedIn: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pwdVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val userReq = remember { FocusRequester() }
    val pwdReq = remember { FocusRequester() }

    // 进入页面自动把焦点放到账号框, 并呼出键盘
    LaunchedEffect(Unit) {
        delay(400)
        runCatching { userReq.requestFocus() }
    }

    fun submit() {
        if (busy) return
        val u = username.trim()
        val p = password
        if (u.isEmpty() || p.isEmpty()) {
            err = "请先填写账号和密码"
            return
        }
        kb?.hide()
        busy = true
        err = null
        scope.launch {
            try {
                val token = app.api.login(u, p)
                // 1) 存 token 2) 加密留档凭据(之后 token 过期自动续登)
                AuthStore.save(app, token, u)
                AuthStore.saveCredentials(app, u, p)
                app.api.token = token
                app.authUsername = u
                app.authExpired = false
                busy = false
                onLoggedIn(u)
            } catch (e: Exception) {
                busy = false
                err = e.message ?: "登录失败"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 36.dp, top = 20.dp, end = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("输入账号登录", fontSize = 28.sp, color = TextMain)
            Text(
                "按 返回 退出本页",
                fontSize = 15.sp,
                color = TextDim,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
        Text(
            "遥控器上下键切换输入框, OK 呼出键盘; 输完按返回键收起键盘, 再选「登录」",
            fontSize = 15.sp,
            color = TextDim,
            modifier = Modifier.padding(top = 10.dp)
        )
        Spacer(Modifier.height(30.dp))

        TvLoginField(
            value = username,
            onValueChange = { username = it; err = null },
            label = "账号",
            req = userReq,
            kb = kb,
            imeAction = ImeAction.Next,
            onIme = {
                runCatching { pwdReq.requestFocus() }
                kb?.show()
            },
            visual = VisualTransformation.None,
        )
        Spacer(Modifier.height(20.dp))
        TvLoginField(
            value = password,
            onValueChange = { password = it; err = null },
            label = "密码",
            req = pwdReq,
            kb = kb,
            imeAction = ImeAction.Done,
            onIme = { submit() },
            visual = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
        )

        Row(
            Modifier.padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.tv.material3.Button(
                shape = cycButtonShape(),
                onClick = { submit() },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = Primary,
                    contentColor = OnMintText,
                    focusedContainerColor = Primary,
                    focusedContentColor = OnMintText,
                ),
            ) {
                Text(if (busy) "登录中…" else "登录并保存", color = OnMintText, fontSize = 16.sp)
            }
            androidx.tv.material3.Button(
                shape = cycButtonShape(),
                onClick = { pwdVisible = !pwdVisible },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = Surface,
                    contentColor = TextMain,
                    focusedContainerColor = Primary,
                    focusedContentColor = OnMintText,
                ),
            ) {
                Text(if (pwdVisible) "隐藏密码" else "显示密码", color = TextMain, fontSize = 16.sp)
            }
        }

        Text(
            when {
                busy -> "正在登录, 请稍候…"
                err != null -> err ?: ""
                else -> "登录成功后账号密码会加密保存在本机, 之后 token 到期会自动续登"
            },
            fontSize = 16.sp,
            color = when {
                busy -> TextDim
                err != null -> Color(0xFFFF8080)
                else -> TextDim
            },
            modifier = Modifier.padding(top = 22.dp)
        )
        Text(
            "没有账号? 可在「手机扫码登录」页注册(需邮箱验证码)",
            fontSize = 14.sp,
            color = TextDim,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/** 电视端输入框: 聚焦即呼出软键盘, 按 OK 也可重新呼出; 回车按 imeAction 流转 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    req: FocusRequester,
    kb: SoftwareKeyboardController?,
    imeAction: ImeAction,
    onIme: () -> Unit,
    visual: VisualTransformation,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label, fontSize = 16.sp) },
        textStyle = TextStyle(fontSize = 22.sp, color = TextMain),
        visualTransformation = visual,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onIme() },
            onDone = { onIme() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextMain,
            unfocusedTextColor = TextMain,
            focusedContainerColor = Surface,
            unfocusedContainerColor = Surface,
            focusedBorderColor = Primary,
            unfocusedBorderColor = TextDim,
            focusedLabelColor = Primary,
            unfocusedLabelColor = TextDim,
            cursorColor = Primary,
        ),
        modifier = Modifier
            .width(560.dp)
            .focusRequester(req)
            .onFocusChanged { st -> if (st.isFocused) kb?.show() }
            .onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyUp && ev.key == Key.DirectionCenter) {
                    kb?.show()
                    true
                } else {
                    false
                }
            },
    )
}

/** 扫码登录子页:电视显示二维码,手机扫码注册/登录,验证码/token 全程经局域网 WS */
@Composable
private fun LoginSubPage(onBack: () -> Unit, onLoggedIn: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as App
    val url = remember { PhoneInputServer.loginUrl() }
    var status by remember { mutableStateOf("等待手机扫码…") }
    var statusOk by remember { mutableStateOf(false) }

    // 进入登录页启动扫码服务器;离开停止(搜索页也会启动,双保险互斥由 server 自己处理)
    DisposableEffect(Unit) {
        PhoneInputServer.start()
        onDispose { PhoneInputServer.stop() }
    }

    // 登录结果流:成功 → 保存 token + 更新登录态
    LaunchedEffect(Unit) {
        PhoneInputServer.loginResult.collect { r ->
            if (r.ok && r.token.isNotBlank()) {
                app.scope.launch {
                    AuthStore.save(app, r.token, r.username)
                    app.api.token = r.token
                    app.authUsername = r.username
                    status = "登录成功, 欢迎 " + r.username
                    statusOk = true
                }
                onLoggedIn(r.username)
            } else {
                status = r.msg.ifBlank { "登录失败" }
                statusOk = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 36.dp, top = 20.dp, end = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("扫码登录", fontSize = 28.sp, color = TextMain)
            Text(
                "按 返回 退出登录页",
                fontSize = 15.sp,
                color = TextDim,
                modifier = Modifier.padding(start = 20.dp)
            )
        }
        if (url == null) {
            Text(
                "未检测到局域网地址，请确认电视已连接网络",
                color = Color(0xFFFF8080), fontSize = 18.sp,
                modifier = Modifier.padding(top = 40.dp)
            )
            return@Column
        }
        Row(
            Modifier.padding(top = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 二维码
            Box(
                Modifier
                    .clip(CycShape.chip)
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                QrCode(url, size = 240.dp)
            }
            Column(
                Modifier.padding(start = 40.dp).width(500.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("1. 手机扫码打开登录页", color = TextMain, fontSize = 18.sp)
                Text("2. 已有账号直接登录；没有账号可注册（邮箱收验证码）", color = TextDim, fontSize = 16.sp)
                Text("3. 登录成功后本页自动关闭", color = TextDim, fontSize = 16.sp)
                Text(
                    status,
                    color = if (statusOk) Color(0xFF7EE787) else TextDim,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
