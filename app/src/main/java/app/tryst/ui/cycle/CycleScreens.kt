// SPDX-License-Identifier: GPL-3.0-or-later
// ╭──────────────────────────────────────────────────────────────────╮
// │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
// │  │                                                            │  │
// │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
// │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
// │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
// │  │   ██╔══██║    ██║███╗██║           │                       │  │
// │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
// │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
// │  │                                                            │  │
// │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
// ╰──────────────────────────────────────────────────────────────────╯
package app.tryst.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tryst.R
import app.tryst.data.cycle.CyclePrediction
import app.tryst.data.cycle.CycleStats
import app.tryst.data.cycle.CycleStatus
import app.tryst.data.db.entity.CycleDayEntity
import app.tryst.data.db.entity.CycleFlow
import app.tryst.data.db.entity.CyclePeriodEntity
import app.tryst.data.db.entity.CycleSymptomEntity
import app.tryst.data.db.entity.SymptomStrength
import app.tryst.data.repository.CycleOwner
import app.tryst.ui.common.TooltipIconButton
import app.tryst.ui.common.adaptiveContentWidth
import app.tryst.ui.settings.CatalogManageScreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val shortDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val monthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

internal fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormatter)

private fun formatShortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDayFormatter)

private fun formatMonthDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(monthDayFormatter)

private const val MILLIS_PER_DAY = 86_400_000L

@Composable
private fun flowLabel(flow: CycleFlow): String = stringResource(
    when (flow) {
        CycleFlow.SPOTTING -> R.string.cycle_flow_spotting
        CycleFlow.LIGHT -> R.string.cycle_flow_light
        CycleFlow.MEDIUM -> R.string.cycle_flow_medium
        CycleFlow.HEAVY -> R.string.cycle_flow_heavy
    },
)

@Composable
private fun problemText(problem: CycleProblem): String = stringResource(
    when (problem) {
        CycleProblem.OVERLAP -> R.string.cycle_problem_overlap
        CycleProblem.END_BEFORE_START -> R.string.cycle_problem_end_before_start
    },
)

@Composable
private fun personName(name: String?): String = name ?: stringResource(R.string.profile_you)

