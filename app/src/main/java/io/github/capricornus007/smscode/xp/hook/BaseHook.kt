package io.github.capricornus007.smscode.xp.hook

import io.github.capricornus007.smscode.xp.hookapi.LoadParam
import io.github.capricornus007.smscode.xp.hookapi.ZygoteParam

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
