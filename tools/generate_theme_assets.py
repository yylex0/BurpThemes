#!/usr/bin/env python3
from __future__ import annotations

import argparse
import math
import random
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"
WALLPAPER_SIZE = (1812, 868)
AVATAR_SIZE = 512
DETAIL_SIZE = (640, 180)
MOTION_SIZE = (180, 96)
MOTION_FRAMES = 20
SCALE = 3
Image = None
ImageDraw = None
ImageFilter = None


THEMES = {
    "great-plateau": {
        "wallpaper": "temple-overworld.png",
        "avatar": "avatar-great-plateau.png",
        "preview": "preview-great-plateau.gif",
        "detail": "detail-great-plateau.png",
        "motion": "motion-great-plateau.gif",
        "base": (8, 27, 35),
        "horizon": (24, 83, 82),
        "accent": (105, 209, 187),
        "motif": (51, 150, 132),
        "gold": (245, 218, 127),
        "secondary": (51, 95, 55),
        "particle": (165, 247, 224),
        "rune": (105, 209, 187),
        "shadow": (3, 12, 16),
    },
    "ancient-slate": {
        "wallpaper": "wallpaper-ancient-slate.png",
        "avatar": "avatar-ancient-slate.png",
        "preview": "preview-ancient-slate.gif",
        "detail": "detail-ancient-slate.png",
        "motion": "motion-ancient-slate.gif",
        "base": (5, 18, 36),
        "horizon": (29, 78, 120),
        "accent": (118, 207, 255),
        "motif": (170, 232, 255),
        "gold": (247, 210, 128),
        "secondary": (27, 80, 126),
        "particle": (221, 246, 255),
        "rune": (164, 226, 255),
        "shadow": (2, 8, 20),
    },
    "korok-forest": {
        "wallpaper": "wallpaper-korok-forest.png",
        "avatar": "avatar-korok-forest.png",
        "preview": "preview-korok-forest.gif",
        "detail": "detail-korok-forest.png",
        "motion": "motion-korok-forest.gif",
        "base": (6, 24, 16),
        "horizon": (32, 82, 49),
        "accent": (116, 205, 120),
        "motif": (95, 178, 107),
        "gold": (231, 196, 89),
        "secondary": (42, 91, 50),
        "particle": (172, 225, 154),
        "rune": (116, 205, 120),
        "shadow": (2, 10, 7),
    },
    "blood-moon": {
        "wallpaper": "wallpaper-blood-moon.png",
        "avatar": "avatar-blood-moon.png",
        "preview": "preview-blood-moon.gif",
        "detail": "detail-blood-moon.png",
        "motion": "motion-blood-moon.gif",
        "base": (12, 1, 8),
        "horizon": (82, 6, 27),
        "accent": (236, 28, 50),
        "motif": (236, 28, 50),
        "gold": (214, 93, 42),
        "secondary": (101, 15, 35),
        "particle": (255, 83, 86),
        "rune": (236, 28, 50),
        "shadow": (5, 0, 4),
    },
    "gloom-depths": {
        "wallpaper": "wallpaper-gloom-depths.png",
        "avatar": "avatar-gloom-depths.png",
        "preview": "preview-gloom-depths.gif",
        "detail": "detail-gloom-depths.png",
        "motion": "motion-gloom-depths.gif",
        "base": (14, 6, 28),
        "horizon": (54, 22, 88),
        "accent": (185, 91, 255),
        "motif": (165, 73, 225),
        "gold": (167, 227, 153),
        "secondary": (70, 28, 109),
        "particle": (214, 148, 255),
        "rune": (167, 227, 153),
        "shadow": (6, 2, 14),
    },
}


def require_pillow() -> None:
    global Image, ImageDraw, ImageFilter
    if Image is not None:
        return
    from PIL import Image as pillow_image, ImageDraw as pillow_image_draw, ImageFilter as pillow_image_filter

    Image = pillow_image
    ImageDraw = pillow_image_draw
    ImageFilter = pillow_image_filter


def clamp(value: float) -> int:
    return max(0, min(255, int(round(value))))


def mix(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def add_rgba(base: Image.Image, overlay: Image.Image) -> None:
    base.alpha_composite(overlay)


def gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int], glow: tuple[int, int, int]) -> Image.Image:
    width, height = size
    image = Image.new("RGBA", size)
    pixels = image.load()
    for y in range(height):
        t = y / max(1, height - 1)
        sky = mix(top, bottom, t)
        for x in range(width):
            radial = 1.0 - min(1.0, math.hypot((x - width * 0.72) / width, (y - height * 0.22) / height) * 1.9)
            color = mix(sky, glow, max(0.0, radial) * 0.22)
            pixels[x, y] = (*color, 255)
    return image


def soft_ellipse(size: tuple[int, int], box: tuple[int, int, int, int], color: tuple[int, int, int], alpha: int, blur: int) -> Image.Image:
    overlay = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    draw.ellipse(box, fill=(*color, alpha))
    return overlay.filter(ImageFilter.GaussianBlur(blur))


