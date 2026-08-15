// 迷霧圖層：整張畫布蓋滿霧，再依走過的格子把霧「挖掉」
// 作法：先在遮罩畫布上以加成模式畫出所有已探索的圓，
//       再用 destination-out 一次挖穿霧層 —— 重疊處不會出現接縫。

import { cellCenter } from './util.js';
import { CELL_M } from './state.js';

const BUCKET_M = 1280;   // 空間索引的格子大小，只畫視野內的點
const MAX_DPR = 2;

export const FogLayer = L.Layer.extend({
  initialize(state, opts) {
    this._state = state;
    this._radiusM = (opts && opts.radius) || 60;
    this._opacity = (opts && opts.opacity != null) ? opts.opacity : 0.88;
    this._index = new Map();   // bucketKey -> [{lat, lng}]
    this._brush = null;
    this._brushR = -1;
    this._frame = null;
    this.rebuildIndex();

    state.addEventListener('fog', (e) => {
      for (const c of e.detail.cells) this._addToIndex(c.lat, c.lng);
      this.schedule();
    });
    state.addEventListener('reload', () => { this.rebuildIndex(); this.schedule(); });
  },

  // ── Leaflet 介面 ───────────────────────────────────
  onAdd(map) {
    this._map = map;
    const canvas = this._canvas = L.DomUtil.create('canvas', 'leaflet-zoom-animated fog-canvas');
    canvas.style.pointerEvents = 'none';
    canvas.style.position = 'absolute';
    canvas.style.zIndex = 400;
    map.getPanes().overlayPane.appendChild(canvas);
    this._ctx = canvas.getContext('2d');
    this._mask = document.createElement('canvas');
    this._maskCtx = this._mask.getContext('2d');
    this._noise = makeNoiseTile();
    this._reset();
  },

  onRemove() {
    if (this._canvas && this._canvas.parentNode) this._canvas.parentNode.removeChild(this._canvas);
    this._map = null;
  },

  getEvents() {
    return {
      viewreset: this._reset,
      resize: this._reset,
      moveend: this._reset,
      move: this._coverViewport,
      zoomend: this._reset,
      zoomanim: this._animateZoom,
    };
  },

  /** 拖曳中：畫布只在被拖出視野時才重新鋪一次，其餘交給 overlayPane 跟著平移 */
  _coverViewport() {
    if (!this._map || !this._origin || this._resetQueued) return;
    const map = this._map;
    const topLeft = map.layerPointToContainerPoint(this._origin);
    const size = map.getSize();
    const slack = 4;
    if (topLeft.x > -slack || topLeft.y > -slack ||
        topLeft.x + this._dim.x < size.x + slack ||
        topLeft.y + this._dim.y < size.y + slack) {
      this._resetQueued = true;
      requestAnimationFrame(() => { this._resetQueued = false; this._reset(); });
    }
  },

  // ── 設定 ───────────────────────────────────────────
  setRadius(m) { this._radiusM = m; this.schedule(); },
  setOpacity(o) { this._opacity = o; this.schedule(); },

  // ── 空間索引 ───────────────────────────────────────
  rebuildIndex() {
    this._index = new Map();
    for (const key of this._state.cells) {
      const { lat, lng } = cellCenter(key, CELL_M);
      this._addToIndex(lat, lng);
    }
  },

  _addToIndex(lat, lng) {
    const k = bucketKey(lat, lng);
    let arr = this._index.get(k);
    if (!arr) this._index.set(k, arr = []);
    arr.push({ lat, lng });
  },

  // ── 繪製 ───────────────────────────────────────────
  schedule() {
    if (this._frame || !this._map) return;
    this._frame = requestAnimationFrame(() => { this._frame = null; this._render(); });
  },

  _reset() {
    if (!this._map) return;
    const map = this._map;
    const size = map.getSize();
    const pad = L.point(Math.round(size.x * 0.18), Math.round(size.y * 0.18));
    const origin = map.containerPointToLayerPoint(pad.multiplyBy(-1));
    const dim = size.add(pad.multiplyBy(2));
    const dpr = Math.min(MAX_DPR, window.devicePixelRatio || 1);

    this._origin = origin;
    this._dim = dim;
    this._dpr = dpr;

    for (const c of [this._canvas, this._mask]) {
      c.width = Math.round(dim.x * dpr);
      c.height = Math.round(dim.y * dpr);
      c.style.width = dim.x + 'px';
      c.style.height = dim.y + 'px';
    }
    L.DomUtil.setPosition(this._canvas, origin);
    this._render();
  },

  _animateZoom(e) {
    const map = this._map;
    const scale = map.getZoomScale(e.zoom, map.getZoom());
    const offset = map._latLngToNewLayerPoint(map.layerPointToLatLng(this._origin), e.zoom, e.center);
    L.DomUtil.setTransform(this._canvas, offset, scale);
  },

  _render() {
    if (!this._map || !this._ctx) return;
    const map = this._map;
    const ctx = this._ctx;
    const mctx = this._maskCtx;
    const { x: w, y: h } = this._dim;
    const dpr = this._dpr;

    // 畫布固定在圖層座標上，Leaflet 拖曳時會連同 overlayPane 一起平移
    const origin = this._origin;

    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    mctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    mctx.clearRect(0, 0, w, h);

    // 1) 鋪滿迷霧
    ctx.globalCompositeOperation = 'source-over';
    ctx.globalAlpha = this._opacity;
    ctx.fillStyle = '#0e1720';
    ctx.fillRect(0, 0, w, h);
    ctx.globalAlpha = this._opacity * 0.5;
    ctx.fillStyle = ctx.createPattern(this._noise, 'repeat');
    ctx.fillRect(0, 0, w, h);
    ctx.globalAlpha = 1;

    // 2) 在遮罩上畫出所有已探索的圓
    const center = map.getCenter();
    const mpp = metersPerPixel(map, center);
    let radiusPx = this._radiusM / mpp;
    const bounds = map.getBounds().pad(0.25);
    let drawn = 0;

    mctx.globalCompositeOperation = 'lighter';

    if (radiusPx >= 1.6) {
      // 一般視距：逐格畫
      const brush = this._getBrush(radiusPx);
      for (const pts of this._visibleBuckets(bounds)) {
        for (const p of pts) {
          if (p.lat < bounds.getSouth() || p.lat > bounds.getNorth()) continue;
          const pt = map.latLngToLayerPoint(p).subtract(origin);
          if (pt.x < -radiusPx || pt.y < -radiusPx || pt.x > w + radiusPx || pt.y > h + radiusPx) continue;
          mctx.drawImage(brush, pt.x - brush.width / 2, pt.y - brush.height / 2);
          drawn++;
        }
      }
    } else {
      // 遠景：一個索引格畫一團，避免上萬次繪製
      const blobPx = Math.max(3, (BUCKET_M / mpp) * 0.75);
      const brush = this._getBrush(blobPx);
      for (const pts of this._visibleBuckets(bounds)) {
        if (!pts.length) continue;
        const p = pts[0];
        const pt = map.latLngToLayerPoint(p).subtract(origin);
        if (pt.x < -blobPx || pt.y < -blobPx || pt.x > w + blobPx || pt.y > h + blobPx) continue;
        mctx.drawImage(brush, pt.x - brush.width / 2, pt.y - brush.height / 2);
        drawn++;
      }
    }

    // 3) 用遮罩挖穿迷霧
    if (drawn) {
      ctx.globalCompositeOperation = 'destination-out';
      ctx.drawImage(this._mask, 0, 0, w, h);

      // 4) 已探索區域補一層極淡的冷光，讓走過的地方看得出來
      mctx.globalCompositeOperation = 'source-in';
      mctx.fillStyle = '#5fd0c5';
      mctx.fillRect(0, 0, w, h);
      ctx.globalCompositeOperation = 'destination-over';
      ctx.globalAlpha = 0.10;
      ctx.drawImage(this._mask, 0, 0, w, h);
      ctx.globalAlpha = 1;
    }
    ctx.globalCompositeOperation = 'source-over';
  },

  /** 取出視野範圍內的索引格 */
  *_visibleBuckets(bounds) {
    const stepLat = BUCKET_M / 111320;
    const i0 = Math.floor(bounds.getSouth() / stepLat) - 1;
    const i1 = Math.ceil(bounds.getNorth() / stepLat) + 1;
    // 緯度跨度過大（世界視角）時直接掃全部索引，比逐格查快
    if (i1 - i0 > 400) { yield* this._index.values(); return; }
    for (let i = i0; i <= i1; i++) {
      const lat = i * stepLat;
      const stepLng = BUCKET_M / (111320 * Math.max(Math.cos(lat * Math.PI / 180), 0.05));
      const j0 = Math.floor(bounds.getWest() / stepLng) - 1;
      const j1 = Math.ceil(bounds.getEast() / stepLng) + 1;
      if (j1 - j0 > 400) { yield* this._index.values(); return; }
      for (let j = j0; j <= j1; j++) {
        const arr = this._index.get(i + ',' + j);
        if (arr) yield arr;
      }
    }
  },

  /** 依半徑快取一支柔邊筆刷 */
  _getBrush(radiusPx) {
    const r = Math.max(2, Math.round(radiusPx));
    if (this._brush && this._brushR === r) return this._brush;
    const size = r * 2;
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const g = c.getContext('2d');
    const grad = g.createRadialGradient(r, r, 0, r, r, r);
    grad.addColorStop(0, 'rgba(255,255,255,1)');
    grad.addColorStop(0.55, 'rgba(255,255,255,1)');
    grad.addColorStop(0.8, 'rgba(255,255,255,0.55)');
    grad.addColorStop(1, 'rgba(255,255,255,0)');
    g.fillStyle = grad;
    g.fillRect(0, 0, size, size);
    this._brush = c;
    this._brushR = r;
    return c;
  },
});

