package com.github.magisk317.smscode.xp.hookapi

object HookEnv {
    @Volatile
    private var apiRef: HookApi? = null

    fun init(api: HookApi) {
        apiRef = api
    }

    val api: HookApi
        get() = apiRef ?: NoopHookApi

    private object NoopHookApi : HookApi {
        override fun hookMethod(member: java.lang.reflect.Member, callback: MethodHook): HookHandle? = null

        override fun hookAllConstructors(clazz: Class<*>, callback: MethodHook): Set<HookHandle> = emptySet()

        override fun hookAllMethods(clazz: Class<*>, methodName: String, callback: MethodHook): Set<HookHandle> = emptySet()

        override fun log(priority: Int, tag: String?, msg: String, tr: Throwable?) {
        }

        override fun getApiVersion(): Int? = null

        override fun getFrameworkName(): String? = null

        override fun getFrameworkVersionCode(): Long? = null

        override fun getXposedBridgeVersion(): Int? = null
    }
}
