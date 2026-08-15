package com.fogofworld.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.fogofworld.data.Bucket
import com.fogofworld.data.FogStore
import com.fogofworld.data.Grid
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 蓋在地圖上的迷霧層。
 *
 * 先鋪滿整層霧，再用 DST_OUT 把走過的地方挖掉 —— 與網頁版同一套作法，
 * 重疊處不會有接縫，邊緣是柔的。挖洞需要獨立圖層，所以用 saveLayer。
 */
@SuppressLint("ViewConstructor")
class FogView(context: Context, private val map: MapView) : View(context) {

    var radiusM: Double = 60.0
        set(value) {
            field = value
            invalidate()
        }

    var opacity: Float = 0.88f
        set(value) {
            field = value
            fogPaint.alpha = (value * 255).roundToInt().coerceIn(0, 255)
            invalidate()
        }

    private val fogPaint = Paint().apply {
        color = Color.rgb(0x0e, 0x17, 0x20)
        alpha = 224
    }

    private val noisePaint = Paint().apply { alpha = 26 }

    private val clearPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
    }

    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xF5, 0xC8, 0x6B) }

    private val playerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x10, 0x16, 0x1F)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 26f,
            intArrayOf(Color.argb(150, 245, 200, 107), Color.TRANSPARENT),
            floatArrayOf(0.35f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private var brush: Bitmap? = null
    private var brushRadius = -1

    private val reuse = android.graphics.Point()
    private val probe = GeoPoint(0.0, 0.0)

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        noisePaint.shader = BitmapShader(makeNoise(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val projection = map.projection
        val box = projection.boundingBox
        val centerLat = box.centerLatitude

        // 1) 這個視角下 1 像素等於幾公尺（用投影實測，不假設圖磚大小）
        probe.setCoords(centerLat, box.centerLongitude)
        projection.toPixels(probe, reuse)
        val x1 = reuse.x
        probe.setCoords(centerLat, box.centerLongitude + 0.01)
        projection.toPixels(probe, reuse)
        val mpp = Grid.metersPerPixel(abs(reuse.x - x1).toDouble(), centerLat)
        val radiusPx = (radiusM / mpp).toFloat()

        // 2) 鋪滿迷霧（獨立圖層才能挖洞）
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, fogPaint)
        canvas.drawRect(0f, 0f, w, h, noisePaint)

        // 3) 把走過的地方挖掉
        val latPad = (radiusPx * mpp) / Grid.M_PER_DEG_LAT * 2
        val north = box.latNorth + latPad
        val south = box.latSouth - latPad
        val west = box.lonWest - latPad * 4
        val east = box.lonEast + latPad * 4

        if (radiusPx >= 1.6f) {
            val b = brushFor(radiusPx)
            val half = b.width / 2f
            forEachVisibleBucket(south, north, west, east) { bucket ->
                for (i in 0 until bucket.size) {
                    val lat = bucket.lats[i]
                    if (lat < south || lat > north) continue
                    probe.setCoords(lat, bucket.lngs[i])
                    projection.toPixels(probe, reuse)
                    val x = reuse.x.toFloat()
                    val y = reuse.y.toFloat()
                    if (x < -radiusPx || y < -radiusPx || x > w + radiusPx || y > h + radiusPx) continue
                    canvas.drawBitmap(b, x - half, y - half, clearPaint)
                }
            }
        } else {
            // 遠景：一個索引格畫一團，避免一次畫上萬個圓
            val blobPx = max(3f, ((Grid.BUCKET_M / mpp) * 0.75).toFloat())
            val b = brushFor(blobPx)
            val half = b.width / 2f
            forEachVisibleBucket(south, north, west, east) { bucket ->
                if (bucket.size == 0) return@forEachVisibleBucket
                probe.setCoords(bucket.lats[0], bucket.lngs[0])
                projection.toPixels(probe, reuse)
                val x = reuse.x.toFloat()
                val y = reuse.y.toFloat()
                if (x < -blobPx || y < -blobPx || x > w + blobPx || y > h + blobPx) return@forEachVisibleBucket
                canvas.drawBitmap(b, x - half, y - half, clearPaint)
            }
        }

        canvas.restoreToCount(layer)

        drawLandmarks(canvas, projection, south, north, west, east)
        drawPlayer(canvas, projection)
    }

    /** 地標圖示畫在霧之上：去過的亮、沒去過的暗 */
    private fun drawLandmarks(
        canvas: Canvas,
        projection: org.osmdroid.views.Projection,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
    ) {
        val visited = FogStore.visitedLandmarkIds()
        for (lm in com.fogofworld.data.Landmarks.all(context)) {
            if (lm.lat < south || lm.lat > north) continue
            if (lm.lng < west || lm.lng > east) continue
            probe.setCoords(lm.lat, lm.lng)
            projection.toPixels(probe, reuse)
            val x = reuse.x.toFloat()
            val y = reuse.y.toFloat()
            if (x < 0 || y < 0 || x > width || y > height) continue
            iconPaint.alpha = if (visited.containsKey(lm.id)) 255 else 110
            canvas.drawText(lm.icon, x, y + iconPaint.textSize / 3f, iconPaint)
        }
    }

    /** 玩家位置：一圈暖光加上實心點 */
    private fun drawPlayer(canvas: Canvas, projection: org.osmdroid.views.Projection) {
        val fix = FogStore.lastFix ?: return
        probe.setCoords(fix.lat, fix.lng)
        projection.toPixels(probe, reuse)
        val x = reuse.x.toFloat()
        val y = reuse.y.toFloat()
        // 光暈的漸層以原點為中心，所以平移座標系再畫
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.drawCircle(0f, 0f, 26f, glowPaint)
        canvas.drawCircle(0f, 0f, 11f, playerRingPaint)
        canvas.drawCircle(0f, 0f, 8f, playerPaint)
        canvas.restoreToCount(saved)
    }

    /** 只走訪視野內的索引格；跨度太大時直接掃全部，比逐格查快 */
    private inline fun forEachVisibleBucket(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
        action: (Bucket) -> Unit,
    ) {
        val index = FogStore.index
        if (index.isEmpty()) return
        val stepLat = Grid.BUCKET_M / Grid.M_PER_DEG_LAT
        val i0 = floor(south / stepLat).toInt() - 1
        val i1 = ceil(north / stepLat).toInt() + 1
        if (i1 - i0 > 400) {
            for (b in index.values) action(b)
            return
        }
        for (i in i0..i1) {
            val lat = i * stepLat
            val stepLng = Grid.BUCKET_M / (Grid.M_PER_DEG_LAT * Grid.lngScale(lat))
            val j0 = floor(west / stepLng).toInt() - 1
            val j1 = ceil(east / stepLng).toInt() + 1
            if (j1 - j0 > 400) {
                for (b in index.values) action(b)
                return
            }
            for (j in j0..j1) {
                index[Grid.key(i, j)]?.let(action)
            }
        }
    }

    /** 依半徑快取一支柔邊筆刷 */
    private fun brushFor(radiusPx: Float): Bitmap {
        val r = max(2, radiusPx.roundToInt())
        val cached = brush
        if (cached != null && brushRadius == r) return cached
        val size = r * 2
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r.toFloat(), r.toFloat(), r.toFloat(),
            intArrayOf(Color.WHITE, Color.WHITE, Color.argb(140, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 0.8f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(r.toFloat(), r.toFloat(), r.toFloat(), p)
        brush = bmp
        brushRadius = r
        return bmp
    }

    /** 迷霧的顆粒感 */
    private fun makeNoise(): Bitmap {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(n * n)
        val rnd = java.util.Random(7)
        for (i in pixels.indices) {
            val v = 120 + rnd.nextInt(70)
            val a = 16 + rnd.nextInt(26)
            pixels[i] = Color.argb(a, v, v, v)
        }
        bmp.setPixels(pixels, 0, n, 0, 0, n, n)
        return bmp
    }
}
