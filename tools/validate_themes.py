#!/usr/bin/env python3
from __future__ import annotations

import argparse
import ast
import binascii
import re
import struct
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THEME_SOURCE = ROOT / "src" / "burp" / "arcade" / "BurpTheme.java"
GENERATOR = ROOT / "tools" / "generate_theme_assets.py"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
GIF_SIGNATURES = (b"GIF87a", b"GIF89a")
WALLPAPER_SIZE = (1812, 868)
AVATAR_SIZE = (512, 512)
DETAIL_SIZE = (640, 180)
MOTION_SIZE = (180, 96)
PREVIEW_MIN_SIZE = (240, 100)
MOTION_MIN_FRAMES = 12


THEME_PATTERN = re.compile(
    r'^\s*([A-Z0-9_]+)\s*\(\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*"([^"]+)"',
    re.DOTALL,
)
COLOR_PATTERN = re.compile(r"new Color\((\d+),\s*(\d+),\s*(\d+)\)")


def enum_constant_chunks(source: str) -> list[str]:
    enum_index = source.find("enum BurpTheme")
    if enum_index < 0:
        return []
    brace_index = source.find("{", enum_index)
    if brace_index < 0:
        return []

    chunks: list[str] = []
    current: list[str] = []
    depth = 0
    in_string = False
    escaped = False
    for char in source[brace_index + 1:]:
        if in_string:
            current.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
            current.append(char)
        elif char == "(":
            depth += 1
            current.append(char)
        elif char == ")":
            depth -= 1
            current.append(char)
        elif char == "," and depth == 0:
            chunk = "".join(current).strip()
            if chunk:
                chunks.append(chunk)
            current = []
        elif char == ";" and depth == 0:
            chunk = "".join(current).strip()
            if chunk:
                chunks.append(chunk)
            break
        else:
            current.append(char)
    return chunks


def parse_themes() -> list[dict[str, object]]:
    source = THEME_SOURCE.read_text(encoding="utf-8")
    themes: list[dict[str, object]] = []
    for chunk in enum_constant_chunks(source):
        match = THEME_PATTERN.match(chunk)
        if not match:
            continue
        enum_name, label, slug, tagline, wallpaper, avatar, preview, detail, motion = (str(value) for value in match.groups())
        theme = {
            "enum": enum_name,
            "label": label,
            "slug": slug,
            "tagline": tagline,
            "wallpaper": wallpaper.removeprefix("/"),
            "avatar": avatar.removeprefix("/"),
            "preview": preview.removeprefix("/"),
            "detail": detail.removeprefix("/"),
            "motion": motion.removeprefix("/"),
        }
        colors = [tuple(int(value) for value in color) for color in COLOR_PATTERN.findall(chunk)]
        if len(colors) == 8:
            theme.update(
                {
                    "base": colors[0],
                    "horizon": colors[1],
                    "accent": colors[2],
                    "gold": colors[3],
                    "text": colors[4],
                    "muted": colors[5],
                    "button": colors[6],
                    "buttonText": colors[7],
                }
            )
            alpha_match = re.search(r",\s*(\d+)\s*,\s*(\d+)\s*\)?\s*$", chunk[chunk.rfind("new Color"):])
            if alpha_match:
                theme["imageAlpha"] = int(alpha_match.group(1))
                theme["washAlpha"] = int(alpha_match.group(2))
        themes.append(theme)
    return themes


def unparsed_theme_constants() -> list[str]:
    source = THEME_SOURCE.read_text(encoding="utf-8")
    unparsed: list[str] = []
    for chunk in enum_constant_chunks(source):
        if not THEME_PATTERN.match(chunk):
            unparsed.append(chunk.split("(", 1)[0].strip() or chunk[:60].strip())
    return unparsed


def as_color(value: object) -> tuple[int, int, int]:
    red, green, blue = value  # type: ignore[misc]
    return int(red), int(green), int(blue)


def relative_luminance(color: tuple[int, int, int]) -> float:
    channels = []
    for channel in color:
        normalized = channel / 255.0
        channels.append(normalized / 12.92 if normalized <= 0.03928 else ((normalized + 0.055) / 1.055) ** 2.4)
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast_ratio(foreground: tuple[int, int, int], background: tuple[int, int, int]) -> float:
    light = max(relative_luminance(foreground), relative_luminance(background))
    dark = min(relative_luminance(foreground), relative_luminance(background))
    return (light + 0.05) / (dark + 0.05)


