package com.tianma.xsmscode.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.text.HtmlCompat
import com.github.tianma8023.xposed.smscode.BuildConfig
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.common.constant.Const
import com.tianma.xsmscode.common.utils.PackageUtils
import com.tianma.xsmscode.common.utils.SPUtils
import com.tianma.xsmscode.common.utils.Utils

class AboutComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AboutContent()
                }
            }
        }
    }

    private fun showPrivacyPolicyDialog() {
        val text = HtmlCompat.fromHtml(
            getString(R.string.privacy_dialog_content),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.privacy_dialog_title)
            .setMessage(text)
            .setPositiveButton(R.string.privacy_dialog_confirm) { _, _ ->
                SPUtils.setPrivacyPolicyAccepted(this, true)
            }
            .setNegativeButton(R.string.privacy_dialog_cancel) { _, _ ->
                SPUtils.setPrivacyPolicyAccepted(this, false)
            }
            .show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AboutContent() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.pref_about_title)) },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors()
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.pref_version_title)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                id = R.string.pref_version_summary,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE
                            )
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.pref_source_code_title)) },
                    supportingContent = { Text(stringResource(id = R.string.pref_source_code_summary)) },
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clickable { Utils.showWebPage(this@AboutComposeActivity, Const.PROJECT_SOURCE_CODE_URL) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.pref_join_qq_group_title)) },
                    supportingContent = { Text(stringResource(id = R.string.pref_join_qq_group_summary, Const.QQ_GROUP_URL)) },
                    modifier = Modifier.clickable { PackageUtils.joinQQGroup(this@AboutComposeActivity) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.pref_privacy_policy_title)) },
                    modifier = Modifier.clickable { showPrivacyPolicyDialog() }
                )
            }
        }
    }
}
