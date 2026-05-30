#!/usr/bin/env python3
"""Regenerate a clean preview-blood-moon.gif.

The checked-in preview-blood-moon.gif was corrupted: it had been re-saved with a
crushed ~32-colour palette which produced multicolour dithering noise (digital
static) in the theme-store card.  Every clean sibling preview (gloom-depths,
korok-forest, ...) is simply a full-width, vertically-centred downscale of that
theme's curated wallpaper at 430x182 with a very subtle shimmer animation and a
smooth 128-colour palette.

This script rebuilds blood-moon's preview the same way, deriving the scene from
the curated assets/wallpaper-blood-moon.png (read-only; never modified) so the
artwork matches the intended blood-moon castle/moon scene instead of inventing
new art.  Output dimensions/frame-count/duration match the other previews
exactly: 430x182, 16 frames, 80 ms/frame, P-mode, single shared palette.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
WALLPAPER = ASSETS / "wallpaper-blood-moon.png"
OUTPUT = ASSETS / "preview-blood-moon.gif"

# Match the clean sibling previews exactly.
SIZE = (430, 182)
FRAMES = 16
DURATION = 80
PALETTE_COLORS = 128

# Blood-moon palette (kept consistent with the generator / BurpTheme registry).
PARTICLE = (255, 83, 86)
ACCENT = (236, 28, 50)
GOLD = (214, 93, 42)


def center_crop_scene() -> Image.Image:
    """Full-width, vertically-centred crop of the wallpaper at the preview ratio."""
    wall = Image.open(WALLPAPER).convert("RGB")
    ww, wh = wall.size
    pw, ph = SIZE
    crop_h = round(ww * ph / pw)
    top = max(0, (wh - crop_h) // 2)
    crop = wall.crop((0, top, ww, top + crop_h))
    return crop.resize(SIZE, Image.Resampling.LANCZOS)


def build_frames() -> list[Image.Image]:
    """Static blood-moon scene with a subtle drifting glow + ember shimmer."""
    base = center_crop_scene()
    width, height = SIZE

    # Pre-compute a handful of ember positions for the shimmer overlay.  The
    # ember layer is rendered per-frame and alpha-composited, then drifted, so
    # the animation stays gentle (a soft pulsing glow), matching the siblings.
    embers = []
    for i in range(46):
        x = (i * 137 + 23) % width
        y = 24 + ((i * 83 + 11) % (height - 60))
        embers.append((x, y, 1 + (i % 3)))

    # Approximate the moon centre (upper-right of the blood-moon scene) for a
    # breathing glow halo.
    moon_cx = int(width * 0.74)
    moon_cy = int(height * 0.26)

    frames: list[Image.Image] = []
    for frame_index in range(FRAMES):
        phase = frame_index / FRAMES
        pulse = 0.5 + 0.5 * math.sin(phase * math.tau)
        drift = math.sin(phase * math.tau) * 3.0

        scene = base.copy().convert("RGBA")
        overlay = Image.new("RGBA", SIZE, (0, 0, 0, 0))
        opx = overlay.load()

        # Breathing halo around the moon: soft radial accent glow.
        halo_radius = 70.0
        halo_strength = 26 + int(pulse * 22)
        for y in range(max(0, moon_cy - 80), min(height, moon_cy + 80)):
            for x in range(max(0, moon_cx - 90), min(width, moon_cx + 90)):
                d = math.hypot(x - moon_cx, y - moon_cy)
                if d < halo_radius:
                    a = int(halo_strength * (1.0 - d / halo_radius) ** 2)
                    if a > 0:
                        opx[x, y] = (*PARTICLE, a)

        # Drifting embers: faint particles that gently brighten/dim per frame.
        for idx, (ex, ey, r) in enumerate(embers):
            x = int((ex + drift * (1 + (idx % 3))) % width)
            y = int(ey + math.sin(phase * math.tau + idx) * 1.5)
            local = 0.5 + 0.5 * math.sin(phase * math.tau + idx * 0.7)
            a = int(28 + local * 34)
            color = GOLD if idx % 9 == 0 else ACCENT if idx % 3 == 0 else PARTICLE
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    px, py = x + dx, y + dy
                    if 0 <= px < width and 0 <= py < height and dx * dx + dy * dy <= r * r:
                        falloff = 1.0 - (math.hypot(dx, dy) / (r + 0.5))
                        opx[px, py] = (*color, int(a * max(0.0, falloff)))

        scene.alpha_composite(overlay)
        frames.append(scene.convert("RGB"))

    return frames


def quantize_with_shared_palette(frames: list[Image.Image]) -> list[Image.Image]:
    """Quantize every frame against one smooth 128-colour palette (no flicker)."""
    # Derive the palette from a blended representative frame so it covers the
    # full scene plus the brightest shimmer state.
    representative = frames[0].copy()
    representative = Image.blend(representative, frames[FRAMES // 2], 0.5)
    palette_image = representative.convert(
        "P", palette=Image.Palette.ADAPTIVE, colors=PALETTE_COLORS
    )
    quantized = []
    for frame in frames:
        quantized.append(
            frame.quantize(
                colors=PALETTE_COLORS, palette=palette_image, dither=Image.Dither.NONE
            )
        )
    return quantized


def main() -> None:
    frames = quantize_with_shared_palette(build_frames())
    frames[0].save(
        OUTPUT,
        save_all=True,
        append_images=frames[1:],
        duration=DURATION,
        loop=0,
        optimize=True,
        disposal=1,
    )
    print(f"Wrote {OUTPUT.relative_to(ROOT)}: {SIZE[0]}x{SIZE[1]}, {len(frames)} frames")


if __name__ == "__main__":
    main()
