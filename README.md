# Pantalla trasera del Cubot KINGKONG 11 — ingeniería inversa

Cómo funciona el panel trasero de 360×360 del **Cubot KINGKONG 11** (Android 15, MediaTek
mt6855), y qué se puede hacer con él **sin root**.

Resultado corto: la pantalla trasera **no** es un Display de Android. Es un panel propietario
pintado por JNI. Pero se puede **encender, apagar y mostrar texto propio** desde una app normal
o desde ADB, sin root ni desbloquear el bootloader.

> Investigación hecha sobre un dispositivo propio, con fines de interoperabilidad.
> Este repo documenta hallazgos; **no redistribuye firmware** del fabricante.

---

## Lo esencial en 30 segundos

```bash
# Encender el panel — funciona sin root, sin permisos especiales
adb shell am broadcast -a com.yft.miniscreen.action.GC_SCREEN_ON

# Apagarlo
adb shell am broadcast -a com.yft.miniscreen.action.GC_SCREEN_OFF

# Comprobar que está pintando de verdad (cada línea = un frame)
adb logcat -s BackScreenNative | grep "ret = 259200"
```

`259200 = 360 × 360 × 2` → el panel es un framebuffer **RGB565 de 360×360**.

---

## Arquitectura

```
                 ┌──────────────────────────────────────────┐
                 │  com.hxy.miniscreen   (UID de SystemUI)  │
                 │  /system/app, persistent                 │
                 │                                          │
  broadcast  ──► │  TinylcdFunction                         │
  GC_SCREEN_ON   │    · receiver EXPORTADO (runtime)        │
                 │    · bucle 30 fps                        │
                 │    · loadBitmapFromView() → RGB565       │
                 │            │                             │
                 │            ▼                             │
                 │  BackScreenNative  (JNI)                 │
                 └────────────┬─────────────────────────────┘
                              │ native_refreshScreen(bmp, 259200)
                              ▼
                   /system/lib64/libBackScreen2.so
                              │
                              ▼
                   /dev/tiny_lcd   (SELinux: mini_device)
                              │
                              ▼
                   ▉ panel trasero 360×360
```

Puntos clave:

- **No hay un segundo Display.** `dumpsys display` reporta `Logical Displays: size=1`.
  `DisplayManager`, `Presentation` y `am start --display N` no sirven de nada.
- El panel **no se compone por SurfaceFlinger**. La vista se rasteriza a `Bitmap` y se empuja por
  JNI. Por eso la capa `MiniScreenWin` figura `invisible` en `dumpsys SurfaceFlinger` **incluso
  con el panel encendido** — es un indicador inválido (ver [RETROSPECTIVA.md](RETROSPECTIVA.md)).
- `com.android.hxyservice` es solo un intermediario: ante el keycode `134`
  (`KEYCODE_MINISCREEN_ON`) se limita a poner `Settings.Global.back_screen_on = 1`.
- El panel trasero tiene **su propio digitalizador táctil** (`hyn_ts`, ejes 0..360 en I2C-6), con
  un `displayId=100` ficticio que no existe entre los displays reales.

---

## Qué se puede hacer sin root

| Objetivo | ¿Sin root? | Cómo |
|---|:---:|---|
| Encender / apagar | ✅ | broadcast `GC_SCREEN_ON` / `GC_SCREEN_OFF` |
| Mostrar **texto propio** | ✅ | notificación + 2 ajustes (abajo) |
| Cambiar la carátula del reloj | ✅ | `Settings.Global.subscreen_current_clock` |
| Saber si está encendido | ✅ | contar `ret = 259200` en logcat |
| Llamar al AIDL del vendor | ✅ | `bindService` + `transact()` crudo |
| **Dibujar píxeles arbitrarios** | ❌ | requiere app de sistema |

### Mostrar texto propio

Dos ajustes vienen **desactivados de fábrica** y son la razón de que "no funcione":

```bash
# 1. Permitir que las notificaciones enciendan el panel (default del recurso = 0)
adb shell settings put global subscreen_noti_screenon 1

# 2. Registrar tu app en la lista de apps permitidas (separador ':')
adb shell settings put global subscreen_selected_noti_apps "<lista>:tu.paquete"
```

Con eso, cualquier app **normal** que postee una notificación hace que el panel se encienda solo
y muestre su `EXTRA_TITLE` y `EXTRA_TEXT`.

### Por qué no se pueden dibujar píxeles arbitrarios

```
UnsatisfiedLinkError: dlopen failed: library "/system/lib64/libBackScreen2.so"
... is not accessible for the namespace "clns-7"
```

**No es SELinux**: es el aislamiento de *namespaces* del linker de Android. La librería no está
en `public.libraries.txt`, así que solo apps que corren en el namespace del sistema
(`/system/app`) pueden cargarla. La API nativa, para referencia:

```java
package com.freeme.backscreen;   // el nombre debe ser exacto: los símbolos JNI dependen de él
public class BackScreenNative {
    public static native boolean native_isScreenOn();
    public static native int native_refreshScreen(Bitmap bitmap, int length);  // length = 259200
    public static native int native_screenOff();
    public static native int native_screenOn();
    public static native int native_setBrightnessScreen(int level);            // 0..15
}
```

