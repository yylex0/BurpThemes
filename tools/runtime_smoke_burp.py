#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import random
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_EXTENSION_JAR = ROOT / "dist" / "arcade-burp-community.jar"
DEFAULT_BURP_JAR = Path("/usr/share/burpsuite/burpsuite.jar")
MAIN_CLASS = "burp.StartBurp"
EXTENSION_CLASS = "burp.arcade.BurpThemeExtension"
ADD_OPENS = [
    "java.base/java.lang=ALL-UNNAMED",
    "java.desktop/javax.swing=ALL-UNNAMED",
    "java.desktop/java.awt=ALL-UNNAMED",
    "java.desktop/java.awt.color=ALL-UNNAMED",
    "java.base/javax.crypto=ALL-UNNAMED",
    "jdk.crypto.cryptoki/sun.security.pkcs11=ALL-UNNAMED",
]


def terminate(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    if os.name == "nt":
        try:
            process.send_signal(signal.CTRL_BREAK_EVENT)
        except (AttributeError, ProcessLookupError, OSError):
            process.terminate()
    else:
        try:
            process_group = os.getpgid(process.pid)
            if process_group == os.getpgrp():
                process.terminate()
            else:
                os.killpg(process_group, signal.SIGTERM)
        except ProcessLookupError:
            return
    try:
        process.wait(timeout=8)
    except subprocess.TimeoutExpired:
        if os.name == "nt":
            process.kill()
        else:
            try:
                process_group = os.getpgid(process.pid)
                if process_group == os.getpgrp():
                    process.kill()
                else:
                    os.killpg(process_group, signal.SIGKILL)
            except ProcessLookupError:
                pass
        process.wait(timeout=8)


def start_xvfb(xvfb_bin: str, temp_path: Path) -> tuple[subprocess.Popen[str], str]:
    for _ in range(20):
        display_number = random.randint(90, 240)
        display = f":{display_number}"
        display_socket = Path("/tmp/.X11-unix") / f"X{display_number}"
        output = (temp_path / "xvfb-output.txt").open("a", encoding="utf-8")
        process = subprocess.Popen(
            [xvfb_bin, display, "-screen", "0", "1280x1024x24", "-nolisten", "tcp"],
            stdout=output,
            stderr=subprocess.STDOUT,
            text=True,
            start_new_session=True,
        )
        output.close()
        socket_deadline = time.monotonic() + 2.0
        while time.monotonic() < socket_deadline:
            if process.poll() is not None:
                break
            if display_socket.exists():
                return process, display
            time.sleep(0.1)
        terminate(process)
    raise RuntimeError("Could not start Xvfb on a free display")


def capture_failure_diagnostics(process: subprocess.Popen[str], temp_path: Path, env: dict[str, str]) -> None:
    if process.poll() is not None:
        return
    jstack_bin = shutil.which("jstack")
    if jstack_bin:
        with (temp_path / "jstack.txt").open("w", encoding="utf-8") as handle:
            subprocess.run([jstack_bin, str(process.pid)], stdout=handle, stderr=subprocess.STDOUT, text=True, timeout=20, check=False)
    jcmd_bin = shutil.which("jcmd")
    if jcmd_bin:
        with (temp_path / "jcmd-thread-print.txt").open("w", encoding="utf-8") as handle:
            subprocess.run([jcmd_bin, str(process.pid), "Thread.print"], stdout=handle, stderr=subprocess.STDOUT, text=True, timeout=20, check=False)
    import_bin = shutil.which("import")
    if import_bin and env.get("DISPLAY"):
        subprocess.run([import_bin, "-window", "root", str(temp_path / "screenshot.png")], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env, timeout=15, check=False)


def xdotool_output(xdotool_bin: str, env: dict[str, str], *args: str) -> str:
    try:
        result = subprocess.run([xdotool_bin, *args], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, env=env, timeout=5, check=False)
    except (OSError, subprocess.TimeoutExpired):
        return ""
    return result.stdout


def window_geometry(xdotool_bin: str, env: dict[str, str], window_id: str) -> dict[str, int] | None:
    output = xdotool_output(xdotool_bin, env, "getwindowgeometry", "--shell", window_id)
    geometry: dict[str, int] = {}
    for line in output.splitlines():
        key, _, value = line.partition("=")
        if key in {"X", "Y", "WIDTH", "HEIGHT"}:
            try:
                geometry[key] = int(value)
            except ValueError:
                return None
    return geometry if {"X", "Y", "WIDTH", "HEIGHT"} <= set(geometry) else None


def log_driver(temp_path: Path, message: str) -> None:
    with (temp_path / "startup-driver.txt").open("a", encoding="utf-8") as handle:
        handle.write(message + "\n")


def xdotool_click(xdotool_bin: str, env: dict[str, str], x: int, y: int) -> int:
    try:
        result = subprocess.run([xdotool_bin, "mousemove", str(x), str(y), "click", "1"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env, timeout=5, check=False)
    except subprocess.TimeoutExpired:
        return 124
    except OSError:
        return 127
    return result.returncode


def drive_startup_ui(xdotool_bin: str, env: dict[str, str], state: dict[str, float | bool], started_at: float, temp_path: Path) -> None:
    if not env.get("DISPLAY"):
        return
    now = time.monotonic()
    if now - float(state.get("last_action", 0.0)) < 1.25 or now - started_at < 2.0:
        return
    safe_mode_ids = [line.strip() for line in xdotool_output(xdotool_bin, env, "search", "--onlyvisible", "--name", "Safe Mode").splitlines() if line.strip()]
    safe_mode_windows: list[tuple[str, dict[str, int]]] = []
    for window_id in safe_mode_ids:
        geometry = window_geometry(xdotool_bin, env, window_id)
        if geometry:
            safe_mode_windows.append((window_id, geometry))
    if safe_mode_windows:
        window_id, geometry = safe_mode_windows[-1]
        x = geometry["X"] + int(geometry["WIDTH"] * 0.70)
        y = geometry["Y"] + geometry["HEIGHT"] - 23
        returncode = xdotool_click(xdotool_bin, env, x, y)
        log_driver(temp_path, f"clicked safe mode no on {window_id} at {x},{y}; rc={returncode}")
        state["last_action"] = now
        return

    window_ids = [line.strip() for line in xdotool_output(xdotool_bin, env, "search", "--onlyvisible", "--name", "Burp Suite Community Edition").splitlines() if line.strip()]
    windows: list[tuple[str, dict[str, int]]] = []
    for window_id in window_ids:
        geometry = window_geometry(xdotool_bin, env, window_id)
        if geometry:
            windows.append((window_id, geometry))
    if windows and not bool(state.get("logged_windows", False)):
        log_driver(temp_path, "visible windows: " + "; ".join(f"{window_id} {geometry}" for window_id, geometry in windows))
        state["logged_windows"] = True
    if not windows:
        return

    update_dialogs = [
        (window_id, geometry)
        for window_id, geometry in windows
        if 520 <= geometry["WIDTH"] <= 760 and 320 <= geometry["HEIGHT"] <= 500
    ]
    if update_dialogs and not bool(state.get("update_closed", False)):
        window_id, geometry = update_dialogs[-1]
        x = geometry["X"] + geometry["WIDTH"] - 183
        y = geometry["Y"] + geometry["HEIGHT"] - 27
        returncode = xdotool_click(xdotool_bin, env, x, y)
        log_driver(temp_path, f"clicked update close on {window_id} at {x},{y}; rc={returncode}")
        state["update_close_attempts"] = float(state.get("update_close_attempts", 0.0)) + 1.0
        if float(state["update_close_attempts"]) >= 3.0:
            state["update_closed"] = True
        state["last_action"] = now
        return

    project_windows = [
        (window_id, geometry)
        for window_id, geometry in windows
        if geometry["WIDTH"] >= 800 and geometry["HEIGHT"] >= 500
    ]
    project_clicks = float(state.get("project_clicks", 0.0))
    if project_windows and project_clicks < 6.0:
        window_id, geometry = max(project_windows, key=lambda item: item[1]["WIDTH"] * item[1]["HEIGHT"])
        x = geometry["X"] + geometry["WIDTH"] - 55
        y = geometry["Y"] + geometry["HEIGHT"] - 26
        returncode = xdotool_click(xdotool_bin, env, x, y)
        log_driver(temp_path, f"clicked startup wizard button on {window_id} at {x},{y}; rc={returncode}")
        state["project_clicks"] = project_clicks + 1.0
        state["last_action"] = now


def main() -> int:
    parser = argparse.ArgumentParser(description="Launch Burp in a temp data dir and verify BurpTheme initializes.")
    parser.add_argument("--extension-jar", type=Path, default=DEFAULT_EXTENSION_JAR)
    parser.add_argument("--burp-jar", type=Path, default=DEFAULT_BURP_JAR)
    parser.add_argument("--java-bin", default=shutil.which("java") or "java")
    parser.add_argument("--xvfb-bin", default=shutil.which("Xvfb"))
    parser.add_argument("--xvfb-run", default=shutil.which("xvfb-run"))
    parser.add_argument("--xdotool-bin", default=shutil.which("xdotool"))
    parser.add_argument("--no-xvfb", action="store_true", help="Run without xvfb-run even if available.")
    parser.add_argument("--no-drive-startup-ui", action="store_true", help="Do not use xdotool to close Burp startup dialogs under the private Xvfb display.")
    parser.add_argument("--timeout", type=float, default=180.0)
    parser.add_argument("--post-init-wait", type=float, default=2.5, help="Seconds to keep Burp running after initialization so delayed theme refreshes can fire.")
    parser.add_argument("--temp-root", type=Path, default=Path(os.environ.get("BURPTHEME_RUNTIME_TMP", ROOT / "build" / "runtime-smoke-tmp")), help="Directory used for temporary Burp runtime smoke files.")
    parser.add_argument("--keep-temp-on-failure", action="store_true", help="Preserve the temporary Burp data/output directory when initialization fails.")
    parser.add_argument("--use-project-file", action="store_true", help="Pass an explicit project file to Burp. Off by default because Community edition may reject disk project creation.")
    args = parser.parse_args()

    if not args.extension_jar.is_file():
        print(f"Extension JAR not found: {args.extension_jar}", file=sys.stderr)
        return 1
    if not args.burp_jar.is_file():
        print(f"Burp JAR not found: {args.burp_jar}", file=sys.stderr)
        return 1

    args.temp_root.mkdir(parents=True, exist_ok=True)
    temp_path = Path(tempfile.mkdtemp(prefix="burptheme-runtime-", dir=args.temp_root))
    keep_temp = False
    try:
        java_temp = temp_path / "java-tmp"
        java_temp.mkdir(parents=True, exist_ok=True)
        marker = temp_path / "smoke-marker.txt"
        output = temp_path / "burp-output.txt"
        env = os.environ.copy()
        xvfb_process = None
        command: list[str] = [args.java_bin, f"-Djava.io.tmpdir={java_temp}"]
        display = None
        if not args.no_xvfb and os.name != "nt" and args.xvfb_bin:
            xvfb_process, display = start_xvfb(args.xvfb_bin, temp_path)
            env["DISPLAY"] = display
        elif args.xvfb_run and not args.no_xvfb:
            command = [args.xvfb_run, "-a", *command]
        for option in ADD_OPENS:
            command.extend(["--add-opens", option])
        command.extend(
            [
                f"-Dburptheme.smokeMarker={marker}",
                "-cp",
                os.pathsep.join((str(args.extension_jar), str(args.burp_jar))),
                MAIN_CLASS,
                "--use-defaults",
                "--disable-auto-update",
                f"--data-dir={temp_path / 'data'}",
            ]
        )
        if args.use_project_file:
            command.append(f"--project-file={temp_path / 'smoke.burp'}")
        command.append(f"--developer-extension-class-name={EXTENSION_CLASS}")

        popen_kwargs: dict[str, object] = {
            "cwd": ROOT,
            "env": env,
            "stderr": subprocess.STDOUT,
            "text": True,
        }
        if os.name == "nt":
            popen_kwargs["creationflags"] = getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0)
        else:
            popen_kwargs["start_new_session"] = True
        with output.open("w", encoding="utf-8") as handle:
            popen_kwargs["stdout"] = handle
            process = subprocess.Popen(command, **popen_kwargs)
        deadline = time.monotonic() + args.timeout
        initialized = False
        post_init_failed = False
        driver_state: dict[str, float | bool] = {}
        started_at = time.monotonic()
        try:
            while time.monotonic() < deadline:
                if marker.is_file() and "initialized" in marker.read_text(encoding="utf-8", errors="replace"):
                    initialized = True
                    break
                if process.poll() is not None:
                    break
                if xvfb_process is not None and args.xdotool_bin and not args.no_drive_startup_ui:
                    drive_startup_ui(args.xdotool_bin, env, driver_state, started_at, temp_path)
                time.sleep(0.5)
            if initialized and args.post_init_wait > 0:
                stable_until = min(deadline, time.monotonic() + args.post_init_wait)
                while time.monotonic() < stable_until:
                    if process.poll() is not None:
                        post_init_failed = True
                        break
                    if xvfb_process is not None and args.xdotool_bin and not args.no_drive_startup_ui:
                        drive_startup_ui(args.xdotool_bin, env, driver_state, started_at, temp_path)
                    time.sleep(0.5)
        finally:
            if not initialized:
                capture_failure_diagnostics(process, temp_path, env)
            terminate(process)
            if xvfb_process is not None:
                terminate(xvfb_process)

        marker_text = marker.read_text(encoding="utf-8", errors="replace") if marker.is_file() else ""
        output_text = output.read_text(encoding="utf-8", errors="replace") if output.is_file() else ""
        if not initialized or post_init_failed:
            if post_init_failed:
                print("BurpTheme runtime smoke failed: Burp exited during post-initialization stability wait.", file=sys.stderr)
            else:
                print("BurpTheme runtime smoke failed: initialize marker was not written.", file=sys.stderr)
            print(f"Command: {' '.join(str(part) for part in command)}", file=sys.stderr)
            if display:
                print(f"Xvfb display: {display}", file=sys.stderr)
            print(f"Process exit code: {process.poll()}", file=sys.stderr)
            for diagnostic in ("jstack.txt", "jcmd-thread-print.txt", "screenshot.png", "xvfb-output.txt", "startup-driver.txt"):
                diagnostic_path = temp_path / diagnostic
                if diagnostic_path.exists():
                    print(f"Diagnostic captured: {diagnostic_path}", file=sys.stderr)
            print("--- marker ---", file=sys.stderr)
            print(marker_text, file=sys.stderr)
            print("--- Burp output ---", file=sys.stderr)
            print(output_text[-6000:], file=sys.stderr)
            if args.keep_temp_on_failure:
                keep_temp = True
                print(f"Preserved temp dir: {temp_path}", file=sys.stderr)
            return 1

        print("Burp runtime smoke initialized extension.")
        print(marker_text.strip())
        warning_lines = [line for line in output_text.splitlines() if "Exception" in line or "Error" in line]
        if warning_lines:
            print("Burp output warnings/errors:")
            for line in warning_lines[-20:]:
                print(line)
        return 0
    finally:
        if not keep_temp:
            shutil.rmtree(temp_path, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