def darken(color: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return tuple(clamp(channel * (1.0 - amount)) for channel in color)


def lighten(color: tuple[int, int, int], amount: float) -> tuple[int, int, int]:
    return mix(color, (255, 255, 255), amount)


def glow(base: Image.Image, box: tuple[int, int, int, int], color: tuple[int, int, int], alpha: int, blur: int) -> None:
    add_rgba(base, soft_ellipse(base.size, box, color, alpha, blur))


def draw_soft_line(base: Image.Image, points: list[tuple[int, int]], color: tuple[int, int, int], alpha: int, width: int, blur: int = 7, core: bool = True) -> None:
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")
    draw.line(points, fill=(*color, alpha), width=width, joint="curve")
    add_rgba(base, layer.filter(ImageFilter.GaussianBlur(blur)))
    if core:
        draw = ImageDraw.Draw(base, "RGBA")
        draw.line(points, fill=(*color, min(255, alpha + 38)), width=max(1, width // 3), joint="curve")


def draw_texture(base: Image.Image, theme: dict[str, tuple[int, int, int]], slug: str, seed: int) -> None:
    rng = random.Random(seed)
    width, height = base.size
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    if slug == "blood-moon":
        palette = [(theme["particle"], 0.55), (theme["motif"], 0.32), (theme["gold"], 0.13)]
        streaks = 82
    elif slug == "korok-forest":
        palette = [(theme["particle"], 0.48), (theme["accent"], 0.35), (theme["gold"], 0.17)]
        streaks = 44
    elif slug == "ancient-slate":
        palette = [(theme["particle"], 0.56), (theme["rune"], 0.32), (theme["gold"], 0.12)]
        streaks = 72
    else:
        palette = [(theme["particle"], 0.50), (theme["accent"], 0.28), (theme["gold"], 0.22)]
        streaks = 60
    for _ in range(2100):
        x = rng.randrange(width)
        y = rng.randrange(height)
        roll = rng.random()
        color = palette[0][0] if roll < palette[0][1] else palette[1][0] if roll < palette[0][1] + palette[1][1] else palette[2][0]
        alpha = rng.randrange(6, 27)
        draw.point((x, y), fill=(*color, alpha))
    for _ in range(streaks):
        x = rng.randrange(-120, width)
        y = rng.randrange(30, height - 100)
        length = rng.randrange(32, 115)
        color = theme["rune"] if rng.random() < 0.72 else theme["gold"]
        drift = rng.randrange(-20, 21) if slug != "blood-moon" else rng.randrange(-46, 47)
        draw.line((x, y, x + length, y + drift), fill=(*color, rng.randrange(10, 30)), width=1)
    add_rgba(base, overlay.filter(ImageFilter.GaussianBlur(0.45)))


def draw_motion_texture(base: Image.Image, theme: dict[str, tuple[int, int, int]], slug: str, seed: int) -> None:
    rng = random.Random(seed)
    width, height = base.size
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    palette = [theme["particle"], theme["accent"], theme["gold"]]
    for _ in range(180):
        x = rng.randrange(width)
        y = rng.randrange(height)
        color = palette[rng.randrange(len(palette))]
        alpha = rng.randrange(8, 28)
        draw.point((x, y), fill=(*color, alpha))
    for _ in range(12):
        x = rng.randrange(-20, width)
        y = rng.randrange(8, max(9, height - 8))
        length = rng.randrange(18, 52)
        color = theme["rune"] if rng.random() < 0.70 else theme["gold"]
        drift = rng.randrange(-10, 11)
        draw.line((x, y, x + length, y + drift), fill=(*color, rng.randrange(18, 46)), width=1)
    add_rgba(base, overlay.filter(ImageFilter.GaussianBlur(0.35)))


def draw_rune_band(base: Image.Image, y: int, theme: dict[str, tuple[int, int, int]], seed: int, alpha: int = 38, slug: str = "ancient-slate") -> None:
    rng = random.Random(seed)
    width, _ = base.size
    draw = ImageDraw.Draw(base, "RGBA")
    x = -40
    while x < width + 80:
        span = rng.randrange(34, 84)
        color = theme["gold"] if rng.random() < 0.20 else theme["rune"]
        if slug == "korok-forest":
            draw.arc((x, y - 16, x + span, y + 20), 188, 342, fill=(*color, alpha), width=2)
            if rng.random() < 0.50:
                draw.ellipse((x + span // 2 - 8, y - 17, x + span // 2 + 8, y - 1), outline=(*theme["particle"], alpha + 20), width=2)
        elif slug == "blood-moon":
            draw.line((x, y, x + span // 2, y + rng.randrange(-20, 20), x + span, y), fill=(*color, alpha + 8), width=2)
            if rng.random() < 0.62:
                tick = rng.randrange(18, 42)
                draw.line((x + span // 2, y, x + span // 2 - rng.randrange(8, 28), y + tick), fill=(*theme["motif"], alpha + 24), width=3)
        else:
            draw.line((x, y, x + span, y), fill=(*color, alpha), width=2)
            if rng.random() < 0.55:
                tick = rng.randrange(12, 34)
                draw.line((x + span // 2, y, x + span // 2, y + tick), fill=(*color, alpha), width=2)
            if rng.random() < 0.32:
                draw.ellipse((x + span - 7, y - 7, x + span + 7, y + 7), outline=(*color, alpha), width=2)
        x += span + rng.randrange(22, 64)


def draw_crystal(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, theme: dict[str, tuple[int, int, int]], alpha: int = 170) -> None:
    accent = theme["accent"]
    motif = theme["motif"]
    gold = theme["gold"]
    points = [
        (cx, cy - radius),
        (cx + radius * 3 // 5, cy - radius // 8),
        (cx + radius // 3, cy + radius),
        (cx - radius // 3, cy + radius),
        (cx - radius * 3 // 5, cy - radius // 8),
    ]
    draw.polygon(points, fill=(*mix(accent, motif, 0.24), alpha), outline=(*gold, min(210, alpha + 20)))
    draw.line((cx, cy - radius + 10, cx, cy + radius - 12), fill=(*lighten(accent, 0.16), min(230, alpha + 25)), width=max(2, radius // 12))
    draw.line((cx - radius // 3, cy - radius // 8, cx + radius // 3, cy - radius // 8), fill=(*gold, min(210, alpha + 10)), width=max(2, radius // 14))


def draw_mountains(draw: ImageDraw.ImageDraw, width: int, height: int, baseline: int, color: tuple[int, int, int], alpha: int, offset: int) -> None:
    step = width // 8
    points = [(0, height), (0, baseline)]
    for x in range(-step, width + step * 2, step):
        peak = baseline - 95 - abs(((x + offset) * 31) % 120)
        shoulder = baseline - abs(((x + offset) * 17) % 55)
        points.append((x + step // 2, peak))
        points.append((x + step, shoulder))
    points.extend([(width, height), (0, height)])
    draw.polygon(points, fill=(*color, alpha))


def draw_ruins(draw: ImageDraw.ImageDraw, width: int, height: int, theme: dict[str, tuple[int, int, int]], kind: str) -> None:
    base = theme["base"]
    accent = theme["accent"]
    motif = theme["motif"]
    gold = theme["gold"]
    shadow = theme["shadow"]
    ground_y = height - 138
    draw.rounded_rectangle((0, ground_y, width, height + 40), radius=0, fill=(*shadow, 118))
    pillars = [
        (width * 0.12, ground_y - 120, 56, 160),
        (width * 0.22, ground_y - 190, 72, 235),
        (width * 0.74, ground_y - 170, 66, 210),
        (width * 0.84, ground_y - 105, 52, 150),
    ]
    for x, y, w, h in pillars:
        x = int(x)
        y = int(y)
        w = int(w)
        h = int(h)
        draw.rounded_rectangle((x, y, x + w, y + h), radius=10, fill=(*mix(base, accent, 0.18), 128), outline=(*accent, 58), width=2)
        draw.line((x + 12, y + 22, x + w - 12, y + 22), fill=(*gold, 60), width=2)
        draw.line((x + w // 2, y + 42, x + w // 2, y + h - 16), fill=(*accent, 48), width=2)

    if kind == "forest":
        for i in range(16):
            cx = int(width * (0.04 + i * 0.063))
            cy = ground_y - 205 - (i % 4) * 18
            draw.ellipse((cx - 95, cy - 70, cx + 95, cy + 72), fill=(*mix(theme["horizon"], motif, 0.18), 44))
    elif kind == "blood":
        draw.ellipse((width - 350, 92, width - 155, 287), fill=(*motif, 58))
        draw.ellipse((width - 290, 136, width - 212, 214), fill=(*gold, 44))
    elif kind == "slate":
        for i in range(7):
            y = 120 + i * 82
            draw.arc((-180, y, width + 180, y + 330), 198, 340, fill=(*accent, 30), width=3)
    else:
        draw.ellipse((width - 330, 74, width - 160, 244), fill=(*gold, 68))
        for i in range(8):
            x = 250 + i * 148
            y = 175 + (i % 3) * 36
            draw.ellipse((x, y, x + 18, y + 18), fill=(*accent, 48))


def draw_floating_relics(draw: ImageDraw.ImageDraw, width: int, height: int, theme: dict[str, tuple[int, int, int]], kind: str) -> None:
    accent = theme["accent"]
    motif = theme["motif"]
    gold = theme["gold"]
    base = theme["base"]
    positions = [(0.42, 0.51), (0.50, 0.46), (0.56, 0.57)]
    for index, (px, py) in enumerate(positions):
        cx = int(width * px)
        cy = int(height * py)
        r = 54 - index * 5
        poly = [
            (cx, cy - r),
            (cx + r, cy - r // 3),
            (cx + r // 2, cy + r),
            (cx - r // 2, cy + r),
            (cx - r, cy - r // 3),
        ]
        draw.polygon(poly, fill=(*mix(base, accent, 0.22), 160), outline=(*accent, 70))
        for offset in (-16, 0, 16):
            draw.ellipse((cx - 24, cy + offset - 4, cx - 10, cy + offset + 10), fill=(*gold, 96))
            draw.ellipse((cx + 8, cy + offset - 4, cx + 22, cy + offset + 10), fill=(*motif, 74))


def draw_particles(draw: ImageDraw.ImageDraw, width: int, height: int, theme: dict[str, tuple[int, int, int]], seed: int, slug: str) -> None:
    accent = theme["accent"]
    motif = theme["motif"]
    gold = theme["gold"]
    count = 170 if slug in {"korok-forest", "blood-moon"} else 140
    for i in range(count):
        x = (i * 139 + seed * 53) % width
        y = 42 + ((i * 83 + seed * 97) % (height - 190))
        radius = 1 + ((i + seed) % (5 if slug == "blood-moon" else 4))
        if slug == "korok-forest":
            color = gold if i % 4 == 0 else theme["particle"]
            alpha = 38 + (i % 6) * 10
        elif slug == "blood-moon":
            color = gold if i % 9 == 0 else motif if i % 3 == 0 else theme["particle"]
            alpha = 42 + (i % 7) * 11
        else:
            color = gold if i % 8 == 0 else accent
            alpha = 28 + (i % 6) * 9
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*color, alpha))


def draw_great_plateau_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    glow(image, (width - 430, 12, width - 82, 360), theme["gold"], 58, 70)
    draw.ellipse((width - 310, 64, width - 144, 230), fill=(*theme["gold"], 88))
    draw_mountains(draw, width, height, height - 198, darken(theme["horizon"], 0.34), 154, 42)
    draw_mountains(draw, width, height, height - 130, darken(theme["base"], 0.44), 206, 129)
    for x, y, w, h in [(148, 496, 76, 250), (330, 430, 96, 330), (1350, 460, 88, 302), (1544, 522, 72, 230)]:
        draw.rounded_rectangle((x, y, x + w, y + h), radius=18, fill=(*darken(theme["horizon"], 0.22), 136), outline=(*theme["accent"], 54), width=2)
        draw.line((x + 18, y + 42, x + w - 18, y + 42), fill=(*theme["gold"], 54), width=2)
        draw.line((x + w // 2, y + 70, x + w // 2, y + h - 28), fill=(*theme["accent"], 48), width=2)
    plateau = [(0, height), (0, height - 116), (260, height - 150), (620, height - 118), (930, height - 176), (1240, height - 134), (width, height - 164), (width, height)]
    draw.polygon(plateau, fill=(*darken(theme["secondary"], 0.34), 150))
    gate_x = width // 2
    draw.rounded_rectangle((gate_x - 120, height - 330, gate_x - 62, height - 128), radius=18, fill=(*darken(theme["horizon"], 0.20), 146), outline=(*theme["accent"], 70), width=2)
    draw.rounded_rectangle((gate_x + 62, height - 330, gate_x + 120, height - 128), radius=18, fill=(*darken(theme["horizon"], 0.20), 146), outline=(*theme["accent"], 70), width=2)
    draw.arc((gate_x - 132, height - 390, gate_x + 132, height - 116), 198, 342, fill=(*theme["gold"], 96), width=7)
    for i in range(22):
        x = (i * 173 + 70) % width
        y = height - 180 + (i % 4) * 17
        draw.arc((x, y - 35, x + 96, y + 44), 170, 332, fill=(*theme["accent"], 36), width=2)
    draw_rune_band(image, height - 108, theme, 58, 32, "great-plateau")


def draw_slate_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    glow(image, (width - 560, 35, width - 90, 420), theme["particle"], 34, 86)
    draw_mountains(draw, width, height, height - 220, darken(theme["horizon"], 0.24), 130, 22)
    draw_mountains(draw, width, height, height - 156, darken(theme["base"], 0.18), 190, 116)
    snow = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(snow, "RGBA")
    for i in range(130):
        x = (i * 163 + 85) % width
        y = 58 + (i * 89) % (height - 158)
        r = 1 + (i % 3)
        sdraw.ellipse((x - r, y - r, x + r, y + r), fill=(*theme["particle"], 36 + (i % 4) * 12))
    add_rgba(image, snow.filter(ImageFilter.GaussianBlur(0.35)))
    ground = [(0, height), (0, height - 116), (310, height - 156), (690, height - 118), (1040, height - 178), (1390, height - 136), (width, height - 158), (width, height)]
    draw.polygon(ground, fill=(*mix(theme["base"], theme["horizon"], 0.18), 174))
    for x, y, w, h in [(230, 520, 86, 220), (382, 456, 108, 286), (1270, 454, 118, 292), (1468, 532, 82, 205)]:
        draw.rounded_rectangle((x, y, x + w, y + h), radius=22, fill=(*darken(theme["horizon"], 0.16), 132), outline=(*theme["accent"], 48), width=2)
        draw.rounded_rectangle((x - 18, y - 14, x + w + 18, y + 26), radius=20, fill=(*theme["particle"], 70))
    gate_x = width // 2 + 175
    draw.rounded_rectangle((gate_x - 124, height - 326, gate_x - 66, height - 122), radius=20, fill=(*darken(theme["horizon"], 0.20), 140), outline=(*theme["accent"], 58), width=2)
    draw.rounded_rectangle((gate_x + 66, height - 326, gate_x + 124, height - 122), radius=20, fill=(*darken(theme["horizon"], 0.20), 140), outline=(*theme["accent"], 58), width=2)
    draw.arc((gate_x - 146, height - 396, gate_x + 146, height - 112), 198, 342, fill=(*theme["rune"], 88), width=6)
    draw.ellipse((gate_x - 22, height - 272, gate_x + 22, height - 228), fill=(*theme["gold"], 112))
    for y in [178, 286, 404]:
        draw_soft_line(image, [(-80, y), (260, y - 18), (620, y + 18), (980, y - 12), (width + 80, y + 8)], theme["particle"], 13, 24, 22, core=False)


def draw_forest_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    glow(image, (width - 460, 34, width - 86, 390), theme["particle"], 48, 62)
    draw_mountains(draw, width, height, height - 178, darken(theme["horizon"], 0.38), 128, 91)
    for i in range(18):
        x = -120 + i * 120
        y = 92 + (i % 5) * 22
        draw.ellipse((x, y, x + 260, y + 155), fill=(*mix(theme["horizon"], theme["motif"], 0.22), 58))
        draw.ellipse((x + 38, y + 58, x + 232, y + 198), fill=(*mix(theme["base"], theme["motif"], 0.15), 48))
    for x, y, scale in [(185, 154, 1.1), (430, 104, 0.8), (1260, 116, 0.95), (1515, 170, 1.2)]:
        leaf = [
            (x, y - int(90 * scale)),
            (x + int(86 * scale), y - int(16 * scale)),
            (x + int(28 * scale), y + int(95 * scale)),
            (x, y + int(66 * scale)),
            (x - int(28 * scale), y + int(95 * scale)),
            (x - int(86 * scale), y - int(16 * scale)),
        ]
        draw.polygon(leaf, fill=(*darken(theme["horizon"], 0.32), 92), outline=(*theme["accent"], 30))
    for x, width_trunk in [(84, 54), (245, 74), (1500, 64), (1650, 82)]:
        draw.rounded_rectangle((x, 300, x + width_trunk, height), radius=28, fill=(*darken(theme["base"], 0.56), 185))
        draw.line((x + width_trunk // 2, 330, x + width_trunk // 2, height), fill=(*theme["gold"], 26), width=2)
    clearing = (width // 2 - 340, height - 330, width // 2 + 340, height - 76)
    draw.ellipse(clearing, fill=(*mix(theme["base"], theme["motif"], 0.10), 102), outline=(*theme["gold"], 46), width=3)
    for i, (x, y) in enumerate([(690, 520), (820, 460), (1000, 488), (1118, 548)]):
        draw.rounded_rectangle((x, y, x + 76, y + 132), radius=20, fill=(*darken(theme["horizon"], 0.24), 126), outline=(*theme["accent"], 58), width=2)
        draw.ellipse((x + 24, y + 34, x + 52, y + 62), fill=(*theme["gold"], 64 + i * 10))
    mask_cx, mask_cy = width // 2, height - 245
    draw.polygon([(mask_cx, mask_cy - 72), (mask_cx + 66, mask_cy - 10), (mask_cx + 26, mask_cy + 78), (mask_cx, mask_cy + 54), (mask_cx - 26, mask_cy + 78), (mask_cx - 66, mask_cy - 10)], fill=(*darken(theme["secondary"], 0.14), 120), outline=(*theme["particle"], 70))
    draw.ellipse((mask_cx - 28, mask_cy - 8, mask_cx - 12, mask_cy + 10), fill=(*theme["gold"], 110))
    draw.ellipse((mask_cx + 12, mask_cy - 8, mask_cx + 28, mask_cy + 10), fill=(*theme["gold"], 110))
    draw.arc((mask_cx - 34, mask_cy + 5, mask_cx + 34, mask_cy + 52), 35, 145, fill=(*theme["base"], 130), width=3)
    for i in range(11):
        x = 540 + i * 82
        y = height - 185 + (i % 3) * 12
        draw.arc((x, y - 34, x + 86, y + 42), 170, 330, fill=(*theme["accent"], 40), width=2)
    for i in range(42):
        x = (i * 311 + 180) % width
        y = 160 + (i * 137) % (height - 280)
        radius = 4 + (i % 3)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(*theme["gold"], 96))
    draw_rune_band(image, height - 122, theme, 73, 28, "korok-forest")


def draw_blood_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    moon = (width - 456, 34, width - 112, 378)
    glow(image, (moon[0] - 95, moon[1] - 95, moon[2] + 95, moon[3] + 95), theme["motif"], 112, 62)
    draw.ellipse(moon, fill=(*theme["accent"], 164))
    draw.ellipse((moon[0] + 72, moon[1] + 58, moon[2] - 114, moon[3] - 96), fill=(*darken(theme["motif"], 0.30), 76))
    draw.ellipse((moon[0] + 170, moon[1] + 92, moon[0] + 214, moon[1] + 138), fill=(*theme["gold"], 38))
    draw_mountains(draw, width, height, height - 212, darken(theme["horizon"], 0.35), 170, 19)
    draw_mountains(draw, width, height, height - 138, darken(theme["base"], 0.36), 218, 137)
    for x, y, w, h in [(210, 506, 74, 280), (382, 420, 104, 360), (1180, 492, 82, 294), (1380, 394, 120, 404), (1572, 540, 72, 230)]:
        draw.polygon([(x, y), (x + w // 2, y - 82), (x + w, y), (x + w, y + h), (x, y + h)], fill=(*darken(theme["base"], 0.54), 196), outline=(*theme["motif"], 54))
        draw.line((x + w // 2, y - 44, x + w // 2, y + h - 32), fill=(*theme["gold"], 32), width=2)
    for y in [260, 438, 604]:
        points = [(-60, y), (250, y - 24), (580, y + 22), (890, y - 18)]
        draw_soft_line(image, points, theme["accent"], 18, 34, 28, core=False)
        points = [(1040, y - 16), (1310, y + 16), (width + 80, y - 8)]
        draw_soft_line(image, points, theme["accent"], 16, 30, 24, core=False)
    for y in [196, 388, 540]:
        points = [(-80, y + 40), (250, y - 4), (570, y + 30), (850, y - 20)]
        draw_soft_line(image, points, theme["motif"], 24, 18, 15, core=True)
        points = [(1040, y - 22), (1330, y + 18), (width + 90, y + 6)]
        draw_soft_line(image, points, theme["motif"], 22, 18, 15, core=True)
    for i in range(22):
        x = 70 + i * 84
        peak = height - 172 - ((i * 43) % 150)
        draw.polygon([(x, height - 106), (x + 25, peak), (x + 54, height - 106)], fill=(*darken(theme["base"], 0.68), 156), outline=(*theme["accent"], 38))
    for i in range(80):
        x = (i * 197 + 90) % width
        y = 86 + (i * 131) % (height - 190)
        r = 2 + (i % 6)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=(*theme["motif"], 58 + (i % 4) * 18))
    draw_rune_band(image, height - 98, theme, 91, 34, "blood-moon")


def draw_gloom_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    glow(image, (width - 560, 40, width - 80, 430), theme["accent"], 68, 86)
    glow(image, (width - 420, height - 360, width - 70, height - 20), theme["rune"], 36, 72)
    draw_mountains(draw, width, height, height - 188, darken(theme["horizon"], 0.34), 140, 55)
    draw_mountains(draw, width, height, height - 122, darken(theme["base"], 0.40), 206, 131)
    for i in range(9):
        cx = 520 + i * 130
        cy = 218 + ((i * 73) % 180)
        r = 34 + (i % 4) * 16
        points = [(cx, cy - r), (cx + r, cy - r // 5), (cx + r // 2, cy + r), (cx - r // 2, cy + r), (cx - r, cy - r // 5)]
        draw.polygon(points, fill=(*mix(theme["horizon"], theme["accent"], 0.22), 108), outline=(*theme["accent"], 64))
    for x, y, w, h in [(240, 485, 86, 240), (392, 424, 124, 330), (1240, 426, 106, 310), (1458, 370, 138, 390)]:
        draw.polygon([(x, y), (x + w // 2, y - 72), (x + w, y), (x + w, y + h), (x, y + h)], fill=(*darken(theme["base"], 0.30), 178), outline=(*theme["accent"], 52))
        draw.line((x + w // 2, y - 34, x + w // 2, y + h - 28), fill=(*theme["rune"], 42), width=2)
    for i in range(14):
        x = 700 + (i * 67) % 610
        y = height - 232 + (i % 3) * 26
        draw.polygon([(x, y - 58), (x + 28, y), (x, y + 72), (x - 28, y)], fill=(*theme["accent"], 82), outline=(*theme["rune"], 58))
    for y in [238, 410, 574]:
        draw_soft_line(image, [(-80, y + 28), (300, y - 14), (690, y + 22), (1030, y - 18), (width + 80, y + 12)], theme["accent"], 20, 34, 26, core=False)
        draw_soft_line(image, [(160, y + 54), (510, y + 8), (880, y + 44), (1320, y - 8)], theme["rune"], 13, 18, 18, core=True)
    for i in range(100):
        x = (i * 211 + 130) % width
        y = 80 + (i * 113) % (height - 180)
        r = 2 + (i % 5)
        color = theme["rune"] if i % 9 == 0 else theme["particle"]
        draw.ellipse((x - r, y - r, x + r, y + r), fill=(*color, 38 + (i % 5) * 9))


def draw_text_safe_regions(image: Image.Image, theme: dict[str, tuple[int, int, int]], slug: str) -> None:
    width, height = image.size
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    shadow = theme["shadow"]
    alpha_boost = 18 if slug == "blood-moon" else 8
    draw.rounded_rectangle((-80, -60, 760, 210), radius=90, fill=(*shadow, 76 + alpha_boost))
    draw.rounded_rectangle((-120, height - 290, 760, height + 120), radius=120, fill=(*shadow, 88 + alpha_boost))
    draw.rounded_rectangle((width // 2 - 410, height - 360, width // 2 + 430, height - 80), radius=140, fill=(*shadow, 58 + alpha_boost))
    top_right_alpha = 72 if slug == "blood-moon" else 40
    draw.rounded_rectangle((width - 600, 28, width + 90, 330), radius=130, fill=(*shadow, top_right_alpha))
    add_rgba(image, overlay.filter(ImageFilter.GaussianBlur(42)))


def draw_generic_wallpaper(image: Image.Image, theme: dict[str, tuple[int, int, int]]) -> None:
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    draw_mountains(draw, width, height, height - 190, darken(theme["horizon"], 0.30), 132, 27)
    draw_mountains(draw, width, height, height - 128, darken(theme["base"], 0.42), 190, 91)
    draw_ruins(draw, width, height, theme, "generic")
    draw_floating_relics(draw, width, height, theme, "generic")
    for index in range(18):
        x = 120 + ((index * 173) % (width - 240))
        y = 130 + ((index * 97) % (height - 330))
        radius = 22 + (index % 5) * 7
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), outline=(*theme["accent"], 28 + (index % 4) * 12), width=2)
    draw_rune_band(image, height - 94, theme, 37, 28, "generic")


def make_wallpaper(slug: str, theme: dict[str, tuple[int, int, int]]) -> Image.Image:
    width, height = WALLPAPER_SIZE
    image = gradient(WALLPAPER_SIZE, theme["base"], theme["horizon"], theme["accent"])
    glow(image, (-240, 76, 580, 720), theme["accent"], 34, 120)
    glow_color = theme["accent"] if slug == "blood-moon" else theme["particle"] if slug == "korok-forest" else theme["gold"]
    glow(image, (width - 660, 10, width + 120, 640), glow_color, 32, 108)
    draw_texture(image, theme, slug, sum(ord(ch) for ch in slug))
    wallpaper_drawers = {
        "great-plateau": draw_great_plateau_wallpaper,
        "ancient-slate": draw_slate_wallpaper,
        "korok-forest": draw_forest_wallpaper,
        "blood-moon": draw_blood_wallpaper,
        "gloom-depths": draw_gloom_wallpaper,
    }
    wallpaper_drawers.get(slug, draw_generic_wallpaper)(image, theme)
    draw = ImageDraw.Draw(image, "RGBA")
    draw_particles(draw, width, height, theme, len(slug), slug)
    draw_text_safe_regions(image, theme, slug)
    vignette = Image.new("RGBA", WALLPAPER_SIZE, (0, 0, 0, 0))
    vdraw = ImageDraw.Draw(vignette, "RGBA")
    vdraw.rectangle((-20, -20, width + 20, height + 20), outline=(*theme["shadow"], 128), width=30)
    vdraw.rectangle((-60, -20, 170, height + 20), fill=(*theme["shadow"], 38))
    vdraw.rectangle((-20, height - 145, width + 20, height + 20), fill=(*theme["shadow"], 42))
    add_rgba(image, vignette.filter(ImageFilter.GaussianBlur(34)))
    return image.convert("RGB")


def draw_avatar_emblem(draw: ImageDraw.ImageDraw, size: int, theme: dict[str, tuple[int, int, int]], slug: str) -> None:
    cx = cy = size // 2
    accent = theme["accent"]
    motif = theme["motif"]
    gold = theme["gold"]
    base = theme["base"]
    horizon = theme["horizon"]
    stroke = max(6, size // 42)
    if slug == "great-plateau":
        mountain = [
            (cx - size * 0.30, cy + size * 0.22),
            (cx - size * 0.08, cy - size * 0.22),
            (cx + size * 0.06, cy + size * 0.04),
            (cx + size * 0.18, cy - size * 0.14),
            (cx + size * 0.32, cy + size * 0.22),
        ]
        draw.polygon(mountain, fill=(*mix(horizon, accent, 0.24), 214), outline=(*gold, 126))
        draw.line((cx - size * 0.08, cy - size * 0.21, cx - size * 0.19, cy + size * 0.02), fill=(*lighten(accent, 0.20), 190), width=stroke)
        draw.ellipse((cx + size * 0.16, cy - size * 0.32, cx + size * 0.30, cy - size * 0.18), fill=(*gold, 220))
        draw.arc((cx - size * 0.35, cy + size * 0.05, cx + size * 0.35, cy + size * 0.38), 190, 350, fill=(*accent, 168), width=stroke)
    elif slug == "ancient-slate":
        for radius, alpha in [(size * 0.32, 230), (size * 0.24, 170), (size * 0.15, 120)]:
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=(*motif, alpha), width=stroke)
        eye = [
            (cx - size * 0.32, cy),
            (cx - size * 0.15, cy - size * 0.13),
            (cx, cy - size * 0.16),
            (cx + size * 0.15, cy - size * 0.13),
            (cx + size * 0.32, cy),
            (cx + size * 0.15, cy + size * 0.13),
            (cx, cy + size * 0.16),
            (cx - size * 0.15, cy + size * 0.13),
        ]
        draw.polygon(eye, fill=(*darken(horizon, 0.10), 210), outline=(*gold, 140))
        draw.ellipse((cx - size * 0.085, cy - size * 0.085, cx + size * 0.085, cy + size * 0.085), fill=(*gold, 220))
        for angle in range(0, 360, 45):
            x2 = cx + int(math.cos(math.radians(angle)) * size * 0.40)
            y2 = cy + int(math.sin(math.radians(angle)) * size * 0.40)
            draw.line((cx, cy, x2, y2), fill=(*motif, 84), width=max(2, stroke // 2))
    elif slug == "korok-forest":
        leaf = [
            (cx, cy - size * 0.34),
            (cx + size * 0.29, cy - size * 0.07),
            (cx + size * 0.12, cy + size * 0.33),
            (cx, cy + size * 0.26),
            (cx - size * 0.12, cy + size * 0.33),
            (cx - size * 0.29, cy - size * 0.07),
        ]
        draw.polygon(leaf, fill=(*mix(accent, motif, 0.72), 214), outline=(*gold, 118))
        draw.line((cx, cy - size * 0.27, cx, cy + size * 0.24), fill=(*base, 190), width=stroke)
        draw.ellipse((cx - size * 0.12, cy - size * 0.02, cx - size * 0.05, cy + size * 0.05), fill=(*gold, 210))
        draw.ellipse((cx + size * 0.05, cy - size * 0.02, cx + size * 0.12, cy + size * 0.05), fill=(*gold, 210))
        draw.arc((cx - size * 0.16, cy + size * 0.03, cx + size * 0.16, cy + size * 0.24), 28, 152, fill=(*base, 180), width=max(3, stroke // 2))
    elif slug == "blood-moon":
        draw.ellipse((cx - size * 0.29, cy - size * 0.31, cx + size * 0.29, cy + size * 0.27), fill=(*motif, 214), outline=(*gold, 96), width=stroke)
        draw.ellipse((cx - size * 0.07, cy - size * 0.19, cx + size * 0.18, cy + size * 0.06), fill=(*gold, 54))
        for xoff, h in [(-0.20, 0.20), (-0.03, 0.31), (0.15, 0.24)]:
            x = cx + size * xoff
            draw.polygon([(x - size * 0.04, cy + size * 0.29), (x, cy + size * 0.02), (x + size * 0.04, cy + size * 0.29)], fill=(*darken(base, 0.28), 225), outline=(*gold, 55))
        draw.arc((cx - size * 0.33, cy - size * 0.08, cx + size * 0.33, cy + size * 0.42), 204, 336, fill=(*gold, 190), width=stroke)
    else:
        diamond = [
            (cx, cy - size * 0.34),
            (cx + size * 0.31, cy),
            (cx, cy + size * 0.34),
            (cx - size * 0.31, cy),
        ]
        draw.polygon(diamond, fill=(*mix(horizon, accent, 0.42), 216), outline=(*gold, 136))
        draw.ellipse((cx - size * 0.17, cy - size * 0.17, cx + size * 0.17, cy + size * 0.17), outline=(*motif, 180), width=stroke)
        draw.line((cx, cy - size * 0.28, cx, cy + size * 0.28), fill=(*gold, 156), width=stroke)
        draw.line((cx - size * 0.25, cy, cx + size * 0.25, cy), fill=(*accent, 138), width=max(3, stroke // 2))


def make_avatar(slug: str, theme: dict[str, tuple[int, int, int]]) -> Image.Image:
    size = AVATAR_SIZE * SCALE
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image, "RGBA")
    base = theme["base"]
    horizon = theme["horizon"]
    accent = theme["accent"]
    gold = theme["gold"]

    for y in range(size):
        t = y / max(1, size - 1)
        color = mix(darken(base, 0.06), lighten(horizon, 0.04), t)
        draw.line((0, y, size, y), fill=(*color, 255))

    glow(image, (size // 2 - 500, size // 2 - 520, size // 2 + 500, size // 2 + 520), accent, 92, 110)
    glow(image, (size - 670, 80, size - 170, 580), gold, 44, 72)
    draw_texture(image, theme, slug, sum(ord(ch) for ch in slug) + 700)
    draw = ImageDraw.Draw(image, "RGBA")
    draw.rounded_rectangle((70, 70, size - 70, size - 70), radius=142, outline=(*accent, 210), width=18)
    draw.rounded_rectangle((104, 104, size - 104, size - 104), radius=116, outline=(*gold, 170), width=8)
    draw.ellipse((size * 0.18, size * 0.18, size * 0.82, size * 0.82), fill=(*darken(base, 0.08), 138), outline=(*accent, 126), width=8)
    for index in range(8):
        angle = index * math.tau / 8.0
        x = size // 2 + int(math.cos(angle) * size * 0.405)
        y = size // 2 + int(math.sin(angle) * size * 0.405)
        if slug == "korok-forest":
            draw.ellipse((x - 18, y - 18, x + 18, y + 18), fill=(*theme["particle"], 70 + (index % 2) * 22))
        elif slug == "blood-moon":
            draw.polygon([(x, y - 28), (x + 18, y + 18), (x, y + 30), (x - 18, y + 18)], fill=(*theme["accent"], 86 + (index % 2) * 24))
        elif slug == "ancient-slate":
            draw.line((x - 24, y, x + 24, y), fill=(*theme["particle"], 118), width=8)
            draw.line((x, y - 24, x, y + 24), fill=(*theme["particle"], 82), width=5)
        else:
            draw.rounded_rectangle((x - 22, y - 22, x + 22, y + 22), radius=6, fill=(*gold, 74 + (index % 2) * 24))

    draw_avatar_emblem(draw, size, theme, slug)

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size, size), radius=88 * SCALE, fill=255)
    image.putalpha(mask)
    return image.resize((AVATAR_SIZE, AVATAR_SIZE), Image.Resampling.LANCZOS)


def make_detail(slug: str, theme: dict[str, tuple[int, int, int]]) -> Image.Image:
    width, height = DETAIL_SIZE
    image = gradient(DETAIL_SIZE, darken(theme["horizon"], 0.30), darken(theme["base"], 0.04), theme["accent"])
    draw_texture(image, theme, slug, 700 + len(slug))
    draw = ImageDraw.Draw(image, "RGBA")
    draw.rectangle((0, 0, width, height), outline=(*theme["accent"], 84), width=2)
    draw.rectangle((0, height - 42, width, height), fill=(*theme["base"], 112))
    draw_rune_band(image, height - 30, theme, 300 + len(slug), 54, slug)
    if slug == "great-plateau":
        for offset in range(-70, width + 140, 130):
            draw.arc((offset, 30, offset + 170, 150), 198, 342, fill=(*theme["accent"], 104), width=4)
            draw.ellipse((offset + 92, 48, offset + 112, 68), fill=(*theme["gold"], 132))
    elif slug == "ancient-slate":
        for x in range(-20, width + 50, 74):
            draw.line((x, 30, x + 48, 104), fill=(*theme["particle"], 120), width=3)
            draw.line((x + 24, 50, x + 8, 80), fill=(*theme["rune"], 84), width=2)
        draw.line((24, 34, width - 24, 34), fill=(*theme["gold"], 78), width=2)
    elif slug == "korok-forest":
        for index in range(20):
            x = 18 + (index * 37) % (width - 36)
            y = 26 + (index * 29) % 86
            draw.ellipse((x - 12, y - 7, x + 12, y + 7), fill=(*theme["particle"], 96), outline=(*theme["gold"], 70), width=1)
            draw.line((x, y - 5, x, y + 11), fill=(*theme["secondary"], 132), width=2)
    elif slug == "blood-moon":
        draw.ellipse((width - 156, 18, width - 40, 134), fill=(*theme["accent"], 104), outline=(*theme["gold"], 90), width=3)
        for index in range(9):
            y = 20 + index * 14
            draw.line((28, y, width - 52, y + (index % 3 - 1) * 12), fill=(*theme["motif"], 88), width=4)
    elif slug == "gloom-depths":
        for index in range(8):
            x = 46 + index * 74
            draw.ellipse((x, 48, x + 58, 88), outline=(*theme["accent"], 118), width=3)
            draw.line((x + 29, 22, x + 29, 120), fill=(*theme["rune"], 74), width=2)
        draw_soft_line(image, [(16, 122), (170, 96), (332, 118), (486, 92), (624, 112)], theme["rune"], 44, 9, 8, core=True)
    else:
        draw_rune_band(image, height // 2, theme, 411, 52, slug)
    return image.convert("RGB")


def make_motion(slug: str, theme: dict[str, tuple[int, int, int]]) -> list[Image.Image]:
    width, height = MOTION_SIZE
    frames: list[Image.Image] = []
    for frame_index in range(MOTION_FRAMES):
        phase = frame_index / MOTION_FRAMES
        image = gradient(MOTION_SIZE, darken(theme["base"], 0.12), darken(theme["horizon"], 0.18), theme["accent"])
        draw_motion_texture(image, theme, slug, 1800 + len(slug))
        draw = ImageDraw.Draw(image, "RGBA")
        draw.rounded_rectangle((2, 2, width - 3, height - 3), radius=16, outline=(*theme["accent"], 96), width=2)
        draw.rounded_rectangle((8, 8, width - 9, height - 9), radius=12, outline=(*theme["gold"], 56), width=1)
        pulse = 0.5 + 0.5 * math.sin(phase * math.tau)
        drift = int(round(math.sin(phase * math.tau) * 11))
        glow(image, (width - 72 + drift, -20, width + 44 + drift, 92), theme["accent"], 54 + int(pulse * 44), 22)
        glow(image, (-38 - drift, height - 70, 76 - drift, height + 26), theme["gold"], 22 + int((1.0 - pulse) * 28), 18)
        draw = ImageDraw.Draw(image, "RGBA")
        draw_rune_band(image, height - 18, theme, 1200 + frame_index, 42, slug)

        cx = width // 2
        cy = height // 2 - 2
        if slug == "great-plateau":
            ridge = [
                (18, cy + 26),
                (46, cy - 6 - drift // 2),
                (68, cy + 16),
                (92, cy - 24 + drift // 3),
                (126, cy + 25),
                (width - 14, cy + 10),
            ]
            draw.line(ridge, fill=(*theme["accent"], 136), width=4, joint="curve")
            draw.arc((22, 22, width - 22, height + 44), 198, 342, fill=(*theme["gold"], 116), width=3)
            draw.ellipse((width - 48 + drift // 2, 18, width - 28 + drift // 2, 38), fill=(*theme["gold"], 150))
        elif slug == "ancient-slate":
            radius = 22 + int(pulse * 8)
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=(*theme["particle"], 138), width=3)
            draw.ellipse((cx - 10, cy - 10, cx + 10, cy + 10), fill=(*theme["gold"], 156))
            for angle in range(0, 360, 45):
                radians = math.radians(angle + frame_index * 7)
                x2 = cx + int(math.cos(radians) * 58)
                y2 = cy + int(math.sin(radians) * 34)
                draw.line((cx, cy, x2, y2), fill=(*theme["accent"], 62), width=2)
            for index in range(11):
                x = 8 + index * 18
                y = 18 + ((index * 13 + frame_index * 5) % 52)
                draw.line((x, y, x + 8, y + 11), fill=(*theme["particle"], 112), width=1)
        elif slug == "korok-forest":
            leaf = [
                (cx, cy - 30 - drift // 3),
                (cx + 36, cy - 7),
                (cx + 14, cy + 36),
                (cx, cy + 24),
                (cx - 14, cy + 36),
                (cx - 36, cy - 7),
            ]
            draw.polygon(leaf, fill=(*mix(theme["accent"], theme["motif"], 0.52), 142), outline=(*theme["gold"], 112))
            draw.line((cx, cy - 24, cx, cy + 28), fill=(*theme["base"], 150), width=3)
            for index in range(14):
                x = 12 + ((index * 23 + frame_index * 7) % (width - 24))
                y = 18 + ((index * 17 + frame_index * 3) % 54)
                draw.ellipse((x - 2, y - 2, x + 3, y + 3), fill=(*theme["gold"], 74 + (index % 4) * 16))
        elif slug == "blood-moon":
            radius = 30 + int(pulse * 7)
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=(*theme["accent"], 134), outline=(*theme["gold"], 74), width=2)
            draw.ellipse((cx - 4, cy - 18, cx + 22, cy + 8), fill=(*theme["gold"], 38))
            for index, xoff in enumerate((-23, -3, 18)):
                x = cx + xoff + int(math.sin(phase * math.tau + index) * 4)
                draw.polygon([(x - 5, cy + 34), (x, cy + 6), (x + 5, cy + 34)], fill=(*darken(theme["base"], 0.36), 220))
            draw_soft_line(image, [(6, 28 + drift), (48, 22), (98, 36 - drift), (174, 25)], theme["motif"], 42, 6, 6, core=True)
        else:
            diamond_radius = 30 + int(pulse * 8)
            diamond = [
                (cx, cy - diamond_radius),
                (cx + diamond_radius, cy),
                (cx, cy + diamond_radius),
                (cx - diamond_radius, cy),
            ]
            draw.polygon(diamond, fill=(*mix(theme["horizon"], theme["accent"], 0.42), 132), outline=(*theme["rune"], 112))
            draw.ellipse((cx - 18, cy - 18, cx + 18, cy + 18), outline=(*theme["accent"], 138), width=3)
            draw_soft_line(image, [(8, 70), (46, 56 + drift), (88, 72), (132, 52 - drift), (174, 66)], theme["rune"], 48, 5, 5, core=True)

        frames.append(image.convert("RGB"))
    return frames


def save_asset(name: str, factory: Callable[[], Image.Image], overwrite: bool) -> None:
    ASSETS.mkdir(exist_ok=True)
    output = ASSETS / name
    if output.exists() and not overwrite:
        raise SystemExit(f"Refusing to overwrite existing asset without --overwrite: {output.relative_to(ROOT)}")
    factory().save(output, optimize=True)
    print(output.relative_to(ROOT))


def save_gif_asset(name: str, factory: Callable[[], list[Image.Image]], overwrite: bool) -> None:
    ASSETS.mkdir(exist_ok=True)
    output = ASSETS / name
    if output.exists() and not overwrite:
        raise SystemExit(f"Refusing to overwrite existing asset without --overwrite: {output.relative_to(ROOT)}")
    frames = factory()
    if not frames:
        raise SystemExit(f"No frames generated for {output.relative_to(ROOT)}")
    frames[0].save(
        output,
        save_all=True,
        append_images=frames[1:],
        duration=72,
        loop=0,
        optimize=True,
    )
    print(output.relative_to(ROOT))


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate BurpTheme wallpaper, avatar, and detail PNG assets.")
    parser.add_argument("--list", action="store_true", help="List generated asset paths without writing files.")
    parser.add_argument("--list-themes", action="store_true", help="List active generator theme slugs without writing files.")
    parser.add_argument("--theme", action="append", choices=sorted(THEMES.keys()), help="Generate or list one theme slug. Repeat for multiple themes.")
    parser.add_argument("--asset", choices=("all", "wallpaper", "avatar", "preview", "detail", "motion"), default="avatar", help="Limit generation/listing to one asset type. Defaults to avatar to protect curated wallpapers.")
    parser.add_argument("--overwrite", action="store_true", help="Allow generation to overwrite existing assets.")
    args = parser.parse_args()
    selected_slugs = args.theme if args.theme else list(THEMES.keys())
    if args.list_themes:
        for slug in sorted(THEMES.keys()):
            print(slug)
        return
    if args.list:
        for slug in selected_slugs:
            theme = THEMES[slug]
            if args.asset in {"all", "wallpaper"}:
                print(f"assets/{theme['wallpaper']}")
            if args.asset in {"all", "avatar"}:
                print(f"assets/{theme['avatar']}")
            if args.asset in {"all", "preview"}:
                print(f"assets/{theme['preview']}")
            if args.asset in {"all", "detail"}:
                print(f"assets/{theme['detail']}")
            if args.asset in {"all", "motion"}:
                print(f"assets/{theme['motion']}")
        return
    require_pillow()
    for slug in selected_slugs:
        theme = THEMES[slug]
        if args.asset in {"all", "wallpaper"}:
            save_asset(theme["wallpaper"], lambda slug=slug, theme=theme: make_wallpaper(slug, theme), args.overwrite)
        if args.asset in {"all", "avatar"}:
            save_asset(theme["avatar"], lambda slug=slug, theme=theme: make_avatar(slug, theme), args.overwrite)
        if args.asset in {"all", "preview"}:
            print(f"Preview GIF generation is curated; use checked-in assets/{theme['preview']}.")
        if args.asset in {"all", "detail"}:
            save_asset(theme["detail"], lambda slug=slug, theme=theme: make_detail(slug, theme), args.overwrite)
        if args.asset in {"all", "motion"}:
            save_gif_asset(theme["motion"], lambda slug=slug, theme=theme: make_motion(slug, theme), args.overwrite)


if __name__ == "__main__":
    main()
