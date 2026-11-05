package com.alican.lumen

import com.alican.lumen.model.FilterCatalog
import com.alican.lumen.model.OriginalPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCatalogTest {

    @Test
    fun `preset ids are unique`() {
        val ids = FilterCatalog.presets.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `catalog covers every group`() {
        val groups = FilterCatalog.presets.map { it.group }.toSet()
        assertTrue(groups.size >= 5)
    }

    @Test
    fun `first preset is the identity look`() {
        assertEquals(OriginalPreset.id, FilterCatalog.presets.first().id)
    }

    @Test
    fun `numeric values stay in a sane range`() {
        FilterCatalog.presets.forEach { preset ->
            val p = preset.params
            assertTrue("${preset.id} exposure", p.exposure in -2f..2f)
            assertTrue("${preset.id} contrast", p.contrast in -1f..1f)
            assertTrue("${preset.id} saturation", p.saturation in -1f..1f)
            assertTrue("${preset.id} vibrance", p.vibrance in -1f..1f)
            assertTrue("${preset.id} fade", p.fade in 0f..1f)
            assertTrue("${preset.id} gamma", p.gamma in 0.5f..1.5f)
            assertTrue("${preset.id} grain", p.grain in 0f..1f)
            assertTrue("${preset.id} grainSize", p.grainSize in 1f..4f)
            assertTrue("${preset.id} vignette", p.vignette in 0f..1f)
            assertTrue("${preset.id} bloom", p.bloom in 0f..1f)
            assertTrue("${preset.id} halation", p.halation in 0f..1f)
            assertTrue("${preset.id} letterbox", p.letterbox in 0f..0.3f)
            assertTrue("${preset.id} mono", p.mono in 0f..1f)
        }
    }

    @Test
    fun `zero intensity is a neutral look`() {
        FilterCatalog.presets.forEach { preset ->
            val p = preset.params.atIntensity(0f)
            assertEquals(preset.id, 0.0, p.exposure.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.contrast.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.saturation.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.grain.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.mono.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.lightLeak.toDouble(), 1e-6)
            assertEquals(preset.id, 0.0, p.letterbox.toDouble(), 1e-6)
            assertEquals(preset.id, 1.0, p.gamma.toDouble(), 1e-6)
            assertTrue(preset.id, !p.dateStamp)
        }
    }

    @Test
    fun `full intensity returns the preset unchanged`() {
        FilterCatalog.presets.forEach { preset ->
            assertEquals(preset.params, preset.params.atIntensity(1f))
        }
    }
}
