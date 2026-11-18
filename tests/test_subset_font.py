import hashlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts import subset_font as font

RULE = "\n".join((
    '@font-face {',
    '    font-family: "Nebulove";',
    f'    {font.SOURCE_MARKER}',
    '    src: url("old.woff2") format("woff2");',
    '    font-display: swap;',
    '    font-style: normal;',
    '}',
))


class FontSubsetTests(unittest.TestCase):
    def test_html_entities_are_decoded(self):
        chars = font.collect_characters(RULE + '<p>&#x4E2D;&#25991;&copy;</p>')
        self.assertTrue(set('中文©').issubset(chars))
        self.assertTrue(set(map(chr, range(32, 127))).issubset(chars))
        self.assertEqual(chars, ''.join(sorted(set(chars))))

    def test_only_the_marked_source_is_replaced(self):
        # Neither comments nor declarations in other fonts identify the source.
        other = ('/* @font-face { font-family: "Nebulove"; } */\n'
                 '@font-face { font-family: "Other"; src: url(other.woff2); }')
        html = RULE + '\n' + other
        result = font.embed_font(html, b'test')
        self.assertEqual(result, html.replace('old.woff2', 'data:font/woff2;base64,dGVzdA=='))
        self.assertTrue(font.INDEX_PATH.is_absolute())

    def test_missing_duplicate_or_invalid_marker_is_rejected(self):
        for html in ('<p>No font</p>', RULE + '\n' + RULE,
                     RULE.replace('format("woff2")', 'format("woff")')):
            with self.assertRaises(ValueError):
                font.embed_font(html, b'font')

    def test_embedding_is_idempotent(self):
        once = font.embed_font(RULE, b'font')
        self.assertEqual(once, font.embed_font(once, b'font'))
        self.assertEqual(font.collect_characters(RULE), font.collect_characters(once))

    def test_failed_download_does_not_modify_html(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'index.html'
            path.write_text(RULE)
            with patch.object(font, 'INDEX_PATH', path), patch.object(
                    font.urllib.request, 'urlopen', side_effect=OSError('offline')):
                with self.assertRaises(OSError):
                    font.main()
            self.assertEqual(path.read_text(), RULE)
            self.assertEqual(list(Path(tmp).iterdir()), [path])

    def test_mismatched_source_is_not_parsed_or_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'index.html'
            path.write_text(RULE)
            with (patch.object(font, 'INDEX_PATH', path),
                  patch.object(font.urllib.request, 'urlopen', return_value=io.BytesIO(b'changed')),
                  patch.object(font, 'build_subset') as subset):
                with self.assertRaisesRegex(ValueError, 'pinned SHA-256'):
                    font.main()
                subset.assert_not_called()
            self.assertEqual(path.read_text(), RULE)

    def test_woff2_subset_keeps_the_mapped_glyph_and_license_metadata(self):
        from fontTools.fontBuilder import FontBuilder
        from fontTools.pens.ttGlyphPen import TTGlyphPen
        from fontTools.ttLib import TTFont

        glyphs = ['.notdef', 'A', 'B']
        builder = FontBuilder(1000, isTTF=True)
        builder.setupGlyphOrder(glyphs)
        builder.setupCharacterMap({ord('A'): 'A', ord('B'): 'B'})
        builder.setupGlyf({name: TTGlyphPen(None).glyph() for name in glyphs})
        builder.setupHorizontalMetrics({name: (500, 0) for name in glyphs})
        builder.setupHorizontalHeader(ascent=800, descent=-200)
        builder.setupNameTable({'familyName': 'Test', 'styleName': 'Regular',
                                'licenseDescription': 'Embedding terms'})
        builder.setupOS2()
        builder.setupPost()
        original = io.BytesIO()
        builder.save(original)
        subset = font.build_subset(original.getvalue(), 'A')
        self.assertEqual(subset, font.build_subset(original.getvalue(), 'A'))
        with TTFont(io.BytesIO(subset)) as result:
            self.assertEqual(result.flavor, 'woff2')
            self.assertEqual(set(result.getBestCmap()), {ord('A')})
            self.assertEqual(result['name'].getDebugName(13), 'Embedding terms')

    def test_main_is_atomic_and_unchanged_runs_do_not_rewrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'index.html'
            path.write_text(RULE)
            with (patch.object(font, 'INDEX_PATH', path), patch.object(
                    font.urllib.request, 'urlopen', side_effect=lambda *a, **k: io.BytesIO(b'full-font')),
                    patch.object(font, 'FONT_SHA256', hashlib.sha256(b'full-font').hexdigest()),
                    patch.object(font, 'build_subset', return_value=b'subset')):
                font.main()
                result = path.read_text()
                before = path.stat().st_mtime_ns
                font.main()
                self.assertEqual(path.stat().st_mtime_ns, before)
                self.assertEqual(path.read_text(), result)
                self.assertEqual(list(Path(tmp).iterdir()), [path])

    def test_embedding_preserves_mixed_line_endings_outside_the_source_url(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'index.html'
            original = (RULE.replace('\n', '\r\n') + '\r\n<p>中文</p>\n').encode('utf-8')
            path.write_bytes(original)
            with (patch.object(font, 'INDEX_PATH', path),
                  patch.object(font.urllib.request, 'urlopen', return_value=io.BytesIO(b'font')),
                  patch.object(font, 'FONT_SHA256', hashlib.sha256(b'font').hexdigest()),
                  patch.object(font, 'build_subset', return_value=b'new')):
                font.main()
            self.assertEqual(path.read_bytes(), original.replace(
                b'old.woff2', b'data:font/woff2;base64,bmV3'))
            self.assertEqual(font.collect_characters(RULE),
                             font.collect_characters(RULE.replace('\n', '\r\n')))

    def test_invalid_template_fails_before_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'index.html'
            path.write_text('<p>no font</p>')
            with patch.object(font, 'INDEX_PATH', path), patch.object(font.urllib.request, 'urlopen') as download:
                with self.assertRaises(ValueError):
                    font.main()
                download.assert_not_called()


if __name__ == '__main__':
    unittest.main()
