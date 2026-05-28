"""
Crawler v2: API interception + DOM extraction for fanqieopen.com book detail pages.
"""
import asyncio
import json
import os
import re
import httpx
from pathlib import Path
from playwright.async_api import async_playwright

DATA_DIR = Path(__file__).parent / "data"
BROWSER_DATA = DATA_DIR / "browser_data"
COVERS_DIR = DATA_DIR / "covers"
VIDEOS_DIR = DATA_DIR / "videos"
BOOK_ID_FILE = Path(__file__).parent.parent / "book_id.txt"

DETAIL_URL = "https://kol.fanqieopen.com/page/content/book-detail"


def parse_book_ids() -> list[dict]:
    results = []
    text = BOOK_ID_FILE.read_text(encoding="utf-8")
    for line in text.strip().split("\n"):
        if not line.strip():
            continue
        m = re.search(r"book_id=(\d+)", line)
        if m:
            name = line.split("https://")[0].strip()
            results.append({"name": name, "book_id": m.group(1)})
    return results


async def download_file(client: httpx.AsyncClient, url: str, path: Path):
    if path.exists():
        print(f"    [skip] {path.name}")
        return
    try:
        r = await client.get(url, timeout=30)
        r.raise_for_status()
        path.write_bytes(r.content)
        print(f"    [cover] {path.name} saved")
    except Exception as e:
        print(f"    [cover fail] {e}")


