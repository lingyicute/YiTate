#!/usr/bin/env python3
"""
Subset the Nebulove font to only the characters used in web/index.html,
then embed the WOFF2 subset as base64 into the @font-face rule.
"""
import re, base64, urllib.request, os, sys

FONT_URL = "https://raw.githubusercontent.com/lingyicute/Nebulove/main/Nebulove.ttf"
INDEX_PATH = "web/index.html"
TMP_FONT_PATH = "/tmp/Nebulove.ttf"

def main():
    if not os.path.exists(INDEX_PATH):
        print(f"Error: {INDEX_PATH} not found.", file=sys.stderr)
        sys.exit(1)

    print("Reading web/index.html...")
    with open(INDEX_PATH, "r", encoding="utf-8") as f:
        html = f.read()

    # 1. Collect all characters needed
    chars = set(html)
    # Ensure full ASCII printable set (32 to 126)
    for c in range(32, 127):
        chars.add(chr(c))
    # Common punctuation
    chars.update(['：', '，', '。', '！', '？', '；', '“', '”', '‘', '’', '（', '）', '【', '】', '—', '…', '·', '《', '》', '×', '＝', '÷', '＋', '－'])

    print(f"Total unique characters needed: {len(chars)}")

    # 2. Download full Nebulove font
    print(f"Downloading font from {FONT_URL}...")
    try:
        urllib.request.urlretrieve(FONT_URL, TMP_FONT_PATH)
    except Exception as e:
        print(f"Failed to download font: {e}", file=sys.stderr)
        sys.exit(1)

    # 3. Subset font using fontTools
    from fontTools.ttLib import TTFont
    from fontTools.subset import Subsetter, Options

    print("Subsetting font...")
    # recalcTimestamp=False keeps the original `head.modified` timestamp
    # (fontTools would otherwise stamp "now" into it), so the subset output
    # is byte-for-byte reproducible and the CI workflow stays idempotent
    # (re-running on a push never produces a spurious diff/commit).
    font = TTFont(TMP_FONT_PATH, recalcTimestamp=False)
    subsetter = Subsetter(options=Options())
    subsetter.populate(text="".join(chars))
    subsetter.subset(font)

    font.flavor = "woff2"
    tmp_woff2 = "/tmp/Nebulove-Subset.woff2"
    font.save(tmp_woff2)

    woff2_size = os.path.getsize(tmp_woff2)
    print(f"Subsetted WOFF2 size: {woff2_size} bytes ({woff2_size / 1024:.2f} KB)")

    # 4. Convert to base64
    with open(tmp_woff2, "rb") as f:
        b64_font = base64.b64encode(f.read()).decode("utf-8")

    # 5. Replace font-face in web/index.html
    font_css = f'''@font-face{{
  font-family:"Nebulove";
  src:url("data:font/woff2;charset=utf-8;base64,{b64_font}") format("woff2"),
      url("https://cdn.jsdelivr.net/gh/lingyicute/Nebulove@main/Nebulove.woff2") format("woff2");
  font-display:swap;
}}'''

    new_html = re.sub(r'@font-face\s*\{[^}]*\}', font_css, html, flags=re.DOTALL)

    if new_html == html:
        print("web/index.html is already up to date. No changes made.")

    with open(INDEX_PATH, "w", encoding="utf-8") as f:
        f.write(new_html)

    print("web/index.html updated successfully!")

if __name__ == "__main__":
    main()