export function fogLayer(state, opts) { return new FogLayer(state, opts); }

function bucketKey(lat, lng) {
  const stepLat = BUCKET_M / 111320;
  const i = Math.floor(lat / stepLat);
  const stepLng = BUCKET_M / (111320 * Math.max(Math.cos(i * stepLat * Math.PI / 180), 0.05));
  return i + ',' + Math.floor(lng / stepLng);
}

/** 目前視角下 1 像素代表幾公尺 */
function metersPerPixel(map, latlng) {
  const p1 = map.latLngToLayerPoint(latlng);
  const p2 = map.latLngToLayerPoint(L.latLng(latlng.lat, latlng.lng + 0.01));
  const dx = Math.abs(p2.x - p1.x) || 1;
  const meters = 111320 * Math.cos(latlng.lat * Math.PI / 180) * 0.01;
  return Math.max(0.05, meters / dx);
}

/** 迷霧的顆粒感 */
function makeNoiseTile() {
  const c = document.createElement('canvas');
  c.width = c.height = 128;
  const g = c.getContext('2d');
  const img = g.createImageData(128, 128);
  for (let i = 0; i < img.data.length; i += 4) {
    const v = 120 + Math.random() * 70;
    img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
    img.data[i + 3] = 16 + Math.random() * 26;
  }
  g.putImageData(img, 0, 0);
  return c;
}
