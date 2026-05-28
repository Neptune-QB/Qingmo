"""Re-scrape descriptions — extract only the 作品简介 section."""
import asyncio
import json
import re
from pathlib import Path
from playwright.async_api import async_playwright

DATA_DIR = Path(__file__).parent / "data"
BROWSER_DATA = DATA_DIR / "browser_data"
DRAMAS_JSON = DATA_DIR / "dramas_data.json"


async def main():
    dramas = json.loads(DRAMAS_JSON.read_text("utf-8"))

    async with async_playwright() as p:
        browser = await p.chromium.launch_persistent_context(str(BROWSER_DATA), headless=False)
        page = await browser.new_page()

        for d in dramas:
            bid = d["book_id"]
            url = f"https://kol.fanqieopen.com/page/content/book-detail?tab_type=6&top_tab_genre=-1&key=203_0&genre=203&book_id={bid}"
            print(f"  {d['title'][:12]}...", end=" ")

            try:
                await page.goto(url, wait_until="load", timeout=20000)
                await asyncio.sleep(4)

                desc = await page.evaluate("""() => {
                    const text = document.body ? document.body.innerText : '';
                    // Find 作品简介 section
                    const idx = text.indexOf('作品简介');
                    if (idx < 0) return '';
                    const after = text.substring(idx + 4).trim();
                    // Cut at the next section marker
                    const endMarkers = ['\\n别', '\\n已完', '\\nBoo', '\\n第', '\\n命', '\\n创', '\\n申'];
                    let end = after.length;
                    for (const m of endMarkers) {
                        const p = after.indexOf(m);
                        if (p > 10 && p < end) end = p;
                    }
                    return after.substring(0, end).trim();
                }""")

                if desc and len(desc) > 10:
                    d["description"] = desc[:500]
                    print(f"OK ({len(desc)} chars)")
                else:
                    print(f"NOT FOUND")

            except Exception as e:
                print(f"FAIL: {e}")

        await browser.close()

    DRAMAS_JSON.write_text(json.dumps(dramas, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\nSaved.")


if __name__ == "__main__":
    asyncio.run(main())
