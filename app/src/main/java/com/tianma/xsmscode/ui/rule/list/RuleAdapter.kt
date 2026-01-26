package com.tianma.xsmscode.ui.rule.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.adapter.ItemCallback
import com.tianma.xsmscode.data.db.entity.SmsCodeRule

class RuleAdapter : ListAdapter<SmsCodeRule, RuleAdapter.VH>(DIFF_CALLBACK) {

    private var mCallback: ItemCallback<SmsCodeRule>? = null

    fun setItemCallback(itemCallback: ItemCallback<SmsCodeRule>?) {
        mCallback = itemCallback
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return VH(itemView)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.bindData(item)
        holder.bindListener(item, position)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mCompanyView: TextView = itemView.findViewById(R.id.rule_company_text_view)
        private val mKeywordView: TextView = itemView.findViewById(R.id.rule_keyword_text_view)
        private val mRegexView: TextView = itemView.findViewById(R.id.rule_regex_text_view)

        fun bindData(item: SmsCodeRule) {
            mCompanyView.text = item.company
            mKeywordView.text = item.codeKeyword
            mRegexView.text = item.codeRegex
        }

        fun bindListener(item: SmsCodeRule, position: Int) {
            mCallback?.let { callback ->
                itemView.setOnClickListener { callback.onItemClicked(itemView, item, position) }
                itemView.setOnLongClickListener { callback.onItemLongClicked(itemView, item, position) }
                itemView.setOnCreateContextMenuListener { menu, v, menuInfo ->
                    callback.onCreateItemContextMenu(menu, v, menuInfo, item, position)
                }
            }
        }
    }

    fun addRule(newRule: SmsCodeRule) {
        if (currentList.contains(newRule)) return
        val updated = currentList.toMutableList()
        updated.add(newRule)
        submitList(updated)
    }

    fun addRule(ruleList: List<SmsCodeRule>) {
        if (ruleList.isEmpty()) return
        val updated = currentList.toMutableList()
        updated.addAll(ruleList.filterNot { updated.contains(it) })
        submitList(updated)
    }

    fun addRule(position: Int, newRule: SmsCodeRule) {
        if (currentList.contains(newRule)) return
        val updated = currentList.toMutableList()
        val safePosition = position.coerceIn(0, updated.size)
        updated.add(safePosition, newRule)
        submitList(updated)
    }

    fun updateAt(position: Int, updatedRule: SmsCodeRule) {
        val updated = currentList.toMutableList()
        if (position < 0 || position >= updated.size) return
        updated[position] = updatedRule
        submitList(updated)
    }

    fun getItemAt(position: Int): SmsCodeRule? {
        return if (position < 0 || position >= itemCount) null else getItem(position)
    }

    fun removeItemAt(position: Int) {
        val updated = currentList.toMutableList()
        if (position < 0 || position >= updated.size) return
        updated.removeAt(position)
        submitList(updated)
    }

    fun setRules(ruleList: List<SmsCodeRule>?) {
        submitList(ruleList?.toList().orEmpty())
    }

    fun getRuleList(): List<SmsCodeRule> = currentList

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SmsCodeRule>() {
            override fun areItemsTheSame(oldItem: SmsCodeRule, newItem: SmsCodeRule): Boolean {
                val oldId = oldItem.id
                val newId = newItem.id
                if (oldId != null && newId != null) {
                    return oldId == newId
                }
                return oldItem.company == newItem.company &&
                    oldItem.codeKeyword == newItem.codeKeyword &&
                    oldItem.codeRegex == newItem.codeRegex
            }

            override fun areContentsTheSame(oldItem: SmsCodeRule, newItem: SmsCodeRule): Boolean {
                return oldItem == newItem
            }
        }
    }
}
