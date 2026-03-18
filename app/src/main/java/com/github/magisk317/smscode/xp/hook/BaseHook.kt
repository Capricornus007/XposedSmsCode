package com.github.magisk317.smscode.xp.hook

import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.ZygoteParam

open class BaseHook : IHook {

    @Throws(Throwable::class)
    override fun initZygote(startupParam: ZygoteParam) {
    }

    open fun hookInitZygote(): Boolean = false

    @Throws(Throwable::class)
    override fun onLoadPackage(lpparam: LoadParam) {
    }

    open fun hookOnLoadPackage(): Boolean = true
}
