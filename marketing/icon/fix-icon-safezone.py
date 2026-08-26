#!/usr/bin/env python3
"""Regenerate every FastMask launcher asset from the Privacy Fold masters.

The 1024px transparent masters already contain the intentional adaptive-icon
safe-zone padding. This script preserves the complete canvas while resizing;
cropping to the alpha bounds would destroy that optical spacing.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "marketing" / "icon"
PLAY = ROOT / "marketing" / "play"
RES = ROOT / "app" / "src" / "main" / "res"

BACKGROUND = (26, 34, 56, 255)

# Adaptive-icon foreground/monochrome sizes per density (108dp canvas).
FG_DENSITIES = {
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}
# Legacy launcher icon sizes (dead code on minSdk 26, regenerated for tidiness).
LEGACY_DENSITIES = {
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}


def layer(source: Image.Image, size: int) -> Image.Image:
    """Resize the complete 108dp-equivalent master canvas."""
    return source.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)


def composed(glyph: Image.Image, size: int, round_mask: bool = False) -> Image.Image:
    """Glyph over the full-bleed midnight-ink launcher background."""
    base = Image.new("RGBA", (size, size), BACKGROUND)
    base.alpha_composite(layer(glyph, size))
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(base, (0, 0), mask)
        return out
    return base


def main() -> None:
    fg = Image.open(ICON / "fastmask-icon-foreground-1024.png").convert("RGBA")
    mono = Image.open(ICON / "fastmask-icon-monochrome-1024.png").convert("RGBA")

    composed(fg, 1024).save(ICON / "fastmask-icon-source-1024.png")

    # Play Store icon: 512x512, no alpha channel (Google rounds it).
    composed(fg, 512).convert("RGB").save(PLAY / "icon-512.png")

    # Adaptive-icon foreground + monochrome, per density.
    for name, size in FG_DENSITIES.items():
        d = RES / f"drawable-{name}"
        layer(fg, size).save(d / "ic_launcher_foreground.png")
        layer(mono, size).save(d / "ic_launcher_monochrome.png")

    # Legacy launcher icons (unused on minSdk 26, kept consistent).
    for name, size in LEGACY_DENSITIES.items():
        m = RES / f"mipmap-{name}"
        composed(fg, size).convert("RGB").save(m / "ic_launcher.png")
        composed(fg, size, round_mask=True).save(m / "ic_launcher_round.png")

    print("done")


if __name__ == "__main__":
    main()