def darker(color: tuple[int, int, int]) -> tuple[int, int, int]:
    return tuple(max(0, int(channel * 0.7)) for channel in color)


def require_contrast(errors: list[str], theme: dict[str, object], label: str, foreground: tuple[int, int, int], background: tuple[int, int, int], minimum: float) -> None:
    ratio = contrast_ratio(foreground, background)
    if ratio < minimum:
        errors.append(f"Theme {theme['enum']} {label} contrast is {ratio:.2f}, expected at least {minimum:.1f}")


def png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        signature = handle.read(8)
        if signature != PNG_SIGNATURE:
            raise ValueError("not a PNG file")
        width = height = None
        seen_ihdr = False
        seen_idat = False
        while True:
            length_bytes = handle.read(4)
            if len(length_bytes) != 4:
                raise ValueError("truncated PNG chunk length")
            length = struct.unpack(">I", length_bytes)[0]
            chunk_type = handle.read(4)
            if len(chunk_type) != 4:
                raise ValueError("truncated PNG chunk type")
            chunk_data = handle.read(length)
            if len(chunk_data) != length:
                raise ValueError(f"truncated PNG {chunk_type.decode('ascii', 'replace')} chunk")
            crc_bytes = handle.read(4)
            if len(crc_bytes) != 4:
                raise ValueError(f"truncated PNG {chunk_type.decode('ascii', 'replace')} CRC")
            expected_crc = struct.unpack(">I", crc_bytes)[0]
            actual_crc = binascii.crc32(chunk_data, binascii.crc32(chunk_type)) & 0xFFFFFFFF
            if actual_crc != expected_crc:
                raise ValueError(f"bad PNG CRC in {chunk_type.decode('ascii', 'replace')} chunk")
            if chunk_type == b"IHDR":
                if seen_ihdr or length != 13:
                    raise ValueError("invalid PNG IHDR")
                width, height = struct.unpack(">II", chunk_data[:8])
                seen_ihdr = True
            elif not seen_ihdr:
                raise ValueError("PNG chunk appears before IHDR")
            elif chunk_type == b"IDAT":
                seen_idat = True
            elif chunk_type == b"IEND":
                if length != 0:
                    raise ValueError("invalid PNG IEND")
                trailing = handle.read(1)
                if trailing:
                    raise ValueError("trailing bytes after PNG IEND")
                break
        if width is None or height is None or not seen_idat:
            raise ValueError("missing PNG image data")
        return width, height


def gif_info(path: Path) -> tuple[int, int, int]:
    data = path.read_bytes()
    if len(data) < 14 or data[:6] not in GIF_SIGNATURES:
        raise ValueError("not a GIF file")
    width, height = struct.unpack("<HH", data[6:10])
    index = 13
    packed = data[10]
    if packed & 0x80:
        index += 3 * (2 ** ((packed & 0x07) + 1))
    frames = 0
    trailer_seen = False
    while index < len(data):
        block = data[index]
        index += 1
        if block == 0x3B:
            trailer_seen = True
            if index != len(data):
                raise ValueError("trailing bytes after GIF trailer")
            break
        if block == 0x21:
            if index >= len(data):
                raise ValueError("truncated GIF extension")
            index += 1
            index = skip_gif_sub_blocks(data, index)
            continue
        if block == 0x2C:
            if index + 9 > len(data):
                raise ValueError("truncated GIF image descriptor")
            image_packed = data[index + 8]
            index += 9
            if image_packed & 0x80:
                index += 3 * (2 ** ((image_packed & 0x07) + 1))
            if index >= len(data):
                raise ValueError("truncated GIF image data")
            index += 1
            index = skip_gif_sub_blocks(data, index)
            frames += 1
            continue
        raise ValueError(f"unknown GIF block 0x{block:02x}")
    if not trailer_seen:
        raise ValueError("GIF trailer missing")
    if frames < 2:
        raise ValueError("GIF preview must contain at least two image frames")
    return width, height, frames


def skip_gif_sub_blocks(data: bytes, index: int) -> int:
    while True:
        if index >= len(data):
            raise ValueError("truncated GIF sub-block")
        size = data[index]
        index += 1
        if size == 0:
            return index
        index += size
        if index > len(data):
            raise ValueError("truncated GIF sub-block data")


