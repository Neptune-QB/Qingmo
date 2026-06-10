"""将原图标缩放到72% 留足安全边距不遮挡文字"""
from PIL import Image
import os

src_icon = r"C:\Users\12730\Desktop\Qingmo\android\app\src\main\res\drawable\qingmo_icon.png"
res_root = r"C:\Users\12730\Desktop\Qingmo\android\app\src\main\res"

mipmap_sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
scale_ratio = 0.72  # 72% 图标大小，四周留28%安全边距

src = Image.open(src_icon).convert("RGBA")
for folder, full_size in mipmap_sizes.items():
    icon_size = int(full_size * scale_ratio)
    resized = src.resize((icon_size, icon_size), resample=Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (full_size, full_size), (0, 0, 0, 0))
    offset = (full_size - icon_size) // 2
    canvas.paste(resized, (offset, offset), mask=resized)
    out_path = os.path.join(res_root, folder, "ic_launcher.png")
    canvas.save(out_path, "PNG")
    print(f"✅ Generated: {out_path} ({full_size}x{full_size})")

# 同步更新drawable下的qingmo_icon到同样缩放版本
final_drawable = os.path.join(res_root, "drawable", "qingmo_icon.png")
biggest = src.resize((192, 192), resample=Image.Resampling.LANCZOS)
canvas_big = Image.new("RGBA", (192, 192), (0,0,0,0))
big_offset = (192 - int(192*scale_ratio)) // 2
biggest_scaled = src.resize((int(192*scale_ratio), int(192*scale_ratio)), resample=Image.Resampling.LANCZOS)
canvas_big.paste(biggest_scaled, (big_offset, big_offset), mask=biggest_scaled)
canvas_big.save(final_drawable, "PNG")
print(f"✅ Updated drawable/qingmo_icon.png 安全边距72%，四周留白不遮挡文字")
