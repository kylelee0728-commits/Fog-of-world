package com.fogofworld.ui

import android.graphics.Point
import android.os.Bundle
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fogofworld.R
import com.fogofworld.data.FogStore
import com.fogofworld.data.Grid
import com.fogofworld.data.Settings
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import kotlin.math.abs

/** 畫面操作地圖只透過這個介面 */
interface MapController {
    fun animateTo(lat: Double, lng: Double)
    fun setZoom(zoom: Double)
    fun invalidateFog()
    /** 0 夜色、1 衛星、2 地形 */
    fun setMapType(type: Int)
}

/**
 * 地圖固定使用 Google Maps SDK。
 * 向量地圖由 SDK 自己管理快取與載入，不需要（也沒有公開 API）另外做預先下載。
 */
@Composable
fun MapHost(
    modifier: Modifier = Modifier,
    radiusM: Double,
    opacity: Float,
    onReady: (MapController) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val holder = remember { arrayOfNulls<MapView>(1) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapsInitializer.initialize(ctx)
            val map = MapView(ctx).apply { onCreate(Bundle()); onResume() }
            holder[0] = map
            val googleHolder = arrayOfNulls<GoogleMap>(1)

            val fog = FogView(ctx) {
                googleHolder[0]?.let { GoogleProjection(it) }
            }.apply {
                this.radiusM = radiusM
                this.opacity = opacity
            }

            map.getMapAsync { google ->
                googleHolder[0] = google
                applyMapType(ctx, google, Settings.mapType(ctx))
                google.uiSettings.apply {
                    isZoomControlsEnabled = false
                    isMapToolbarEnabled = false
                    isMyLocationButtonEnabled = false
                    isCompassEnabled = false
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

                    override fun setMapType(type: Int) = applyMapType(ctx, google, type)
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
            val map = holder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> map.onStart()
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                Lifecycle.Event.ON_STOP -> map.onStop()
                Lifecycle.Event.ON_DESTROY -> map.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder[0]?.onDestroy()
            holder[0] = null
        }
    }
}

/**
 * 夜色樣式只有在一般地圖上有意義；衛星與地形本身就有影像，
 * 套上暗色樣式反而看不出東西，所以切過去時要把樣式清掉。
 */
private fun applyMapType(ctx: android.content.Context, map: GoogleMap, type: Int) {
    runCatching {
        when (type) {
            1 -> {
                map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                map.setMapStyle(null)
            }
            2 -> {
                map.mapType = GoogleMap.MAP_TYPE_TERRAIN
                map.setMapStyle(null)
            }
            else -> {
                map.mapType = GoogleMap.MAP_TYPE_NORMAL
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(ctx, R.raw.map_style_night))
            }
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