async def run():
    Path(BROWSER_DATA).mkdir(parents=True, exist_ok=True)
    Path(COVERS_DIR).mkdir(parents=True, exist_ok=True)
    Path(VIDEOS_DIR).mkdir(parents=True, exist_ok=True)

    dramas = parse_book_ids()
    print(f"Found {len(dramas)} dramas.\n")

    async with async_playwright() as p:
        browser = await p.chromium.launch_persistent_context(str(BROWSER_DATA), headless=False)
        page = await browser.new_page()

        # --- API interceptor ---
        api_data = []

        async def on_response(response):
            url = response.url
            if "/api/" in url and response.headers.get("content-type", "").startswith("application/json"):
                try:
                    body = await response.json()
                    api_data.append({"url": url, "body": body})
                except Exception:
                    pass

        page.on("response", on_response)

        print("Opening fanqieopen.com — detecting login...")
        await page.goto("https://kol.fanqieopen.com/page/content", wait_until="domcontentloaded")
        await asyncio.sleep(3)
        for _ in range(120):
            logged_in = await page.evaluate("""() => {
                const pw = document.querySelector('input[type="password"]');
                const user = document.querySelector('[class*="avatar"], [class*="user"], [class*="profile"], [class*="login"]');
                return !pw || !!user;
            }""")
            if logged_in:
                print("Login OK.\n")
                break
            await asyncio.sleep(2)
            if _ % 15 == 0:
                print("  Waiting for login...")

        # --- Scrape each drama ---
        for i, drama in enumerate(dramas):
            name = drama["name"]
            book_id = drama["book_id"]
            full_url = f"{DETAIL_URL}?tab_type=6&top_tab_genre=-1&key=203_0&genre=203&book_id={book_id}"
            print(f"[{i+1}/{len(dramas)}] {name} (book_id={book_id})")

            api_data.clear()

            try:
                await page.goto(full_url, wait_until="domcontentloaded", timeout=20000)
                await asyncio.sleep(5)  # let API calls complete

                # Try API data first
                book_info = {}
                for resp in api_data:
                    body = resp["body"]
                    data = body.get("data", body)
                    if isinstance(data, dict):
                        if "name" in data or "title" in data:
                            book_info["title"] = data.get("name") or data.get("title") or data.get("book_name", "")
                            book_info["author"] = data.get("author_name") or data.get("author", "")
                            book_info["description"] = data.get("description") or data.get("intro") or data.get("summary", "")
                            book_info["cover_url"] = data.get("cover_url") or data.get("cover") or data.get("image_url", "")
                            book_info["tags"] = data.get("tags") or data.get("categories") or []
                            book_info["total_chapters"] = data.get("total_chapters") or data.get("total_episodes") or 0
                            print(f"  [API] title={book_info.get('title','')[:30]}")

                # Fallback: DOM extraction
                dom_data = await page.evaluate("""() => {
                    // Try multiple selectors for title
                    let title = '';
                    const selectors = [
                        '[class*="book-name"]', '[class*="drama-name"]', '[class*="work-name"]',
                        'meta[property="og:title"]', 'meta[name="title"]',
                        'h1', 'h2', 'h3',
                        '[class*="title"]:not(nav *):not(header *)',
                    ];
                    for (const sel of selectors) {
                        const el = document.querySelector(sel);
                        if (el) {
                            const text = (el.content || el.textContent || '').trim();
                            if (text.length > 1 && text.length < 80 && !text.includes('创作工具') && !text.includes('番茄')) {
                                title = text;
                                break;
                            }
                        }
                    }

                    // Cover image
                    let cover = '';
                    document.querySelectorAll('img').forEach(img => {
                        const src = img.src || '';
                        if (src.includes('cover') || src.includes('cdn.fqcdn.com') || src.includes('byteimg')) {
                            cover = src;
                        }
                    });

                    // Extract total episodes from any text
                    let totalEpisodes = 0;
                    const fullText = document.body ? document.body.innerText : '';
                    const epMatch = fullText.match(/(\\d+)\\s*集/);
                    if (epMatch) totalEpisodes = parseInt(epMatch[1]);

                    // Tags
                    const tags = [];
                    document.querySelectorAll('[class*="tag"], [class*="label"], [class*="category"], [class*="genre"]').forEach(el => {
                        const t = el.textContent.trim();
                        if (t && t.length < 15 && !t.includes('创作') && !t.includes('番茄')) tags.push(t);
                    });

                    return { title, cover, totalEpisodes, tags: [...new Set(tags)] };
                }""")

                # Merge: API takes priority, DOM fills gaps
                final_title = book_info.get("title") or dom_data.get("title") or name
                final_author = book_info.get("author") or ""
                final_desc = book_info.get("description") or ""
                final_cover = book_info.get("cover_url") or dom_data.get("cover") or ""
                final_tags = list(set(book_info.get("tags", []) + dom_data.get("tags", [])))
                total_ep = book_info.get("total_chapters") or dom_data.get("totalEpisodes") or 0

                # Filter out noise tags
                final_tags = [t for t in final_tags if not any(x in t for x in ["App", "新任务", "结算", "高转化", "已完结", "集"])]
                # Extract episode count from tags like "已完结84集" or "84集"
                for t in book_info.get("tags", []) + dom_data.get("tags", []):
                    m = re.search(r'(\d+)集', t)
                    if m and total_ep == 0:
                        total_ep = int(m.group(1))
                        break

                print(f"  Title: {final_title[:40]}")
                print(f"  Episodes: {total_ep}")
                print(f"  Cover: {final_cover[:60]}")
                print(f"  Tags: {final_tags[:5]}")

                # Download cover
                local_cover = f"covers/{i+1:02d}.jpg"
                if final_cover and final_cover.startswith("http"):
                    async with httpx.AsyncClient() as client:
                        await download_file(client, final_cover, COVERS_DIR / f"{i+1:02d}.jpg")

                drama["title"] = final_title
                drama["author"] = final_author
                drama["description"] = final_desc
                drama["cover_url"] = final_cover
                drama["local_cover"] = local_cover
                drama["tags"] = final_tags
                drama["total_episodes"] = total_ep

            except Exception as e:
                print(f"  ERROR: {e}")
                drama["error"] = str(e)
                drama["title"] = drama.get("title", name)

        await browser.close()

    output = DATA_DIR / "dramas_data.json"
    output.write_text(json.dumps(dramas, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nSaved to {output}")


if __name__ == "__main__":
    asyncio.run(run())
