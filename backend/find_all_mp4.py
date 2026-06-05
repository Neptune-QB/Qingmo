import os
root_dir = r"C:\Users\12730\desktop\Qingmo"
found = []
for root, dirs, files in os.walk(root_dir):
    for f in files:
        if f.lower().endswith(".mp4"):
            found.append(os.path.join(root, f))
print(f"🔍 全项目目录搜索到MP4总数: {len(found)}")
for p in found: print(f"  → {p}")
