package com.watson.nutrilog.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watson.nutrilog.R
import com.watson.nutrilog.data.MonthlyComparison
import com.watson.nutrilog.data.MonthlyReport
import com.watson.nutrilog.data.MonthlyStats
import com.watson.nutrilog.data.NutriSettings
import com.watson.nutrilog.data.TargetRecommendation
import com.watson.nutrilog.data.WeeklyComparison
import com.watson.nutrilog.data.WeeklyReport
import com.watson.nutrilog.data.WeeklyStats
import com.watson.nutrilog.ui.theme.NutrientColors
import com.watson.nutrilog.ui.theme.numeric
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ReportTab {
    WEEKLY, MONTHLY
}

sealed interface WeeklyReportUiState {
    data class Loading(val message: String? = null) : WeeklyReportUiState
    data class Error(val error: String, val stats: WeeklyStats? = null) : WeeklyReportUiState
    data class Success(
        val report: WeeklyReport,
        val stats: WeeklyStats,
        val isApplied: Boolean = false,
        val isGenerating: Boolean = false,
    ) : WeeklyReportUiState
    data class Empty(val stats: WeeklyStats?) : WeeklyReportUiState
}

sealed interface MonthlyReportUiState {
    data class Loading(val message: String? = null) : MonthlyReportUiState
    data class Error(val error: String, val stats: MonthlyStats? = null) : MonthlyReportUiState
    data class Success(
        val report: MonthlyReport,
        val stats: MonthlyStats,
        val isGenerating: Boolean = false,
    ) : MonthlyReportUiState
    data class Empty(val stats: MonthlyStats?) : MonthlyReportUiState
}

