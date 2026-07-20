package ru.fire_core.xauplayer.ui.screens.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ru.fire_core.xauplayer.ui.viewmodel.AccountViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

@Composable
fun AccountTab(
    modifier: Modifier = Modifier, 
    vm: AccountViewModel = hiltViewModel(), 
    settingsVm: ru.fire_core.xauplayer.ui.viewmodel.SettingsViewModel = hiltViewModel(),
    onLogout: () -> Unit = {}
) {
    val devices by vm.devices.collectAsState()
    val activity by vm.activity.collectAsState()
    val account by vm.account.collectAsState()
    val accentColorHex by settingsVm.accentColor.collectAsState()
    val offlineMode by settingsVm.offlineMode.collectAsState()
    val sessionRefreshResult by settingsVm.sessionRefreshResult.collectAsState()

    // Определяем оффлайн/служебный аккаунт для прикольной подписи
    val isOfflineAccount = offlineMode ||
        account?.email == ru.fire_core.xauplayer.core.config.AppConfig.SERVICE_ACCOUNT_EMAIL
    val offlineCaption = remember { ru.fire_core.xauplayer.core.humor.FunPhrases.offlineCaption() }

    LaunchedEffect(Unit) {
        vm.load()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp), 
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок с кнопками
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account?.name ?: (account?.email ?: "Аккаунт"), 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.SemiBold
                )
                account?.email?.let { 
                    Text(it, style = MaterialTheme.typography.bodyMedium) 
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.switchAccount { onLogout() } }
                ) {
                    Text("Другой аккаунт")
                }
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), 
                    onClick = { vm.logout { onLogout() } }
                ) {
                    Text("Выйти", color = MaterialTheme.colorScheme.onError)
                }
            }
        }
        
        // Прикольная подпись для оффлайн/служебного аккаунта
        if (isOfflineAccount) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    offlineCaption,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // Оффлайн-режим и обновление сессии
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { settingsVm.refreshSession() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Обновить сессию")
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Оффлайн", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = offlineMode,
                    onCheckedChange = { settingsVm.setOfflineMode(it) }
                )
            }
        }
        sessionRefreshResult?.let { ok ->
            Text(
                if (ok) "Сессия обновлена" else "Не удалось обновить сессию",
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        // Сетка активности
        Text(
            "Активность прослушивания",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        ActivityGrid(
            activity = activity,
            accentColorHex = accentColorHex,
            modifier = Modifier.fillMaxWidth()
        )
        
        HorizontalDivider()
        
        // Устройства
        Text(
            "Устройства",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { d ->
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween, 
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.deviceName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Последний вход: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(d.lastLogin))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { vm.revoke(d.id) }) { 
                        Text("Выкинуть") 
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityGrid(
    activity: Map<String, Int>,
    accentColorHex: String,
    modifier: Modifier = Modifier
) {
    // Парсим акцентный цвет
    val accentColor = remember(accentColorHex) {
        try {
            Color(android.graphics.Color.parseColor(accentColorHex))
        } catch (e: Exception) {
            Color(0xFF1DB954) // Spotify green по умолчанию
        }
    }
    
    // Вычисляем максимальное значение для нормализации
    val maxValue = remember(activity) {
        activity.values.maxOrNull() ?: 1
    }
    
    // MaterialTheme.colorScheme используется в composable контексте
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    
    // Вспомогательная функция для вычисления уровня интенсивности
    fun getIntensityLevel(value: Int): Int {
        return when {
            value == 0 -> 0
            maxValue == 0 -> 0
            else -> {
                // Вычисляем уровень на основе процента от максимума
                val percent = value.toFloat() / maxValue.toFloat()
                when {
                    percent <= 0.25f -> 1
                    percent <= 0.5f -> 2
                    percent <= 0.75f -> 3
                    else -> 4
                }
            }
        }
    }
    
    // Вспомогательная функция для получения цвета уровня
    fun getColorForLevel(level: Int): Color {
        return when (level) {
            0 -> surfaceVariantColor.copy(alpha = 0.2f) // Нет активности
            1 -> accentColor.copy(alpha = 0.3f)  // Уровень 1: минимальная яркость
            2 -> accentColor.copy(alpha = 0.5f)  // Уровень 2: средняя яркость
            3 -> accentColor.copy(alpha = 0.7f)  // Уровень 3: высокая яркость
            4 -> accentColor.copy(alpha = 1.0f)  // Уровень 4: максимальная яркость
            else -> surfaceVariantColor.copy(alpha = 0.2f)
        }
    }
    
    // Генерируем список дат за последний год (53 недели)
    // В GitHub сетке: недели идут слева направо, дни недели сверху вниз
    val weeks = remember {
        val today = LocalDate.now()
        val weeksList = mutableListOf<List<LocalDate>>()
        
        // Начинаем с понедельника ~52 недели назад (чтобы покрыть год)
        val weekFields = WeekFields.of(Locale.getDefault())
        val startDate = today.minusWeeks(52)
            .with(weekFields.dayOfWeek(), 1) // Понедельник
        
        var currentWeekStart = startDate
        while (currentWeekStart.isBefore(today.plusWeeks(1))) {
            val week = (0..6).map { currentWeekStart.plusDays(it.toLong()) }
            weeksList.add(week)
            currentWeekStart = currentWeekStart.plusWeeks(1)
        }
        
        weeksList
    }
    
    // Форматируем даты для поиска в activity
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    
    // Scroll states для синхронизации месяцев и сетки
    val gridScrollState = rememberScrollState()
    val monthScrollState = rememberScrollState()
    
    // Синхронизация: когда прокручивается сетка, прокручиваются и месяцы
    LaunchedEffect(gridScrollState.value) {
        if (kotlin.math.abs(monthScrollState.value - gridScrollState.value) > 1) {
            monthScrollState.scrollTo(gridScrollState.value)
        }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Подписи месяцев (верх) - отображаются над сеткой недель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp)
                .horizontalScroll(monthScrollState),
        ) {
            var lastMonth = -1
            weeks.forEachIndexed { weekIndex, week ->
                val firstDayOfWeek = week[0]
                val currentMonth = firstDayOfWeek.monthValue
                
                // Показываем месяц только когда он меняется
                if (currentMonth != lastMonth) {
                    Text(
                        text = firstDayOfWeek.format(DateTimeFormatter.ofPattern("MMM", Locale("ru"))),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    lastMonth = currentMonth
                } else {
                    // Добавляем отступ для выравнивания (12.dp на неделю + 4.dp между неделями = 16.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
        }
        
        // Сетка дней (недели горизонтально, дни недели вертикально)
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Дни недели (слева, фиксированные)
            Column(
                modifier = Modifier.padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                dayLabels.forEach { day ->
                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.height(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Недели (колонки, с прокруткой) - основной прокручиваемый контейнер
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(gridScrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                weeks.forEach { week ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        week.forEach { date ->
                            val dateStr = date.format(dateFormatter)
                            val minutes = activity[dateStr] ?: 0
                            val level = getIntensityLevel(minutes)
                            val color = getColorForLevel(level)
                            
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
        
        // Легенда
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Меньше",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (0..4).forEach { level ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(getColorForLevel(level), RoundedCornerShape(2.dp))
                        )
                    }
                }
                Text(
                    "Больше",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Подписи с диапазонами времени
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Значения оттенков:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val ranges = remember(maxValue) {
                    listOf(
                        0 to "Нет активности",
                        1 to "0 - ${(maxValue * 0.25).toInt()} мин",
                        2 to "${(maxValue * 0.25).toInt() + 1} - ${(maxValue * 0.5).toInt()} мин",
                        3 to "${(maxValue * 0.5).toInt() + 1} - ${(maxValue * 0.75).toInt()} мин",
                        4 to "${(maxValue * 0.75).toInt() + 1}+ мин"
                    )
                }
                ranges.forEach { (level, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(getColorForLevel(level), RoundedCornerShape(2.dp))
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}


