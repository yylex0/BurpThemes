#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

import validate_themes


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR = ROOT / "dist" / "arcade-burp-community.jar"
DEFAULT_BURP_JAR = Path("/usr/share/burpsuite/burpsuite.jar")
MAIN_CLASS = "burp.arcade.BurpThemeExtension"
MAIN_CLASS_ENTRY = "burp/arcade/BurpThemeExtension.class"
LEGACY_CLASS_ENTRY = "burp/arcade/ArcadeBurpExtension.class"
MANIFEST_ENTRY = "META-INF/MANIFEST.MF"
DEFAULT_MAX_CLASS_MAJOR = 61


def fail(message: str, errors: list[str]) -> None:
    errors.append(message)


def class_major(class_bytes: bytes) -> int:
    if len(class_bytes) < 8 or class_bytes[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a Java class file")
    return struct.unpack(">H", class_bytes[6:8])[0]


def max_class_major_for_release(release: str) -> int:
    try:
        release_number = int(release)
    except ValueError:
        return DEFAULT_MAX_CLASS_MAJOR
    if release_number < 5:
        return DEFAULT_MAX_CLASS_MAJOR
    return release_number + 44


def class_strings(class_bytes: bytes) -> set[str]:
    # Java class constants are modified UTF-8, but ASCII keys/classes are safe to
    # recover with a conservative printable-byte scan.
    return {match.group(0).decode("ascii", "ignore") for match in re.finditer(rb"[ -~]{3,}", class_bytes)}


def run_source_scan(errors: list[str]) -> None:
    source = "\n".join(path.read_text(encoding="utf-8") for path in sorted((ROOT / "src").glob("**/*.java")))
    forbidden_patterns = {
        "HTTP handler registration": r"registerHttpHandler|HttpHandler",
        "proxy intercept mutation": r"proxy\(\)\.intercept|InterceptedRequest|InterceptedResponse",
        "request/response mutation": r"setRequest|setResponse|withBody|withHeader",
        "scanner registration": r"scanner\(\)|registerScan",
    }
    for label, pattern in forbidden_patterns.items():
        if re.search(pattern, source):
            fail(f"Unexpected traffic-affecting API found in source: {label}", errors)
    required_source = [
        "registerSuiteTab",
        "registerUnloadingHandler",
        "restore(true)",
        "suiteWideThemeEnabledV2",
        "selectedThemeId",
        "selectedTheme",
        "previewResource",
        "detailResource",
        "motionResource",
    ]
    for token in required_source:
        if token not in source:
            fail(f"Required source token missing: {token}", errors)


def run_burp_api_scan(burp_jar: Path, errors: list[str]) -> None:
    if not burp_jar.is_file():
        fail(f"Burp JAR not found for API scan: {burp_jar}", errors)
        return
    required_entries = [
        "burp/api/montoya/BurpExtension.class",
        "burp/api/montoya/MontoyaApi.class",
        "burp/api/montoya/core/Registration.class",
        "burp/api/montoya/persistence/PersistedObject.class",
    ]
    with zipfile.ZipFile(burp_jar) as burp_zip:
        names = set(burp_zip.namelist())
    for entry in required_entries:
        if entry not in names:
            fail(f"Required Montoya API class missing from Burp JAR: {entry}", errors)


def run_jar_scan(jar_path: Path, max_class_major: int, errors: list[str]) -> None:
    if not jar_path.is_file():
        fail(f"Extension JAR not found: {jar_path}", errors)
        return

    themes = validate_themes.parse_themes()
    expected_asset_paths = {
        str(theme["wallpaper"]) for theme in themes
    } | {
        str(theme["avatar"]) for theme in themes
    } | {
        str(theme["preview"]) for theme in themes
    } | {
        str(theme["detail"]) for theme in themes
    } | {
        str(theme["motion"]) for theme in themes
    }

    with zipfile.ZipFile(jar_path) as jar_file:
        names = set(jar_file.namelist())
        if MANIFEST_ENTRY not in names:
            fail("Manifest missing from extension JAR", errors)
            manifest = ""
        else:
            manifest = jar_file.read(MANIFEST_ENTRY).decode("utf-8", "replace")
        if f"Burp-Extension-Class: {MAIN_CLASS}" not in manifest:
            fail(f"Manifest entrypoint is not {MAIN_CLASS}", errors)
        for entry in (MAIN_CLASS_ENTRY, LEGACY_CLASS_ENTRY):
            if entry not in names:
                fail(f"Required class missing from extension JAR: {entry}", errors)

        jar_assets = {name for name in names if name.startswith("assets/") and not name.endswith("/")}
        if jar_assets != expected_asset_paths:
            fail(f"JAR assets differ from registry: expected {sorted(expected_asset_paths)}, got {sorted(jar_assets)}", errors)
        for asset in sorted(expected_asset_paths):
            try:
                data = jar_file.read(asset)
            except KeyError:
                continue
            if asset.endswith(".gif"):
                with tempfile.TemporaryDirectory(prefix="burptheme-gif-") as temp_dir:
                    asset_path = Path(temp_dir) / Path(asset).name
                    asset_path.write_bytes(data)
                    try:
                        width, height, frames = validate_themes.gif_info(asset_path)
                    except ValueError as exception:
                        fail(f"Invalid packaged GIF asset {asset}: {exception}", errors)
                    else:
                        if Path(asset).name.startswith("motion-"):
                            if (width, height) != validate_themes.MOTION_SIZE or frames < validate_themes.MOTION_MIN_FRAMES:
                                fail(f"Packaged motion GIF too weak for {asset}: {width}x{height}, {frames} frames", errors)
                        elif width < validate_themes.PREVIEW_MIN_SIZE[0] or height < validate_themes.PREVIEW_MIN_SIZE[1] or frames < 8:
                            fail(f"Packaged preview GIF too weak for {asset}: {width}x{height}, {frames} frames", errors)
                continue
            asset_name = Path(asset).name
            if asset_name.startswith("wallpaper-") or asset_name == "temple-overworld.png":
                expected_size = validate_themes.WALLPAPER_SIZE
            elif asset_name.startswith("detail-"):
                expected_size = validate_themes.DETAIL_SIZE
            else:
                expected_size = validate_themes.AVATAR_SIZE
            if not data.startswith(validate_themes.PNG_SIGNATURE):
                fail(f"Packaged asset is not a PNG: {asset}", errors)
                continue
            with tempfile.TemporaryDirectory(prefix="burptheme-png-") as temp_dir:
                asset_path = Path(temp_dir) / Path(asset).name
                asset_path.write_bytes(data)
                actual_size = validate_themes.png_size(asset_path)
            if actual_size != expected_size:
                fail(f"Unexpected packaged asset dimensions for {asset}: {actual_size}", errors)

        stale_fragments = ("ThemeQuickSwitchPanel", "TWILIGHT_REALM", "twilight-realm", "avatar-twilight", "wallpaper-twilight", "deregisterTabs")
        for name in names:
            if any(fragment in name for fragment in stale_fragments):
                fail(f"Stale JAR entry found: {name}", errors)
            if name.endswith(".class") and not name.startswith("burp/arcade/"):
                fail(f"Unexpected packaged class outside burp/arcade: {name}", errors)

        forbidden_class_strings = (
            "burp/api/montoya/http/",
            "burp/api/montoya/proxy/http/",
            "HttpHandler",
            "registerHttpHandler",
            "InterceptedRequest",
            "InterceptedResponse",
        )
        for name in sorted(entry for entry in names if entry.endswith(".class")):
            strings = class_strings(jar_file.read(name))
            for token in forbidden_class_strings:
                if token in strings:
                    fail(f"Unexpected traffic-affecting class string in {name}: {token}", errors)

        if MAIN_CLASS_ENTRY in names:
            main_class = jar_file.read(MAIN_CLASS_ENTRY)
            major = class_major(main_class)
            if major > max_class_major:
                fail(f"{MAIN_CLASS_ENTRY} classfile major is {major}, expected <= {max_class_major}", errors)
            strings = class_strings(main_class)
            for token in ("selectedThemeId", "selectedTheme", "suiteWideThemeEnabledV2", "deregisterRegistrations"):
                if token not in strings:
                    fail(f"Required class string missing from {MAIN_CLASS_ENTRY}: {token}", errors)
            if "registerThemeMenu" in strings:
                fail("Theme switching menu registration should not be present in extension class", errors)
            if "suiteWideTheme" in strings:
                fail("Stale exact suiteWideTheme preference key found in extension class", errors)


def run_compile_check(burp_jar: Path, release: str, errors: list[str]) -> None:
    sources = sorted(str(path) for path in (ROOT / "src").glob("**/*.java"))
    with tempfile.TemporaryDirectory(prefix="burptheme-smoke-classes-") as output:
        command = [
            "javac",
            "--release",
            release,
            "-Xlint:all",
            "-classpath",
            str(burp_jar),
            "-sourcepath",
            str(ROOT / "src"),
            "-d",
            output,
            *sources,
        ]
        try:
            subprocess.run(command, cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except (OSError, subprocess.CalledProcessError) as exception:
            fail(f"Compile smoke check failed: {exception}", errors)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run comprehensive BurpTheme extension smoke checks.")
    parser.add_argument("--jar", type=Path, default=DEFAULT_JAR, help="Extension JAR to inspect.")
    parser.add_argument("--burp-jar", type=Path, default=DEFAULT_BURP_JAR, help="Burp Suite JAR used for API/compile checks.")
    parser.add_argument("--release", default="17", help="Java release target for compile smoke check.")
    parser.add_argument("--skip-compile", action="store_true", help="Skip javac compile smoke check.")
    args = parser.parse_args()

    errors: list[str] = []
    validation_errors = validate_themes.validate()
    errors.extend(validation_errors)
    run_source_scan(errors)
    run_burp_api_scan(args.burp_jar, errors)
    run_jar_scan(args.jar, max_class_major_for_release(args.release), errors)
    if not args.skip_compile:
        run_compile_check(args.burp_jar, args.release, errors)

    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("Comprehensive smoke checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
