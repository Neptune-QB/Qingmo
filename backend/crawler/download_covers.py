"""Simple: visit each book detail page, wait for images to load, extract cover URL from HTML."""
import asyncio
import re
import json
from pathlib import Path
from playwright.async_api import async_playwright

DATA_DIR = Path(__file__).parent / "data"
BROWSER_DATA = DATA_DIR / "browser_data"
COVERS = DATA_DIR / "covers"
COVERS.mkdir(parents=True, exist_ok=True)
BOOK_ID_FILE = Path(__file__).parent.parent / "book_id.txt"


async def main():
    text = BOOK_ID_FILE.read_text("utf-8")
    dramas = []
    for line in text.strip().split("\n"):
        m = re.search(r"book_id=(\d+)", line)
        if m:
            dramas.append({"name": line.split("https://")[0].strip(), "book_id": m.group(1)})

    async with async_playwright() as p:
        browser = await p.chromium.launch_persistent_context(str(BROWSER_DATA), headless=False)
        page = await browser.new_page()

        for i, d in enumerate(dramas):
            path = COVERS / f"{i+1:02d}.jpg"
            url = f"https://kol.fanqieopen.com/page/content/book-detail?tab_type=6&top_tab_genre=-1&key=203_0&genre=203&book_id={d['book_id']}"

            if path.exists():
                print(f"[{i+1}] {d['name']} - exists")
                continue

            try:
                print(f"[{i+1}] {d['name']}...", end=" ")
                # load = wait for all resources including images
                await page.goto(url, wait_until="load", timeout=20000)
                await asyncio.sleep(3)

                html = await page.content()

                # Find cover image URLs in rendered HTML
                cover_urls = set()
                # Pattern 1: img src
                for m in re.finditer(r'<img[^>]+src="([^"]+)"', html):
                    src = m.group(1)
                    if any(k in src.lower() for k in ["cover", "novel", "cdn.fqcdn", "byteimg", "fqnovelpic"]):
                        cover_urls.add(src)
                # Pattern 2: background-image in style
                for m in re.finditer(r'background-image:\s*url\(["\']?([^"\')\s]+)', html):
                    src = m.group(1)
                    if any(k in src.lower() for k in ["cover", "novel", "cdn.fqcdn", "byteimg", "fqnovelpic"]):
                        cover_urls.add(src)

                if cover_urls:
                    cover_url = list(cover_urls)[0]
                    # Fix HTML entities in URL
                    cover_url = cover_url.replace("&quot;", "").replace("&amp;", "&")
                    print(f"found {cover_url[:80]}")

                    # Download via browser page.goto (has auth cookies + referer)
                    resp = await page.goto(cover_url, wait_until="domcontentloaded", timeout=10000)
                    if resp and resp.status == 200:
                        body = await resp.body()
                        path.write_bytes(body)
                        print(f"    -> OK ({len(body)} bytes)")
                        await page.go_back()
                    else:
                        print(f"    -> HTTP {resp.status if resp else 'none'}")
                else:
                    print("no cover in HTML")

                    # Last resort: try CDN URL pattern anyway
                    cdn_url = f"https://cdn.fqcdn.com/novel/{d['book_id']}/cover.jpg"
                    resp = await page.goto(cdn_url, wait_until="domcontentloaded", timeout=10000)
                    if resp and resp.status == 200:
                        body = await resp.body()
                        path.write_bytes(body)
                        print(f"    -> CDN OK ({len(body)} bytes)")
                        await page.go_back()
                    else:
                        print(f"    -> CDN HTTP {resp.status if resp else 'none'}")

            except Exception as e:
                print(f"FAIL: {e}")

        await browser.close()
        print("\nDone.")


if __name__ == "__main__":
    asyncio.run(main())