---

## Contenido del repo

| Ruta | Qué es |
|---|---|
| [`DIAGNOSTICO.md`](DIAGNOSTICO.md) | Documento técnico completo, con el comando y la salida de cada hallazgo |
| [`RETROSPECTIVA.md`](RETROSPECTIVA.md) | **Qué funcionó, qué no, y qué haría distinto.** Notas de método, incluidos los errores que llevaron a conclusiones falsas |
| [`tools/subscreen.py`](tools/subscreen.py) | Control del panel por ADB. Sin dependencias |
| [`app/`](app/) | App cliente de ejemplo. Se compila **sin Gradle ni Android Studio** |
| [`docs/decompiled/`](docs/decompiled/) | Fragmentos decompilados relevantes, para referencia |

### La herramienta

```bash
python tools/subscreen.py status              # estado real (cuenta frames)
python tools/subscreen.py on | off | toggle   # broadcast
python tools/subscreen.py setup               # habilita la vía de notificaciones (una vez)
python tools/subscreen.py text "Hola mundo"   # texto propio en el panel
python tools/subscreen.py clock N             # cambia la carátula
python tools/subscreen.py dump                # captura dumpsys
```

`text` y `setup` necesitan la app de [`app/`](app/) instalada.

### Compilar la app sin Android Studio

`app/build.sh` hace la cadena completa: `javac → d8 → aapt2 link → zipalign → apksigner → adb install`.
Necesitas un JDK, `android.jar` y las platform/build-tools; las rutas están al principio del
script. Tres detalles que cuestan tiempo si no se saben:

- **`d8` 8.2.2 falla** al dexar ciertas clases anónimas de `javac` moderno. La app está escrita
  sin clases anónimas a propósito.
- **`launchMode="singleTop"`** es obligatorio, o `am start` no entrega intents repetidos a una
  instancia ya viva.
- **`apksigner.bat` exige `JAVA_HOME`**; no basta con `java` en el `PATH`.

---

## Reproducirlo en otro dispositivo

Si tienes otro teléfono con pantalla trasera, el método está en
[RETROSPECTIVA.md](RETROSPECTIVA.md). El resumen:

1. `dumpsys display` → ¿es un Display real o un panel propietario?
2. `pm list packages -f` → localiza los paquetes del vendor.
3. Decompila **todos** los paquetes relacionados antes de teorizar. No solo el que parece el
   principal — ese fue el error que más tiempo costó aquí.
4. Busca receivers registrados en **runtime**, no solo los del manifest:
   `grep -rnE 'registerReceiver(AsUser)?\(' .` y comprueba si pasan `RECEIVER_EXPORTED`.
   El manifest cuenta solo la mitad de la historia.
5. `aapt2 dump resources` → revisa los `bool/config_*` e `integer/default_*`. Ahí están las
   puertas que el OEM deja cerradas de fábrica.
6. Encuentra un **oráculo válido** en logcat antes de concluir nada, y calíbralo con un control
   que ya sepas que funciona (el botón físico).

---

## Referencia rápida

| Dato | Valor |
|---|---|
| Dispositivo | Cubot KINGKONG 11 (`KINGKONG_11_EEA`) |
| Android | 15 (SDK 35), parche 2026-09-05 |
| SoC | MediaTek mt6855 |
| Build | `CUBOT_F041_KINGKONG_11_V14_20260909`, `user`/`release-keys` |
| Panel | 360×360, RGB565, 259 200 B/frame, 30 fps (8 en ahorro) |
| Nodo | `/dev/tiny_lcd` (major 507, SELinux `mini_device`) |
| Táctil trasero | `hyn_ts`, `/dev/input/event5`, I2C-6 `6-005a` |
| Librería | `/system/lib64/libBackScreen2.so` |
| Apps | `com.hxy.miniscreen` (dibuja), `com.android.hxyservice` (intermediario) |
| Broadcasts | `com.yft.miniscreen.action.GC_SCREEN_ON` / `GC_SCREEN_OFF` |
| Keycode | `134` = `KEYCODE_MINISCREEN_ON` |
| AIDL | `com.mediatek.hxymanager.IHxyService` (en `framework.jar`) |

---

## Licencia y alcance

El código de este repo (`tools/`, `app/`) es de dominio público — úsalo como quieras.

`docs/decompiled/` contiene fragmentos cortos de código propietario de Cubot/MediaTek, incluidos
con fines de documentación e interoperabilidad. **No se incluye ningún APK, `.so` ni `.jar` del
fabricante**; si quieres reproducir la decompilación, extrae los archivos de tu propio
dispositivo.

Probado en un solo dispositivo. Otras variantes del KINGKONG 11 pueden diferir: los recursos del
fabricante, por ejemplo, apuntan a rutas de gestos (`/proc/gesture_enable`, un nodo en `i2c-1`)
que **no existen** en la unidad analizada, cuyo táctil trasero está en `i2c-6`.
