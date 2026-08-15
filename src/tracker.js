// 位置來源：真實 GPS，或不用出門也能試玩的模擬行走

import { distanceM, lngScale, M_PER_DEG_LAT } from './util.js';

export class Tracker extends EventTarget {
  constructor() {
    super();
    this.watchId = null;
    this.running = false;
    this.mode = 'gps';
    this.simSpeed = 12;          // 公尺/秒
    this.simPos = null;
    this.simDir = { x: 0, y: 0 }; // 方向鍵
    this.simTarget = null;        // 點擊地圖後的目標
    this._timer = null;
    this._lastTick = 0;
  }

  emit(type, detail) { this.dispatchEvent(new CustomEvent(type, { detail })); }

  start(mode, opts = {}) {
    this.stop(true);
    this.mode = mode;
    this.running = true;
    if (mode === 'sim') {
      this.simPos = opts.start || this.simPos || { lat: 25.0339, lng: 121.5645 };
      this._lastTick = performance.now();
      this._timer = setInterval(() => this._simTick(), 200);
      this.emit('status', { state: 'live', text: '模擬行走中' });
      this._emitPos(this.simPos.lat, this.simPos.lng);
      return;
    }
    if (!('geolocation' in navigator)) {
      this.running = false;
      this.emit('status', { state: 'error', text: '此裝置不支援定位' });
      return;
    }
    this.emit('status', { state: 'wait', text: '定位中…' });
    this.watchId = navigator.geolocation.watchPosition(
      (p) => {
        const c = p.coords;
        this.emit('status', {
          state: 'live',
          text: `定位中 ±${Math.round(c.accuracy)} 公尺`,
          accuracy: c.accuracy,
        });
        this._emitPos(c.latitude, c.longitude, c.altitude, c.accuracy, p.timestamp);
      },
      (err) => {
        const msg = {
          1: '定位權限被拒絕，請到瀏覽器設定開啟',
          2: '目前拿不到定位訊號',
          3: '定位逾時，重新嘗試中',
        }[err.code] || '定位發生問題';
        this.emit('status', { state: 'error', text: msg });
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 20000 }
    );
  }

  stop(silent = false) {
    this.running = false;
    if (this.watchId != null) { navigator.geolocation.clearWatch(this.watchId); this.watchId = null; }
    if (this._timer) { clearInterval(this._timer); this._timer = null; }
    this.simDir = { x: 0, y: 0 };
    this.simTarget = null;
    if (!silent) this.emit('status', { state: 'idle', text: '已暫停探索' });
  }

  _emitPos(lat, lng, alt, accuracy, ts) {
    this.emit('position', {
      lat, lng,
      alt: Number.isFinite(alt) ? alt : undefined,
      accuracy: Number.isFinite(accuracy) ? accuracy : 5,
      ts: ts || Date.now(),
      synthetic: this.mode === 'sim',
    });
  }

  // ── 模擬 ───────────────────────────────────────────
  setSimDirection(x, y) { this.simDir = { x, y }; if (x || y) this.simTarget = null; }
  setSimTarget(lat, lng) { this.simTarget = { lat, lng }; this.simDir = { x: 0, y: 0 }; }

  _simTick() {
    const now = performance.now();
    const dt = Math.min(1, (now - this._lastTick) / 1000);
    this._lastTick = now;

    const p = this.simPos;
    let dx = this.simDir.x, dy = this.simDir.y;

    if (this.simTarget) {
      const d = distanceM(p.lat, p.lng, this.simTarget.lat, this.simTarget.lng);
      if (d < this.simSpeed * dt) { this.simTarget = null; }
      else {
        dy = (this.simTarget.lat - p.lat);
        dx = (this.simTarget.lng - p.lng) * lngScale(p.lat);
        const len = Math.hypot(dx, dy) || 1;
        dx /= len; dy /= len;
      }
    }

    if (!dx && !dy) return;
    const len = Math.hypot(dx, dy) || 1;
    const step = this.simSpeed * dt;
    p.lat += (dy / len) * step / M_PER_DEG_LAT;
    p.lng += (dx / len) * step / (M_PER_DEG_LAT * lngScale(p.lat));
    p.lat = Math.max(-85, Math.min(85, p.lat));
    p.lng = ((p.lng + 180) % 360 + 360) % 360 - 180;
    this._emitPos(p.lat, p.lng, 0, 3);
  }
}
