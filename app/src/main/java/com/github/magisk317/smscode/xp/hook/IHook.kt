package com.github.magisk317.smscode.xp.hook

import com.github.magisk317.smscode.xp.hookapi.LoadParam
import com.github.magisk317.smscode.xp.hookapi.ZygoteParam

interface IHook {

    @Throws(Throwable::class)
    fun initZygote(startupParam: ZygoteParam)

    @Throws(Throwable::class)
    fun onLoadPackage(lpparam: LoadParam)
}
