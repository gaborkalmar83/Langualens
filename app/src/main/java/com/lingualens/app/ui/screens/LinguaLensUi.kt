package com.lingualens.app.ui.screens

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingualens.app.anki.AnkiBridge
import com.lingualens.app.data.Repo
import com.lingualens.app.data.SavedItem
import com.lingualens.app.service.BubbleService
import com.lingualens.app.service.ScreenReaderService
import com.lingualens.app.srs.Grade
import com.lingualens.app.srs.Sm2
import com.lingualens.app.translate.Nl2En
import com.lingualens.app.util.CsvExport
import com.lingualens.app.util.Speaker
import kotlinx.coroutines.launch

@Composable
fun LinguaLensRoot(
    onOpenUrl: (String) -> Unit,
    onOpenText: (String) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestAnki: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Lezen") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Woorden") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("Oefenen") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Instellingen") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> ReadScreen(padding, onOpenUrl, onOpenText, onRequestOverlay, onRequestAccessibility)
            1 -> WordsScreen(padding)
            2 -> ReviewScreen(padding)
            else -> SettingsScreen(padding, onRequestAnki)
        }
    }
}

/* ------------------------------- Lezen ------------------------------- */

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

    var url by remember { mutableStateOf(repo.prefs.lastUrl) }
    var text by remember { mutableStateOf("") }
    var modelReady by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { modelReady = Nl2En.isModelDownloaded() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("LinguaLens", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Nederlands lezen met Engels eronder.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        if (!modelReady) {
            Card(colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Offline model nog niet gedownload", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ongeveer 30 MB. Daarna werkt vertalen zonder internet.",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val error = Nl2En.prepare()
                                busy = false
                                modelReady = error == null
                                toast(context, error ?: "Model klaar")
                            }
                        }
                    ) { Text(if (busy) "Bezig…" else "Download model") }
                }
            }
        }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Link naar artikel") },
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
        ) { Text("Open in tweetalige lezer") }

        Divider()

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Of plak hier Nederlandse tekst") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { if (text.isNotBlank()) onOpenText(text.trim()) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lees deze tekst") }

        Divider()

        Text("Overal vertalen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            "1. Selecteer tekst in Chrome, Discord of WhatsApp en kies \"LinguaLens: vertaal\" in het menu.\n" +
                    "2. Of zet de zwevende knop aan en tik erop om het hele scherm te vertalen.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )

        val canOverlay = Settings.canDrawOverlays(context)
        val accessibilityOn = ScreenReaderService.isRunning()

        PermissionRow(
            label = "Tekenen over andere apps",
            granted = canOverlay,
            onFix = onRequestOverlay
        )
        PermissionRow(
            label = "Toegankelijkheid: LinguaLens screen reader",
            granted = accessibilityOn,
            onFix = onRequestAccessibility
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = canOverlay,
                onClick = { BubbleService.start(context) },
                modifier = Modifier.weight(1f)
            ) { Text("Knop aan") }
            OutlinedButton(
                onClick = { BubbleService.stop(context) },
                modifier = Modifier.weight(1f)
            ) { Text("Knop uit") }
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
            OutlinedButton(onClick = onFix) { Text("Zet aan") }
        }
    }
}

