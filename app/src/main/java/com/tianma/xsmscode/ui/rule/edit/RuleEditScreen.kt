package com.tianma.xsmscode.ui.rule.edit

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
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

    val snackbarHostState = remember { SnackbarHostState() }

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
                            snackbarHostState.showSnackbar(context.getString(R.string.rule_duplicated_prompt))
                        }
                    }
                    is RuleEditEvent.TemplateSaved -> {
                        val msg = if (event.success) R.string.save_template_succeed else R.string.save_template_failed
                        snackbarHostState.showSnackbar(context.getString(msg))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                val ruleToSave = SmsCodeRule(company, keyword, regex, id = codeRule.id)
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
                placeholder = { Text("eg. 验证码") },
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
                    prefix = { Text("RE: ", color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.weight(1f),
                    isError = validationErrorState?.codeRegexValid == false,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                         val ruleToSave = SmsCodeRule(company, keyword, regex, id = codeRule.id)
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

    QuickChooseDialog(
        onDismiss = { showQuickChoose = false },
        onConfirm = { generatedRegex ->
            regex = generatedRegex
            showQuickChoose = false
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickChooseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val codeTypes = stringArrayResource(R.array.sms_code_type_list)
    var selectedTypeIndex by remember { mutableIntStateOf(0) }
    var codeLength by remember { mutableStateOf("") }
    var lengthError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.quick_choose)) },
        text = {
            Column {
                 var expanded by remember { mutableStateOf(false) }
                 ExposedDropdownMenuBox(
                     expanded = expanded,
                     onExpandedChange = { expanded = it }
                 ) {
                     OutlinedTextField(
                         value = codeTypes[selectedTypeIndex],
                         onValueChange = {},
                         readOnly = true,
                         label = { Text(stringResource(R.string.rule_code_type)) },
                         trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                         colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                         modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                     )
                     ExposedDropdownMenu(
                         expanded = expanded,
                         onDismissRequest = { expanded = false }
                     ) {
                         codeTypes.forEachIndexed { index, type ->
                             DropdownMenuItem(
                                 text = { Text(type) },
                                 onClick = {
                                     selectedTypeIndex = index
                                     expanded = false
                                 },
                                 contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
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
                     label = { Text(stringResource(R.string.rule_code_length)) },
                     isError = lengthError,
                     modifier = Modifier.fillMaxWidth(),
                     keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                 )
                 AnimatedVisibility(
                     visible = lengthError,
                     enter = expandVertically() + fadeIn(),
                     exit = shrinkVertically() + fadeOut()
                 ) {
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
