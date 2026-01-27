package com.tianma.xsmscode.ui.rule.edit

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.tianma8023.xposed.smscode.R
import com.tianma.xsmscode.data.db.entity.SmsCodeRule
import com.tianma.xsmscode.common.constant.Const
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    ruleEditType: Int,
    initialRule: SmsCodeRule? = null,
    initialRuleId: Long? = null,
    onBack: () -> Unit,
    viewModel: RuleEditViewModel = koinViewModel()
) {
    val context = LocalContext.current
    
    // Initialize ViewModel
    LaunchedEffect(Unit) {
        val args = Bundle().apply {
            putInt(Const.KEY_RULE_EDIT_TYPE, ruleEditType)
            if (initialRuleId != null) {
                putLong(Const.KEY_RULE_ID, initialRuleId)
            } else {
                putParcelable(Const.KEY_CODE_RULE, initialRule)
            }
        }
        viewModel.handleArguments(args)
    }

    val codeRule by viewModel.codeRuleLiveData.observeAsState(SmsCodeRule())
    
    var validationErrorState by remember { mutableStateOf<RuleEditViewModel.ValidationResult?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel.eventsFlow, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.eventsFlow.collect { event ->
                when (event) {
                    is RuleEditEvent.HideSoftInput -> keyboardController?.hide()
                    is RuleEditEvent.ValidationError -> validationErrorState = event.result
                    is RuleEditEvent.CodeRuleSaved -> {
                        if (event.success) {
                            onBack()
                        } else {
                            Toast.makeText(context, R.string.rule_duplicated_prompt, Toast.LENGTH_LONG).show()
                        }
                    }
                    is RuleEditEvent.TemplateSaved -> {
                        val msg = if (event.success) R.string.save_template_succeed else R.string.save_template_failed
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Local state for inputs to allow editing before saving
    var company by remember(codeRule) { mutableStateOf(codeRule.company ?: "") }
    var keyword by remember(codeRule) { mutableStateOf(codeRule.codeKeyword) }
    var regex by remember(codeRule) { mutableStateOf(codeRule.codeRegex) }
    
    // Quick Choose Dialog State
    var showQuickChoose by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    val title = if (ruleEditType == Const.EDIT_TYPE_CREATE) 
                        stringResource(R.string.create_rule) 
                    else 
                        stringResource(R.string.edit_rule)
                    Text(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val ruleToSave = SmsCodeRule(company, keyword, regex).apply { id = codeRule.id }
                viewModel.saveIfValid(ruleToSave)
            }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.save))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = company,
                onValueChange = { company = it },
                label = { Text(stringResource(R.string.rule_company_hint)) },
                modifier = Modifier.fillMaxWidth(),
                isError = validationErrorState?.companyValid == false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text(stringResource(R.string.rule_keyword_hint)) },
                modifier = Modifier.fillMaxWidth(),
                isError = validationErrorState?.keywordValid == false,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = regex,
                    onValueChange = { regex = it },
                    label = { Text(stringResource(R.string.rule_code_regex_hint)) },
                    modifier = Modifier.weight(1f),
                    isError = validationErrorState?.codeRegexValid == false,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                         val ruleToSave = SmsCodeRule(company, keyword, regex).apply { id = codeRule.id }
                         viewModel.saveIfValid(ruleToSave)
                    })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { showQuickChoose = true }) {
                    Text(stringResource(R.string.quick_choose))
                }
            }
        }
    }

    if (showQuickChoose) {
        QuickChooseDialog(
            onDismiss = { showQuickChoose = false },
            onConfirm = { generatedRegex ->
                regex = generatedRegex
                showQuickChoose = false
            }
        )
    }
}

@Composable
fun QuickChooseDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val codeTypes = stringArrayResource(R.array.sms_code_type_list)
    var selectedTypeIndex by remember { mutableStateOf(0) }
    var codeLength by remember { mutableStateOf("") }
    var lengthError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_choose)) },
        text = {
            Column {
                 // Spinner replacement
                 // For simplicity, using a list of RadioButtons or a simpler selector
                 // In Compose, DropdownMenu or ExposedDropdownMenu is standard.
                 // Let's use a simple Text + Dropdown for now.
                 var expanded by remember { mutableStateOf(false) }
                 Box {
                     OutlinedButton(onClick = { expanded = true }) {
                         Text(codeTypes[selectedTypeIndex])
                     }
                     DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                         codeTypes.forEachIndexed { index, type ->
                             DropdownMenuItem(
                                 text = { Text(type) },
                                 onClick = {
                                     selectedTypeIndex = index
                                     expanded = false
                                 }
                             )
                         }
                     }
                 }
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 OutlinedTextField(
                     value = codeLength,
                     onValueChange = { 
                         codeLength = it
                         lengthError = false 
                     },
                     label = { Text("Length") }, // Need a string resource
                     isError = lengthError,
                     keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                 )
                 if (lengthError) {
                     Text(stringResource(R.string.code_length_empty_prompt), color = MaterialTheme.colorScheme.error)
                 }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (codeLength.isEmpty()) {
                    lengthError = true
                    return@TextButton
                }
                val codeType = codeTypes[selectedTypeIndex]
                val format = "(?<!%s)%s{%s}(?!%s)"
                val codeRegex = String.format(format, codeType, codeType, codeLength, codeType)
                onConfirm(codeRegex)
            }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