/* ------------------------------- Woorden ------------------------------- */

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
            it.dutch.contains(query, true) || it.english.contains(query, true)
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
            label = { Text("Zoek (${all.size} bewaard)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id }) { item ->
                SavedRow(
                    item = item,
                    onSpeak = { Speaker.speak(context, item.dutch) },
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
            Text(item.dutch, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                item.english.ifBlank { "—" },
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary
            )
            if (item.source.isNotBlank()) {
                Text(
                    item.source.take(60),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
        IconButton(onClick = onSpeak) {
            Icon(Icons.Default.Refresh, contentDescription = "Spreek uit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Verwijder")
        }
    }
}

/* ------------------------------- Oefenen ------------------------------- */

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
        Text(
            "Oefenen",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        if (!loaded) {
            Text("Laden…")
            return@Column
        }

        if (current == null) {
            Text("Niets te herhalen op dit moment.")
            OutlinedButton(onClick = {
                scope.launch { queue = repo.due(); index = 0; revealed = false }
            }) { Text("Opnieuw laden") }
            return@Column
        }

        Text(
            "${index + 1} / ${queue.size}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(current.dutch, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                if (revealed) {
                    Divider()
                    Text(
                        current.english.ifBlank { "—" },
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (current.context.isNotBlank() && current.context != current.dutch) {
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
            onClick = { Speaker.speak(context, current.dutch) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Spreek uit") }

        if (!revealed) {
            Button(
                onClick = { revealed = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Toon antwoord") }
        } else {
            fun grade(g: Grade) {
                val updated = Sm2.apply(current, g)
                scope.launch { repo.update(updated) }
                revealed = false
                index += 1
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton("Opnieuw", Sm2.previewLabel(current, Grade.AGAIN)) { grade(Grade.AGAIN) }
                GradeButton("Lastig", Sm2.previewLabel(current, Grade.HARD)) { grade(Grade.HARD) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton("Goed", Sm2.previewLabel(current, Grade.GOOD)) { grade(Grade.GOOD) }
                GradeButton("Makkelijk", Sm2.previewLabel(current, Grade.EASY)) { grade(Grade.EASY) }
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

/* ------------------------------- Instellingen ------------------------------- */

@Composable
private fun SettingsScreen(padding: PaddingValues, onRequestAnki: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo.get(context) }
    val prefs = repo.prefs
    val scope = rememberCoroutineScope()

    var sentenceMode by remember { mutableStateOf(prefs.readerMode == "sentence") }
    var hideEnglish by remember { mutableStateOf(prefs.hideEnglish) }
    var autoTranslate by remember { mutableStateOf(prefs.autoTranslateOnLoad) }
    var autoAnki by remember { mutableStateOf(prefs.autoPushAnki) }
    var deck by remember { mutableStateOf(prefs.ankiDeck) }
    var model by remember { mutableStateOf(prefs.ankiModel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Instellingen", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        SettingSwitch("Per zin vertalen (uit = per alinea)", sentenceMode) {
            sentenceMode = it
            prefs.readerMode = if (it) "sentence" else "paragraph"
        }
        SettingSwitch("Engels verbergen tot je erop tikt", hideEnglish) {
            hideEnglish = it; prefs.hideEnglish = it
        }
        SettingSwitch("Automatisch vertalen bij openen", autoTranslate) {
            autoTranslate = it; prefs.autoTranslateOnLoad = it
        }

        Divider()
        Text("Anki", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

        val ankiInstalled = AnkiBridge.isAvailable(context)
        Text(
            if (ankiInstalled) "AnkiDroid gevonden op dit toestel."
            else "AnkiDroid niet gevonden. Exporteren naar bestand werkt wel.",
            fontSize = 13.sp
        )

        OutlinedTextField(
            value = deck,
            onValueChange = { deck = it; prefs.ankiDeck = it },
            label = { Text("Deck") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; prefs.ankiModel = it },
            label = { Text("Notitietype (meestal Basic)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitch("Nieuwe items meteen naar AnkiDroid sturen", autoAnki) {
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
            ) { Text("Stuur naar Anki") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val items = repo.all()
                        if (items.isEmpty()) {
                            toast(context, "Nog niets bewaard")
                        } else {
                            val file = CsvExport.write(context, items)
                            CsvExport.share(context, file)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Exporteer") }
        }

        Divider()
        Text("Offline model", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch { toast(context, Nl2En.prepare() ?: "Model klaar") }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Downloaden") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        toast(context, if (Nl2En.deleteModel()) "Verwijderd" else "Mislukt")
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Verwijderen") }
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