def generator_assets() -> set[str]:
    result = subprocess.run(
        [sys.executable, str(GENERATOR), "--list", "--asset", "all"],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return {line.strip() for line in result.stdout.splitlines() if line.strip()}


def generator_slugs() -> set[str]:
    result = subprocess.run(
        [sys.executable, str(GENERATOR), "--list-themes"],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return {line.strip() for line in result.stdout.splitlines() if line.strip()}


def generator_theme_data() -> dict[str, dict[str, object]]:
    tree = ast.parse(GENERATOR.read_text(encoding="utf-8"), filename=str(GENERATOR))
    for node in tree.body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == "THEMES":
                    data = ast.literal_eval(node.value)
                    if isinstance(data, dict):
                        return data
    return {}


def validate() -> list[str]:
    errors: list[str] = []
    for constant in unparsed_theme_constants():
        errors.append(f"Could not parse BurpTheme enum constant: {constant}")

    themes = parse_themes()
    if not themes:
        return ["No themes found in src/burp/arcade/BurpTheme.java"]

    seen_enum: set[str] = set()
    seen_label: set[str] = set()
    seen_slug: set[str] = set()
    expected_assets: set[str] = set()
    for theme in themes:
        for key, seen in (("enum", seen_enum), ("label", seen_label), ("slug", seen_slug)):
            value = theme[key]
            if value in seen:
                errors.append(f"Duplicate theme {key}: {value}")
            seen.add(value)
        if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", theme["slug"]):
            errors.append(f"Theme {theme['enum']} has invalid slug: {theme['slug']}")
        if not theme["tagline"].strip():
            errors.append(f"Theme {theme['enum']} has a blank tagline")
        for key in ("base", "horizon", "accent", "gold", "text", "muted", "button", "buttonText"):
            if key not in theme:
                errors.append(f"Theme {theme['enum']} is missing {key} color")
        for key in ("imageAlpha", "washAlpha"):
            if key not in theme:
                errors.append(f"Theme {theme['enum']} is missing {key}")
            elif not 0 <= int(theme[key]) <= 255:
                errors.append(f"Theme {theme['enum']} has invalid {key}: {theme[key]}")
        if all(key in theme for key in ("base", "horizon", "accent", "text", "muted", "button", "buttonText")):
            base = as_color(theme["base"])
            horizon = as_color(theme["horizon"])
            accent = as_color(theme["accent"])
            text = as_color(theme["text"])
            muted = as_color(theme["muted"])
            button = as_color(theme["button"])
            button_text = as_color(theme["buttonText"])
            require_contrast(errors, theme, "text/base", text, base, 4.5)
            require_contrast(errors, theme, "text/base.darker", text, darker(base), 4.5)
            require_contrast(errors, theme, "muted/base.darker", muted, darker(base), 3.0)
            require_contrast(errors, theme, "buttonText/button", button_text, button, 4.5)
            require_contrast(errors, theme, "buttonText/button.darker", button_text, darker(button), 4.5)
            require_contrast(errors, theme, "buttonText/horizon.darker", button_text, darker(horizon), 4.5)
            require_contrast(errors, theme, "base.darker/accent", darker(base), accent, 4.5)

        for key, expected_size in (("wallpaper", WALLPAPER_SIZE), ("avatar", AVATAR_SIZE), ("detail", DETAIL_SIZE)):
            asset = theme[key]
            expected_assets.add(asset)
            path = ROOT / asset
            if not path.is_file():
                errors.append(f"Missing {key} asset for {theme['enum']}: {asset}")
                continue
            try:
                actual_size = png_size(path)
            except ValueError as exception:
                errors.append(f"Invalid PNG for {theme['enum']} {key}: {asset} ({exception})")
                continue
            if actual_size != expected_size:
                errors.append(
                    f"Wrong {key} size for {theme['enum']}: {asset} is {actual_size[0]}x{actual_size[1]}, expected {expected_size[0]}x{expected_size[1]}"
                )
        preview = theme["preview"]
        expected_assets.add(preview)
        preview_path = ROOT / preview
        if not preview_path.is_file():
            errors.append(f"Missing preview asset for {theme['enum']}: {preview}")
        else:
            try:
                width, height, frames = gif_info(preview_path)
            except ValueError as exception:
                errors.append(f"Invalid GIF for {theme['enum']} preview: {preview} ({exception})")
            else:
                if width < PREVIEW_MIN_SIZE[0] or height < PREVIEW_MIN_SIZE[1]:
                    errors.append(f"Preview GIF for {theme['enum']} is too small: {width}x{height}")
                if frames < 8:
                    errors.append(f"Preview GIF for {theme['enum']} has too few frames: {frames}, expected at least 8")

        motion = theme["motion"]
        expected_assets.add(motion)
        motion_path = ROOT / motion
        if not motion_path.is_file():
            errors.append(f"Missing motion asset for {theme['enum']}: {motion}")
        else:
            try:
                width, height, frames = gif_info(motion_path)
            except ValueError as exception:
                errors.append(f"Invalid GIF for {theme['enum']} motion: {motion} ({exception})")
            else:
                if (width, height) != MOTION_SIZE:
                    errors.append(f"Wrong motion size for {theme['enum']}: {motion} is {width}x{height}, expected {MOTION_SIZE[0]}x{MOTION_SIZE[1]}")
                if frames < MOTION_MIN_FRAMES:
                    errors.append(f"Motion GIF for {theme['enum']} has too few frames: {frames}, expected at least {MOTION_MIN_FRAMES}")

    expected_slugs = {theme["slug"] for theme in themes}
    try:
        generated_slugs = generator_slugs()
    except (OSError, subprocess.CalledProcessError) as exception:
        errors.append(f"Could not list generator theme slugs: {exception}")
        generated_slugs = set()
    if generated_slugs and generated_slugs != expected_slugs:
        for slug in sorted(expected_slugs - generated_slugs):
            errors.append(f"Generator is missing active theme slug: {slug}")
        for slug in sorted(generated_slugs - expected_slugs):
            errors.append(f"Generator lists inactive theme slug: {slug}")

    generator_data = generator_theme_data()
    for theme in themes:
        slug = theme["slug"]
        generated_theme = generator_data.get(slug, {})
        for java_key, generator_key in (("base", "base"), ("horizon", "horizon"), ("accent", "accent"), ("gold", "gold"), ("button", "secondary")):
            if java_key not in theme or generator_key not in generated_theme:
                errors.append(f"Could not validate generator {generator_key} color for {theme['enum']}")
                continue
            generated_color = tuple(generated_theme[generator_key])
            if generated_color != theme[java_key]:
                errors.append(f"Generator {generator_key} color for {theme['enum']} is {generated_color}, expected {theme[java_key]}")

    try:
        generated_assets = generator_assets()
    except (OSError, subprocess.CalledProcessError) as exception:
        errors.append(f"Could not list generator assets: {exception}")
        generated_assets = set()

    if generated_assets and generated_assets != expected_assets:
        missing_from_generator = sorted(expected_assets - generated_assets)
        stale_in_generator = sorted(generated_assets - expected_assets)
        for asset in missing_from_generator:
            errors.append(f"Generator does not list active asset: {asset}")
        for asset in stale_in_generator:
            errors.append(f"Generator lists inactive asset: {asset}")

    active_asset_names = {Path(str(asset)).name for asset in expected_assets}
    for path in sorted((ROOT / "assets").glob("*.png")):
        if path.name not in active_asset_names:
            errors.append(f"Inactive PNG asset remains in assets/: assets/{path.name}")
    for path in sorted((ROOT / "assets").glob("*.gif")):
        if path.name not in active_asset_names:
            errors.append(f"Inactive GIF asset remains in assets/: assets/{path.name}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate BurpTheme registry and theme assets.")
    parser.add_argument("--list-assets", action="store_true", help="Print active asset paths from BurpTheme.java.")
    parser.add_argument("--list-themes", action="store_true", help="Print active theme slugs from BurpTheme.java.")
    args = parser.parse_args()

    themes = parse_themes()
    if args.list_themes:
        for slug in sorted(str(theme["slug"]) for theme in themes):
            print(slug)
        return 0
    if args.list_assets:
        for asset in sorted({str(theme["wallpaper"]) for theme in themes} | {str(theme["avatar"]) for theme in themes} | {str(theme["preview"]) for theme in themes} | {str(theme["detail"]) for theme in themes} | {str(theme["motion"]) for theme in themes}):
            print(asset)
        return 0

    errors = validate()
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f"Validated {len(themes)} themes and {len(themes) * 5} assets.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
