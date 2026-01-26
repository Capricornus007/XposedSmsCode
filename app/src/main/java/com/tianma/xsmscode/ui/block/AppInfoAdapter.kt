package com.tianma.xsmscode.ui.block

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tianma8023.xposed.smscode.databinding.ItemAppInfoBinding
import com.tianma.xsmscode.common.adapter.ItemCallback
import com.tianma.xsmscode.data.db.entity.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppInfoAdapter(private val mContext: Context) :
    ListAdapter<AppInfo, AppInfoAdapter.VH>(DIFF_CALLBACK) {

    private val mPackageManager: PackageManager = mContext.packageManager
    private var mItemCallback: ItemCallback<AppInfo>? = null
    private val mScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemAppInfoBinding.inflate(LayoutInflater.from(mContext), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val data = getItemAt(position)
        holder.bindData(data, position)
        holder.bindListener(data, position)
    }

    fun setItemCallback(callback: ItemCallback<AppInfo>) {
        mItemCallback = callback
    }

    inner class VH(private val binding: ItemAppInfoBinding) : RecyclerView.ViewHolder(binding.root) {

        private var loadIconJob: Job? = null

        fun bindData(data: AppInfo, position: Int) {
            loadIconJob?.cancel()
            binding.appIconView.setImageDrawable(null) // Reset or set placeholder
            
            loadIconJob = mScope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    try {
                        mPackageManager.getApplicationIcon(data.packageName)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (drawable != null) {
                    binding.appIconView.setImageDrawable(drawable)
                }
            }

            binding.appLabelView.text = data.label
            binding.pkgNameView.text = data.packageName
            binding.blockedCheckbox.isChecked = data.blocked
        }

        fun bindListener(data: AppInfo, position: Int) {
            mItemCallback?.let { callback ->
                itemView.setOnClickListener { callback.onItemClicked(itemView, data, position) }
                itemView.setOnLongClickListener { 
                    callback.onItemLongClicked(itemView, data, position)
                    true
                }
                binding.blockedCheckbox.setOnClickListener { callback.onItemClicked(itemView, data, position) }
            }
        }
    }

    fun getItemAt(position: Int): AppInfo {
        return getItem(position)
    }

    fun setItemList(appInfoList: List<AppInfo>) {
        submitList(appInfoList.toList())
    }

    fun setItemSelected(position: Int) {
        val updated = currentList.toMutableList()
        if (position < 0 || position >= updated.size) return
        val item = updated[position]
        updated[position] = item.copy(blocked = item.blocked)
        submitList(updated)
    }

    fun removeItemAt(position: Int) {
        val updated = currentList.toMutableList()
        if (position < 0 || position >= updated.size) return
        updated.removeAt(position)
        submitList(updated)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
