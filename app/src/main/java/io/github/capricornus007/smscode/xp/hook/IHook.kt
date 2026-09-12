package io.github.capricornus007.smscode.xp.hook

import io.github.capricornus007.smscode.xp.hookapi.LoadParam
import io.github.capricornus007.smscode.xp.hookapi.ZygoteParam

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: ZygoteParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: LoadParam)
}
