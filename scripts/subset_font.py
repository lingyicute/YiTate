#!/usr/bin/env python3
"""Subset Nebulove deterministically and replace only its marked CSS source."""
import base64
import hashlib
from html import unescape
from io import BytesIO
import os
from pathlib import Path
import re
import tempfile
import urllib.request

# Font source and separate license: https://github.com/lingyicute/Nebulove
FONT_REVISION = "df0d98ed69435d0ff1f3705c084aca057f4ca9a5"
FONT_URL = f"https://raw.githubusercontent.com/lingyicute/Nebulove/{FONT_REVISION}/Nebulove.ttf"
FONT_SHA256 = "c40a831c4577807aaf7e264fad8dadb6dd2b8fe329bd39abdf74e3ef72770ae4"
INDEX_PATH = Path(__file__).resolve().parents[1] / "web/index.html"
SOURCE_MARKER = "/* Nebulove source: managed by scripts/subset_font.py */"
FONT_SOURCE = re.compile(
    rf'^[ \t]*{re.escape(SOURCE_MARKER)}[ \t]*\r?\n'
    r'[ \t]*src:[ \t]*url\("(?P<url>[^"\r\n]*)"\)[ \t]+format\("woff2"\);[ \t]*\r?$',
    re.MULTILINE,
)
PUNCTUATION = "：，。！？；“”‘’（）【】—…·《》×＝÷＋－"


def find_font_source(html):
    match = FONT_SOURCE.search(html)
    if match is None or html.count(SOURCE_MARKER) != 1:
        raise ValueError("Expected exactly one marked Nebulove WOFF2 source")
    return match.span("url")


def collect_characters(html):
    start, end = find_font_source(html)
    # Decode HTML entities (e.g. &#x4E2D;) and omit the old font data.
    text = unescape((html[:start] + html[end:]).replace("\r\n", "\n"))
    return "".join(sorted(set(text) | set(map(chr, range(32, 127))) | set(PUNCTUATION)))


def build_subset(font_data, characters):
    from fontTools.ttLib import TTFont
    from fontTools.subset import Subsetter, Options

    with TTFont(BytesIO(font_data), recalcTimestamp=False) as font:
        options = Options()
        # Keep upstream copyright, source and license records in every language.
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.name_legacy = True
        subsetter = Subsetter(options=options)
        subsetter.populate(text=characters)
        subsetter.subset(font)
        font.flavor = "woff2"
        output = BytesIO()
        font.save(output)
        return output.getvalue()


def embed_font(html, font_data):
    start, end = find_font_source(html)
    encoded = base64.b64encode(font_data).decode("ascii")
    return html[:start] + f"data:font/woff2;base64,{encoded}" + html[end:]


def main():
    path = Path(INDEX_PATH)
    # Read raw bytes to preserve newlines and recognize edits during generation.
    original = path.read_bytes()
    html = original.decode("utf-8")

    def require_unchanged():
        if path.read_bytes() != original:
            raise RuntimeError(f"{path} changed during font generation; rerun the script")

    characters = collect_characters(html)  # Validate the template before downloading.
    print(f"Subsetting {len(characters)} characters from {FONT_URL}")
    with urllib.request.urlopen(FONT_URL, timeout=30) as response:
        source = response.read()
    if hashlib.sha256(source).hexdigest() != FONT_SHA256:
        raise ValueError("Nebulove source does not match the pinned SHA-256")
    subset = build_subset(source, characters)
    updated = embed_font(html, subset)
    if updated == html:
        require_unchanged()
        print("web/index.html is already up to date.")
        return

    # Unique temp file, atomic replacement, and no partial HTML on a write error.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, suffix=".html", delete=False) as output:
            temporary = Path(output.name)
            output.write(updated.encode("utf-8"))
        os.chmod(temporary, path.stat().st_mode)
        require_unchanged()
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()
    print(f"Embedded {len(subset)} bytes of WOFF2 into {path}")


if __name__ == "__main__":
    main()
