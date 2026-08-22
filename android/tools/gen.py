# -*- coding: utf-8 -*-
"""把手繪的 24x24 路徑輸出成 Android vector drawable。"""
import pathlib, sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from icons import ICONS

OUT = pathlib.Path(__file__).resolve().parent.parent / 'app/src/main/res/drawable'
OUT.mkdir(parents=True, exist_ok=True)

TPL = '''<?xml version="1.0" encoding="utf-8"?>
<!-- 手繪圖示，24x24 網格；顏色一律由使用端 tint -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="{path}" />
</vector>
'''

for name, path in ICONS.items():
    (OUT / f'{name}.xml').write_text(TPL.format(path=path), encoding='utf-8')
print(f'產出 {len(ICONS)} 個 drawable')
