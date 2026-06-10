import os
import shutil

res_root = r"C:\Users\12730\Desktop\Qingmo\android\app\src\main\res"
src_icon = r"C:\Users\12730\Desktop\Qingmo\android\app\src\main\res\drawable\qingmo_icon.png"

dirs = [
    "mipmap-anydpi-v26",
    "mipmap-mdpi",
    "mipmap-hdpi",
    "mipmap-xhdpi",
    "mipmap-xxhdpi",
    "mipmap-xxxhdpi",
]

for d in dirs:
    full_path = os.path.join(res_root, d)
    os.makedirs(full_path, exist_ok=True)
    print(f"  Created dir: {full_path}")

# 复制原图到各mipmap文件夹作为ic_launcher.png
for d in dirs[1:]:
    dst = os.path.join(res_root, d, "ic_launcher.png")
    shutil.copy2(src_icon, dst)
    print(f"  Copied icon to: {dst}")

# Adaptive Icon XML (Android 8.0+)
adaptive_icon_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/qingmo_icon"/>
</adaptive-icon>
"""
adaptive_path = os.path.join(res_root, "mipmap-anydpi-v26", "ic_launcher.xml")
with open(adaptive_path, "w", encoding="utf-8") as f:
    f.write(adaptive_icon_xml)
print(f"  Wrote adaptive icon: {adaptive_path}")

# colors.xml 图标背景色
colors_xml_path = os.path.join(res_root, "values", "colors.xml")
colors_content = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1A535C</color>
</resources>
"""
with open(colors_xml_path, "w", encoding="utf-8") as f:
    f.write(colors_content)
print(f"  Wrote colors: {colors_xml_path}")

print("\n✅ App 图标全配置完成！")
