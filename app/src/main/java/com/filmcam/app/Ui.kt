package com.filmcam.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val Muted = Color(0xFF9A9A9A)

@Composable
fun FilterRow(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
        itemsIndexed(FilterPresets.all) { index, preset ->
            val isSelected = index == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(index) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(preset.swatch)
                        .border(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) Color.White else Color(0x33FFFFFF),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    preset.name,
                    color = if (isSelected) Color.White else Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(60.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(
            display,
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
        )
    }
}

/** 필터 탭: 필터 목록 + 필터 강도 */
@Composable
fun FilterTab(
    selected: Int,
    onSelect: (Int) -> Unit,
    strength: Float,
    onStrength: (Float) -> Unit,
) {
    Column {
        FilterRow(selected, onSelect)
        LabeledSlider(
            label = "강도",
            value = strength,
            range = 0f..1f,
            display = "${(strength * 100).roundToInt()}%",
            onChange = onStrength,
        )
    }
}

/** 조절 탭: 프리셋 위에 취향대로 더하고 빼는 슬라이더들 */
@Composable
fun AdjustTab(adjust: SnapshotStateList<Float>) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            "초기화",
            color = Color(0xFFF2B77A),
            fontSize = 13.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { for (i in adjust.indices) adjust[i] = 0f },
        )
        Adjust.values().forEach { item ->
            val i = item.ordinal
            LabeledSlider(
                label = item.label,
                value = adjust[i],
                range = -1f..1f,
                display = "${(adjust[i] * 100).roundToInt()}",
                onChange = { adjust[i] = it },
            )
        }
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
