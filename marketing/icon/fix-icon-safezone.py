#!/usr/bin/env python3
"""Fix FastMask icon assets: remove opaque-black corners and rescale the glyph
into the Android adaptive-icon safe zone.

Source masters (glyph on transparency, with opaque-black canvas corners):
  marketing/icon/v3-foreground-1024.png
  marketing/icon/v3-monochrome-1024.png

Regenerates every derived asset from those two cleaned masters.
"""
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "marketing" / "icon"
PLAY = ROOT / "marketing" / "play"
RES = ROOT / "app" / "src" / "main" / "res"

CREAM = (251, 236, 211, 255)
SAFE_RATIO = 0.64  # glyph bbox max-dim as fraction of canvas (Android safe zone)

# Adaptive-icon foreground/monochrome sizes per density (108dp canvas).
FG_DENSITIES = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}
# Legacy launcher icon sizes (dead code on minSdk 26, regenerated for tidiness).
LEGACY_DENSITIES = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}


def flood_fill_corners(img: Image.Image):
    """Make the connected opaque-black corner regions transparent.

    Flood-fills from each of the 4 corners across dark, non-transparent pixels.
    In the foreground the glyph is navy (~25,43,58) — it fails the `dark()`
    test, so the fill never crosses into the artwork. Returns the cleaned image
    plus the set of filled coords, which serves as an exact corner mask for the
    monochrome layer (whose pure-black glyph would otherwise be eaten by the
    fill, since it touches the canvas edges).
    """
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    seen = [[False] * w for _ in range(h)]
    filled = set()

    def dark(p):
        r, g, b, a = p
        return a > 20 and r < 22 and g < 22 and b < 22

    q = deque()
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        if dark(px[cx, cy]):
            q.append((cx, cy))
            seen[cy][cx] = True
    while q:
        x, y = q.popleft()
        px[x, y] = (0, 0, 0, 0)
        filled.add((x, y))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and dark(px[nx, ny]):
                seen[ny][nx] = True
                q.append((nx, ny))
    return img, filled


def clear_pixels(img: Image.Image, coords) -> Image.Image:
    """Set the given pixel coords to fully transparent."""
    img = img.convert("RGBA")
    px = img.load()
    for x, y in coords:
        px[x, y] = (0, 0, 0, 0)
    return img


def safezone(glyph: Image.Image, size: int, ratio: float = SAFE_RATIO) -> Image.Image:
    """Crop the cleaned glyph to its alpha bbox, scale so its largest dimension
    is `ratio * size`, and center it on a transparent `size`x`size` canvas."""
    bbox = glyph.getbbox()
    if bbox is None:
        raise ValueError("glyph has no visible pixels after corner cleanup")
    cropped = glyph.crop(bbox)
    cw, ch = cropped.size
    scale = (ratio * size) / max(cw, ch)
    nw, nh = max(1, round(cw * scale)), max(1, round(ch * scale))
    resized = cropped.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(resized, ((size - nw) // 2, (size - nh) // 2), resized)
    return canvas


def composed(glyph: Image.Image, size: int, round_mask: bool = False) -> Image.Image:
    """Glyph in safe zone over a solid cream square (full-bleed)."""
    base = Image.new("RGBA", (size, size), CREAM)
    base.alpha_composite(safezone(glyph, size))
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        return out
    return base


def main() -> None:
    fg, corner_mask = flood_fill_corners(Image.open(ICON / "v3-foreground-1024.png"))
    # The monochrome glyph is pure black and touches the canvas edges, so a
    # flood-fill would eat it. The corner regions are geometrically identical
    # to the foreground's, so reuse that exact mask instead.
    mono = clear_pixels(Image.open(ICON / "v3-monochrome-1024.png"), corner_mask)

    # Masters.
    safezone(fg, 1024).save(ICON / "v3-foreground-1024.png")
    safezone(mono, 1024).save(ICON / "v3-monochrome-1024.png")
    composed(fg, 1024).save(ICON / "v3-source-1024.png")

    # Play Store icon: 512x512, no alpha channel (Google rounds it).
    composed(fg, 512).convert("RGB").save(PLAY / "icon-512.png")

    # Adaptive-icon foreground + monochrome, per density.
    for name, size in FG_DENSITIES.items():
        d = RES / f"drawable-{name}"
        safezone(fg, size).save(d / "ic_launcher_foreground.png")
        safezone(mono, size).save(d / "ic_launcher_monochrome.png")

    # Legacy launcher icons (unused on minSdk 26, kept consistent).
    for name, size in LEGACY_DENSITIES.items():
        m = RES / f"mipmap-{name}"
        composed(fg, size).convert("RGB").save(m / "ic_launcher.png")
        composed(fg, size, round_mask=True).save(m / "ic_launcher_round.png")

    print("done")


if __name__ == "__main__":
    main()
