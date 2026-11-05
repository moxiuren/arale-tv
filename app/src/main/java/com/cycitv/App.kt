package com.cycitv

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.cycitv.data.JwtExp
import com.cycitv.data.SecureCred
import com.cycitv.data.api.ApiException
import com.cycitv.data.api.CycaniApi
import com.cycitv.data.repository.HomeRepository
import com.cycitv.data.room.AppDatabase
import okio.Path.Companion.toOkioPath
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 用户认证信息(登录 token + 加密凭据),存 DataStore,不进仓库 */
private val Context.authDataStore by preferencesDataStore(name = "auth")

object AuthStore {
    val Token = stringPreferencesKey("token")
    val Username = stringPreferencesKey("username")

    /** 加密后的账号 / 密码(Keystore AES-GCM),token 过期时静默续登用 */
    val CredUser = stringPreferencesKey("cred_user")
    val CredPass = stringPreferencesKey("cred_pass")

    /** 显示模式: true=暗色(默认), false=白天 */
    val DarkTheme = booleanPreferencesKey("dark_theme")

    suspend fun loadToken(context: Context): Pair<String?, String?> {
        val p = context.authDataStore.data.first()
        return p[Token] to p[Username]
    }

    suspend fun save(context: Context, token: String, username: String) {
        context.authDataStore.edit { it[Token] = token; it[Username] = username }
    }

    /** 保存登录凭据(密文落盘,不存明文); 加密失败则放弃保存,不影响本次登录 */
    suspend fun saveCredentials(context: Context, username: String, password: String) {
        val eu = SecureCred.encrypt(username) ?: return
        val ep = SecureCred.encrypt(password) ?: return
        context.authDataStore.edit { it[CredUser] = eu; it[CredPass] = ep }
    }

    /** 读回明文凭据; 无凭据或解密失败(密钥失效)返回 null */
    suspend fun loadCredentials(context: Context): Pair<String, String>? {
        val p = context.authDataStore.data.first()
        val u = SecureCred.decrypt(p[CredUser]) ?: return null
        val pw = SecureCred.decrypt(p[CredPass]) ?: return null
        if (u.isBlank() || pw.isBlank()) return null
        return u to pw
    }

    /** 读显示模式; 默认暗色 */
    suspend fun loadDarkTheme(context: Context): Boolean =
        context.authDataStore.data.first()[DarkTheme] ?: true

    /** 存显示模式 */
    suspend fun saveDarkTheme(context: Context, dark: Boolean) {
        context.authDataStore.edit { it[DarkTheme] = dark }
    }

    /** 退出登录: 登录态与凭据一并清除,之后不再自动续登 */
    suspend fun clear(context: Context) {
        context.authDataStore.edit {
            it.remove(Token); it.remove(Username); it.remove(CredUser); it.remove(CredPass)
        }
    }
}