@Composable
fun WeeklyReportScreen(
    selectedTab: ReportTab = ReportTab.WEEKLY,
    onTabSelect: (ReportTab) -> Unit = {},
    // 週報資料
    weekStart: LocalDate,
    currentWeekStart: LocalDate,
    uiState: WeeklyReportUiState,
    onShiftWeek: (Long) -> Unit,
    onGenerate: () -> Unit,
    onApplyRecommendation: (TargetRecommendation) -> Unit,
    // 月報資料
    activeMonth: YearMonth = YearMonth.now(),
    currentMonth: YearMonth = YearMonth.now(),
    monthlyUiState: MonthlyReportUiState = MonthlyReportUiState.Loading(),
    onShiftMonth: (Long) -> Unit = {},
    onGenerateMonthly: () -> Unit = {},
    // 共同資料
    settings: NutriSettings,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("MM/dd")
    val yearFmt = DateTimeFormatter.ofPattern("yyyy")
    val weekEnd = weekStart.plusDays(6)
    val isCurrentWeek = weekStart == currentWeekStart
    val canGoForwardWeek = weekStart < currentWeekStart

    val isCurrentMonth = activeMonth == currentMonth
    val canGoForwardMonth = activeMonth < currentMonth

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.report_center_title),
                closeLabel = stringResource(R.string.close),
                onClose = onClose,
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 20.dp)
        ) {
            // 頂部紙墨雙分籤：[ 週報 Weekly ] | [ 月報 Monthly ]
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
                    .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(2.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selectedTab == ReportTab.WEEKLY) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        .clickable { onTabSelect(ReportTab.WEEKLY) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.report_tab_weekly),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selectedTab == ReportTab.WEEKLY) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selectedTab == ReportTab.MONTHLY) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                        .clickable { onTabSelect(ReportTab.MONTHLY) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.report_tab_monthly),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selectedTab == ReportTab.MONTHLY) MaterialTheme.colorScheme.inverseOnSurface else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (selectedTab == ReportTab.WEEKLY) {
                // 週別翻閱器
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable { onShiftWeek(-1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${weekStart.format(yearFmt)} 年",
                            style = MaterialTheme.typography.bodySmall.numeric(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${weekStart.format(dateFmt)} – ${weekEnd.format(dateFmt)}" +
                                    if (isCurrentWeek) "（本週）" else "",
                            style = MaterialTheme.typography.titleMedium.numeric(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable(enabled = canGoForwardWeek) { onShiftWeek(1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "›",
                            fontSize = 24.sp,
                            color = if (canGoForwardWeek)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        )
                    }
                }

                Hairline()

                // 週報內容區
                when (uiState) {
                    is WeeklyReportUiState.Loading -> {
                        ReportLoadingView(uiState.message ?: stringResource(R.string.weekly_report_generating))
                    }

                    is WeeklyReportUiState.Error -> {
                        ReportErrorView(
                            error = uiState.error,
                            hasApiKey = settings.nvidiaApiKey.isNotBlank(),
                            onOpenSettings = onOpenSettings,
                            onRetry = onGenerate,
                        )
                    }

                    is WeeklyReportUiState.Empty -> {
                        val stats = uiState.stats
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (stats != null && stats.loggedDaysCount > 0) {
                                StatsOverviewCard(stats)

                                if (settings.nvidiaApiKey.isBlank()) {
                                    ApiKeyPromptCard(onOpenSettings)
                                } else {
                                    StampButton(
                                        label = stringResource(R.string.weekly_report_generate),
                                        onClick = onGenerate,
                                        withPlus = true,
                                    )
                                }
                            } else {
                                EmptyDataView(stringResource(R.string.weekly_report_no_data))
                            }
                        }
                    }

                    is WeeklyReportUiState.Success -> {
                        val report = uiState.report
                        val stats = uiState.stats
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            StatsOverviewCard(stats)

                            report.recommendation?.let { rec ->
                                RecommendationCard(
                                    recommendation = rec,
                                    isApplied = uiState.isApplied,
                                    onApply = { onApplyRecommendation(rec) },
                                )
                            }

                            Hairline()

                            MarkdownReportView(markdown = report.markdownContent)

                            Hairline()

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextAction(
                                    label = stringResource(R.string.weekly_report_regenerate),
                                    onClick = onGenerate,
                                )
                            }
                        }
                    }
                }
            } else {
                // 月報模式
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable { onShiftMonth(-1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("‹", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${activeMonth.year} 年",
                            style = MaterialTheme.typography.bodySmall.numeric(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${"%02d".format(activeMonth.monthValue)} 月健康覆盤" +
                                    if (isCurrentMonth) "（本月）" else "",
                            style = MaterialTheme.typography.titleMedium.numeric(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(
                        Modifier
                            .size(36.dp)
                            .clickable(enabled = canGoForwardMonth) { onShiftMonth(1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "›",
                            fontSize = 24.sp,
                            color = if (canGoForwardMonth)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        )
                    }
                }

                Hairline()

                // 月報內容區
                when (monthlyUiState) {
                    is MonthlyReportUiState.Loading -> {
                        ReportLoadingView(monthlyUiState.message ?: stringResource(R.string.monthly_report_generating))
                    }

                    is MonthlyReportUiState.Error -> {
                        ReportErrorView(
                            error = monthlyUiState.error,
                            hasApiKey = settings.nvidiaApiKey.isNotBlank(),
                            onOpenSettings = onOpenSettings,
                            onRetry = onGenerateMonthly,
                        )
                    }

                    is MonthlyReportUiState.Empty -> {
                        val stats = monthlyUiState.stats
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (stats != null && stats.loggedDaysCount > 0) {
                                MonthlyStatsOverviewCard(stats)

                                if (settings.nvidiaApiKey.isBlank()) {
                                    ApiKeyPromptCard(onOpenSettings)
                                } else {
                                    StampButton(
                                        label = stringResource(R.string.monthly_report_generate),
                                        onClick = onGenerateMonthly,
                                        withPlus = true,
                                    )
                                }
                            } else {
                                EmptyDataView(stringResource(R.string.monthly_report_no_data))
                            }
                        }
                    }

                    is MonthlyReportUiState.Success -> {
                        val report = monthlyUiState.report
                        val stats = monthlyUiState.stats
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            MonthlyStatsOverviewCard(stats)

                            Hairline()

                            MarkdownReportView(markdown = report.markdownContent)

                            Hairline()

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextAction(
                                    label = stringResource(R.string.weekly_report_regenerate),
                                    onClick = onGenerateMonthly,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeltaBadge(
    delta: Double,
    unit: String,
    periodLabel: String,
) {
    if (delta == 0.0) return
    val isPositive = delta > 0
    val arrow = if (isPositive) "↑" else "↓"
    val formatted = if (abs(delta) < 1.0) "%.1f".format(abs(delta)) else abs(delta).roundToInt().toString()
    Text(
        text = "$arrow $formatted $unit $periodLabel",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp).numeric(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun StatsOverviewCard(stats: WeeklyStats) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle(stringResource(R.string.weekly_report_stats_title))

        // 上排：總攝取 vs 實測總消耗
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.weekly_report_total_consumed))
                Text(
                    "${stats.totalCaloriesConsumed.roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "記錄 ${stats.loggedDaysCount} 天 · 均 ${stats.avgDailyCaloriesConsumed.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaCaloriesInPerDay, "kcal/日", stringResource(R.string.vs_prev_week))
                }
            }
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.weekly_report_total_burned))
                Text(
                    "${(stats.totalActiveCaloriesBurned + stats.estimatedBmrPerDay * stats.loggedDaysCount.coerceAtLeast(1)).roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "活動消耗 ${(stats.totalActiveCaloriesBurned).roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaActiveBurnPerDay, "kcal/日", stringResource(R.string.vs_prev_week))
                }
            }
        }

        Hairline()

        // 下排：實測 TDEE vs 淨熱量收支
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.weekly_report_avg_tdee))
                Text(
                    "${stats.avgDailyTdee.roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "BMR ${stats.estimatedBmrPerDay.roundToInt()} + 運動",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaDailyTdee, "kcal", stringResource(R.string.vs_prev_week))
                }
            }
            Column(Modifier.weight(1f)) {
                val isDeficit = stats.netEnergyBalance <= 0
                SectionLabel(stringResource(R.string.weekly_report_net_deficit))
                Text(
                    if (isDeficit)
                        "赤字 -${abs(stats.netEnergyBalance).roundToInt()}"
                    else
                        "盈餘 +${stats.netEnergyBalance.roundToInt()}",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = if (isDeficit) NutrientColors.Accent else NutrientColors.Warning,
                )
                Text(
                    if (isDeficit)
                        "預估減脂 ${abs((stats.estimatedFatChangeKg * 100).roundToInt() / 100.0)} kg"
                    else
                        "預估增重 ${(stats.estimatedFatChangeKg * 100).roundToInt() / 100.0} kg",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaFatChangeKg, "kg", stringResource(R.string.vs_prev_week))
                }
            }
        }

        Hairline()

        // 三大營養素均值
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                SectionLabel("平均蛋白質")
                Text(
                    "${stats.avgProteinG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaProteinG, "g", stringResource(R.string.vs_prev_week))
                }
            }
            Column {
                SectionLabel("平均脂肪")
                Text(
                    "${stats.avgFatG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
            Column {
                SectionLabel("平均碳水")
                Text(
                    "${stats.avgCarbsG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MonthlyStatsOverviewCard(stats: MonthlyStats) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle(stringResource(R.string.monthly_report_stats_title))

        // 上排：全月總攝取 vs 全月手錶消耗
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.monthly_report_total_consumed))
                Text(
                    "${stats.totalCaloriesConsumed.roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "記錄 ${stats.loggedDaysCount} 天 · 均 ${stats.avgDailyCaloriesConsumed.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaCaloriesInPerDay, "kcal/日", stringResource(R.string.vs_prev_month))
                }
            }
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.monthly_report_total_burned))
                Text(
                    "${(stats.totalActiveCaloriesBurned + stats.estimatedBmrPerDay * stats.loggedDaysCount.coerceAtLeast(1)).roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "活動消耗 ${(stats.totalActiveCaloriesBurned).roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaActiveBurnPerDay, "kcal/日", stringResource(R.string.vs_prev_month))
                }
            }
        }

        Hairline()

        // 下排：實測 TDEE vs 全月淨熱量收支
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.monthly_report_avg_tdee))
                Text(
                    "${stats.avgDailyTdee.roundToInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = scheme.onSurface,
                )
                Text(
                    "BMR ${stats.estimatedBmrPerDay.roundToInt()} + 日均活動",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaDailyTdee, "kcal", stringResource(R.string.vs_prev_month))
                }
            }
            Column(Modifier.weight(1f)) {
                val isDeficit = stats.netEnergyBalance <= 0
                SectionLabel(stringResource(R.string.monthly_report_net_deficit))
                Text(
                    if (isDeficit)
                        "赤字 -${abs(stats.netEnergyBalance).roundToInt()}"
                    else
                        "盈餘 +${stats.netEnergyBalance.roundToInt()}",
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    color = if (isDeficit) NutrientColors.Accent else NutrientColors.Warning,
                )
                Text(
                    if (isDeficit)
                        "全月預計減脂 ${abs((stats.estimatedFatChangeKg * 10).roundToInt() / 10.0)} kg"
                    else
                        "全月預計增重 ${(stats.estimatedFatChangeKg * 10).roundToInt() / 10.0} kg",
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = scheme.onSurfaceVariant,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaFatChangeKg, "kg", stringResource(R.string.vs_prev_month))
                }
            }
        }

        Hairline()

        // 三大營養素月平均
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                SectionLabel("平均蛋白質")
                Text(
                    "${stats.avgProteinG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
                stats.comparison?.let {
                    DeltaBadge(it.deltaProteinG, "g", stringResource(R.string.vs_prev_month))
                }
            }
            Column {
                SectionLabel("平均脂肪")
                Text(
                    "${stats.avgFatG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
            Column {
                SectionLabel("平均碳水")
                Text(
                    "${stats.avgCarbsG.roundToInt()} g",
                    style = MaterialTheme.typography.titleMedium.numeric(),
                    color = scheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: TargetRecommendation,
    isApplied: Boolean,
    onApply: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, NutrientColors.Accent, RoundedCornerShape(4.dp))
            .background(scheme.surfaceContainerLow)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.weekly_report_recommendation_title),
                style = MaterialTheme.typography.titleMedium,
                color = NutrientColors.Accent,
            )
            if (isApplied) {
                Text(
                    "✓ 已套用",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
        }

        // 推薦熱量與三大營養素數值排版
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MacroTargetItem("熱量", "${recommendation.calorieTarget}", "kcal")
            MacroTargetItem("蛋白質", "${recommendation.proteinTargetG}", "g")
            MacroTargetItem("脂肪", "${recommendation.fatTargetG}", "g")
            MacroTargetItem("碳水", "${recommendation.carbsTargetG}", "g")
        }

        if (recommendation.reason.isNotBlank()) {
            Text(
                recommendation.reason,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        if (!isApplied) {
            StampButton(
                label = stringResource(R.string.weekly_report_apply_recommendation),
                onClick = onApply,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.weekly_report_applied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MacroTargetItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.numeric(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                unit,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 1.dp),
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class OrderedItem(val indexText: String, val text: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private fun parseTableRow(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    return trimmed.split("|").map { it.trim() }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    val headingRegex = Regex("""^(#{1,6})\s*(.*)$""")
    val orderedRegex = Regex("""^(\d+[\.\)])\s+(.*)$""")

    while (i < lines.size) {
        val rawLine = lines[i]
        val line = rawLine.trim()

        if (line.isBlank()) {
            i++
            continue
        }

        // 1. Table check: consecutive lines with | separators
        if (line.startsWith("|") && line.contains("|") && line.count { it == '|' } >= 2) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().count { it == '|' } >= 2) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.isNotEmpty()) {
                val headerCells = parseTableRow(tableLines[0])
                val hasDelimiter = tableLines.size >= 2 &&
                    tableLines[1].replace(Regex("[|:\\-\\s]"), "").isEmpty() &&
                    tableLines[1].contains("-")
                val dataStartIndex = if (hasDelimiter) 2 else 1
                val rows = mutableListOf<List<String>>()
                for (r in dataStartIndex until tableLines.size) {
                    val rowCells = parseTableRow(tableLines[r])
                    if (rowCells.isNotEmpty()) {
                        rows.add(rowCells)
                    }
                }
                blocks.add(MarkdownBlock.Table(headers = headerCells, rows = rows))
                continue
            }
        }

        // 2. Heading: matches #{1,6} with or without space (e.g. ###1. or ### 1.)
        val headerMatch = headingRegex.find(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val text = headerMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
            continue
        }

        // 3. Divider
        if (line == "---" || line == "***" || line == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 4. Blockquote
        if (line.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // 5. Bullet list
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") || line.startsWith("• ")) {
            val bulletText = line.substring(2).trim()
            blocks.add(MarkdownBlock.Bullet(bulletText))
            i++
            continue
        }

        // 6. Ordered list
        val orderedMatch = orderedRegex.find(line)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1]
            val text = orderedMatch.groupValues[2]
            blocks.add(MarkdownBlock.OrderedItem(num, text))
            i++
            continue
        }

        // 7. Regular paragraph
        blocks.add(MarkdownBlock.Paragraph(line))
        i++
    }
    return blocks
}

@Composable
private fun MarkdownReportView(markdown: String) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val topPadding = when (block.level) {
                        1 -> 20.dp
                        2 -> 16.dp
                        else -> 12.dp
                    }
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    val color = when (block.level) {
                        1, 2 -> scheme.onSurface
                        else -> NutrientColors.Accent
                    }
                    Text(
                        text = formatMarkdownInline(block.text),
                        style = style,
                        color = color,
                        modifier = Modifier.padding(top = topPadding, bottom = 4.dp),
                    )
                }
                is MarkdownBlock.Bullet -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NutrientColors.Accent,
                        )
                        Text(
                            text = formatMarkdownInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MarkdownBlock.OrderedItem -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            block.indexText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold).numeric(),
                            color = NutrientColors.Accent,
                        )
                        Text(
                            text = formatMarkdownInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(NutrientColors.Accent)
                        )
                        Text(
                            text = formatMarkdownInline(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    MarkdownTableView(headers = block.headers, rows = block.rows)
                }
                is MarkdownBlock.Divider -> {
                    Hairline(Modifier.padding(vertical = 6.dp))
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatMarkdownInline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableView(headers: List<String>, rows: List<List<String>>) {
    if (headers.isEmpty() && rows.isEmpty()) return

    val colCount = headers.size.coerceAtLeast(rows.maxOfOrNull { it.size } ?: 1)
    val getColWidth: (Int) -> Dp = { idx ->
        if (colCount <= 2) {
            if (idx == 0) 130.dp else 190.dp
        } else {
            if (idx == 0) 110.dp else 140.dp
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                // 表頭
                if (headers.isNotEmpty()) {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        headers.indices.forEach { colIdx ->
                            val header = headers.getOrNull(colIdx).orEmpty()
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .width(getColWidth(colIdx))
                                    .padding(horizontal = 10.dp),
                            )
                        }
                    }
                    Hairline()
                }

                // 資料列
                rows.forEachIndexed { rowIdx, rowCells ->
                    if (rowIdx > 0 || headers.isEmpty()) {
                        Hairline()
                    }
                    Row(
                        Modifier
                            .background(
                                if (rowIdx % 2 == 1)
                                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                                else
                                    Color.Transparent
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        for (colIdx in 0 until colCount) {
                            val cellText = rowCells.getOrNull(colIdx).orEmpty()
                            Text(
                                text = formatMarkdownInline(cellText),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (colIdx == 0)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .width(getColWidth(colIdx))
                                    .padding(horizontal = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        if (colCount > 2) {
            Text(
                "‹ 左右滑動查看完整表格 ›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun ReportLoadingView(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            CircularProgressIndicator(
                color = NutrientColors.Accent,
                modifier = Modifier.size(42.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.report_loading_bg_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReportErrorView(
    error: String,
    hasApiKey: Boolean,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (!hasApiKey) {
            StampButton(
                label = stringResource(R.string.go_to_settings),
                onClick = onOpenSettings,
            )
        } else {
            StampButton(
                label = stringResource(R.string.retry),
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun ApiKeyPromptCard(onOpenSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .border(1.dp, NutrientColors.FieldBorder, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.settings_nvidia_no_key),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.settings_nvidia_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StampButton(
                label = stringResource(R.string.go_to_settings),
                onClick = onOpenSettings,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyDataView(emptyMessage: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatMarkdownInline(text: String) = buildAnnotatedString {
    val tokenRegex = Regex("""(\*\*(.*?)\*\*|`([^`]+)`|\*([^*]+)\*)""")
    var lastIdx = 0
    val matches = tokenRegex.findAll(text).toList()
    for (match in matches) {
        if (match.range.first > lastIdx) {
            append(text.substring(lastIdx, match.range.first))
        }
        val full = match.value
        when {
            full.startsWith("**") && full.endsWith("**") -> {
                val inner = match.groupValues[2]
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(inner)
                }
            }
            full.startsWith("`") && full.endsWith("`") -> {
                val inner = match.groupValues[3]
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(inner)
                }
            }
            full.startsWith("*") && full.endsWith("*") -> {
                val inner = match.groupValues[4]
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inner)
                }
            }
        }
        lastIdx = match.range.last + 1
    }
    if (lastIdx < text.length) {
        append(text.substring(lastIdx))
    }
}
