#!/usr/bin/env python3
"""Subset Nebulove deterministically and replace only its own @font-face rule."""
import base64
from html import unescape
from io import BytesIO
import os
from pathlib import Path
import re
import tempfile
import urllib.request

FONT_URL = "https://raw.githubusercontent.com/lingyicute/Nebulove/main/Nebulove.ttf"
INDEX_PATH = Path("web/index.html")
FONT_FACE = re.compile(r"@font-face\s*\{[^}]*\}", re.DOTALL | re.IGNORECASE)
NEBULOVE_FAMILY = re.compile(
    r'''font-family\s*:\s*(?:"Nebulove"|'Nebulove'|Nebulove)\s*;''', re.IGNORECASE
)
PUNCTUATION = "：，。！？；“”‘’（）【】—…·《》×＝÷＋－"


def find_font_rule(html):
    matches = [m for m in FONT_FACE.finditer(html) if NEBULOVE_FAMILY.search(m.group())]
    if len(matches) != 1:
        raise ValueError("Expected exactly one Nebulove @font-face rule")
    return matches[0]


def collect_characters(html):
    # Decode numeric/named HTML entities (e.g. &#x4E2D;). Ignore previous font data.
    text = unescape(FONT_FACE.sub("", html))
    return "".join(sorted(set(text) | set(map(chr, range(32, 127))) | set(PUNCTUATION)))


def build_subset(font_data, characters):
    from fontTools.ttLib import TTFont
    from fontTools.subset import Subsetter, Options

    with TTFont(BytesIO(font_data), recalcTimestamp=False) as font:
        subsetter = Subsetter(options=Options())
        subsetter.populate(text=characters)
        subsetter.subset(font)
        font.flavor = "woff2"
        output = BytesIO()
        font.save(output)
        return output.getvalue()


def embed_font(html, font_data):
    match = find_font_rule(html)
    encoded = base64.b64encode(font_data).decode("ascii")
    css = f'''@font-face{{
  font-family:"Nebulove";
  src:url("data:font/woff2;charset=utf-8;base64,{encoded}") format("woff2"),
      url("https://cdn.jsdelivr.net/gh/lingyicute/Nebulove@main/Nebulove.woff2") format("woff2");
  font-display:swap;
}}'''
    return html[:match.start()] + css + html[match.end():]


def main():
    path = Path(INDEX_PATH)
    html = path.read_text(encoding="utf-8")
    find_font_rule(html)  # Fail before downloading if the template is invalid.
    characters = collect_characters(html)
    print(f"Subsetting {len(characters)} characters from {FONT_URL}")
    with urllib.request.urlopen(FONT_URL, timeout=30) as response:
        subset = build_subset(response.read(), characters)
    updated = embed_font(html, subset)
    if updated == html:
        print("web/index.html is already up to date.")
        return

    # Unique temp file, atomic replacement, and no partial HTML on a write error.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         suffix=".html", delete=False) as output:
            temporary = Path(output.name)
            output.write(updated)
        os.chmod(temporary, path.stat().st_mode)
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    print(f"Embedded {len(subset)} bytes of WOFF2 into {path}")


if __name__ == "__main__":
    main()
