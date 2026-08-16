package com.langualens.app.ui.screens

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.langualens.app.R
import com.langualens.app.anki.AnkiBridge
import com.langualens.app.data.Repo
import com.langualens.app.data.SavedItem
import com.langualens.app.service.BubbleService
import com.langualens.app.service.ScreenReaderService
import com.langualens.app.srs.Grade
import com.langualens.app.srs.Sm2
import com.langualens.app.translate.Languages
import com.langualens.app.translate.Translate
import com.langualens.app.util.CsvExport
import com.langualens.app.util.Speaker
import kotlinx.coroutines.launch

@Composable
fun LanguaLensRoot(
    onOpenUrl: (String) -> Unit,
    onOpenText: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestAnki: () -> Unit,
    onInterfaceLanguageChanged: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_read)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_words)) }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_practice)) }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> ReadScreen(
                padding, onOpenUrl, onOpenText, onRequestOverlay, onRequestAccessibility
            )
            1 -> WordsScreen(padding)
            2 -> ReviewScreen(padding)
            else -> SettingsScreen(padding, onRequestAnki, onInterfaceLanguageChanged)
        }
    }
}

/* ---------------------------- language picker ---------------------------- */

@Composable
private fun LanguagePickerDialog(
    title: String,
    options: List<Languages.Lang>,
    selectedTag: String,
    searchable: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.english.contains(query, true) || it.native.contains(query, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (searchable) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(filtered, key = { it.tag.ifEmpty { "system" } }) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(lang.tag) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (lang.tag == selectedTag) "•  " else "    ",
                                fontSize = 15.sp
                            )
                            Text(lang.label, fontSize = 15.sp)
                        }
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

/* ------------------------------- read tab ------------------------------- */

@Composable
private fun ReadScreen(
    padding: PaddingValues,
    onOpenUrl: (String) -> Unit,
    onOpenText: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf(repo.prefs.sourceLanguage) }
    var target by remember { mutableStateOf(repo.prefs.targetLanguage) }
    var picking by remember { mutableStateOf(0) } // 0 none, 1 source, 2 target
    var url by remember { mutableStateOf(repo.prefs.lastUrl) }
    var text by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(source, target) {
        repo.setLanguages(source, target)
        modelReady = Translate.isReady()
    }

    if (picking != 0) {
        LanguagePickerDialog(
            title = stringResource(
                if (picking == 1) R.string.from_language else R.string.to_language
            ),
            options = Languages.ORDERED,
            selectedTag = if (picking == 1) source else target,
            searchable = true,
            onPick = { tag ->
                if (picking == 1) source = tag else target = tag
                picking = 0
            },
            onDismiss = { picking = 0 }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.app_name), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.read_subtitle),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.from_language), fontSize = 11.sp)
                    Text(Languages.nameOf(source), fontSize = 15.sp)
                }
            }
            IconButton(onClick = {
                val old = source
                source = target
                target = old
            }) {
                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.swap_languages))
            }
            OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.to_language), fontSize = 11.sp)
                    Text(Languages.nameOf(target), fontSize = 15.sp)
                }
            }
        }

        if (source == target) {
            Text(
                stringResource(R.string.same_language),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error
            )
        } else if (!modelReady) {
            Card {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.model_missing_title),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.model_missing_body), fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val error = Translate.prepare()
                                busy = false
                                modelReady = error == null
                                toast(
                                    context,
                                    error ?: context.getString(R.string.model_ready)
                                )
                            }
                        }
                    ) {
                        Text(
                            stringResource(
                                if (busy) R.string.working else R.string.download_model
                            )
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(stringResource(R.string.url_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (url.isNotBlank()) onOpenUrl(url.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.open_reader)) }

        Divider()

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.paste_label)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { if (text.isNotBlank()) onOpenText(text.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.read_this_text)) }

        Divider()

        Text(
            stringResource(R.string.translate_anywhere),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            stringResource(R.string.howto_body),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )

        val canOverlay = Settings.canDrawOverlays(context)
        val accessibilityOn = ScreenReaderService.isRunning()

        PermissionRow(
            label = stringResource(R.string.perm_overlay),
            granted = canOverlay,
            onFix = onRequestOverlay
        )
        PermissionRow(
            label = stringResource(R.string.perm_accessibility),
            granted = accessibilityOn,
            onFix = onRequestAccessibility
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = canOverlay,
                onClick = { BubbleService.start(context) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.bubble_on)) }
            OutlinedButton(
                onClick = { BubbleService.stop(context) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.bubble_off)) }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (granted) "✓  " else "•  ") + label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp
        )
        if (!granted) {
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.turn_on)) }
        }
    }
}

/* ------------------------------- words tab ------------------------------- */

@Composable
private fun WordsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()
    val all by repo.observeAll().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }

    val filtered = remember(all, query) {
        if (query.isBlank()) all
        else all.filter {
            it.sourceText.contains(query, true) || it.targetText.contains(query, true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_hint, all.size)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.nothing_saved),
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { item ->
                SavedRow(
                    item = item,
                    onSpeak = { Speaker.speak(context, item.sourceText, item.sourceLang) },
                    onDelete = { scope.launch { repo.delete(item) } }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun SavedRow(item: SavedItem, onSpeak: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.sourceText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                item.targetText.ifBlank { "—" },
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary
            )
            if (item.origin.isNotBlank()) {
                Text(
                    item.origin.take(60),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        IconButton(onClick = onSpeak) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.speak))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
        }
    }
}

/* ------------------------------ practice tab ------------------------------ */