/** A one-day picker that can't pick a future day. The picker speaks UTC-midnight millis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleDayPicker(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val today = remember { todayEpochDay() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis / MILLIS_PER_DAY <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) { DatePicker(state = state) }
}

@Composable
private fun ProblemDialog(problem: CycleProblem?, onDismiss: () -> Unit) {
    if (problem == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(problemText(problem)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

// ---------------------------------------------------------------------------------------
// Cycle tab: everyone, where they are, and one-tap "started" / "ended"
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(onOpenPerson: (CycleOwner) -> Unit, viewModel: CycleViewModel = hiltViewModel()) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val people = tab.people
    var pickFor by remember { mutableStateOf<CycleOwner?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cycle_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).adaptiveContentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.cycle_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(people, key = { "${it.owner.kind}/${it.owner.id}" }) { person ->
                PersonCard(
                    person = person,
                    onOpen = { onOpenPerson(person.owner) },
                    onStartToday = { viewModel.start(person.owner) },
                    onStartOtherDay = { pickFor = person.owner },
                    onEndToday = { viewModel.endToday(it) },
                )
            }
            if (tab.hiddenMale > 0) {
                // Says why someone's missing, and how to bring them back if their sex was set wrong (D-71).
                item {
                    Text(
                        if (people.isEmpty()) {
                            stringResource(R.string.cycle_nobody)
                        } else {
                            pluralStringResource(R.plurals.cycle_hidden_male, tab.hiddenMale, tab.hiddenMale)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pickFor?.let { owner ->
        CycleDayPicker(initial = todayEpochDay(), onPick = { viewModel.start(owner, it) }, onDismiss = { pickFor = null })
    }
    ProblemDialog(viewModel.problem, viewModel::clearProblem)
}

@Composable
private fun PersonCard(
    person: CyclePerson,
    onOpen: () -> Unit,
    onStartToday: () -> Unit,
    onStartOtherDay: () -> Unit,
    onEndToday: (CyclePeriodEntity) -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(personName(person.name), style = MaterialTheme.typography.titleMedium)
            Text(statusLine(person.status), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Mid-period the next-period guess is noise; show it once the period is over.
            if (person.status is CycleStatus.Between) {
                person.prediction?.let { PredictionText(it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val s = person.status) {
                    // Once the real end is set there's nothing to end; a new period can't start inside this one.
                    is CycleStatus.OnPeriod -> if (s.period.endEstimated) {
                        Button(onClick = { onEndToday(s.period) }) { Text(stringResource(R.string.cycle_ended_today)) }
                    }
                    else -> {
                        Button(onClick = onStartToday) { Text(stringResource(R.string.cycle_started_today)) }
                        OutlinedButton(onClick = onStartOtherDay) { Text(stringResource(R.string.cycle_started_other_day)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLine(status: CycleStatus): String = when (status) {
    CycleStatus.None -> stringResource(R.string.cycle_status_none)
    is CycleStatus.OnPeriod -> if (status.period.endEstimated) {
        stringResource(R.string.cycle_status_on_estimated, status.dayNumber, formatDay(status.period.endEpochDay))
    } else {
        stringResource(R.string.cycle_status_on, status.dayNumber, formatDay(status.period.endEpochDay))
    }
    is CycleStatus.Between -> stringResource(R.string.cycle_status_between, formatDay(status.last.startEpochDay), status.cycleDay)
}

/** The next-period estimate in words. Always says it's an estimate; never a single hard date. */
@Composable
private fun PredictionText(prediction: CyclePrediction) {
    val text = when (prediction) {
        is CyclePrediction.NotEnoughData -> {
            val more = prediction.need - prediction.have
            pluralStringResource(R.plurals.cycle_prediction_need_more, more, more)
        }
        is CyclePrediction.Irregular -> stringResource(R.string.cycle_prediction_irregular)
        is CyclePrediction.Next -> if (prediction.lateByDays > 0) {
            pluralStringResource(
                R.plurals.cycle_prediction_late,
                prediction.lateByDays,
                formatMonthDay(prediction.earliest),
                formatMonthDay(prediction.latest),
                prediction.lateByDays,
            )
        } else {
            stringResource(
                R.string.cycle_prediction_next,
                formatMonthDay(prediction.likely),
                formatMonthDay(prediction.earliest),
                formatMonthDay(prediction.latest),
            )
        }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
}

/** Cycle and period length for one person, plus the estimate when predictions are on. */
@Composable
private fun StatsCard(stats: CycleStats, prediction: CyclePrediction?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.cycle_stats_title), style = MaterialTheme.typography.titleSmall)
            val usual = stats.usualCycle
            if (usual != null) {
                Text(stringResource(R.string.cycle_stats_cycle, usual, stats.shortestCycle ?: usual, stats.longestCycle ?: usual))
                Text(
                    pluralStringResource(R.plurals.cycle_stats_based_on, stats.cycleLengths.size, stats.cycleLengths.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.cycle_stats_cycle_unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            stats.usualPeriod?.let { Text(pluralStringResource(R.plurals.cycle_stats_period, it, it)) }
            prediction?.let { PredictionText(it) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// One person's periods
// ---------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyclePersonScreen(
    onBack: () -> Unit,
    onOpenPeriod: (String) -> Unit,
    onManageSymptoms: (CycleOwner) -> Unit,
    viewModel: CyclePersonViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val periods by viewModel.periods.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val prediction by viewModel.prediction.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(personName(name)) },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { onManageSymptoms(viewModel.owner) }) {
                        Text(stringResource(R.string.cycle_symptoms_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.cycle_log_period)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).adaptiveContentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats?.let { s -> item { StatsCard(s, prediction) } }
            if (periods.isEmpty()) {
                item { Text(stringResource(R.string.cycle_person_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(periods, key = { it.id }) { period ->
                Card(onClick = { onOpenPeriod(period.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cycle_period_range, formatDay(period.startEpochDay), formatDay(period.endEpochDay)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            pluralStringResource(
                                if (period.endEstimated) R.plurals.cycle_period_days_estimated else R.plurals.cycle_period_days,
                                period.lengthDays,
                                period.lengthDays,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        period.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    if (picking) {
        CycleDayPicker(
            initial = todayEpochDay(),
            onPick = { viewModel.log(it, onOpenPeriod) },
            onDismiss = { picking = false },
        )
    }
    ProblemDialog(viewModel.problem, viewModel::clearProblem)
}

// ---------------------------------------------------------------------------------------
// One period: dates, per-day flow, note
// ---------------------------------------------------------------------------------------

private enum class DateField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CyclePeriodScreen(
    onBack: () -> Unit,
    onManageSymptoms: (CycleOwner) -> Unit,
    viewModel: CyclePeriodViewModel = hiltViewModel(),
) {
    val draft = viewModel.draft
    val symptomList by viewModel.symptomList.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<DateField?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.missing) { if (viewModel.missing) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cycle_period_title)) },
                navigationIcon = {
                    TooltipIconButton(label = stringResource(R.string.cd_back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }, enabled = draft != null) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (draft == null) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).adaptiveContentWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = DateField.START }) {
                        Text(stringResource(R.string.cycle_start_on, formatDay(draft.period.startEpochDay)))
                    }
                    OutlinedButton(onClick = { picking = DateField.END }) {
                        Text(stringResource(R.string.cycle_end_on, formatDay(draft.period.endEpochDay)))
                    }
                }
                if (draft.period.endEstimated) {
                    Text(
                        stringResource(R.string.cycle_end_estimated_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.cycle_flow_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SymptomsHeader(
                    hasSymptoms = symptomList.isNotEmpty(),
                    onManage = { onManageSymptoms(CycleOwner(draft.period.ownerKind, draft.period.ownerId)) },
                )
            }
            items(draft.days, key = { it.epochDay }) { day ->
                DayRow(
                    day = day,
                    symptomList = symptomList,
                    logged = draft.symptoms[day.epochDay].orEmpty(),
                    onFlow = { viewModel.setFlow(day.epochDay, it) },
                    onSymptom = { viewModel.cycleSymptom(day.epochDay, it) },
                )
            }
            item {
                OutlinedTextField(
                    value = draft.period.note.orEmpty(),
                    onValueChange = viewModel::setNote,
                    label = { Text(stringResource(R.string.encounter_field_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextButton(onClick = { confirmDelete = true }) {
                    Text(stringResource(R.string.cycle_delete_period), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (draft != null) {
        when (picking) {
            DateField.START -> CycleDayPicker(draft.period.startEpochDay, viewModel::setStart) { picking = null }
            DateField.END -> CycleDayPicker(draft.period.endEpochDay, viewModel::setEnd) { picking = null }
            null -> Unit
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.cycle_delete_period_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    ProblemDialog(viewModel.problem, viewModel::clearProblem)
}

/** The line above the days: how symptom chips work, and the way to this person's symptom list. */
@Composable
private fun SymptomsHeader(hasSymptoms: Boolean, onManage: () -> Unit) {
    Column {
        Text(
            stringResource(if (hasSymptoms) R.string.cycle_symptoms_hint else R.string.cycle_symptoms_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onManage) {
            Text(stringResource(if (hasSymptoms) R.string.cycle_symptoms_edit else R.string.cycle_symptoms_add))
        }
    }
}

@Composable
private fun strengthLabel(strength: SymptomStrength): String = stringResource(
    when (strength) {
        SymptomStrength.MILD -> R.string.symptom_strength_mild
        SymptomStrength.MEDIUM -> R.string.symptom_strength_medium
        SymptomStrength.STRONG -> R.string.symptom_strength_strong
    },
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayRow(
    day: CycleDayEntity,
    symptomList: List<CycleSymptomEntity>,
    logged: Map<String, SymptomStrength>,
    onFlow: (CycleFlow) -> Unit,
    onSymptom: (String) -> Unit,
) {
    Column {
        Row {
            Text(
                formatShortDay(day.epochDay),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(120.dp).alignByBaseline(),
            )
            if (day.estimated) {
                Text(
                    stringResource(R.string.cycle_day_estimated),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CycleFlow.entries.forEach { flow ->
                FilterChip(
                    selected = day.flowLevel == flow,
                    onClick = { onFlow(flow) },
                    label = { Text(flowLabel(flow)) },
                )
            }
        }
        if (symptomList.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                symptomList.forEach { symptom ->
                    val strength = logged[symptom.id]
                    FilterChip(
                        selected = strength != null,
                        onClick = { onSymptom(symptom.id) },
                        label = {
                            Text(
                                if (strength == null) {
                                    symptom.label
                                } else {
                                    stringResource(R.string.cycle_symptom_chip, symptom.label, strengthLabel(strength))
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// One person's symptom list (0.7.4, D-73)
// ---------------------------------------------------------------------------------------

@Composable
fun CycleSymptomsScreen(onBack: () -> Unit, viewModel: CycleSymptomsViewModel = hiltViewModel()) {
    val rows by viewModel.symptoms.collectAsStateWithLifecycle()
    CatalogManageScreen(
        R.string.cycle_symptoms_title, R.string.cycle_symptoms_desc, R.string.cycle_symptoms_add_label, R.string.cycle_symptoms_empty,
        rows.map { it.id to it.label }, viewModel::add, viewModel::rename, viewModel::delete, onBack,
    )
}

// ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
