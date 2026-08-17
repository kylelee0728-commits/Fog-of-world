package com.fogofworld.ui

import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.fogofworld.BuildConfig
import com.fogofworld.data.FogStore
import com.fogofworld.data.Grid
import com.fogofworld.data.MapSource
import com.fogofworld.data.Settings
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import kotlin.math.abs
import com.google.android.gms.maps.MapView as GoogleMapView
import org.osmdroid.views.MapView as OsmMapView

/** 畫面操作地圖只透過這個介面，換地圖引擎時上層不用改 */
interface MapController {
    fun animateTo(lat: Double, lng: Double)
    fun setZoom(zoom: Double)
    fun invalidateFog()

    /** 只有 osmdroid 有；Google 引擎回傳 null（它沒有公開的離線下載 API） */
    fun osmMapView(): OsmMapView?
}

object MapEngine {
    fun googleKey(): String = BuildConfig.MAPS_API_KEY

    /** 有金鑰而且使用者沒有指定自訂圖磚時，就用 Google */
    fun useGoogle(context: Context): Boolean =
        googleKey().isNotBlank() && Settings.tileUrl(context).isBlank()
}

@Composable
fun MapHost(
    modifier: Modifier = Modifier,
    radiusM: Double,
    opacity: Float,
    onReady: (MapController) -> Unit,
) {
    val useGoogle = MapEngine.useGoogle(LocalContext())
    if (useGoogle) GoogleMapHost(modifier, radiusM, opacity, onReady)
    else OsmMapHost(modifier, radiusM, opacity, onReady)
}

@Composable
private fun LocalContext(): Context = androidx.compose.ui.platform.LocalContext.current

// ── Google Maps ────────────────────────────────────────

@Composable
private fun GoogleMapHost(
    modifier: Modifier,
    radiusM: Double,
    opacity: Float,
    onReady: (MapController) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var holder: GoogleMapView? = null

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapsInitializer.initialize(ctx)
            val map = GoogleMapView(ctx).apply { onCreate(Bundle()); onResume() }
            holder = map
            val fogHolder = arrayOfNulls<FogView>(1)
            val googleHolder = arrayOfNulls<GoogleMap>(1)

            val fog = FogView(ctx) {
                val g = googleHolder[0] ?: return@FogView null
                GoogleProjection(g)
            }.apply {
                this.radiusM = radiusM
                this.opacity = opacity
            }
            fogHolder[0] = fog

            map.getMapAsync { google ->
                googleHolder[0] = google
                // 暗色地圖才配得上迷霧；沒有樣式檔時就用 NIGHT 模式
                runCatching {
                    google.setMapStyle(MapStyleOptions.loadRawResourceStyle(ctx, com.fogofworld.R.raw.map_style_night))
                }
                google.uiSettings.apply {
                    isZoomControlsEnabled = false
                    isMapToolbarEnabled = false
                    isMyLocationButtonEnabled = false
                }
                google.setOnCameraMoveListener { fog.invalidate() }
                google.setOnCameraIdleListener { fog.invalidate() }
                val start = FogStore.lastFix
                google.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(start?.lat ?: 25.0339, start?.lng ?: 121.5645),
                        if (start != null) 16f else 3f,
                    )
                )
                onReady(object : MapController {
                    override fun animateTo(lat: Double, lng: Double) {
                        google.animateCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lng)))
                    }

                    override fun setZoom(zoom: Double) {
                        google.animateCamera(CameraUpdateFactory.zoomTo(zoom.toFloat()))
                    }

                    override fun invalidateFog() = fog.invalidate()

                    override fun osmMapView(): OsmMapView? = null
                })
                fog.invalidate()
            }

            FrameLayout(ctx).apply {
                addView(map, FrameLayout.LayoutParams(-1, -1))
                addView(fog, FrameLayout.LayoutParams(-1, -1))
            }
        },
    )

    // MapView 需要手動轉發生命週期
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val map = holder ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                Lifecycle.Event.ON_START -> map.onStart()
                Lifecycle.Event.ON_STOP -> map.onStop()
                Lifecycle.Event.ON_DESTROY -> map.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder?.onDestroy()
        }
    }
}

private class GoogleProjection(private val map: GoogleMap) : FogProjection {
    private val projection = map.projection
    private val region = projection.visibleRegion.latLngBounds

    override fun toScreen(lat: Double, lng: Double, out: Point) {
        val p = projection.toScreenLocation(LatLng(lat, lng))
        out.x = p.x
        out.y = p.y
    }

    override fun south(): Double = region.southwest.latitude
    override fun north(): Double = region.northeast.latitude
    override fun west(): Double = minOf(region.southwest.longitude, region.northeast.longitude)
    override fun east(): Double = maxOf(region.southwest.longitude, region.northeast.longitude)
    override fun centerLat(): Double = map.cameraPosition.target.latitude

    override fun metersPerPixel(): Double {
        val lat = centerLat()
        val lng = map.cameraPosition.target.longitude
        val a = projection.toScreenLocation(LatLng(lat, lng))
        val b = projection.toScreenLocation(LatLng(lat, lng + 0.01))
        return Grid.metersPerPixel(abs(b.x - a.x).toDouble(), lat)
    }
}

// ── osmdroid ───────────────────────────────────────────

@Composable
private fun OsmMapHost(
    modifier: Modifier,
    radiusM: Double,
    opacity: Float,
    onReady: (MapController) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val map = OsmMapView(ctx).apply {
                setTileSource(MapSource.current(ctx))
                setMultiTouchControls(true)
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                val start = FogStore.lastFix
                controller.setZoom(if (start != null) 16.0 else 3.0)
                controller.setCenter(GeoPoint(start?.lat ?: 25.0339, start?.lng ?: 121.5645))
            }
            val fog = FogView(ctx) { OsmProjection(map) }.apply {
                this.radiusM = radiusM
                this.opacity = opacity
            }
            map.addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    fog.invalidate(); return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    fog.invalidate(); return false
                }
            })
            onReady(object : MapController {
                override fun animateTo(lat: Double, lng: Double) {
                    map.controller.animateTo(GeoPoint(lat, lng))
                }

                // osmdroid 的 setZoom 會回傳 double，這裡要吞掉回傳值
                override fun setZoom(zoom: Double) {
                    map.controller.setZoom(zoom)
                }

                override fun invalidateFog() = fog.invalidate()

                override fun osmMapView(): OsmMapView = map
            })
            FrameLayout(ctx).apply {
                addView(map, FrameLayout.LayoutParams(-1, -1))
                addView(fog, FrameLayout.LayoutParams(-1, -1))
            }
        },
    )
}

private class OsmProjection(private val map: OsmMapView) : FogProjection {
    private val projection = map.projection
    private val box = projection.boundingBox
    private val probe = GeoPoint(0.0, 0.0)
    private val tmp = Point()

    override fun toScreen(lat: Double, lng: Double, out: Point) {
        probe.setCoords(lat, lng)
        projection.toPixels(probe, out)
    }

    override fun south(): Double = box.latSouth
    override fun north(): Double = box.latNorth
    override fun west(): Double = box.lonWest
    override fun east(): Double = box.lonEast
    override fun centerLat(): Double = box.centerLatitude

    override fun metersPerPixel(): Double {
        val lat = box.centerLatitude
        toScreen(lat, box.centerLongitude, tmp)
        val x1 = tmp.x
        toScreen(lat, box.centerLongitude + 0.01, tmp)
        return Grid.metersPerPixel(abs(tmp.x - x1).toDouble(), lat)
    }
}
