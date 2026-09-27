#!/usr/bin/env python3
"""Control de la pantalla trasera del Cubot KINGKONG 11 via ADB.

Opera en el nivel de privilegio `shell`, que es el que concede ADB. Eso alcanza
para encender, apagar y cambiar la caratula del panel trasero, porque su estado
se persiste en Settings.Global (ver DIAGNOSTICO.md §6.3).

NO alcanza para dibujar contenido propio en el panel: eso vive detras de
/dev/tiny_lcd, etiquetado SELinux `mini_device` (§5.1).

Uso:
    python subscreen.py status
    python subscreen.py on
    python subscreen.py off
    python subscreen.py toggle
    python subscreen.py clock 7
    python subscreen.py dump          # captura dumpsys con el estado actual
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

# Claves descubiertas en el diagnostico. Todas viven en Settings.Global.
KEY_ON = "back_screen_on"
KEY_CLOCK = "subscreen_current_clock"
KEY_VOICE = "subscreen_take_photo_voice_enable"
KEY_TEST = "yft_miniscreen_test"

# Broadcasts del receiver EXPORTADO de TinylcdFunction. Esta es la palanca real
# de encendido/apagado (DIAGNOSTICO.md §8.quater), no la clave de Settings.
ACTION_ON = "com.yft.miniscreen.action.GC_SCREEN_ON"
ACTION_OFF = "com.yft.miniscreen.action.GC_SCREEN_OFF"

# El panel es un framebuffer RGB565 de 360x360 empujado por JNI.
PANEL_W = PANEL_H = 360
FRAME_BYTES = PANEL_W * PANEL_H * 2   # 259200, el "ret" que loguea BackScreenNative

# Via de TEXTO PROPIO: una notificacion de una app registrada aparece en el panel.
# Requiere estas dos claves (venian desactivadas de fabrica).
KEY_NOTI_SCREENON = "subscreen_noti_screenon"
KEY_NOTI_APPS = "subscreen_selected_noti_apps"
HELPER_PKG = "com.example.subscreenpoke"
HELPER_ACTIVITY = f"{HELPER_PKG}/.MainActivity"

# Si adb no esta en el PATH, define la variable de entorno ADB_PATH.
FALLBACK_ADB = os.environ.get("ADB_PATH", "")
HERE = Path(__file__).resolve().parent


class AdbError(RuntimeError):
    pass


def find_adb() -> str:
    """adb del PATH, o la copia que instalamos, o error claro."""
    env = os.environ.get("ADB")
    for candidate in (env, shutil.which("adb"), FALLBACK_ADB):
        if candidate and Path(candidate).is_file():
            return candidate
    raise AdbError(
        "No encuentro adb. Pasalo en la variable de entorno ADB, o instalalo en\n"
        f"  {FALLBACK_ADB}"
    )


class Device:
    def __init__(self, adb: str, serial: str | None = None) -> None:
        self.adb = adb
        self.serial = serial

    def _base(self) -> list[str]:
        return [self.adb] + (["-s", self.serial] if self.serial else [])

    def run(self, *args: str, check: bool = True) -> str:
        proc = subprocess.run(
            self._base() + list(args),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        if check and proc.returncode != 0:
            raise AdbError(f"adb {' '.join(args)} fallo:\n{proc.stderr.strip()}")
        return proc.stdout.replace("\r", "")

    def shell(self, cmd: str, check: bool = True) -> str:
        return self.run("shell", cmd, check=check).strip()

    def require_device(self) -> str:
        """Verifica que haya exactamente un dispositivo autorizado."""
        lines = [
            ln for ln in self.run("devices").splitlines()[1:] if ln.strip()
        ]
        if not lines:
            raise AdbError(
                "Ningun dispositivo conectado. Revisa el cable y que la\n"
                "depuracion USB este activa."
            )
        unauthorized = [ln for ln in lines if "unauthorized" in ln]
        if unauthorized and len(unauthorized) == len(lines):
            raise AdbError(
                "El dispositivo esta `unauthorized`. Desbloquea el telefono y\n"
                "acepta el dialogo de depuracion USB (marca 'Permitir siempre')."
            )
        ready = [ln for ln in lines if ln.split()[-1] == "device" or "\tdevice" in ln]
        ready = [ln for ln in lines if ln.split()[1] == "device"] if not ready else ready
        if not ready:
            raise AdbError(f"Dispositivo en estado inesperado:\n" + "\n".join(lines))
        if len(ready) > 1 and not self.serial:
            raise AdbError(
                "Hay varios dispositivos. Elige uno con --serial:\n"
                + "\n".join(ready)
            )
        return ready[0].split()[0]

    # --- Settings.Global -------------------------------------------------

    def get_global(self, key: str) -> str | None:
        val = self.shell(f"settings get global {key}")
        return None if val in ("", "null") else val

    def put_global(self, key: str, value: str) -> None:
        self.shell(f"settings put global {key} {value}")

    # --- Lectura de estado real -----------------------------------------

    def frames_flowing(self, seconds: float = 1.5) -> bool | None:
        """Detecta si el panel esta recibiendo frames de verdad.

        Es el unico indicador fiable: la capa MiniScreenWin figura como
        `invisible` en SurfaceFlinger incluso con el panel encendido, porque el
        panel NO se compone por SurfaceFlinger -- la vista se rasteriza a Bitmap
        y se empuja por JNI (BackScreenNative). Ver DIAGNOSTICO.md §8.quater.

        Aqui se leen las lineas `refreshScreen--- end, ret = 259200` del logcat.
        """
        self.run("logcat", "-c", check=False)
        time.sleep(seconds)
        out = self.run("logcat", "-d", "-s", "BackScreenNative", check=False)
        hits = out.count(f"ret = {FRAME_BYTES}")
        return hits > 0

    def rear_touch_active(self) -> bool | None:
        """El digitalizador trasero hyn_ts se activa junto con el panel.

        Se ancla en el bloque `Device N: hyn_ts` de dumpsys input y lee la
        primera linea Viewport que aparezca dentro de ese bloque -- que es la
        del displayId=100 ficticio del vendor (DIAGNOSTICO.md §5.5).
        """
        out = self.shell("dumpsys input", check=False)
        lines = out.splitlines()
        start = None
        for i, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("Device ") and stripped.endswith(": hyn_ts"):
                start = i
                break
        if start is None:
            return None
        for line in lines[start + 1 :]:
            stripped = line.strip()
            # Fin del bloque: empieza el siguiente Device.
            if stripped.startswith("Device ") and stripped.endswith(":"):
                break
            if stripped.startswith("Device ") and ": " in stripped:
                break
            if "Viewport" in line and "isActive=" in line:
                return "isActive=[1]" in line
        return None


def cmd_status(dev: Device) -> int:
    serial = dev.require_device()
    model = dev.shell("getprop ro.product.model")
    on = dev.get_global(KEY_ON)
    clock = dev.get_global(KEY_CLOCK)
    flowing = dev.frames_flowing()
    touch = dev.rear_touch_active()

    def yn(v: bool | None) -> str:
        return "?" if v is None else ("si" if v else "no")

    print(f"Dispositivo            : {model} ({serial})")
    print(f"{KEY_ON:<23}: {on}  -> pantalla trasera {'ENCENDIDA' if on == '1' else 'apagada'}")
    print(f"{KEY_CLOCK:<23}: {clock}")
    print(f"Frames al panel        : {yn(flowing)} ({FRAME_BYTES} B RGB565 @360x360)")
    print(f"Tactil trasero activo  : {yn(touch)} (hyn_ts, 360x360)")
    return 0


def _set_power(dev: Device, value: str) -> int:
    """Enciende/apaga el panel con el broadcast exportado del vendor."""
    dev.require_device()
    action = ACTION_ON if value == "1" else ACTION_OFF
    verb = "ENCENDER" if value == "1" else "APAGAR"
    before = dev.get_global(KEY_ON)

    out = dev.shell(f"am broadcast -a {action}", check=False)
    if "SecurityException" in out:
        print("Broadcast RECHAZADO (" + action + "):", file=sys.stderr)
        print(out, file=sys.stderr)
        return 2
    ok = "result=0" in out or "Broadcast completed" in out
    print(f"{verb}: broadcast {action} -> {'aceptado' if ok else 'respuesta inesperada'}")

    after = dev.get_global(KEY_ON)
    print(f"{KEY_ON}: {before} -> {after}")

    flowing = dev.frames_flowing()
    if flowing is None:
        print("Frames al panel: ?")
    else:
        print(f"Frames al panel ({FRAME_BYTES} B RGB565): {'SI, fluyendo' if flowing else 'no'}")
    if value == "1" and not flowing:
        print("  AVISO: se pidio encender pero no se ven frames.", file=sys.stderr)
        return 1
    return 0


def cmd_on(dev: Device) -> int:
    return _set_power(dev, "1")


def cmd_off(dev: Device) -> int:
    return _set_power(dev, "0")


def cmd_toggle(dev: Device) -> int:
    dev.require_device()
    current = dev.get_global(KEY_ON)
    return _set_power(dev, "0" if current == "1" else "1")


def cmd_clock(dev: Device, index: int) -> int:
    dev.require_device()
    before = dev.get_global(KEY_CLOCK)
    dev.put_global(KEY_CLOCK, str(index))
    after = dev.get_global(KEY_CLOCK)
    print(f"{KEY_CLOCK}: {before} -> {after}")
    return 0 if after == str(index) else 1


def cmd_text(dev: Device, message: str) -> int:
    """Muestra texto propio en el panel via notificacion de la app helper."""
    dev.require_device()

    # 1. la via de notificaciones tiene que estar habilitada
    if dev.get_global(KEY_NOTI_SCREENON) != "1":
        dev.put_global(KEY_NOTI_SCREENON, "1")
        print(f"{KEY_NOTI_SCREENON} -> 1 (estaba desactivado)")

    # 2. la app helper tiene que estar en la lista de apps permitidas
    apps = dev.get_global(KEY_NOTI_APPS) or ""
    if HELPER_PKG not in apps:
        print(f"ERROR: {HELPER_PKG} no esta en {KEY_NOTI_APPS}.", file=sys.stderr)
        print("Ejecuta primero:  python subscreen.py setup", file=sys.stderr)
        return 2

    # 3. postear
    safe = message.replace('"', "'")
    dev.shell(f'am start -n {HELPER_ACTIVITY} --es test noti --es msg "{safe}"', check=False)
    print(f'Notificacion enviada: "{message}"')

    time.sleep(2)
    print(f"{KEY_ON} = {dev.get_global(KEY_ON)}")
    flowing = dev.frames_flowing()
    print(f"Frames al panel: {'SI' if flowing else 'no'}")
    return 0


DEFAULT_NOTI_APPS = (
    "com.android.incall:com.kugou.android:com.android.deskclock:com.android.mms:"
    "com.google.android.calendar:com.google.android.keep:com.mediatek.notebook:"
    "com.google.android.dialer:com.google.android.apps.messaging:com.google.android.gm:"
    "com.google.android.apps.tachyon:com.tencent.mm:com.tencent.mobileqq:"
    "com.android.dialer:com.android.email:com.coolapk.market:"
    "com.google.android.apps.youtube.music"
)


def cmd_setup(dev: Device) -> int:
    """Habilita la via de notificaciones y registra la app helper."""
    dev.require_device()
    dev.put_global(KEY_NOTI_SCREENON, "1")
    apps = dev.get_global(KEY_NOTI_APPS) or DEFAULT_NOTI_APPS
    if HELPER_PKG not in apps:
        apps = apps + ":" + HELPER_PKG
        dev.put_global(KEY_NOTI_APPS, apps)
    dev.shell(f"pm grant {HELPER_PKG} android.permission.POST_NOTIFICATIONS", check=False)
    print(f"{KEY_NOTI_SCREENON} = {dev.get_global(KEY_NOTI_SCREENON)}")
    print(f"{HELPER_PKG} en la lista: {HELPER_PKG in (dev.get_global(KEY_NOTI_APPS) or '')}")
    print("Listo. Prueba:  python subscreen.py text \"Hola mundo\"")
    return 0


def cmd_dump(dev: Device) -> int:
    dev.require_device()
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    state = dev.get_global(KEY_ON)
    outdir = HERE / f"dump-{stamp}-back_screen_on_{state}"
    outdir.mkdir(parents=True, exist_ok=True)
    for name, cmd in (
        ("surfaceflinger.txt", "dumpsys SurfaceFlinger"),
        ("window.txt", "dumpsys window windows"),
        ("display.txt", "dumpsys display"),
        ("input.txt", "dumpsys input"),
    ):
        (outdir / name).write_text(dev.shell(cmd, check=False), encoding="utf-8")
        print(f"  {outdir.name}/{name}")
    print(f"Capturado con {KEY_ON}={state} en {outdir}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Control de la pantalla trasera del Cubot KINGKONG 11 via ADB.",
        epilog="Requiere depuracion USB autorizada. No requiere root.",
    )
    parser.add_argument("--serial", help="serial del dispositivo si hay varios")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("status", help="muestra el estado del panel trasero")
    sub.add_parser("on", help="enciende el panel trasero")
    sub.add_parser("off", help="apaga el panel trasero")
    sub.add_parser("toggle", help="invierte el estado actual")
    p_clock = sub.add_parser("clock", help="cambia la caratula del reloj")
    p_clock.add_argument("index", type=int, help="indice de caratula (el actual era 13)")
    p_text = sub.add_parser("text", help="muestra texto propio en el panel")
    p_text.add_argument("message", help="el texto a mostrar")
    sub.add_parser("setup", help="habilita la via de notificaciones (una sola vez)")
    sub.add_parser("dump", help="captura dumpsys con el estado actual")

    args = parser.parse_args(argv)

    try:
        dev = Device(find_adb(), args.serial)
        if args.cmd == "status":
            return cmd_status(dev)
        if args.cmd == "on":
            return cmd_on(dev)
        if args.cmd == "off":
            return cmd_off(dev)
        if args.cmd == "toggle":
            return cmd_toggle(dev)
        if args.cmd == "clock":
            return cmd_clock(dev, args.index)
        if args.cmd == "text":
            return cmd_text(dev, args.message)
        if args.cmd == "setup":
            return cmd_setup(dev)
        if args.cmd == "dump":
            return cmd_dump(dev)
    except AdbError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
