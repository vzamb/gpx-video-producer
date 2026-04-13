package com.gpxvideo.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gpxvideo.core.model.GeoBounds
import com.gpxvideo.core.model.GpxData
import com.gpxvideo.core.model.GpxPoint
import com.gpxvideo.core.model.GpxSegment
import com.gpxvideo.core.model.GpxTrack
import com.gpxvideo.core.overlayrenderer.OverlayFrameData
import com.gpxvideo.core.overlayrenderer.SvgOverlayRenderer
import com.gpxvideo.core.overlayrenderer.SvgPlaceholderResolver
import com.gpxvideo.core.overlayrenderer.SvgTemplateLoader
import com.gpxvideo.core.overlayrenderer.TemplateFontProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant

/**
 * Instrumented test that verifies the SVG template rendering pipeline:
 * 1. Template discovery from assets
 * 2. SVG loading and parsing
 * 3. Placeholder resolution (text layers + chart/map bounds)
 * 4. Full bitmap rendering with sample data
 *
 * Rendered bitmaps are saved to /sdcard/Download/svg_test_output/ for visual inspection.
 */
@RunWith(AndroidJUnit4::class)
class SvgTemplateRenderTest {

    private lateinit var fontProvider: TemplateFontProvider
    private lateinit var loader: SvgTemplateLoader
    private lateinit var renderer: SvgOverlayRenderer

    private val outputDir = "/sdcard/Download/svg_test_output"

    private val sampleFrameData = OverlayFrameData(
        distance = 12345.0,
        elevation = 450.0,
        elevationGain = 320.0,
        speed = 4.5,
        pace = "5:23",
        heartRate = 162,
        grade = 3.5,
        elapsedTime = 3723000L,
        progress = 0.45f,
        latitude = 45.5,
        longitude = 7.0
    )

    private val sampleGpxData: GpxData by lazy {
        val points = buildSampleGpxPoints()
        GpxData(
            tracks = listOf(GpxTrack(name = "Test Track", segments = listOf(GpxSegment(points)))),
            bounds = GeoBounds(
                minLatitude = points.minOf { it.latitude },
                maxLatitude = points.maxOf { it.latitude },
                minLongitude = points.minOf { it.longitude },
                maxLongitude = points.maxOf { it.longitude }
            ),
            totalDistance = 12345.0,
            totalElevationGain = 320.0,
            totalElevationLoss = 80.0,
            totalDuration = Duration.ofSeconds(3723)
        )
    }

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fontProvider = TemplateFontProvider(context)
        loader = SvgTemplateLoader(context, fontProvider)
        renderer = SvgOverlayRenderer(fontProvider)

