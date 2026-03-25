package com.aether.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.os.Handler
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.common.util.concurrent.MoreExecutors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Android Auto screen — AETHER navigation display with fullscreen viz mode.
 *
 * MAP mode:  Tron-styled perspective grid with live location, compass, scanning pulse.
 *            Now-playing HUD overlay, speed in MPH (US) or KM/H, enhanced grid visuals.
 * VIZ mode:  Fullscreen audio-reactive void pulse visualization.
 *
 * Toggle via "VIZ" / "MAP" button in action strip.
 */
class AetherMapScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var surfaceContainer: SurfaceContainer? = null
    private var locationClient: FusedLocationProviderClient? = null
    private var currentLatLng: LatLng? = null
    private var currentBearing: Float = 0f
    private var currentSpeed: Float = 0f
    private var locationCallback: LocationCallback? = null

    // Mode toggle
    private var vizMode = false
    private val startTime = SystemClock.elapsedRealtime()

    // Now-playing state (fed by MediaController)
    private var nowPlayingTitle: String? = null
    private var nowPlayingArtist: String? = null
    private val useImperial = Locale.getDefault().country == "US"

    // Animation
    private var animationHandler: Handler? = null
    private var animationRunnable: Runnable? = null
    private var animating = false

    // ── Paints ──

    private val bgPaint = Paint().apply { color = Color.BLACK }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A1740")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val gridBrightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#362050")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9D4EDD")
        style = Paint.Style.FILL
    }
    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7B2FBE")
        style = Paint.Style.FILL
        alpha = 60
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9D4EDD")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B76EFF")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B42A0")
        textSize = 20f
        textAlign = Paint.Align.LEFT
    }
    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00DCF5")
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Pre-allocated paints for draw loops (avoid GC pressure at 30fps)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9D4EDD")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00DCF5")
        alpha = 30
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.LEFT
    }
    private val sceneLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f
        textAlign = Paint.Align.RIGHT
    }

    // Now-playing HUD paints
    private val npTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B76EFF")
        textSize = 22f
        textAlign = Paint.Align.LEFT
    }
    private val npArtistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B42A0")
        textSize = 17f
        textAlign = Paint.Align.LEFT
    }
    private val horizonGradPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Reusable float array for HSVToColor (avoid per-frame allocation)
    private val hsvArray = floatArrayOf(0f, 0f, 0f)

    // ── Viz state ──
    private data class VizRing(var phase: Float, val speed: Float, val hueOffset: Float)
    private data class VizParticle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, var maxLife: Float, val hue: Float, val size: Float
    )

    private val vizRings = (0 until 20).map {
        VizRing(it * 0.2f, 0.3f + (it % 5) * 0.05f, it * 18f)
    }.toMutableList()

    private val vizParticles = mutableListOf<VizParticle>()
    private var lastParticleSpawn = 0L

    // ── Template ──

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(if (vizMode) "MAP" else "VIZ")
                    .setOnClickListener { toggleMode() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("AETHER")
                    .setOnClickListener { }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    private fun toggleMode() {
        vizMode = !vizMode
        invalidate()
        drawFrame()
    }

    // ── SurfaceCallback ──

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surfaceContainer = container
        startLocationUpdates()
        startAnimation()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) { drawFrame() }
    override fun onStableAreaChanged(stableArea: Rect) { drawFrame() }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        surfaceContainer = null
        stopAnimation()
        stopLocationUpdates()
    }

    // ── Animation Loop ──

    private fun startAnimation() {
        if (animating) return
        animating = true
        animationHandler = Handler(carContext.mainLooper)
        animationRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (animating) animationHandler?.postDelayed(this, 33) // ~30fps
            }
        }
        animationRunnable?.let { animationHandler?.post(it) }
    }

    private fun stopAnimation() {
        animating = false
        animationRunnable?.let { animationHandler?.removeCallbacks(it) }
    }

    private fun drawFrame() {
        if (vizMode) drawViz() else drawMap()
    }

    /** Update now-playing metadata (called from AetherNavigationSession). */
    fun updateNowPlaying(title: String?, artist: String?) {
        nowPlayingTitle = title
        nowPlayingArtist = artist
    }

    // ── Location ──

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        locationClient = LocationServices.getFusedLocationProviderClient(carContext)

        locationClient?.lastLocation?.addOnSuccessListener { location ->
            location?.let {
                currentLatLng = LatLng(it.latitude, it.longitude)
                currentBearing = it.bearing
                currentSpeed = it.speed
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500)
            .setMinUpdateIntervalMillis(800)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    currentLatLng = LatLng(it.latitude, it.longitude)
                    currentBearing = it.bearing
                    currentSpeed = it.speed
                }
            }
        }

        locationClient?.requestLocationUpdates(request, locationCallback!!, carContext.mainLooper)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { locationClient?.removeLocationUpdates(it) }
        locationCallback = null
        locationClient = null
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MAP MODE — Tron perspective grid with location
    // ══════════════════════════════════════════════════════════════════════

    private fun drawMap() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        val canvas: Canvas = surface.lockCanvas(null) ?: return

        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val cx = w / 2f
            val cy = h * 0.55f
            val t = (SystemClock.elapsedRealtime() - startTime) / 1000f

            canvas.drawColor(Color.BLACK)

            // ── Horizon gradient (subtle purple at vanishing point) ──
            val vanishY = h * 0.15f
            horizonGradPaint.shader = LinearGradient(
                cx, 0f, cx, vanishY + 60f,
                Color.parseColor("#1A0A2E"),
                Color.BLACK,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, vanishY + 60f, horizonGradPaint)
            horizonGradPaint.shader = null

            val horizonAlpha = 40

            // ── Road width scales with speed (wider lines = faster feel) ──
            val speedFactor = min(1f, currentSpeed / 30f) // 0..1 at 0..108 km/h
            val roadWidthBoost = 1f + speedFactor * 1.5f

            // Horizontal lines with perspective
            for (i in 0 until 30) {
                val frac = i / 30f
                val y = vanishY + (h - vanishY) * frac * frac // quadratic spacing
                val spread = frac * w * 0.6f
                val alpha = (frac * horizonAlpha + 15).toInt()
                gridPaint.alpha = min(255, alpha)
                val isMajor = i % 5 == 0
                val paint = if (isMajor) gridBrightPaint else gridPaint
                if (isMajor) {
                    paint.alpha = min(255, alpha + 20)
                    paint.strokeWidth = 1.5f * roadWidthBoost
                } else {
                    paint.strokeWidth = 1f
                }
                canvas.drawLine(cx - spread, y, cx + spread, y, paint)
            }

            // Vertical lines converging to vanishing point
            for (i in -8..8) {
                val bottomX = cx + i * (w / 14f)
                val alpha = max(10, 50 - abs(i) * 5)
                gridPaint.alpha = alpha
                gridPaint.strokeWidth = if (i == 0) 2f * roadWidthBoost else 1f
                canvas.drawLine(cx, vanishY, bottomX, h, gridPaint)
            }
            gridPaint.strokeWidth = 1f // restore

            // ── Scanning pulse rings ──
            val pulsePhase = (t * 0.6f) % 3f
            for (ring in 0 until 3) {
                val rPhase = (pulsePhase + ring * 1f) % 3f
                val radius = rPhase / 3f * min(w, h) * 0.5f
                val alpha = ((1f - rPhase / 3f) * 100).toInt()
                pulsePaint.alpha = max(0, alpha)
                pulsePaint.strokeWidth = 1.5f + (1f - rPhase / 3f) * 2f
                canvas.drawCircle(cx, cy, radius, pulsePaint)
            }

            // ── Location dot ──
            dotGlowPaint.alpha = 40 + (sin(t * 3.0).toFloat() * 20).toInt()
            canvas.drawCircle(cx, cy, 50f, dotGlowPaint)
            canvas.drawCircle(cx, cy, 30f, dotGlowPaint)
            canvas.drawCircle(cx, cy, 14f, dotPaint)

            // ── Compass arrow ──
            val arrowLen = 35f
            val arrowRad = Math.toRadians(currentBearing.toDouble() - 90.0).toFloat()
            val ax = cx + cos(arrowRad) * arrowLen
            val ay = cy + sin(arrowRad) * arrowLen
            canvas.drawLine(cx, cy, ax, ay, arrowPaint)

            // ── HUD overlay ──
            val loc = currentLatLng

            // Coordinates
            if (loc != null) {
                canvas.drawText(
                    String.format("%.4f, %.4f", loc.latitude, loc.longitude),
                    cx, cy + 80f, textPaint
                )
            } else {
                // Temporarily adjust for signal text, then restore
                val savedCyanSize = cyanPaint.textSize
                val savedCyanAlign = cyanPaint.textAlign
                cyanPaint.textSize = 24f
                canvas.drawText("ACQUIRING SIGNAL...", cx, cy + 80f, cyanPaint)
                cyanPaint.textSize = savedCyanSize
                cyanPaint.textAlign = savedCyanAlign
            }

            // Speed — display MPH for US locale, KM/H otherwise
            if (useImperial) {
                val speedMph = (currentSpeed * 2.23694f).toInt()
                cyanPaint.textSize = 48f
                cyanPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("$speedMph", w - 40f, h - 80f, cyanPaint)
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("MPH", w - 40f, h - 50f, labelPaint)
            } else {
                val speedKmh = (currentSpeed * 3.6f).toInt()
                cyanPaint.textSize = 48f
                cyanPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("$speedKmh", w - 40f, h - 80f, cyanPaint)
                labelPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("KM/H", w - 40f, h - 50f, labelPaint)
            }

            // AETHER NAV branding
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.textSize = 22f
            canvas.drawText("AETHER NAV", 24f, 36f, labelPaint)

            // Bearing
            val bearingStr = when {
                currentBearing < 22.5f || currentBearing >= 337.5f -> "N"
                currentBearing < 67.5f -> "NE"
                currentBearing < 112.5f -> "E"
                currentBearing < 157.5f -> "SE"
                currentBearing < 202.5f -> "S"
                currentBearing < 247.5f -> "SW"
                currentBearing < 292.5f -> "W"
                else -> "NW"
            }
            canvas.drawText("HEADING: $bearingStr  ${currentBearing.toInt()}\u00B0", 24f, 64f, labelPaint)

            // ── Now-playing overlay (bottom-left HUD) ──
            val title = nowPlayingTitle
            val artist = nowPlayingArtist
            if (title != null && title.isNotEmpty()) {
                // Truncate long titles for car display
                val displayTitle = if (title.length > 30) title.substring(0, 27) + "..." else title
                val displayArtist = if (artist != null && artist.length > 35) artist.substring(0, 32) + "..." else artist

                npTitlePaint.alpha = (180 + sin(t * 0.8).toFloat() * 40).toInt()
                canvas.drawText("\u266B $displayTitle", 24f, h - 60f, npTitlePaint)

                if (displayArtist != null && displayArtist.isNotEmpty()) {
                    canvas.drawText(displayArtist, 24f, h - 36f, npArtistPaint)
                }
            }

            // Restore shared paints to their default state
            cyanPaint.textSize = 36f
            cyanPaint.textAlign = Paint.Align.CENTER
            labelPaint.textSize = 20f
            labelPaint.textAlign = Paint.Align.LEFT

            // Scan line sweeping
            val scanAngle = (t * 40f) % 360f
            val scanRad = Math.toRadians(scanAngle.toDouble()).toFloat()
            val scanLen = min(w, h) * 0.35f
            canvas.drawLine(cx, cy, cx + cos(scanRad) * scanLen, cy + sin(scanRad) * scanLen, scanPaint)

        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VIZ MODE — Fullscreen void pulse visualization
    // ══════════════════════════════════════════════════════════════════════

    private fun drawViz() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        val canvas: Canvas = surface.lockCanvas(null) ?: return

        try {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val cx = w / 2f
            val cy = h / 2f
            val t = (SystemClock.elapsedRealtime() - startTime) / 1000f
            val maxR = max(w, h) * 0.7f

            canvas.drawColor(Color.BLACK)

            // ── Expanding rings ──
            for (ring in vizRings) {
                ring.phase = (ring.phase + ring.speed * 0.033f) // ~30fps step
                if (ring.phase > 4f) ring.phase -= 4f

                val radius = (ring.phase / 4f) * maxR
                val fade = 1f - ring.phase / 4f
                val alpha = (fade * 180).toInt()
                if (alpha <= 0) continue

                // Color cycles through purple → cyan → magenta
                hsvArray[0] = (270f + ring.hueOffset + t * 15f) % 360f
                hsvArray[1] = 0.75f
                hsvArray[2] = 0.95f
                ringPaint.color = Color.HSVToColor(alpha, hsvArray)
                ringPaint.strokeWidth = 1.5f + fade * 4f
                canvas.drawCircle(cx, cy, radius, ringPaint)
            }

            // ── Central glow ──
            val glowPulse = 0.7f + sin(t * 2.5).toFloat() * 0.3f
            val glowRadius = 60f * glowPulse
            glowPaint.shader = RadialGradient(
                cx, cy, glowRadius,
                Color.parseColor("#9D4EDD"),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)

            // Core dot
            dotPaint.alpha = 255
            canvas.drawCircle(cx, cy, 8f + glowPulse * 4f, dotPaint)

            // ── Particles ──
            val now = SystemClock.elapsedRealtime()
            if (now - lastParticleSpawn > 80 && vizParticles.size < 60) {
                val angle = (Math.random() * 2 * PI).toFloat()
                val speed = 1.5f + (Math.random() * 3f).toFloat()
                vizParticles.add(VizParticle(
                    x = cx, y = cy,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 0f,
                    maxLife = 60f + (Math.random() * 40f).toFloat(),
                    hue = (270f + Math.random().toFloat() * 120f) % 360f,
                    size = 2f + (Math.random() * 3f).toFloat()
                ))
                lastParticleSpawn = now
            }

            val iter = vizParticles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.x += p.vx
                p.y += p.vy
                p.life += 1f
                if (p.life >= p.maxLife || p.x < -20 || p.x > w + 20 || p.y < -20 || p.y > h + 20) {
                    iter.remove()
                    continue
                }
                val fade = 1f - p.life / p.maxLife
                val alpha = (fade * 200).toInt()
                hsvArray[0] = p.hue; hsvArray[1] = 0.7f; hsvArray[2] = 0.9f
                particlePaint.color = Color.HSVToColor(alpha, hsvArray)
                canvas.drawCircle(p.x, p.y, p.size * fade, particlePaint)
            }

            // ── "AETHER" branding ──
            val brandAlpha = (128 + sin(t * 0.5).toFloat() * 60).toInt()
            brandPaint.color = Color.argb(brandAlpha, 107, 66, 160)
            canvas.drawText("AETHER VIZ", 20f, 34f, brandPaint)

            // Scene label
            sceneLabelPaint.color = Color.argb((100 + sin(t).toFloat() * 40).toInt(), 0, 220, 245)
            canvas.drawText("VOID PULSE", w - 20f, 34f, sceneLabelPaint)

        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }
}
