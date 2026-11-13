package com.filmcam.app

import androidx.compose.ui.graphics.Color

/** 0.5 가 중립(변화 없음)인 색. 그림자/하이라이트 틴트에 사용 */
data class Rgb(val r: Float = 0.5f, val g: Float = 0.5f, val b: Float = 0.5f)

/** 셰이더로 넘어가는 필터 값 전체 */
data class FilterParams(
    val exposure: Float = 0f,      // 밝기 (-1..1)
    val contrast: Float = 0f,      // 대비 (-1..1)
    val saturation: Float = 0f,    // 채도 (-1..1)
    val temperature: Float = 0f,   // 온도: + 따뜻함 / - 차가움
    val tint: Float = 0f,          // 틴트: + 핑크 / - 그린
    val fade: Float = 0f,          // 페이드(블랙 들어올리기) 0..1
    val glow: Float = 0f,          // 글로우(부드러운 빛번짐) 0..1
    val grain: Float = 0f,         // 필름 그레인 0..1
    val vignette: Float = 0f,      // 비네팅 0..1
    val mono: Float = 0f,          // 흑백 정도 0..1
    val split: Float = 0f,         // 그림자/하이라이트 색 입히기 강도 0..1
    val shadowTint: Rgb = Rgb(),
    val highlightTint: Rgb = Rgb(),
)

/** 사용자가 슬라이더로 조절하는 항목 (프리셋 값에 더해짐) */
enum class Adjust(val label: String) {
    EXPOSURE("밝기"),
    CONTRAST("대비"),
    SATURATION("채도"),
    TEMPERATURE("온도"),
    TINT("틴트"),
    FADE("페이드"),
    GLOW("글로우"),
    GRAIN("그레인"),
    VIGNETTE("비네팅"),
}

fun FilterParams.withAdjust(a: List<Float>): FilterParams = copy(
    exposure = (exposure + a[Adjust.EXPOSURE.ordinal]).coerceIn(-1.5f, 1.5f),
    contrast = (contrast + a[Adjust.CONTRAST.ordinal]).coerceIn(-1f, 1f),
    saturation = (saturation + a[Adjust.SATURATION.ordinal]).coerceIn(-1f, 1f),
    temperature = (temperature + a[Adjust.TEMPERATURE.ordinal]).coerceIn(-1.5f, 1.5f),
    tint = (tint + a[Adjust.TINT.ordinal]).coerceIn(-1.5f, 1.5f),
    fade = (fade + a[Adjust.FADE.ordinal]).coerceIn(0f, 1f),
    glow = (glow + a[Adjust.GLOW.ordinal]).coerceIn(0f, 1f),
    grain = (grain + a[Adjust.GRAIN.ordinal]).coerceIn(0f, 1f),
    vignette = (vignette + a[Adjust.VIGNETTE.ordinal]).coerceIn(0f, 1f),
)

data class FilterPreset(val name: String, val swatch: Color, val params: FilterParams)

