package com.tianma.xsmscode.ui.record

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tianma8023.xposed.smscode.databinding.ItemCodeRecordBinding
import com.tianma.xsmscode.common.adapter.ItemCallback
import com.tianma.xsmscode.common.adapter.ItemChildCallback
import com.tianma.xsmscode.data.db.entity.SmsMsg
import java.text.SimpleDateFormat
import java.util.*

class CodeRecordAdapter(
    private val mContext: Context
) : ListAdapter<RecordItem, CodeRecordAdapter.VH>(DIFF_CALLBACK) {

    private val mFormat = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
    private var mItemCallback: ItemCallback<RecordItem>? = null
    private var mItemChildCallback: ItemChildCallback<RecordItem>? = null

    @IntDef(RECORD_MODE_NORMAL, RECORD_MODE_EDIT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class RecordMode

    private var mMode: Int = RECORD_MODE_NORMAL

    fun setItemCallback(itemCallback: ItemCallback<RecordItem>?) {
        mItemCallback = itemCallback
    }

    fun setItemChildCallback(itemChildCallback: ItemChildCallback<RecordItem>?) {
        mItemChildCallback = itemChildCallback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemCodeRecordBinding.inflate(LayoutInflater.from(mContext), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val data = getItemAt(position)
        holder.bindData(data, position)
        holder.bindListener(data, position)
    }

    inner class VH(private val binding: ItemCodeRecordBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bindData(data: RecordItem, position: Int) {
            val smsMsg = data.smsMsg
            val company = smsMsg.company
            if (!company.isNullOrBlank()) {
                binding.companyTextView.text = company
            } else {
                binding.companyTextView.text = smsMsg.sender
            }
            binding.smscodeTextView.text = smsMsg.smsCode
            binding.dateTextView.text = mFormat.format(Date(smsMsg.date))

            if (mMode == RECORD_MODE_NORMAL) {
                binding.checkbox.visibility = View.GONE
            } else {
                binding.checkbox.visibility = View.VISIBLE
                binding.checkbox.isChecked = data.isSelected
            }

            if (TextUtils.isEmpty(smsMsg.body)) {
                binding.recordDetailsView.visibility = View.GONE
            } else {
                binding.recordDetailsView.visibility = View.VISIBLE
            }
        }

        fun bindListener(data: RecordItem, position: Int) {
            mItemCallback?.let { callback ->
                itemView.setOnClickListener { callback.onItemClicked(itemView, data, position) }
                itemView.setOnLongClickListener { callback.onItemLongClicked(itemView, data, position) }
            }

            mItemChildCallback?.let { callback ->
                binding.recordDetailsView.setOnClickListener {
                    callback.onItemChildClicked(binding.recordDetailsView, data, position)
                }
                binding.checkbox.setOnClickListener {
                    callback.onItemChildClicked(binding.checkbox, data, position)
                }
            }
        }
    }

    fun getItemAt(position: Int): RecordItem = getItem(position)

    fun setItemSelected(position: Int, selected: Boolean) {
        val updated = currentList.toMutableList()
        if (position < 0 || position >= updated.size) return
        val item = updated[position]
        val newItem = RecordItem(item.smsMsg).apply { isSelected = selected }
        updated[position] = newItem
        submitList(updated)
    }

    fun isItemSelected(position: Int): Boolean = getItemAt(position).isSelected

    fun setAllSelected(selected: Boolean) {
        val updated = currentList.map { item ->
            RecordItem(item.smsMsg).apply { isSelected = selected }
        }
        submitList(updated)
    }

    fun isAllSelected(): Boolean {
        if (currentList.isEmpty()) return false
        return currentList.all { it.isSelected }
    }

    fun removeSelectedItems(): List<SmsMsg> {
        val recordsToRemove = currentList.filter { it.isSelected }
        val messagesToRemove = recordsToRemove.map { it.smsMsg }
        val updated = currentList.filterNot { it.isSelected }
        submitList(updated)
        return messagesToRemove
    }

    fun addItems(smsMsgList: List<SmsMsg>) {
        val updated = currentList.toMutableList()
        val existing = updated.map { it.smsMsg }.toSet()
        val itemsToAdd = smsMsgList.filterNot { existing.contains(it) }
            .map { RecordItem(it) }
        if (itemsToAdd.isEmpty()) return
        updated.addAll(itemsToAdd)
        updated.sortByDescending { it.smsMsg.date }
        submitList(updated)
    }

    fun setMode(@RecordMode mode: Int) {
        if (mMode != mode) {
            mMode = mode
            notifyDataSetChanged()
        }
    }

    @RecordMode
    fun getMode(): Int = mMode

    companion object {
        const val RECORD_MODE_NORMAL = 0
        const val RECORD_MODE_EDIT = 1
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecordItem>() {
            override fun areItemsTheSame(oldItem: RecordItem, newItem: RecordItem): Boolean {
                val oldId = oldItem.smsMsg.id
                val newId = newItem.smsMsg.id
                if (oldId != null && newId != null) {
                    return oldId == newId
                }
                return oldItem.smsMsg.date == newItem.smsMsg.date &&
                    oldItem.smsMsg.sender == newItem.smsMsg.sender &&
                    oldItem.smsMsg.body == newItem.smsMsg.body
            }

            override fun areContentsTheSame(oldItem: RecordItem, newItem: RecordItem): Boolean {
                return oldItem.smsMsg == newItem.smsMsg && oldItem.isSelected == newItem.isSelected
            }
        }
    }
}
