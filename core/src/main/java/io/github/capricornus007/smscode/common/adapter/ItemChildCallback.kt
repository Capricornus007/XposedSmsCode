package io.github.capricornus007.smscode.common.adapter

import android.view.View

fun interface ItemChildCallback<E> {
    fun onItemChildClicked(childView: View, item: E, position: Int)
}