        // Ensure output directory exists
        File(outputDir).mkdirs()
    }

    @Test
    fun testTemplateDiscovery() {
        val templates = loader.discoverTemplates()
        assertTrue("Should discover at least 3 SVG templates, found: ${templates.size}", templates.size >= 3)

        val ids = templates.map { it.id }.toSet()
        println("Discovered SVG templates: $ids")

        // Verify expected templates are found
        assertTrue("Should find 'cinematic' template", ids.contains("cinematic"))
        assertTrue("Should find 'hero' template", ids.contains("hero"))
        assertTrue("Should find 'pro_dashboard' template", ids.contains("pro_dashboard"))
    }

    @Test
    fun testCinematic9x16() {
        testTemplate("cinematic", 1080, 1920)
    }

    @Test
    fun testCinematic16x9() {
        testTemplate("cinematic", 1920, 1080)
    }

    @Test
    fun testCinematic4x5() {
        testTemplate("cinematic", 1080, 1350)
    }

    @Test
    fun testCinematic1x1() {
        testTemplate("cinematic", 1080, 1080)
    }

    @Test
    fun testHero9x16() {
        testTemplate("hero", 1080, 1920)
    }

    @Test
    fun testHero16x9() {
        testTemplate("hero", 1920, 1080)
    }

    @Test
    fun testProDashboard9x16() {
        testTemplate("pro_dashboard", 1080, 1920)
    }

    @Test
    fun testProDashboard16x9() {
        testTemplate("pro_dashboard", 1920, 1080)
    }

    @Test
    fun testGraph9x16() {
        testTemplate("graph", 1080, 1920)
    }

    @Test
    fun testCustom9x16() {
        testTemplate("custom", 1080, 1920)
    }

    @Test
    fun testPulp9x16() {
        testTemplate("pulp", 1080, 1920)
    }

    @Test
    fun testPlaceholderResolution() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loaded = loader.loadSync("cinematic", 1080, 1920)
        assertNotNull("Should load cinematic template", loaded)

        val textLayers = SvgPlaceholderResolver.resolveTextLayers(loaded!!.rawSvgString, 1080, 1920)
        println("Cinematic 9x16 text layers: ${textLayers.keys}")
        assertTrue("Should find text layers", textLayers.isNotEmpty())

        // Check that common stat layers are found
        val hasDistance = textLayers.keys.any { it.contains("distance") }
        assertTrue("Should find distance stat layer", hasDistance)

        val placeholders = SvgPlaceholderResolver.resolveFromSvg(loaded.rawSvgString, 1080, 1920)
        println("Cinematic 9x16 placeholders: ${placeholders.keys}")

        // Check chart/map placeholders
        val hasChart = placeholders.keys.any { it.contains("elevation") || it.contains("chart") }
        val hasMap = placeholders.keys.any { it.contains("route") || it.contains("map") }
        println("Has elevation chart: $hasChart, Has route map: $hasMap")
    }

    /**
     * Loads, parses, and renders a template, then saves the bitmap for inspection.
     */
    private fun testTemplate(templateId: String, width: Int, height: Int) {
        val ratio = loader.ratioKey(width, height)

        // 1. Load template
        val loaded = loader.loadSync(templateId, width, height)
        assertNotNull("Should load '$templateId' at ${ratio}", loaded)
        loaded!!

        println("✓ Loaded $templateId ($ratio): SVG length=${loaded.rawSvgString.length}, meta=${loaded.meta.displayName}")

        // 2. Verify placeholder resolution
        val textLayers = SvgPlaceholderResolver.resolveTextLayers(loaded.rawSvgString, width, height)
        println("  Text layers: ${textLayers.keys.joinToString()}")
        assertTrue("$templateId should have text layers", textLayers.isNotEmpty())

        // 3. Render bitmap with a dark background so white text is visible
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bgCanvas = android.graphics.Canvas(bitmap)
        bgCanvas.drawColor(Color.argb(255, 20, 20, 30))  // Dark background to see white text
        
        val overlay = renderer.render(
            svg = loaded.svg,
            svgString = loaded.rawSvgString,
            width = width,
            height = height,
            frameData = sampleFrameData,
            gpxData = sampleGpxData,
            accentColor = Color.argb(204, 68, 138, 255),
            activityTitle = "Morning Run"
        )

        // Composite overlay onto dark background
        bgCanvas.drawBitmap(overlay, 0f, 0f, null)

        assertNotNull("Bitmap should not be null", bitmap)
        assertEquals("Bitmap width", width, bitmap.width)
        assertEquals("Bitmap height", height, bitmap.height)

        // 4. Verify bitmap has content (not all transparent)
        val hasVisiblePixels = checkBitmapHasContent(bitmap)
        assertTrue("$templateId bitmap should have visible content", hasVisiblePixels)

        // 5. Save for visual inspection
        val filename = "${templateId}_${ratio}.png"
        saveBitmap(bitmap, filename)
        println("  ✓ Rendered and saved to $outputDir/$filename (${width}x${height})")
    }

    private fun checkBitmapHasContent(bitmap: Bitmap): Boolean {
        // Sample pixels across the bitmap
        val step = 20
        var nonTransparentCount = 0
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 0) {
                    nonTransparentCount++
                }
            }
        }
        return nonTransparentCount > 10
    }

    private fun saveBitmap(bitmap: Bitmap, filename: String) {
        val file = File(outputDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    companion object {
        fun buildSampleGpxPoints(): List<GpxPoint> {
            val baseTime = Instant.parse("2024-01-15T08:00:00Z")
            val points = mutableListOf<GpxPoint>()
            for (i in 0..100) {
                val t = i / 100.0
                val lat = 45.5 + t * 0.02
                val lon = 7.0 + t * 0.03 + Math.sin(t * Math.PI * 2) * 0.005
                val ele = 300.0 + 150 * Math.sin(t * Math.PI) + 50 * Math.sin(t * Math.PI * 3)
                val timeOffset = (t * 3723).toLong()
                val speed = 3.0 + 2.0 * Math.sin(t * Math.PI * 2)
                points.add(
                    GpxPoint(
                        latitude = lat,
                        longitude = lon,
                        elevation = ele,
                        time = baseTime.plusSeconds(timeOffset),
                        speed = speed,
                        heartRate = (140 + 30 * Math.sin(t * Math.PI * 4)).toInt()
                    )
                )
            }
            return points
        }
    }
}
