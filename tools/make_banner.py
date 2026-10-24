"""Draws the README banner and logo: docs/assets/banner.svg and docs/assets/logo.svg.

The SR pieces come from make_icon.py; the words are the app's own fonts (Montserrat, Roboto)
turned into outlines, so GitHub shows them the same everywhere. Run: python3 tools/make_banner.py
"""
import os, sys
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

sys.path.insert(0, os.path.dirname(__file__))
import make_icon as icon  # noqa: E402

HERE = os.path.dirname(__file__)
FONTS = os.path.join(HERE, "..", "app", "src", "main", "res", "font")
OUT = os.path.join(HERE, "..", "docs", "assets")

BG = "#312E2B"  # a little lighter than the app, so the pieces' dark outline shows
GREEN = "#81B64C"
TEXT = "#FFFFFF"
DIM = "#A8A6A3"


def words(text, font_file, size, x, y, fill, spacing=0.0):
    """Text as one SVG path, baseline at y, starting at x. Returns (svg, width)."""
    font = TTFont(os.path.join(FONTS, font_file))
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    pen = SVGPathPen(glyphs)
    cursor = 0.0
    for ch in text:
        name = cmap.get(ord(ch))
        if name is None:
            continue
        g = glyphs[name]
        g.draw(TransformPen(pen, (scale, 0, 0, -scale, x + cursor, y)))
        cursor += g.width * scale + spacing
    return f'<path d="{pen.getCommands()}" fill="{fill}"/>', cursor


def pieces(tx, ty, scale):
    """The SR mark on its base, as drawn in the app icon, placed at (tx, ty)."""
    def p(d, fill, stroke=None):
        s = f' stroke="{stroke}" stroke-width="{icon.STROKE}" stroke-linejoin="round"' if stroke else ""
        return f'<path d="{d}" fill="{fill}"{s}/>'
    g = "".join(p(d, icon.OUTLINE, icon.OUTLINE) for d in (icon.S, icon.R, icon.BASE))
    g += p(icon.BASE, icon.BASE_FILL) + p(icon.BASE_BAND, icon.BASE_TOP)
    for i, (d, fill, shade, top) in enumerate(((icon.S, icon.S_FILL, icon.S_SHADE, 112), (icon.R, icon.R_FILL, icon.R_SHADE, 97))):
        clip = icon.clip_below(0.72, top, icon.BASELINE)
        g += p(d, fill) + f'<clipPath id="sr{i}"><path d="{clip}"/></clipPath><g clip-path="url(#sr{i})">{p(d, shade)}</g>'
    return f'<g transform="translate({tx} {ty}) scale({scale})">{g}</g>'


# Move labels as in the review: colour and symbol.
LABELS = [
    ("#1BACA6", "!!"), ("#5C8BB0", "!"), ("#81B64C", "★"), ("#96BC4B", "✓"), ("#A88865", "B"),
    ("#F7C631", "?!"), ("#FFA459", "?"), ("#FF7769", "✕"), ("#FA412D", "??"),
]


def banner():
    w, h = 1280, 440
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">', f'<rect width="{w}" height="{h}" fill="{BG}"/>']
    # A board fading in from the right edge.
    out.append('<defs><linearGradient id="fade" x1="0" x2="1"><stop offset="0" stop-color="#312E2B" stop-opacity="1"/>'
               '<stop offset="1" stop-color="#312E2B" stop-opacity="0.55"/></linearGradient></defs>')
    s = 55
    for r in range(h // s + 1):
        for c in range(w // s + 1):
            if c * s < 620:
                continue
            col = "#EDD5AF" if (r + c) % 2 == 0 else "#B88662"
            out.append(f'<rect x="{c * s}" y="{r * s}" width="{s}" height="{s}" fill="{col}" opacity="0.10"/>')
    out.append(f'<rect width="{w}" height="{h}" fill="url(#fade)"/>')
    # The pieces.
    out.append(pieces(80, 20, 1.2))
    # Name and line.
    x = 470
    name1, wd = words("Sonder", "montserrat_extrabold.ttf", 92, x, 215, TEXT)
    name2, _ = words("Chess", "montserrat_extrabold.ttf", 92, x + wd, 215, GREEN)
    out += [name1, name2]
    line, _ = words("Free game review for your Chess.com games, on your phone.", "roboto_medium.ttf", 26, x + 4, 268, DIM)
    out.append(line)
    # The move labels, as round badges.
    bx = x + 22
    for col, sym in LABELS:
        out.append(f'<circle cx="{bx}" cy="330" r="22" fill="{col}"/>')
        if sym == "★":
            import math
            pts = " ".join(f"{bx + (10 if k % 2 == 0 else 4.2) * math.sin(k * math.pi / 5):.1f},{330 - (10 if k % 2 == 0 else 4.2) * math.cos(k * math.pi / 5):.1f}" for k in range(10))
            out.append(f'<polygon points="{pts}" fill="#FFFFFF"/>')
        elif sym == "✓":
            out.append(f'<path d="M{bx - 9} {331} l6 6 l12 -13" fill="none" stroke="#FFFFFF" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"/>')
        elif sym == "✕":
            out.append(f'<path d="M{bx - 7} 323 l14 14 M{bx + 7} 323 l-14 14" stroke="#FFFFFF" stroke-width="4.5" stroke-linecap="round"/>')
        else:
            g, sw = words(sym, "montserrat_extrabold.ttf", 22, 0, 0, "#FFFFFF")
            out.append(f'<g transform="translate({bx - sw / 2:.1f} 338)">{g}</g>')
        bx += 58
    # A green wave along the bottom, like the other Verisonder banners.
    out.append(f'<path d="M0 440 Q462 322 1280 344 L1280 366 Q551 356 0 440 Z" fill="{GREEN}"/>')
    out.append('<path d="M0 440 Q533 374 1280 390 L1280 440 Z" fill="#151413"/>')
    out.append("</svg>")
    return "\n".join(out)


def logo():
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><rect width="512" height="512" rx="112" fill="#383430"/>{pieces(57, -23, 1.55)}</svg>'


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    open(os.path.join(OUT, "banner.svg"), "w").write(banner())
    open(os.path.join(OUT, "logo.svg"), "w").write(logo())
    print("done")