@Composable
private fun ReviewScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val scope = rememberCoroutineScope()

    var queue by remember { mutableStateOf<List<SavedItem>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        queue = repo.due()
        loaded = true
    }

    val current = queue.getOrNull(index)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.practice_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)

        if (!loaded) {
            Text(stringResource(R.string.loading))
            return@Column
        }

        if (current == null) {
            Text(stringResource(R.string.nothing_due))
            OutlinedButton(onClick = {
                scope.launch { queue = repo.due(); index = 0; revealed = false }
            }) { Text(stringResource(R.string.reload)) }
            return@Column
        }

        Text(
            stringResource(R.string.card_progress, index + 1, queue.size),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.sourceText, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                if (revealed) {
                    Divider()
                    Text(
                        current.targetText.ifBlank { "—" },
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (current.context.isNotBlank() && current.context != current.sourceText) {
                        Text(
                            current.context,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { Speaker.speak(context, current.sourceText, current.sourceLang) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.speak)) }

        if (!revealed) {
            Button(
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.show_answer)) }
        } else {
            fun grade(g: Grade) {
                val updated = Sm2.apply(current, g)
                scope.launch { repo.update(updated) }
                revealed = false
                index += 1
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton(
                    stringResource(R.string.grade_again),
                    Sm2.previewLabel(current, Grade.AGAIN)
                ) { grade(Grade.AGAIN) }
                GradeButton(
                    stringResource(R.string.grade_hard),
                    Sm2.previewLabel(current, Grade.HARD)
                ) { grade(Grade.HARD) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton(
                    stringResource(R.string.grade_good),
                    Sm2.previewLabel(current, Grade.GOOD)
                ) { grade(Grade.GOOD) }
                GradeButton(
                    stringResource(R.string.grade_easy),
                    Sm2.previewLabel(current, Grade.EASY)
                ) { grade(Grade.EASY) }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, hint: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 14.sp)
            Text(hint, fontSize = 11.sp)
        }
    }
}

/* ------------------------------ settings tab ------------------------------ */

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    onRequestAnki: () -> Unit,
    onInterfaceLanguageChanged: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val prefs = repo.prefs
    val scope = rememberCoroutineScope()

    var paragraphMode by remember { mutableStateOf(prefs.readerMode == "paragraph") }
    var hideTranslation by remember { mutableStateOf(prefs.hideTranslation) }
    var autoTranslate by remember { mutableStateOf(prefs.autoTranslateOnLoad) }
    var autoAnki by remember { mutableStateOf(prefs.autoPushAnki) }
    var deck by remember { mutableStateOf(prefs.ankiDeck) }
    var model by remember { mutableStateOf(prefs.ankiModel) }
    var uiLanguage by remember { mutableStateOf(prefs.uiLanguage) }
    var pickingUi by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { downloaded = Translate.downloadedTags() }

    if (pickingUi) {
        LanguagePickerDialog(
            title = stringResource(R.string.interface_language),
            options = Languages.INTERFACE,
            selectedTag = uiLanguage,
            searchable = false,
            onPick = { tag ->
                uiLanguage = tag
                prefs.uiLanguage = tag
                pickingUi = false
                onInterfaceLanguageChanged()
            },
            onDismiss = { pickingUi = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.interface_language),
                modifier = Modifier.weight(1f),
                fontSize = 15.sp
            )
            OutlinedButton(onClick = { pickingUi = true }) {
                Text(Languages.interfaceName(uiLanguage))
            }
        }

        Divider()
        Text(
            stringResource(R.string.section_reading),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        SettingSwitch(stringResource(R.string.mode_paragraph), paragraphMode) {
            paragraphMode = it
            prefs.readerMode = if (it) "paragraph" else "sentence"
        }
        SettingSwitch(stringResource(R.string.hide_translation), hideTranslation) {
            hideTranslation = it; prefs.hideTranslation = it
        }
        SettingSwitch(stringResource(R.string.auto_translate), autoTranslate) {
            autoTranslate = it; prefs.autoTranslateOnLoad = it
        }

        Divider()
        Text(stringResource(R.string.section_anki), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

        Text(
            stringResource(
                if (AnkiBridge.isAvailable(context)) R.string.anki_found else R.string.anki_missing
            ),
            fontSize = 13.sp
        )

        OutlinedTextField(
            value = deck,
            onValueChange = { deck = it; prefs.ankiDeck = it },
            label = { Text(stringResource(R.string.anki_deck)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; prefs.ankiModel = it },
            label = { Text(stringResource(R.string.anki_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitch(stringResource(R.string.anki_auto_push), autoAnki) {
            autoAnki = it; prefs.autoPushAnki = it
            if (it) onRequestAnki()
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    onRequestAnki()
                    scope.launch { toast(context, repo.pushAllToAnki()) }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.anki_push)) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val items = repo.all()
                        if (items.isEmpty()) {
                            toast(context, context.getString(R.string.nothing_saved))
                        } else {
                            CsvExport.share(context, CsvExport.write(context, items))
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.export)) }
        }

        Divider()
        Text(
            stringResource(R.string.section_models),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Text(
            stringResource(
                R.string.downloaded_models,
                if (downloaded.isEmpty()) stringResource(R.string.none)
                else downloaded.sorted().joinToString(", ") { Languages.nameOf(it) }
            ),
            fontSize = 13.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val error = Translate.prepare()
                        toast(context, error ?: context.getString(R.string.model_ready))
                        downloaded = Translate.downloadedTags()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.download)) }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val ok = Translate.deleteModel(prefs.sourceLanguage)
                        toast(
                            context,
                            context.getString(
                                if (ok) R.string.model_deleted else R.string.action_failed
                            )
                        )
                        downloaded = Translate.downloadedTags()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.remove)) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
