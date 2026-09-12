package io.github.capricornus007.smscode.xp

import android.os.Bundle
import android.util.Log
import io.github.capricornus007.smscode.BuildConfig
import io.github.capricornus007.smscode.common.utils.XLog
import io.github.capricornus007.smscode.xp.hook.BaseHook
import io.github.capricornus007.smscode.xp.hook.code.SmsHandlerHook
import io.github.capricornus007.smscode.xp.hook.google.GoogleMessagesHook
import io.github.capricornus007.smscode.xp.hook.me.ModuleUtilsHook
import io.github.capricornus007.smscode.xp.hook.permission.PermissionGranterHook
import io.github.capricornus007.smscode.xp.hook.system.SystemInputInjectorHook
import io.github.capricornus007.smscode.xp.hook.telephony.SmsProviderHook
import io.github.capricornus007.smscode.xp.hookapi.HookEnv
import io.github.capricornus007.smscode.xp.hookapi.LibXposedHookApi
import io.github.capricornus007.smscode.xp.hookapi.LoadParam
import io.github.capricornus007.smscode.xp.hookapi.ZygoteParam
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

class LibXposedEntry : XposedModule() {
    companion object {
        private const val TAG = "XSmsCode"
        private const val STATE_PROCESS_NAME = "processName"
        private const val STATE_PACKAGES = "packages"
    }

    private val hookList: List<BaseHook> = listOf(
        SmsHandlerHook(),
        GoogleMessagesHook(),
        ModuleUtilsHook(),
        PermissionGranterHook(),
        SystemInputInjectorHook(),
        SmsProviderHook(),
    )

    private var processName: String = "unknown"
    private var moduleActive = false
    private val loadedPackages = ConcurrentHashMap<String, WeakReference<ClassLoader>>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        if (apiVersion < XposedInterface.API_102) {
            log(Log.WARN, TAG, "LibXposed API ${apiVersion} is too old; API 102 is required", null)
            return
        }
        processName = if (param.isSystemServer) "android" else param.processName
        moduleActive = true
        HookEnv.init(LibXposedHookApi(this))
        installInitHooks()
        try {
            XLog.setLogLevel(BuildConfig.LOG_LEVEL)
        } catch (t: Throwable) {
            XLog.e("", t)
        }
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        if (!moduleActive) return
        loadedPackages["android"] = WeakReference(param.classLoader)
        val loadParam = LoadParam("android", processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!moduleActive) return
        loadedPackages[param.packageName] = WeakReference(param.classLoader)
        val loadParam = LoadParam(param.packageName, processName, param.classLoader)
        dispatchLoad(loadParam)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (!moduleActive) return false
        val state = Bundle().apply {
            putString(STATE_PROCESS_NAME, processName)
            putStringArrayList(STATE_PACKAGES, ArrayList(loadedPackages.keys.sorted()))
        }
        moduleActive = false
        param.setSavedInstanceState(state)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        param.oldHookHandles.forEach { handle ->
            runCatching { handle.unhook() }
                .onFailure { log(Log.WARN, TAG, "Failed to remove an old hook", it) }
        }

        val state = param.savedInstanceState as? Bundle
        processName = state?.getString(STATE_PROCESS_NAME)
            ?: if (param.isSystemServer) "android" else param.processName
        loadedPackages.clear()
        HookEnv.init(LibXposedHookApi(this))
        moduleActive = true
        installInitHooks()

        val packages = state?.getStringArrayList(STATE_PACKAGES).orEmpty()
        for (packageName in packages) {
            resolveClassLoader(packageName)?.let { classLoader ->
                loadedPackages[packageName] = WeakReference(classLoader)
                dispatchLoad(LoadParam(packageName, processName, classLoader))
            }
        }
    }

    private fun installInitHooks() {
        for (hook in hookList) {
            if (hook.hookInitZygote()) {
                runCatching { hook.initZygote(ZygoteParam()) }
                    .onFailure { XLog.e("Failed to initialize ${hook.javaClass.name}", it) }
            }
        }
    }

    private fun dispatchLoad(loadParam: LoadParam) {
        XLog.d("LibXposedEntry: Loaded package: ${loadParam.packageName} process: ${loadParam.processName}")
        if ("android" == loadParam.packageName || "system" == loadParam.packageName) {
            XLog.w(
                "LibXposedEntry: Android/system package loaded: pkg=%s process=%s",
                loadParam.packageName,
                loadParam.processName,
            )
        }
        for (hook in hookList) {
            if (hook.hookOnLoadPackage()) {
                hook.onLoadPackage(loadParam)
            }
        }
    }

    private fun resolveClassLoader(packageName: String): ClassLoader? {
        loadedPackages[packageName]?.get()?.let { return it }
        if (packageName == "android" || packageName == "system") {
            return Thread.currentThread().contextClassLoader
        }

        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val current = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
                ?: return@runCatching null
            val fields = listOf("mPackages", "mResourcePackages")
            for (fieldName in fields) {
                val field = activityThreadClass.getDeclaredField(fieldName).apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val packages = field.get(current) as? Map<Any?, Any?> ?: continue
                val entry = packages[packageName] ?: continue
                val reference = entry as? WeakReference<*> ?: continue
                val loadedPackage = reference.get() ?: continue
                val classLoader = loadedPackage.javaClass.getMethod("getClassLoader").invoke(loadedPackage)
                if (classLoader is ClassLoader) return@runCatching classLoader
            }
            Thread.currentThread().contextClassLoader
        }.getOrNull()
    }
}
