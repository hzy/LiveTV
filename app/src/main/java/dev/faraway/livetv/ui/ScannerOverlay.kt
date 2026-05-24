package dev.faraway.livetv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.faraway.livetv.MainViewModel
import dev.faraway.livetv.scanner.DiscoveredChannel
import dev.faraway.livetv.scanner.ScanPresets

private val Accent = Color(0xFF4FC3F7)
private val Bg = Color(0xF20A0A12)
private val Card = Color(0xEB1E1E28)
private val Subtle = Color(0x99FFFFFF)
private val Dim = Color(0x66FFFFFF)

/**
 * Full-screen channel scanner UI. Activated by pressing MENU on the remote;
 * dismissed by BACK. While running, displays live progress + a list of
 * discovered channels. Accepts D-pad input directly so the host activity
 * doesn't need to know about scanner-specific shortcuts beyond MENU/BACK.
 */
@Composable
fun ScannerOverlay(viewModel: MainViewModel) {
    AnimatedVisibility(
        visible = viewModel.isScannerOpen,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ScannerContent(viewModel)
    }
}

@Composable
private fun ScannerContent(vm: MainViewModel) {
    val presets = ScanPresets.Preset.values()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 36.dp)
        ) {
            Text(
                text = "频道扫描",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "扫描 239.3.1.1 - 239.3.1.254 上的多播 RTP 流",
                fontSize = 13.sp,
                color = Subtle,
            )

            Spacer(Modifier.height(20.dp))
            PresetRow(
                presets = presets,
                selectedIndex = vm.scannerPresetIndex,
                running = vm.scanInProgress,
            )

            Spacer(Modifier.height(20.dp))
            ProgressPanel(
                running = vm.scanInProgress,
                checked = vm.scanChecked,
                total = vm.scanTotal,
                current = vm.scanCurrentTarget,
                foundCount = vm.scanFound.size,
                error = vm.scanError,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (vm.scanInProgress) "实时发现" else "已发现 (${vm.scanFound.size})",
                fontSize = 14.sp,
                color = Subtle,
            )
            Spacer(Modifier.height(8.dp))
            FoundList(items = vm.scanFound)

            Spacer(Modifier.height(16.dp))
            HintBar(running = vm.scanInProgress)
        }
    }
}

@Composable
private fun PresetRow(
    presets: Array<ScanPresets.Preset>,
    selectedIndex: Int,
    running: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.forEachIndexed { i, p ->
            val isSelected = i == selectedIndex
            val borderColor = if (isSelected && !running) Accent else Color(0x1AFFFFFF)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected && !running) Color(0x334FC3F7) else Card)
                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .widthIn(min = 180.dp)
            ) {
                Text(
                    text = p.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected && !running) Accent else Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = p.description, fontSize = 12.sp, color = Subtle)
            }
        }
    }
}

@Composable
private fun ProgressPanel(
    running: Boolean,
    checked: Int,
    total: Int,
    current: String,
    foundCount: Int,
    error: String?,
) {
    val pct = if (total > 0) (checked.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusText = when {
                error != null -> "错误"
                running -> "扫描中"
                total == 0 -> "未开始"
                else -> "完成"
            }
            Text(text = statusText, fontSize = 14.sp, color = Subtle)
            Spacer(Modifier.width(12.dp))
            if (running || total > 0) {
                Text(
                    text = "$checked / $total · 已发现 $foundCount",
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0x14FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(4.dp)
                    .background(Accent)
            )
        }
        if (running && current.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(text = "当前: $current", fontSize = 12.sp, color = Dim)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error, fontSize = 12.sp, color = Color(0xFFFF8888))
        }
    }
}

@Composable
private fun FoundList(items: List<DiscoveredChannel>) {
    val state = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) state.scrollToItem(items.lastIndex)
    }
    if (items.isEmpty()) {
        Text(
            text = "暂无 — 扫描开始后实时显示",
            fontSize = 13.sp,
            color = Dim,
            modifier = Modifier.padding(start = 4.dp)
        )
        return
    }
    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
    ) {
        items(items, key = { "${it.ip}:${it.port}" }) { ch ->
            FoundItem(ch)
        }
    }
}

@Composable
private fun FoundItem(ch: DiscoveredChannel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (ch.source) {
                        "SDT" -> Color(0xFF66BB6A)
                        "内置" -> Accent
                        else -> Color(0xFFFFC107)
                    }
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ch.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Text(
                text = "${ch.ip}:${ch.port} · ${ch.source}",
                fontSize = 11.sp,
                color = Dim,
            )
        }
    }
}

@Composable
private fun HintBar(running: Boolean) {
    val text = if (running) {
        "[OK] 取消扫描   ·   [返回] 关闭"
    } else {
        "[←→] 选择速度   ·   [OK] 开始扫描   ·   [Del] 清除已发现   ·   [返回] 关闭"
    }
    Text(text = text, fontSize = 12.sp, color = Color(0x59FFFFFF))
}
