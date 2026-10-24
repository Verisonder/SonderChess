"""Builds the SonderChess launcher icon: the Verisonder SR mark (blue S, white R) drawn like
the app's pieces - a thick dark outline, a darker band low down for depth - standing together
on one piece base, on the app's dark background.

Run: python3 tools/make_icon.py  -> app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml
The letters are the Verisonder avatar's pixel S and R, redrawn as clean blocks (tools/sr_mark.txt).
"""
import os, re

HERE = os.path.dirname(__file__)
OUT = os.path.join(HERE, "..", "app", "src", "main", "res", "drawable")
MARK = open(os.path.join(HERE, "sr_mark.txt")).read().split("\n")
S_PATH = MARK[0].split(" ", 1)[1]
R_PATH = MARK[1].split(" ", 1)[1]

OUTLINE = "#262421"
S_FILL, S_SHADE = "#4FB0E6", "#2C83B8"
R_FILL, R_SHADE = "#FFFFFF", "#D4D3D0"
BASE_FILL, BASE_TOP = "#D4D3D0", "#F4F4F2"
BG = "#383430"  # a touch lighter than the app background, so the dark piece outline still shows
STROKE = 15  # outline width in mark units (the mark is drawn on a 256 grid)


def shift(path, dx, dy):
    """Moves every rectangle of a traced path (M x y h.. v.. h.. z)."""
    def mv(m):
        return f"M{float(m.group(1)) + dx:g} {float(m.group(2)) + dy:g}"
    return re.sub(r"M(-?[\d.]+) (-?[\d.]+)", mv, path)


# Both letters stand on one baseline (the R is raised in the avatar), on one long base.
BASELINE = 232
R = shift(R_PATH, 0, BASELINE - 160)
S = S_PATH
BASE = f"M8 {BASELINE}h240a6 6 0 0 1 6 6v18a6 6 0 0 1 -6 6h-240a6 6 0 0 1 -6 -6v-18a6 6 0 0 1 6 -6z"
BASE_BAND = f"M8 {BASELINE}h240a6 6 0 0 1 6 6v3h-252v-3a6 6 0 0 1 6 -6z"

# Fit the group into the adaptive icon's safe zone (a 66 unit circle in the middle of 108).
X0, X1, Y0, Y1 = 2 - STROKE / 2, 254 + STROKE / 2, 97 - STROKE / 2, BASELINE + 30 + STROKE / 2
SCALE = 54 / (X1 - X0)
TX = 54 - (X0 + X1) / 2 * SCALE
TY = 54 - (Y0 + Y1) / 2 * SCALE - 1


def clip_below(frac, y_top, y_bot):
    y = y_top + (y_bot - y_top) * frac
    return f"M-20 {y:g}h300v{y_bot - y + 20:g}h-300z"


def vector(body, comment):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- {comment} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        f"{body}</vector>\n"
    )


def path(d, fill=None, stroke=None, width=0):
    a = f'android:pathData="{d}"'
    if fill:
        a = f'android:fillColor="{fill}" ' + a
    if stroke:
        a += f' android:strokeColor="{stroke}" android:strokeWidth="{width}" android:strokeLineJoin="round"'
    return f"        <path {a} />\n"


def foreground():
    g = f'    <group android:translateX="{TX:.3f}" android:translateY="{TY:.3f}" android:scaleX="{SCALE:.5f}" android:scaleY="{SCALE:.5f}">\n'
    # 1. The outline: everything stroked thick in the dark colour; the fills cover its inner half.
    for d in (S, R, BASE):
        g += path(d, OUTLINE, OUTLINE, STROKE)
    # 2. The base, with a lighter top edge.
    g += path(BASE, BASE_FILL) + path(BASE_BAND, BASE_TOP)
    # 3. Each letter, then a darker band over its lower part.
    for d, fill, shade, top in ((S, S_FILL, S_SHADE, 112), (R, R_FILL, R_SHADE, 97)):
        g += path(d, fill)
        g += f'        <group>\n            <clip-path android:pathData="{clip_below(0.72, top, BASELINE)}" />\n'
        g += "    " + path(d, shade)
        g += "        </group>\n"
    g += "    </group>\n"
    return vector(g, "SonderChess icon: the Verisonder SR mark drawn as chess pieces (tools/make_icon.py)")


def monochrome():
    g = f'    <group android:translateX="{TX:.3f}" android:translateY="{TY:.3f}" android:scaleX="{SCALE:.5f}" android:scaleY="{SCALE:.5f}">\n'
    for d in (S, R, BASE):
        g += path(d, "#FFFFFF")
    g += "    </group>\n"
    return vector(g, "Themed-icon version: the SR mark and base in one colour (tools/make_icon.py)")


def background():
    return vector(path("M0 0h108v108h-108z", BG), "SonderChess icon background: plain dark grey (tools/make_icon.py)")


def svg_preview():
    """The same drawing as SVG, for checking by eye."""
    def p(d, fill, stroke=None):
        s = f' stroke="{stroke}" stroke-width="{STROKE}" stroke-linejoin="round"' if stroke else ""
        return f'<path d="{d}" fill="{fill}"{s}/>'
    bg = f'<rect width="108" height="108" fill="{BG}"/>'
    g = "".join(p(d, OUTLINE, OUTLINE) for d in (S, R, BASE)) + p(BASE, BASE_FILL) + p(BASE_BAND, BASE_TOP)
    for i, (d, fill, shade, top) in enumerate(((S, S_FILL, S_SHADE, 112), (R, R_FILL, R_SHADE, 97))):
        g += p(d, fill) + f'<clipPath id="c{i}"><path d="{clip_below(0.72, top, BASELINE)}"/></clipPath><g clip-path="url(#c{i})">{p(d, shade)}</g>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">{bg}<g transform="translate({TX} {TY}) scale({SCALE})">{g}</g></svg>'


if __name__ == "__main__":
    open(os.path.join(OUT, "ic_launcher_foreground.xml"), "w").write(foreground())
    open(os.path.join(OUT, "ic_launcher_background.xml"), "w").write(background())
    open(os.path.join(OUT, "ic_launcher_monochrome.xml"), "w").write(monochrome())
    if os.environ.get("PREVIEW"):
        open(os.environ["PREVIEW"], "w").write(svg_preview())
    print("done")
