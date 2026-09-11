package com.watson.nutrilog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.MonthlyReport
import com.watson.nutrilog.data.MonthlyStats
import com.watson.nutrilog.data.TargetRecommendation
import com.watson.nutrilog.data.WeeklyReport
import com.watson.nutrilog.data.WeeklyStats
import com.watson.nutrilog.ui.theme.numeric
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AI 週報／月報。從月曆那一列「AI 週報／月報」開進來。
 *
 * **統計在上、報告在下，而且統計不等報告。** 統計是本機算的、隨時都有；報告要花一次
 * AI 呼叫才有。還沒產生報告的那一週照樣看得到數字，要不要花那一次呼叫由使用者決定。
 *
 * 從對方的版本改寫：拿掉卡片、emoji 與表格元件，本文只認標題、條列與段落 ——
 * prompt 本來就只要這三種，模型偶爾多給的粗體、表格也降成純文字，不讓它長出第四種版面。
 */
@Composable
fun ReportScreen(
    tab: ReportTab,
    weekStart: LocalDate,
    month: YearMonth,
    weeklyState: ReportUiState<WeeklyReport>,
    monthlyState: ReportUiState<MonthlyReport>,
    weeklyStats: WeeklyStats?,
    monthlyStats: MonthlyStats?,
    onSelectTab: (ReportTab) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onGenerateWeekly: () -> Unit,
    onGenerateMonthly: () -> Unit,
    onApplyTargets: (TargetRecommendation) -> Unit,
    onOpenApiSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val today = LocalDate.now()
    val weekly = tab == ReportTab.WEEKLY
    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.report_title),
                // 「返回」不是「關閉」：回的是月曆，和設定子頁同一個講法
                closeLabel = stringResource(R.string.settings_back),
                onClose = onBack,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            NutriTabs(
                labels = listOf(
                    stringResource(R.string.report_tab_weekly),
                    stringResource(R.string.report_tab_monthly),
                ),
                selectedIndex = ReportTab.entries.indexOf(tab),
                onSelect = { onSelectTab(ReportTab.entries[it]) },
            )
            PeriodHeader(
                label = if (weekly) weekLabel(weekStart, today) else monthLabel(month, today),
                // 未來的週／月沒有東西可寫，箭頭淡掉而不是按了沒反應
                canGoForward = if (weekly) !weekStart.plusDays(7).isAfter(today)
                else month.isBefore(YearMonth.from(today)),
                onPrevious = { if (weekly) onShiftWeek(-1) else onShiftMonth(-1) },
                onNext = { if (weekly) onShiftWeek(1) else onShiftMonth(1) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Hairline(Modifier.padding(horizontal = 22.dp))
            // 週報和月報各自記捲動位置：從讀到一半的週報切去月報，不該從月報的中段開始
            key(tab) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (weekly) {
                        WeeklyContent(weeklyState, weeklyStats, onGenerateWeekly, onApplyTargets, onOpenApiSettings)
                    } else {
                        MonthlyContent(monthlyState, monthlyStats, onGenerateMonthly, onOpenApiSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyContent(
    state: ReportUiState<WeeklyReport>,
    stats: WeeklyStats?,
    onGenerate: () -> Unit,
    onApply: (TargetRecommendation) -> Unit,
    onOpenApi: () -> Unit,
) {
    val hasData = stats == null || stats.loggedDaysCount > 0 || stats.totalActiveCaloriesBurned > 0
    if (stats != null && hasData) {
        val c = stats.comparison
        StatsBlock(
            loggedDays = stats.loggedDaysCount,
            avgIntake = stats.avgDailyCaloriesConsumed,
            intakeDelta = c?.deltaCaloriesInPerDay,
            exercise = stringResource(R.string.report_exercise, stats.totalActiveCaloriesBurned.grouped()),
            // 比較值是「每天平均」的差，上面那個數字是一週總和，換回同一個單位才能並排
            exerciseDelta = c?.let { it.deltaActiveBurnPerDay * 7 },
            tdee = stats.avgDailyTdee,
            bmr = stats.estimatedBmrPerDay,
            balance = stats.netEnergyBalance,
            fatKg = stats.estimatedFatChangeKg,
            protein = stats.avgProteinG,
            fat = stats.avgFatG,
            carbs = stats.avgCarbsG,
            deltaRes = R.string.report_vs_last_week,
        )
        Hairline()
    }
    ReportBody(
        state = state,
        hasData = hasData,
        emptyRes = R.string.report_empty_week,
        noDataRes = R.string.report_no_data_week,
        generateRes = R.string.report_generate_week,
        onGenerate = onGenerate,
        onOpenApi = onOpenApi,
    ) { report ->
        MarkdownBody(report.markdownContent)
        report.recommendation?.let { recommendation ->
            Hairline()
            RecommendationBlock(recommendation, report.isApplied, onApply)
        }
        Hairline()
        RegenerateRow(report.generatedAt, report.model, onGenerate)
    }
}

@Composable
private fun MonthlyContent(
    state: ReportUiState<MonthlyReport>,
    stats: MonthlyStats?,
    onGenerate: () -> Unit,
    onOpenApi: () -> Unit,
) {
    val hasData = stats == null || stats.loggedDaysCount > 0 || stats.totalActiveCaloriesBurned > 0
    if (stats != null && hasData) {
        val c = stats.comparison
        StatsBlock(
            loggedDays = stats.loggedDaysCount,
            avgIntake = stats.avgDailyCaloriesConsumed,
            intakeDelta = c?.deltaCaloriesInPerDay,
            // 月份長短不一，總和拿來跟上個月比不公平，所以月報的運動講每天平均
            exercise = stringResource(R.string.report_exercise_daily, stats.avgDailyActiveBurned.grouped()),
            exerciseDelta = c?.deltaActiveBurnPerDay,
            tdee = stats.avgDailyTdee,
            bmr = stats.estimatedBmrPerDay,
            balance = stats.netEnergyBalance,
            fatKg = stats.estimatedFatChangeKg,
            protein = stats.avgProteinG,
            fat = stats.avgFatG,
            carbs = stats.avgCarbsG,
            deltaRes = R.string.report_vs_last_month,
        )
        Hairline()
    }
    ReportBody(
        state = state,
        hasData = hasData,
        emptyRes = R.string.report_empty_month,
        noDataRes = R.string.report_no_data_month,
        generateRes = R.string.report_generate_month,
        onGenerate = onGenerate,
        onOpenApi = onOpenApi,
    ) { report ->
        MarkdownBody(report.markdownContent)
        Hairline()
        RegenerateRow(report.generatedAt, report.model, onGenerate)
    }
}

/**
 * 報告那一段在各種狀態下的樣子。週報與月報只差文案與「準備好之後」長什麼樣。
 *
 * [hasData] 為 false 時不給「產生」那顆章：一筆紀錄都沒有的期間，按下去只會花一次
 * 呼叫換來一份在講「沒有資料」的報告。
 */
@Composable
private fun <T> ReportBody(
    state: ReportUiState<T>,
    hasData: Boolean,
    emptyRes: Int,
    noDataRes: Int,
    generateRes: Int,
    onGenerate: () -> Unit,
    onOpenApi: () -> Unit,
    ready: @Composable (T) -> Unit,
) {
    when (state) {
        ReportUiState.Loading -> IndeterminateRule(Modifier.padding(top = 12.dp))
        ReportUiState.Empty -> {
            Note(stringResource(if (hasData) emptyRes else noDataRes))
            if (hasData) StampButton(label = stringResource(generateRes), onClick = onGenerate)
        }
        ReportUiState.Generating -> {
            IndeterminateRule(Modifier.padding(top = 12.dp))
            Note(stringResource(R.string.report_generating))
        }
        // 指名是哪一家：兩家各有一把金鑰，只講「沒設定」的話很可能去填錯的那一把
        is ReportUiState.MissingKey -> {
            Note(stringResource(R.string.report_missing_key, state.provider.label))
            StampButton(label = stringResource(R.string.report_go_api), onClick = onOpenApi)
        }
        is ReportUiState.Failed -> {
            Text(
                withNumerals(stringResource(R.string.report_failed, state.reason)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            StampButton(label = stringResource(R.string.retry), onClick = onGenerate)
        }
        is ReportUiState.Ready -> ready(state.report)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        withNumerals(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PeriodHeader(
    label: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clickable(onClick = onPrevious),
            contentAlignment = Alignment.Center,
        ) {
            ChevronMark(scheme.onSurface, pointsLeft = true)
        }
        Text(
            withNumerals(label),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(36.dp)
                .then(if (canGoForward) Modifier.clickable(onClick = onNext) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            ChevronMark(if (canGoForward) scheme.onSurface else scheme.outlineVariant, pointsLeft = false)
        }
    }
}

@Composable
private fun StatsBlock(
    loggedDays: Int,
    avgIntake: Double,
    intakeDelta: Double?,
    exercise: String,
    exerciseDelta: Double?,
    tdee: Double,
    bmr: Double,
    balance: Double,
    fatKg: Double,
    protein: Double,
    fat: Double,
    carbs: Double,
    deltaRes: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatLine(
            stringResource(R.string.report_logged, loggedDays, avgIntake.grouped()),
            intakeDelta?.let { stringResource(deltaRes, it.signed()) },
        )
        StatLine(exercise, exerciseDelta?.let { stringResource(deltaRes, it.signed()) })
        StatLine(stringResource(R.string.report_tdee, tdee.grouped(), bmr.grouped()), null)
        StatLine(stringResource(R.string.report_balance, balance.signed(), fatKg.signedKg()), null)
        StatLine(
            listOf(
                stringResource(R.string.report_average),
                stringResource(R.string.nutrient_protein) + " " + protein.roundToInt(),
                stringResource(R.string.nutrient_fat) + " " + fat.roundToInt(),
                stringResource(R.string.nutrient_carbs) + " " + carbs.roundToInt(),
            ).joinToString("  "),
            null,
        )
    }
}

@Composable
private fun StatLine(text: String, delta: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(withNumerals(text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        delta?.let {
            Text(
                withNumerals(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** 週報建議的下週目標。和身型計算同一條規則：算出來的只是建議，按了才寫進設定。 */
@Composable
private fun RecommendationBlock(
    recommendation: TargetRecommendation,
    applied: Boolean,
    onApply: (TargetRecommendation) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SectionLabel(stringResource(R.string.report_recommend_title))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(
            R.string.nutrient_calories to recommendation.calorieTarget,
            R.string.nutrient_protein to recommendation.proteinTargetG,
            R.string.nutrient_fat to recommendation.fatTargetG,
            R.string.nutrient_carbs to recommendation.carbsTargetG,
        ).forEach { (label, value) ->
            Column {
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text("%,d".format(value), style = MaterialTheme.typography.titleMedium.numeric())
            }
        }
    }
    if (recommendation.reason.isNotBlank()) {
        Text(
            withNumerals(recommendation.reason),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
    // 套用過就退成外框章：看得出這件事做過了，也不會手滑再蓋一次自己後來手改的目標
    StampButton(
        label = stringResource(if (applied) R.string.report_applied else R.string.bmr_apply),
        onClick = { onApply(recommendation) },
        enabled = !applied,
    )
}

/**
 * 「重新產生」配上這份報告是何時、用哪個模型寫的。空心章：它是次要動作，
 * 畫面上真正的主章是「套用為每日目標」。
 */
@Composable
private fun RegenerateRow(generatedAt: Long, model: String, onGenerate: () -> Unit) {
    val stamp = GeneratedAtFormat.format(Instant.ofEpochMilli(generatedAt).atZone(ZoneId.systemDefault()))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StampButton(
            label = stringResource(R.string.report_regenerate),
            onClick = onGenerate,
            color = Color.Transparent,
            modifier = Modifier.weight(1f),
        )
        // 也給 weight：OpenRouter 的模型名稱很長，不收的話會把章擠到看不見
        Text(
            withNumerals("$stamp · $model"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun MarkdownBody(markdown: String) {
    val blocks = remember(markdown) { parseReportMarkdown(markdown) }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                // 標題的層級靠字距（titleSmall），和整個 app 的小標同一個訊號，不靠加粗
                is ReportBlock.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 12.dp),
                )
                is ReportBlock.Item -> Row {
                    Text(
                        withNumerals(block.marker),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        withNumerals(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                is ReportBlock.Paragraph -> Text(withNumerals(block.text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private sealed interface ReportBlock {
    data class Heading(val text: String) : ReportBlock
    data class Item(val marker: String, val text: String) : ReportBlock
    data class Paragraph(val text: String) : ReportBlock
}

private val HeadingLine = Regex("""^#{1,6}\s*(.+)$""")
private val BulletLine = Regex("""^[-*+•・]\s+(.+)$""")

// 「1.」「1)」後面要有空白才算條列，不然「3.5 公斤」開頭的句子會被切成第 3 點；「1、」照中文習慣可以不空
private val OrderedLine = Regex("""^(\d+)(?:[.)]\s+|、\s*)(.+)$""")
private val TableDelimiter = Regex("""^\|?[\s:|-]+\|?$""")
private val SingleEmphasis = Regex("""\*(\S[^*]*?)\*""")

/** 只認標題、條列、段落三種；其餘的 Markdown 記號拿掉，內容留著。 */
private fun parseReportMarkdown(markdown: String): List<ReportBlock> = markdown.lines().mapNotNull { raw ->
    val line = raw.trim()
    if (line.isEmpty() || line.startsWith("```") || line.all { it == '-' || it == '*' || it == '_' }) {
        return@mapNotNull null
    }
    HeadingLine.matchEntire(line)?.let { return@mapNotNull ReportBlock.Heading(plainInline(it.groupValues[1])) }
    BulletLine.matchEntire(line)?.let { return@mapNotNull ReportBlock.Item("・", plainInline(it.groupValues[1])) }
    OrderedLine.matchEntire(line)?.let {
        return@mapNotNull ReportBlock.Item(it.groupValues[1] + ".", plainInline(it.groupValues[2]))
    }
    if (line.startsWith("|")) {
        if (TableDelimiter.matches(line)) return@mapNotNull null
        return@mapNotNull ReportBlock.Paragraph(
            line.trim('|').split('|').joinToString("　") { plainInline(it.trim()) }
        )
    }
    ReportBlock.Paragraph(plainInline(line.removePrefix(">").trim()))
}

private fun plainInline(text: String): String = text
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")
    .replace(SingleEmphasis) { it.groupValues[1] }

@Composable
private fun weekLabel(start: LocalDate, today: LocalDate): String {
    val end = start.plusDays(6)
    val range = if (start.month == end.month) {
        stringResource(R.string.report_week_range, start.monthValue, start.dayOfMonth, end.dayOfMonth)
    } else {
        stringResource(R.string.report_week_range_cross, start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
    }
    val current = !today.isBefore(start) && !today.isAfter(end)
    return if (current) range + stringResource(R.string.report_this_week) else range
}

@Composable
private fun monthLabel(month: YearMonth, today: LocalDate): String {
    val label = stringResource(R.string.report_month_label, month.year, month.monthValue)
    return if (month == YearMonth.from(today)) label + stringResource(R.string.report_this_month) else label
}

private val GeneratedAtFormat = DateTimeFormatter.ofPattern("M/d HH:mm")

private fun Double.grouped(): String = "%,d".format(roundToInt())

/** 帶正負號的整數。負號用 U+2212，和今日頁「運動 −350」同一個字。 */
private fun Double.signed(): String {
    val r = roundToInt()
    return when {
        r > 0 -> "+" + "%,d".format(r)
        r < 0 -> "−" + "%,d".format(-r)
        else -> "0"
    }
}

private fun Double.signedKg(): String {
    val tenths = (abs(this) * 10).roundToInt()
    if (tenths == 0) return "0"
    return (if (this < 0) "−" else "+") + "${tenths / 10}.${tenths % 10}"
}
