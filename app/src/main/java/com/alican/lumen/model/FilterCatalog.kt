package com.alican.lumen.model

/**
 * The full look library, derived from the reference guide:
 *  - Retro / analog (35mm, Y2K CCD, CineStill 800T, Lomo)
 *  - Modern vibrant (Vivid, Teal & Orange, Cyberpunk, Tropical)
 *  - Camera colour science (Leica, Fujifilm)
 *  - Lo-fi / dream (VHS, faded memory, dreamy bloom, midnight liminal)
 *  - Cinematic nature (overcast, Super 8, bleach bypass, infrared)
 *
 * All numbers are starting points tuned for the shared shader pipeline; they
 * render at full source resolution with no resampling of the original image.
 */
object FilterCatalog {

    private fun t(r: Float, g: Float, b: Float) = Tint(r, g, b)

    val presets: List<FilterPreset> = listOf(
        OriginalPreset,

        // ---------------------------------------------------------------- RETRO
        FilterPreset(
            id = "fun_saver_98",
            name = "FunSaver '98",
            subtitle = "35mm tek kullanımlık film",
            group = FilterGroup.RETRO,
            swatch = listOf(0xFF3A2418, 0xFFB4703A, 0xFFF2D9A8),
            params = FilterParams(
                exposure = 0.03f, temperature = 0.20f, tint = 0.02f,
                contrast = 0.10f, saturation = 0.06f, vibrance = 0.12f,
                fade = 0.24f, gamma = 1.02f,
                shadowTint = t(0.02f, 0.01f, -0.04f), shadowAmount = 0.25f,
                highlightTint = t(0.07f, 0.03f, -0.02f), highlightAmount = 0.35f,
                grain = 0.48f, grainSize = 1.7f,
                vignette = 0.28f, bloom = 0.14f,
                halation = 0.40f, chromatic = 0.18f,
                lightLeak = 0.38f, dateStamp = true,
            ),
        ),
        FilterPreset(
            id = "y2k_ccd",
            name = "Y2K CCD",
            subtitle = "Kompakt dijital + sert flaş",
            group = FilterGroup.RETRO,
            swatch = listOf(0xFF16202A, 0xFF6C8AA0, 0xFFE7EEF2),
            params = FilterParams(
                exposure = 0.10f, temperature = 0.10f, tint = -0.02f,
                contrast = 0.34f, saturation = -0.08f, vibrance = 0.18f,
                fade = 0.04f, gamma = 1.0f,
                shadowTint = t(-0.02f, -0.01f, 0.06f), shadowAmount = 0.30f,
                highlightTint = t(0.09f, 0.06f, 0.03f), highlightAmount = 0.40f,
                grain = 0.32f, grainSize = 1.2f,
                vignette = 0.34f, bloom = 0.38f,
                halation = 0.12f, chromatic = 0.30f, pixelSize = 2.0f,
            ),
        ),
        FilterPreset(
            id = "cinestill_800t",
            name = "CineStill 800T",
            subtitle = "Tungsten gece filmi",
            group = FilterGroup.RETRO,
            swatch = listOf(0xFF0B1620, 0xFF1E5A63, 0xFFD2432F),
            params = FilterParams(
                exposure = 0.0f, temperature = -0.14f, tint = -0.04f,
                contrast = 0.14f, saturation = -0.04f, vibrance = 0.10f,
                fade = 0.06f, gamma = 0.98f,
                shadowTint = t(-0.10f, 0.02f, 0.12f), shadowAmount = 0.55f,
                highlightTint = t(0.10f, 0.02f, -0.05f), highlightAmount = 0.30f,
                grain = 0.30f, grainSize = 1.6f,
                vignette = 0.22f, bloom = 0.22f,
                halation = 0.75f, chromatic = 0.25f,
            ),
        ),
        FilterPreset(
            id = "lomo",
            name = "Lomo LC-A",
            subtitle = "Aşırı doygun, koyu köşeler",
            group = FilterGroup.RETRO,
            swatch = listOf(0xFF0B0B12, 0xFF2255A4, 0xFF0F8A5F),
            params = FilterParams(
                exposure = -0.04f, temperature = 0.02f,
                contrast = 0.46f, saturation = 0.46f, vibrance = 0.28f,
                fade = 0.0f, gamma = 0.95f,
                shadowTint = t(-0.03f, 0.0f, 0.06f), shadowAmount = 0.25f,
                grain = 0.34f, grainSize = 1.5f,
                vignette = 0.78f, bloom = 0.12f,
                halation = 0.10f, chromatic = 0.55f,
            ),
        ),

        // -------------------------------------------------------------- VIBRANT
        FilterPreset(
            id = "vivid",
            name = "Vivid",
            subtitle = "Akıllı doygunlaştırma",
            group = FilterGroup.VIBRANT,
            swatch = listOf(0xFF0E4C92, 0xFF31A36B, 0xFFFFC93C),
            params = FilterParams(
                contrast = 0.12f, saturation = 0.24f, vibrance = 0.42f,
                sharpen = 0.14f,
            ),
        ),
        FilterPreset(
            id = "teal_orange",
            name = "Teal & Orange",
            subtitle = "Hollywood renk zıtlığı",
            group = FilterGroup.VIBRANT,
            swatch = listOf(0xFF0E3A47, 0xFF2A8FA3, 0xFFFF8A3D),
            params = FilterParams(
                contrast = 0.18f, saturation = 0.04f, vibrance = 0.10f,
                gamma = 1.02f,
                shadowTint = t(-0.12f, 0.0f, 0.12f), shadowAmount = 0.62f,
                highlightTint = t(0.13f, 0.05f, -0.06f), highlightAmount = 0.50f,
                sharpen = 0.12f,
            ),
        ),
        FilterPreset(
            id = "cyberpunk",
            name = "Cyberpunk",
            subtitle = "Neo-Tokyo neon gece",
            group = FilterGroup.VIBRANT,
            swatch = listOf(0xFF120421, 0xFF6A0FA8, 0xFF00E5FF),
            params = FilterParams(
                exposure = -0.06f, temperature = 0.04f, tint = 0.14f,
                contrast = 0.26f, saturation = 0.34f, vibrance = 0.36f,
                fade = 0.02f,
                shadowTint = t(-0.12f, -0.06f, 0.20f), shadowAmount = 0.70f,
                highlightTint = t(0.16f, 0.0f, 0.16f), highlightAmount = 0.50f,
                grain = 0.12f, bloom = 0.30f,
                halation = 0.28f, halationTint = t(1f, 0.1f, 0.55f),
                chromatic = 0.35f,
            ),
        ),
        FilterPreset(
            id = "tropical_pop",
            name = "Tropical Pop",
            subtitle = "Yaz & yüksek kontrast",
            group = FilterGroup.VIBRANT,
            swatch = listOf(0xFF00627A, 0xFF00BFA6, 0xFFFFD166),
            params = FilterParams(
                exposure = 0.08f, temperature = 0.05f,
                contrast = 0.22f, saturation = 0.36f, vibrance = 0.46f,
                fade = 0.02f,
                highlightTint = t(0.06f, 0.05f, 0.0f), highlightAmount = 0.20f,
                sharpen = 0.16f,
            ),
        ),

        // ------------------------------------------------------------------ PRO
        FilterPreset(
            id = "leica_authentic",
            name = "Leica Authentic",
            subtitle = "Doğal mikro kontrast",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF1A1A1A, 0xFF7A3B2E, 0xFFD8CFC0),
            params = FilterParams(
                contrast = 0.24f, saturation = 0.04f, vibrance = 0.10f,
                fade = 0.02f,
                shadowTint = t(0.0f, 0.01f, 0.03f), shadowAmount = 0.20f,
                highlightTint = t(0.03f, 0.01f, 0.0f), highlightAmount = 0.15f,
                sharpen = 0.22f,
            ),
        ),
        FilterPreset(
            id = "leica_vibrant",
            name = "Leica Vibrant",
            subtitle = "Canlı Leica dokusu",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF12212B, 0xFF8E2C24, 0xFFE8C46A),
            params = FilterParams(
                contrast = 0.26f, saturation = 0.18f, vibrance = 0.24f,
                highlightTint = t(0.04f, 0.02f, 0.0f), highlightAmount = 0.20f,
                sharpen = 0.22f,
            ),
        ),
        FilterPreset(
            id = "leica_monochrom",
            name = "Leica Monochrom",
            subtitle = "Kadifemsi siyah-beyaz",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF000000, 0xFF4A4A4A, 0xFFEDEDED),
            params = FilterParams(
                contrast = 0.32f, gamma = 0.94f,
                mono = 1f, channelMix = t(0.34f, 0.46f, 0.20f),
                grain = 0.08f, vignette = 0.12f, sharpen = 0.18f,
            ),
        ),
        FilterPreset(
            id = "leica_bw_natural",
            name = "BW Natural",
            subtitle = "Yumuşak gri geçişler",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF1E1E1E, 0xFF767676, 0xFFDCDCDC),
            params = FilterParams(
                contrast = 0.10f, gamma = 1.02f,
                mono = 1f, channelMix = t(0.30f, 0.59f, 0.11f),
                grain = 0.05f, sharpen = 0.10f,
            ),
        ),
        FilterPreset(
            id = "leica_bw_high",
            name = "BW High Contrast",
            subtitle = "Sert sokak siyah-beyazı",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF000000, 0xFF333333, 0xFFFFFFFF),
            params = FilterParams(
                contrast = 0.58f, gamma = 0.90f,
                mono = 1f, channelMix = t(0.32f, 0.52f, 0.16f),
                grain = 0.14f, vignette = 0.18f, sharpen = 0.22f,
            ),
        ),
        FilterPreset(
            id = "fuji_provia",
            name = "Fuji Provia",
            subtitle = "Dengeli standart",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF1C3F5E, 0xFF5C8A4A, 0xFFE7DCC0),
            params = FilterParams(
                contrast = 0.10f, saturation = 0.06f, vibrance = 0.12f,
                sharpen = 0.10f,
            ),
        ),
        FilterPreset(
            id = "fuji_velvia",
            name = "Fuji Velvia",
            subtitle = "Manzara doygunluğu",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF0A3D2E, 0xFF2E8B57, 0xFF1B5FA8),
            params = FilterParams(
                contrast = 0.20f, saturation = 0.46f, vibrance = 0.36f,
                shadowTint = t(-0.03f, -0.01f, 0.05f), shadowAmount = 0.20f,
                highlightTint = t(0.04f, 0.02f, 0.0f), highlightAmount = 0.20f,
                sharpen = 0.16f,
            ),
        ),
        FilterPreset(
            id = "fuji_classic_chrome",
            name = "Classic Chrome",
            subtitle = "Düşük doygun dergi havası",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF2A2E33, 0xFF6B6E63, 0xFFC2BEB2),
            params = FilterParams(
                contrast = 0.28f, saturation = -0.30f, vibrance = -0.06f,
                gamma = 0.98f,
                shadowTint = t(-0.02f, 0.0f, 0.04f), shadowAmount = 0.40f,
                highlightTint = t(0.03f, 0.03f, 0.0f), highlightAmount = 0.25f,
                sharpen = 0.10f,
            ),
        ),
        FilterPreset(
            id = "fuji_astia",
            name = "Fuji Astia",
            subtitle = "Yumuşak portre",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF6B4A3A, 0xFFB98A72, 0xFFF0DCCB),
            params = FilterParams(
                contrast = 0.05f, saturation = 0.08f, vibrance = 0.14f,
                gamma = 1.03f,
                highlightTint = t(0.05f, 0.02f, 0.01f), highlightAmount = 0.25f,
                sharpen = 0.05f,
            ),
        ),
        FilterPreset(
            id = "fuji_classic_neg",
            name = "Classic Neg",
            subtitle = "Sıcak aile fotoğrafı",
            group = FilterGroup.PRO,
            swatch = listOf(0xFF3A2A1E, 0xFF9C6B3F, 0xFFE3C79A),
            params = FilterParams(
                exposure = 0.02f, temperature = 0.13f,
                contrast = 0.22f, saturation = 0.10f, vibrance = 0.12f,
                fade = 0.08f,
                shadowTint = t(0.04f, 0.0f, -0.03f), shadowAmount = 0.35f,
                highlightTint = t(0.07f, 0.03f, -0.01f), highlightAmount = 0.35f,
                grain = 0.16f, grainSize = 1.6f,
            ),
        ),

        // ---------------------------------------------------------------- DREAM
        FilterPreset(
            id = "vhs_split",
            name = "VHS Split",
            subtitle = "RGB kayması & kaset paraziti",
            group = FilterGroup.DREAM,
            swatch = listOf(0xFF141B2E, 0xFF2E5C7A, 0xFFB04A6E),
            params = FilterParams(
                exposure = -0.05f, temperature = 0.06f,
                contrast = 0.06f, saturation = 0.10f, vibrance = 0.10f,
                fade = 0.26f, gamma = 1.02f,
                shadowTint = t(-0.05f, 0.02f, 0.07f), shadowAmount = 0.35f,
                grain = 0.36f, grainSize = 1.0f,
                vignette = 0.26f, bloom = 0.16f,
                chromatic = 1.1f, scanline = 0.16f,
            ),
        ),
        FilterPreset(
            id = "faded_memory",
            name = "Faded Memory",
            subtitle = "Soluk Kodachrome",
            group = FilterGroup.DREAM,
            swatch = listOf(0xFF4A4038, 0xFF8C7A66, 0xFFD9C9AE),
            params = FilterParams(
                exposure = -0.22f, contrast = -0.30f,
                saturation = -0.34f, vibrance = -0.10f,
                fade = 0.46f, gamma = 1.05f,
                shadowTint = t(-0.02f, -0.01f, 0.05f), shadowAmount = 0.35f,
                highlightTint = t(0.06f, 0.05f, 0.03f), highlightAmount = 0.40f,
                grain = 0.26f, grainSize = 1.8f, vignette = 0.18f,
            ),
        ),
        FilterPreset(
            id = "dreamy_bloom",
            name = "Dreamy Bloom",
            subtitle = "Buğulu rüya sekansı",
            group = FilterGroup.DREAM,
            swatch = listOf(0xFF2A2440, 0xFF8E7CB8, 0xFFF4E3D7),
            params = FilterParams(
                exposure = 0.06f, contrast = -0.12f,
                saturation = 0.04f, vibrance = 0.12f,
                fade = 0.12f,
                highlightTint = t(0.07f, 0.04f, 0.02f), highlightAmount = 0.35f,
                bloom = 0.70f, halation = 0.30f,
                grain = 0.10f, vignette = 0.12f,
            ),
        ),
        FilterPreset(
            id = "midnight_liminal",
            name = "Midnight Liminal",
            subtitle = "Gece mavisi & soluk sarı",
            group = FilterGroup.DREAM,
            swatch = listOf(0xFF080C18, 0xFF1B2C4C, 0xFFD8C48A),
            params = FilterParams(
                exposure = -0.34f, contrast = -0.10f,
                saturation = -0.18f, vibrance = 0.06f,
                fade = 0.18f,
                shadowTint = t(-0.10f, 0.0f, 0.24f), shadowAmount = 0.75f,
                highlightTint = t(0.16f, 0.12f, -0.02f), highlightAmount = 0.60f,
                grain = 0.22f, grainSize = 1.5f,
                vignette = 0.38f, bloom = 0.26f,
            ),
        ),

        // --------------------------------------------------------------- NATURE
        FilterPreset(
            id = "overcast_moody",
            name = "Overcast Moody",
            subtitle = "Sisli, hüzünlü doğa",
            group = FilterGroup.NATURE,
            swatch = listOf(0xFF2E3833, 0xFF6B7A6E, 0xFFB9C2B6),
            params = FilterParams(
                exposure = -0.12f, contrast = -0.08f,
                saturation = -0.32f, vibrance = -0.12f,
                fade = 0.12f,
                shadowTint = t(-0.02f, 0.04f, 0.03f), shadowAmount = 0.45f,
                highlightTint = t(0.0f, 0.02f, 0.02f), highlightAmount = 0.20f,
                grain = 0.14f, grainSize = 1.7f, vignette = 0.15f,
            ),
        ),
        FilterPreset(
            id = "super8",
            name = "Super 8mm",
            subtitle = "Vintage selüloit film",
            group = FilterGroup.NATURE,
            swatch = listOf(0xFF1E1509, 0xFF9A7A3C, 0xFFE8D4A0),
            params = FilterParams(
                exposure = -0.06f, temperature = 0.22f,
                contrast = 0.12f, saturation = -0.12f, vibrance = 0.06f,
                fade = 0.32f, gamma = 1.02f,
                shadowTint = t(0.05f, 0.02f, -0.04f), shadowAmount = 0.35f,
                grain = 0.58f, grainSize = 2.1f,
                vignette = 0.32f, bloom = 0.16f,
                halation = 0.28f, chromatic = 0.35f,
                lightLeak = 0.28f, letterbox = 0.11f,
                scanline = 0.08f, dateStamp = true,
            ),
        ),
        FilterPreset(
            id = "bleach_bypass",
            name = "Bleach Bypass",
            subtitle = "Gümüş banyosu, sert kontrast",
            group = FilterGroup.NATURE,
            swatch = listOf(0xFF1A1C1E, 0xFF5E6468, 0xFFCFD3D4),
            params = FilterParams(
                exposure = -0.04f,
                contrast = 0.58f, saturation = -0.58f, vibrance = -0.20f,
                gamma = 0.90f,
                shadowTint = t(-0.02f, 0.0f, 0.03f), shadowAmount = 0.20f,
                grain = 0.12f, sharpen = 0.20f, vignette = 0.14f,
            ),
        ),
        FilterPreset(
            id = "infrared",
            name = "Infrared IR",
            subtitle = "Pembe yapraklar, lacivert gök",
            group = FilterGroup.NATURE,
            swatch = listOf(0xFF0B1230, 0xFF9A3F6A, 0xFFF2B6C6),
            params = FilterParams(
                contrast = 0.30f, saturation = 0.24f, vibrance = 0.20f,
                hueShift = 2.60f,
                shadowTint = t(-0.06f, -0.02f, 0.16f), shadowAmount = 0.55f,
                highlightTint = t(0.16f, 0.0f, 0.10f), highlightAmount = 0.45f,
                bloom = 0.32f, halation = 0.20f,
                grain = 0.10f, vignette = 0.20f,
            ),
        ),
    )

    fun byId(id: String?): FilterPreset =
        presets.firstOrNull { it.id == id } ?: OriginalPreset
}