class App : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var api: CycaniApi
        private set
    val giri = com.cycitv.data.api.GirigiriApi()
    lateinit var home: HomeRepository
        private set
    lateinit var imageLoader: ImageLoader
        private set

    /** 本进程内"硬解会崩、需软解"的集数名单(退出进程后重新积累) */
    val softDecodeSections = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    /** Room DB 在后台线程构建,不阻塞冷启动;首次被使用时(播放页)通过 await 等待 */
    private val dbReady = kotlinx.coroutines.CompletableDeferred<AppDatabase>()
    suspend fun db(): AppDatabase = dbReady.await()

    /** 当前登录用户名(供 UI 显示);无登录为 null */
    @Volatile
    var authUsername: String? = null

    /** 静默续登失败(凭据已失效/被清除)时为 true,供设置页提示重新登录 */
    @Volatile
    var authExpired: Boolean = false

    /** 显示模式(明/暗): 由设置页切换, 持久化到 DataStore, MainActivity 订阅后驱动 CycTheme */
    private val _darkTheme = kotlinx.coroutines.flow.MutableStateFlow(true)
    val darkTheme: kotlinx.coroutines.flow.StateFlow<Boolean> = _darkTheme

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        scope.launch { AuthStore.saveDarkTheme(this@App, dark) }
    }

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        api = CycaniApi(client)

        // 图片专用 OkHttp: 放宽每主机并发, 避免首屏封面排队
        val imageClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().apply { maxRequestsPerHost = 12 })
            .build()

        // token 失效(HTTP 401 / 业务码 1002)时由 API 层回调: 用本地加密保存的凭据静默重登,
        // 免去用户"自己退出登录再登录"的手工操作
        api.relogin = {
            val cred = AuthStore.loadCredentials(this@App)
            if (cred == null) {
                authExpired = true   // 无凭据可用(未保存/被清除), 只能手动重登
                null
            } else {
                val (u, p) = cred
                val t = api.login(u, p)
                AuthStore.save(this@App, t, u)
                authUsername = u
                authExpired = false
                Log.i("CYC", "静默续登成功 user=" + u + " exp=" + JwtExp.of(t))
                t
            }
        }

        // 扫码登录:手机端提交的注册/登录操作,落到站方 API;成功返回 token
        com.cycitv.data.PhoneInputServer.authApi = { op, u, p, e, c ->
            when (op) {
                "login" -> api.login(u, p)
                // 注册成功后自动登录,把 token 直接返回给手机端
                "register" -> { api.register(u, p, e, c); api.login(u, p) }
                "sendCode" -> api.sendCode(e, c)
                else -> throw IllegalStateException("unknown op " + op)
            }
        }
        // 手机端扫码登录成功后,把凭据加密留档(供日后 token 过期自动续登)
        com.cycitv.data.PhoneInputServer.onCredentials = { u, p ->
            AuthStore.saveCredentials(this@App, u, p)
        }

        // 启动时恢复显示模式(明/暗)
        scope.launch {
            runCatching { _darkTheme.value = AuthStore.loadDarkTheme(this@App) }
        }
        // 启动时恢复登录态:从 DataStore 读 token 注入 api,播放才带认证
        scope.launch {
            val (tok, user) = AuthStore.loadToken(this@App)
            if (!tok.isNullOrBlank()) {
                api.token = tok
                authUsername = user
                // token 是 7 天有效的 JWT: 剩余不足 24h(或已过期/解析不出 exp)时开机即续期,
                // 避免"设置里显示已登录、实际播放时才发现掉线"的老问题
                val left = JwtExp.leftSeconds(tok)
                if (left == null || left < 86_400L) {
                    try {
                        api.relogin?.invoke()
                    } catch (e: ApiException) {
                        // 账号密码已失效(改密/封禁) → 标记失效, 设置页提示重新登录
                        Log.w("CYC", "启动续登被拒: " + e.message)
                        authExpired = true
                    } catch (e: Exception) {
                        // 网络等临时问题: 保留现有 token, 等真正请求时再兜底重试
                        Log.w("CYC", "启动续登暂不可用: " + e.message)
                    }
                }
            }
        }
        // Room build 较慢,放后台线程,避免冷启动卡主线程
        scope.launch {
            try {
                dbReady.complete(
                    Room.databaseBuilder(this@App, AppDatabase::class.java, "cycitv.db")
                        .fallbackToDestructiveMigration()
                        .build()
                )
            } catch (e: Throwable) {
                dbReady.completeExceptionally(e)
            }
        }
        home = HomeRepository(api, scope)
        imageLoader = ImageLoader.Builder(this)
            // 图片走独立 OkHttp: 默认每主机只允许 5 个并发, 首屏会"一条一条"地出图             // 提高到 12 并发后首屏明显更快(封面几乎都来自同一图床)             .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            // 内存缓存: 占可用内存 25%, 快速左右切换命中缓存则不重新解码
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(this@App, 0.25)
                    .build()
            }
            // 磁盘缓存: 256MB, 再次进入同一行不走网络
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            // 关闭 crossfade: 快速滚动时每张图淡入反而是卡顿来源
            .crossfade(false)
            .build()
    }
}
