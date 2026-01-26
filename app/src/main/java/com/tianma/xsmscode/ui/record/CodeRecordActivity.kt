package com.tianma.xsmscode.ui.record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.ui.app.base.BaseActivity

/**
 * Sms Code Records
 */
class CodeRecordActivity : BaseActivity() {

    private val containerId = View.generateViewId()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CodeRecordContent() }

        supportFragmentManager
            .beginTransaction()
            .replace(containerId, CodeRecordFragment.newInstance())
            .commit()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun CodeRecordContent() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = getString(R.string.smscode_records)) },
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
        @JvmStatic
        fun startToMe(context: Context) {
            val intent = Intent(context, CodeRecordActivity::class.java)
            context.startActivity(intent)
        }
    }
}
