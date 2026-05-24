package dev.faraway.livetv.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.faraway.livetv.Channel
import dev.faraway.livetv.MainViewModel

// Theme colors
private val AccentColor = Color(0xFF4FC3F7)
private val PanelBg = Color(0xF7101018)
private val CardBg = Color(0xEB1E1E28)
private val SubtleText = Color(0x66FFFFFF)
private val DimText = Color(0x99FFFFFF)
private val ItemShape = RoundedCornerShape(10.dp)
private val FocusBorderColor = AccentColor.copy(alpha = 0.6f)
private val LogoShape = RoundedCornerShape(8.dp)
private val LogoBg = Color(0x14FFFFFF)

/**
 * All overlay UI: channel info popup + channel list panel.
 */
@Composable
fun TVOverlay(viewModel: MainViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Channel switch info (bottom center)
        ChannelInfoOverlay(
            visible = viewModel.isChannelInfoVisible,
            currentChannel = viewModel.currentChannel,
            targetChannel = viewModel.targetChannel,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Channel list panel (left side)
        ChannelListPanel(
            visible = viewModel.isChannelListOpen,
            channels = viewModel.filteredChannels,
            categories = viewModel.categories,
            activeCategoryIndex = viewModel.activeCategoryIndex,
            focusIndex = viewModel.listFocusIndex,
            currentChannelId = viewModel.currentChannel.id
        )

        // Channel scanner (full screen)
        ScannerOverlay(viewModel = viewModel)
    }
}

/**
 * Channel switching overlay - shows current → target channel.
 */
@Composable
fun ChannelInfoOverlay(
    visible: Boolean,
    currentChannel: Channel,
    targetChannel: Channel,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.padding(bottom = 80.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(CardBg, Color(0xF0141420))
                    )
                )
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Column {
                // "Current: XX CCTV-X"
                Text(
                    text = "当前: ${currentChannel.number} ${currentChannel.name}",
                    fontSize = 14.sp,
                    color = SubtleText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Target channel info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Big channel number
                    Text(
                        text = targetChannel.number,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentColor,
                        modifier = Modifier.widthIn(min = 80.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = targetChannel.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (targetChannel.program.isNotEmpty()) {
                            Text(
                                text = "正在播出: ${targetChannel.program}",
                                fontSize = 14.sp,
                                color = SubtleText,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Hint
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(Color(0x14FFFFFF))
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "按 [确认] 切换  ·  [↑↓] 继续选台",
                    fontSize = 12.sp,
                    color = Color(0x59FFFFFF),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Channel list panel - slides in from the left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelListPanel(
    visible: Boolean,
    channels: List<Channel>,
    categories: List<String>,
    activeCategoryIndex: Int,
    focusIndex: Int,
    currentChannelId: Int
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusIndex) {
        if (visible && channels.isNotEmpty()) {
            listState.scrollToItem(focusIndex.coerceIn(0, channels.size - 1))
        }
    }

    val offsetX by animateFloatAsState(
        targetValue = if (visible) 0f else -1f,
        animationSpec = tween(durationMillis = 200),
        label = "panelOffset"
    )

    if (offsetX > -1f) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(420.dp)
                .graphicsLayer {
                    translationX = offsetX * size.width
                }
                .background(PanelBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "频道列表",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "共 ${channels.size} 个频道",
                        fontSize = 13.sp,
                        color = SubtleText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, category ->
                        val isActive = index == activeCategoryIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isActive) Color(0x334FC3F7)
                                    else Color(0x0DFFFFFF)
                                )
                                .then(
                                    if (isActive) Modifier.border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                    else Modifier
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 13.sp,
                                color = if (isActive) AccentColor else DimText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x0FFFFFFF))
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                        ChannelListItem(
                            channel = channel,
                            isFocused = index == focusIndex,
                            isActive = channel.id == currentChannelId
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single channel item in the list.
 */
@Composable
fun ChannelListItem(
    channel: Channel,
    isFocused: Boolean,
    isActive: Boolean
) {
    val bgColor = when {
        isFocused -> Color(0x1A4FC3F7)
        isActive -> Color(0x264FC3F7)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(ItemShape)
            .drawBehind {
                drawRoundRect(color = bgColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            }
            .then(
                if (isFocused) Modifier.border(2.dp, FocusBorderColor, ItemShape)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channel.number,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = SubtleText,
            modifier = Modifier.widthIn(min = 36.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(LogoShape)
                .background(LogoBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.name.first().toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DimText
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            if (channel.program.isNotEmpty()) {
                Text(
                    text = channel.program,
                    fontSize = 12.sp,
                    color = SubtleText,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentColor)
            )
        }
    }
}
