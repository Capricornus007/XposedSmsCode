package com.tianma.xsmscode.ui.rule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.eventbus.Event
import com.tianma.xsmscode.data.eventbus.XEventBus
import com.tianma.xsmscode.ui.app.base.BaseActivity
import com.tianma.xsmscode.ui.rule.edit.RuleEditFragment
import com.tianma.xsmscode.ui.rule.list.RuleListFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.setContent
import kotlinx.coroutines.launch

/**
 * User custom smscode codeRule list
 */
class CodeRulesActivity : BaseActivity() {

    private var mFragmentManager: FragmentManager? = null
    private var containerId: Int = View.generateViewId()
    private lateinit var titleState: MutableState<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            titleState = remember { mutableStateOf(getString(R.string.rule_list)) }
            CodeRulesContent(titleState)
        }
        handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                XEventBus.observe<Event.StartRuleEditEvent>().collect { event ->
                    val ruleEditFragment = RuleEditFragment.newInstance(event.type, event.codeRule)
                    mFragmentManager?.beginTransaction()
                        ?.replace(containerId, ruleEditFragment, TAG_RULE_EDIT)
                        ?.addToBackStack(TAG_RULE_EDIT)
                        ?.commit()
                    if (event.type == RuleEditFragment.EDIT_TYPE_CREATE) {
                        titleState.value = getString(R.string.create_rule)
                    } else {
                        titleState.value = getString(R.string.edit_rule)
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        var ruleListFragment: RuleListFragment? = null

        if (Intent.ACTION_VIEW == action) {
            val uri = intent.data
            if (uri != null) {
                // Import rules by back file URI
                ruleListFragment = RuleListFragment.newInstance(uri)
            }
        }

        if (ruleListFragment == null) {
            ruleListFragment = RuleListFragment.newInstance()
        }

        mFragmentManager = supportFragmentManager
        mFragmentManager?.addOnBackStackChangedListener {
            val count = mFragmentManager?.backStackEntryCount ?: 0
            if (count > 0) {
                 // Nothing to do for now, title is set when adding to backstack
            } else {
                titleState.value = getString(R.string.rule_list)
            }
        }
        mFragmentManager?.beginTransaction()
            ?.replace(containerId, ruleListFragment, TAG_RULE_LIST)
            ?.commit()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun CodeRulesContent(title: MutableState<String>) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title.value) },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = containerId
                    }
                }
            )
        }
    }

    companion object {
        private const val TAG_RULE_EDIT = "tag_rule_edit"
        private const val TAG_RULE_LIST = "tag_rule_list"

        @JvmStatic
        fun startToMe(context: Context) {
            val intent = Intent(context, CodeRulesActivity::class.java)
            context.startActivity(intent)
        }
    }
}