object FilterPresets {
    val all: List<FilterPreset> = listOf(
        FilterPreset("원본", Color(0xFFBDBDBD), FilterParams()),

        FilterPreset(
            "버터", Color(0xFFF7E3A1),
            FilterParams(
                exposure = 0.10f, contrast = -0.12f, saturation = -0.05f,
                temperature = 0.35f, fade = 0.18f, glow = 0.35f,
                grain = 0.08f, vignette = 0.12f, split = 0.7f,
                shadowTint = Rgb(0.52f, 0.49f, 0.42f),
                highlightTint = Rgb(0.58f, 0.52f, 0.38f),
            ),
        ),
        FilterPreset(
            "크림", Color(0xFFFBEBD7),
            FilterParams(
                exposure = 0.22f, contrast = -0.20f, saturation = -0.15f,
                temperature = 0.15f, tint = 0.10f, fade = 0.22f, glow = 0.50f,
                grain = 0.05f, vignette = 0.05f, split = 0.5f,
                shadowTint = Rgb(0.50f, 0.48f, 0.50f),
                highlightTint = Rgb(0.55f, 0.50f, 0.46f),
            ),
        ),
        FilterPreset(
            "허니", Color(0xFFE9B54F),
            FilterParams(
                exposure = 0.05f, contrast = 0.05f, saturation = 0.15f,
                temperature = 0.50f, tint = 0.05f, fade = 0.10f, glow = 0.25f,
                grain = 0.10f, vignette = 0.20f, split = 0.6f,
                shadowTint = Rgb(0.52f, 0.47f, 0.40f),
                highlightTint = Rgb(0.58f, 0.50f, 0.34f),
            ),
        ),
        FilterPreset(
            "빈티지", Color(0xFFB59A78),
            FilterParams(
                contrast = -0.05f, saturation = -0.20f,
                temperature = 0.25f, tint = 0.05f, fade = 0.40f, glow = 0.20f,
                grain = 0.45f, vignette = 0.35f, split = 0.8f,
                shadowTint = Rgb(0.44f, 0.50f, 0.52f),
                highlightTint = Rgb(0.56f, 0.50f, 0.40f),
            ),
        ),
        FilterPreset(
            "레트로필름", Color(0xFF8E9A72),
            FilterParams(
                contrast = 0.15f, temperature = 0.10f, tint = -0.10f,
                fade = 0.30f, glow = 0.15f, grain = 0.60f, vignette = 0.40f,
                split = 0.8f,
                shadowTint = Rgb(0.45f, 0.52f, 0.50f),
                highlightTint = Rgb(0.55f, 0.52f, 0.42f),
            ),
        ),
        FilterPreset(
            "골든아워", Color(0xFFF2A65A),
            FilterParams(
                exposure = 0.10f, contrast = 0.10f, saturation = 0.20f,
                temperature = 0.60f, tint = 0.10f, fade = 0.08f, glow = 0.40f,
                grain = 0.10f, vignette = 0.25f, split = 0.7f,
                shadowTint = Rgb(0.55f, 0.48f, 0.45f),
                highlightTint = Rgb(0.60f, 0.50f, 0.35f),
            ),
        ),
        FilterPreset(
            "피치", Color(0xFFF8B9A5),
            FilterParams(
                exposure = 0.15f, contrast = -0.10f,
                temperature = 0.20f, tint = 0.30f, fade = 0.15f, glow = 0.40f,
                grain = 0.06f, vignette = 0.10f, split = 0.6f,
                shadowTint = Rgb(0.52f, 0.48f, 0.50f),
                highlightTint = Rgb(0.58f, 0.50f, 0.46f),
            ),
        ),
        FilterPreset(
            "로즈", Color(0xFFF1B5C6),
            FilterParams(
                exposure = 0.10f, contrast = -0.10f, saturation = -0.05f,
                temperature = -0.05f, tint = 0.45f, fade = 0.20f, glow = 0.45f,
                grain = 0.06f, vignette = 0.12f, split = 0.6f,
                shadowTint = Rgb(0.50f, 0.46f, 0.52f),
                highlightTint = Rgb(0.55f, 0.48f, 0.50f),
            ),
        ),
        FilterPreset(
            "민트데이", Color(0xFFB7DCCB),
            FilterParams(
                exposure = 0.15f, contrast = -0.08f, saturation = -0.10f,
                temperature = -0.10f, tint = -0.25f, fade = 0.15f, glow = 0.35f,
                grain = 0.05f, vignette = 0.08f, split = 0.6f,
                shadowTint = Rgb(0.46f, 0.50f, 0.52f),
                highlightTint = Rgb(0.50f, 0.54f, 0.50f),
            ),
        ),
        FilterPreset(
            "블루아워", Color(0xFF8FA9D6),
            FilterParams(
                contrast = 0.05f, saturation = -0.05f,
                temperature = -0.40f, tint = 0.05f, fade = 0.20f, glow = 0.30f,
                grain = 0.12f, vignette = 0.30f, split = 0.7f,
                shadowTint = Rgb(0.44f, 0.48f, 0.56f),
                highlightTint = Rgb(0.50f, 0.52f, 0.56f),
            ),
        ),
        FilterPreset(
            "로모", Color(0xFF6B8F5E),
            FilterParams(
                contrast = 0.30f, saturation = 0.30f, temperature = 0.10f,
                fade = 0.05f, grain = 0.20f, vignette = 0.70f,
            ),
        ),
        FilterPreset(
            "세피아", Color(0xFFA9835A),
            FilterParams(
                mono = 1f, fade = 0.25f, grain = 0.30f, vignette = 0.35f,
                split = 1f,
                shadowTint = Rgb(0.50f, 0.46f, 0.40f),
                highlightTint = Rgb(0.60f, 0.52f, 0.38f),
            ),
        ),
        FilterPreset(
            "소프트모노", Color(0xFF7A7A7A),
            FilterParams(
                contrast = 0.10f, mono = 1f, fade = 0.15f, glow = 0.20f,
                grain = 0.35f, vignette = 0.30f,
            ),
        ),
    )
}
