package com.alican.lumen.model

/** Signed RGB offset used for split toning / halation / light leaks. */
data class Tint(val r: Float, val g: Float, val b: Float)

/**
 * Complete description of a look. Every preset is rendered by the same GPU
 * pipeline, so adding a new filter never touches the renderer.
 *
 * Units: exposure is in stops, contrast/saturation/vibrance/fade are -1..1
 * (fade 0..1), gamma is a multiplier around 1.0, grain/vignette/bloom/halation/
 * lightLeak/scanline are 0..1, chromatic is a relative radius factor.
 */
data class FilterParams(
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val fade: Float = 0f,
    val gamma: Float = 1f,
    val hueShift: Float = 0f,
    val shadowTint: Tint = Tint(0f, 0f, 0f),
    val shadowAmount: Float = 0f,
    val highlightTint: Tint = Tint(0f, 0f, 0f),
    val highlightAmount: Float = 0f,
    val mono: Float = 0f,
    val channelMix: Tint = Tint(0.299f, 0.587f, 0.114f),
    val grain: Float = 0f,
    val grainSize: Float = 1.5f,
    val vignette: Float = 0f,
    val bloom: Float = 0f,
    val halation: Float = 0f,
    val halationTint: Tint = Tint(1f, 0.22f, 0.10f),
    val chromatic: Float = 0f,
    val lightLeak: Float = 0f,
    val lightLeakTint: Tint = Tint(1f, 0.36f, 0.12f),
    val letterbox: Float = 0f,
    val scanline: Float = 0f,
    val pixelSize: Float = 0f,
    val sharpen: Float = 0f,
    val dateStamp: Boolean = false,
) {
    /**
     * Blends this look toward identity, used by the intensity slider. Booleans
     * and structural effects switch on above half strength.
     */
    fun atIntensity(intensity: Float): FilterParams {
        val t = intensity.coerceIn(0f, 1f)
        if (t >= 1f) return this
        fun f(v: Float) = v * t
        fun blendTint(a: Tint) = Tint(a.r * t, a.g * t, a.b * t)
        return copy(
            exposure = f(exposure),
            temperature = f(temperature),
            tint = f(tint),
            contrast = f(contrast),
            saturation = f(saturation),
            vibrance = f(vibrance),
            fade = f(fade),
            gamma = 1f + (gamma - 1f) * t,
            hueShift = hueShift * t,
            shadowTint = blendTint(shadowTint),
            shadowAmount = f(shadowAmount),
            highlightTint = blendTint(highlightTint),
            highlightAmount = f(highlightAmount),
            mono = f(mono),
            channelMix = Tint(0.299f + (channelMix.r - 0.299f) * t, 0.587f + (channelMix.g - 0.587f) * t, 0.114f + (channelMix.b - 0.114f) * t),
            grain = f(grain),
            grainSize = grainSize,
            vignette = f(vignette),
            bloom = f(bloom),
            halation = f(halation),
            halationTint = Tint(1f + (halationTint.r - 1f) * t, halationTint.g * t, halationTint.b * t),
            chromatic = f(chromatic),
            lightLeak = f(lightLeak),
            lightLeakTint = Tint(1f + (lightLeakTint.r - 1f) * t, lightLeakTint.g * t, lightLeakTint.b * t),
            letterbox = f(letterbox),
            scanline = f(scanline),
            pixelSize = f(pixelSize),
            sharpen = f(sharpen),
            dateStamp = dateStamp && t > 0.5f,
        )
    }
}

enum class FilterGroup(val label: String) {
    ORIGINAL("Orijinal"),
    RETRO("Retro & Analog"),
    VIBRANT("Canlı & Modern"),
    PRO("Leica / Fujifilm"),
    DREAM("Lo-Fi & Rüya"),
    NATURE("Doğa & Sinema"),
}

data class FilterPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val group: FilterGroup,
    /** Two colours used to draw the preset chip preview gradient. */
    val swatch: List<Long>,
    val params: FilterParams,
)

/** The identity look; always the first chip in the editor. */
val OriginalPreset = FilterPreset(
    id = "original",
    name = "Orijinal",
    subtitle = "Filtre yok",
    group = FilterGroup.ORIGINAL,
    swatch = listOf(0xFF2B2B31, 0xFF6E6E78, 0xFFC9C9D2),
    params = FilterParams(),
)
