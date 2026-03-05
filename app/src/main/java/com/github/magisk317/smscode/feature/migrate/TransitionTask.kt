package com.github.magisk317.smscode.feature.migrate

import android.content.Context
import com.github.magisk317.smscode.feature.migrate.db.DBTransition
import java.util.ArrayList

/**
 * Task to execute data migration transitions
 */
class TransitionTask(context: Context) {
    private val mTransitionList: MutableList<ITransition> = ArrayList()

    init {
        init(context)
    }

    private fun init(context: Context) {
        mTransitionList.add(DBTransition(context))
    }

    suspend fun run() {
        for (transition in mTransitionList) {
            if (transition.shouldTransit()) {
                transition.doTransition()
            }
        }
    }
}
