# Diagnóstico de pantalla trasera — Cubot KINGKONG 11

Diagnóstico vía ADB del panel trasero: qué es, cómo se activa y hasta dónde se puede llegar
sin root.

**Alcance de lo que se tocó en el teléfono.** Las secciones §1 a §7 son **de solo lectura**: no
se instaló, desinstaló ni modificó nada; los APK se copiaron *del* teléfono *a* la PC
(`adb pull`), que es una operación de lectura. La §8 sí contiene **escrituras**, hechas con
autorización explícita y todas reversibles: `settings put global back_screen_on 0|1`,
`settings put global subscreen_current_clock N` y un intento de broadcast que el sistema rechazó.
No se desbloqueó el bootloader ni se rooteó nada: §9 es un análisis de viabilidad, no un registro
de acciones.

- **Fecha:** 2026-09-24 (§1–§7), 2026-09-25 (§8–§10)
- **Host:** Windows 11, `adb` 1.0.41 (37.0.1) en `C:\Users\<USUARIO>\adb-tools\platform-tools`
- **Carpeta de trabajo:** `C:\Users\<USUARIO>\diag-kingkong11`
- **Conclusión:** **Escenario B — servicio propietario.** La pantalla trasera **no** es un
  Display lógico de Android. Ver [§7](#7-conclusión-escenario-b).

> ### ⚠️ Leer antes que nada: §8 y §8.ter contienen conclusiones que resultaron FALSAS
> Se dio por imposible encender el panel sin root. **Es falso.** Sí se puede, y también se puede
> mostrar texto propio. El mecanismo real está en [§8.quater](#8quater-el-mecanismo-real-correcciones),
> que es la sección que manda sobre todas las anteriores. Las secciones erróneas se conservan
> con su corrección marcada, porque el camino equivocado explica por qué el indicador engañaba.

---

## 1. Conexión, modelo, versión de Android y parche

```
adb devices -l
```
```
List of devices attached
<SERIAL>   device product:KINGKONG_11_EEA model:KINGKONG_11 device:KINGKONG_11 transport_id:1
```

> El dispositivo apareció primero como `unauthorized`; se autorizó aceptando el diálogo RSA
> de depuración USB en el teléfono.

```
adb shell getprop <propiedad>
```

| Propiedad | Valor |
|---|---|
| `ro.product.manufacturer` | `CUBOT` |
| `ro.product.model` | `KINGKONG 11` |
| `ro.product.name` | `KINGKONG_11_EEA` |
| `ro.product.device` | `KINGKONG_11` |
| `ro.build.version.release` | **`15`** (Android 15) |
| `ro.build.version.sdk` | **`35`** |
| `ro.build.version.security_patch` | **`2026-09-05`** |
| `ro.build.id` | `AP3A.240905.015.A2` |
| `ro.build.display.id` | `CUBOT_F041_KINGKONG_11_V14_20260909` |
| `ro.build.fingerprint` | `CUBOT/KINGKONG_11_EEA/KINGKONG_11:15/AP3A.240905.015.A2/2026363:user/release-keys` |
| `ro.board.platform` / `ro.hardware` | `mt6855` (MediaTek) |

**Hallazgo:** build `user` / `release-keys`, es decir firmware de producción sin root.
El parche de seguridad (2026-09-05) está al día respecto al build (2026-09-09).

---

## 2. ¿La pantalla trasera es un Display lógico? → **No**

```
adb shell dumpsys display
adb shell dumpsys SurfaceFlinger
```

Recortes decisivos de `dumpsys display`:

```
Display States: size=1
Display Devices: size=1
Logical Displays: size=1
    mDisplayId=0
```

Único display físico/lógico presente:

| Campo | Valor |
|---|---|
| `mDisplayId` | `0` |
| `uniqueId` | `local:0` |
| Nombre | `"Pantalla integrada"` |
| Resolución | **1080 x 2400** |
| Modos | 60 / 90 / 120.00001 Hz |
| Densidad | 400 dpi (391.64 x 391.64) |
| `type` | `INTERNAL`, `address {port=0}` |
| Flags | `FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY`, `FLAG_ROTATES_WITH_CONTENT`, `FLAG_SECURE`, `FLAG_SUPPORTS_PROTECTED_BUFFERS`, `FLAG_TRUSTED` |

`mViewports` también reporta un solo viewport (`displayId=0`, `local:0`, `physicalPort=0`).

Confirmación en `dumpsys SurfaceFlinger`:

```
Display 0 (HWC display 0): no display identification data
Display 0 (physical, "Primary display")
    activeMode={id=0, hwcId=0, resolution=1080x2400, vsyncRate=60.00 Hz, dpi=391.64x391.64, group=0}
```

**Hallazgo:** existe **un único display** (id `0`, `local:0`, 1080x2400, HWC display 0).
No hay un segundo `DisplayDeviceInfo`, ni un `local:1`, ni display virtual/externo.
La pantalla trasera **no está expuesta como Display a Android**, así que no es alcanzable
con `DisplayManager`, `Presentation`, `--display` ni `ActivityOptions.setLaunchDisplayId()`.

---

## 3. Paquetes relacionados

```
adb shell pm list packages -f          # 260 paquetes en total
```

Filtrando por `subscreen|miniscreen|rear|second|sub`:

```
package:/system/app/HxyMiniScreen/HxyMiniScreen.apk=com.hxy.miniscreen
```

Un solo match directo. Ampliando a paquetes de vendor no-AOSP se encontró la contraparte
del mecanismo, que no coincidía con las palabras clave:

```
package:/system/app/HxyService/HxyService.apk=com.android.hxyservice
package:/system/app/HxyNavigationBarSettings/HxyNavigationBarSettings.apk=com.android.settings.navigationbar
package:/product/overlay/HxyFrameOverlay/HxyFrameOverlay.apk=com.android.hxyframeoverlay
package:/system/app/NewFactoryTest/NewFactoryTest.apk=com.teksun.factorytest
package:/system_ext/app/HxyCamera/HxyCamera.apk=com.mediatek.camera
```

Ambos procesos están corriendo:

```
adb shell ps -A -o USER,PID,NAME
```
```
system       2272 com.android.hxyservice
u0_a76      19794 com.hxy.miniscreen
root          954 [mt_mini_top]          # hilo de kernel
u0_a76       2060 com.android.systemui   # mismo UID que miniscreen
```

**Hallazgo:** "Hxy" es el prefijo del OEM/ODM. `com.hxy.miniscreen` corre con **el mismo UID
que SystemUI** (`u0_a76`) y `com.android.hxyservice` con **UID `system`**. El hilo de kernel
`[mt_mini_top]` sugiere un driver dedicado a la pantalla pequeña.

---

## 4. Manifests de los APK extraídos

```
adb pull /system/app/HxyMiniScreen/HxyMiniScreen.apk apks/HxyMiniScreen.apk
adb pull /system/app/HxyService/HxyService.apk        apks/HxyService.apk
aapt2 dump xmltree apks/<apk> --file AndroidManifest.xml
aapt2 dump badging apks/HxyMiniScreen.apk
```

| APK | Tamaño | SHA-256 |
|---|---|---|
| `apks/HxyMiniScreen.apk` | 34 936 184 B | `05d96edec6fac7feb8b76b8597de2cd792901dded8c741939baa0ec53cf2ce4b` |
| `apks/HxyService.apk` | 130 537 547 B | — |

### 4.1 `com.hxy.miniscreen` (etiqueta "MiniScreen")

```
package: name='com.hxy.miniscreen' versionCode='35' versionName='15'
sdkVersion:'35'  targetSdkVersion:'35'
application-label:'MiniScreen'
application: icon='res/drawable/ic_settings_subscreen.xml'
```

Atributos de nivel `<manifest>` / `<application>`:

| Atributo | Valor |
|---|---|
| `android:sharedUserId` | **`android.uid.systemui`** |
| `android:name` | `com.hxy.miniscreen.application.MiniScreenApp` |
| `android:persistent` | **`true`** |
| `android:usesNonSdkApi` | **`true`** |
| `android:resizeableActivity` | `false` |
| `android:allowBackup` | `true` |
| `android:usesCleartextTraffic` | `true` |

**Servicios**

| Servicio | `exported` | Notas |
|---|---|---|
| `com.hxy.miniscreen.MiniScreenService` | **no declarado → `false`** | Sin `intent-filter`. Lleva un atributo insólito `android:authorities="com.hxy.miniscreen.clock.ScreenService"` (`authorities` no aplica a `<service>`; probable error del OEM). **No es invocable desde otra app.** |
| `androidx.room.MultiInstanceInvalidationService` | `false` | AndroidX, `directBootAware=true` |

**Receivers**

| Receiver | `exported` | `permission` |
|---|---|---|
| `androidx.profileinstaller.ProfileInstallReceiver` | `true` | `android.permission.DUMP` (protege el acceso; es AndroidX estándar, no del subscreen) |

**Providers**

| Provider | `exported` | Authority |
|---|---|---|
| `com.hxy.miniscreen.clock.db.SubscreenClockProvider` | **`true`** (sin permiso) | `com.hxy.miniscreen.clock.db.SubscreenClock` |
| `androidx.core.content.FileProvider` | `false` | `com.hxy.miniscreen.fileProvider` |
| `androidx.startup.InitializationProvider` | `false` | `com.hxy.miniscreen.androidx-startup` |

**Activities**

| Activity | `exported` | Notas |
|---|---|---|
| `com.hxy.miniscreen.settings.SettingActivity` | `true` | `MAIN` + `com.android.settings.action.EXTRA_SETTINGS`; `process="com.hxy.miniscreensetting"`, `launchMode=3` (singleInstance). Se inyecta en Ajustes vía `meta-data com.android.settings.category.ia.homepage` |
| `...settings.clock.AddClockActivity` | `true` | |
| `...settings.clock.ClockSelectActivity` | `true` | |
| `...settings.YftMiniScreenHelp` | `true` | |
| `...settings.music.MusicActivity` | `true` | |
| `...settings.notification.NofiAppsSelectActivity` | no declarado → `false` | |
| `com.android.settingslib.avatarpicker.AvatarPickerActivity` | `true` | de SettingsLib |

**Permisos solicitados** (21 `uses-permission`, `INTERNET` duplicado)

```
android.permission.CHANGE_CONFIGURATION          android.permission.WRITE_SETTINGS
android.permission.SYSTEM_ALERT_WINDOW           android.permission.WRITE_SECURE_SETTINGS
android.permission.SYSTEM_OVERLAY_WINDOW         android.permission.READ_CONTACTS
android.permission.VIBRATE                       android.permission.READ_PHONE_STATE
android.permission.CAMERA                        android.permission.READ_SMS
android.permission.STATUS_BAR_SERVICE            android.permission.ACCESS_NETWORK_STATE
android.permission.READ_EXTERNAL_STORAGE         android.permission.READ_DEVICE_CONFIG
android.permission.WRITE_EXTERNAL_STORAGE        android.permission.INTERNET  (x2)
android.permission.MANAGE_EXTERNAL_STORAGE       com.universal.rewards.read
com.hxy.miniscreen.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

Permiso propio declarado: `com.hxy.miniscreen.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
`protectionLevel=0x2` (`signature`).

**Hallazgo:** los permisos son de nivel privilegiado (`WRITE_SECURE_SETTINGS`,
`STATUS_BAR_SERVICE`, `SYSTEM_OVERLAY_WINDOW`) y solo se conceden porque el APK es de
`/system/app` y comparte el UID de SystemUI. **Ninguno es reproducible por una app de
terceros.** Las clases internas confirman el propósito:

```
com/hxy/miniscreen/MiniScreenService        com/hxy/miniscreen/tinylcd/TinylcdFunction
com/hxy/miniscreen/MiniScreenMainFunction   com/hxy/miniscreen/tinylcd/FlipTurnOnFunction
com/hxy/miniscreen/frame/MiniScreenFunction com/hxy/miniscreen/tinylcd/OnWithNotification
com/hxy/miniscreen/clock/db/SubscreenClockProvider  com/hxy/miniscreen/tinylcd/OnWithDoubleClick
                                            com/hxy/miniscreen/tinylcd/LuxLevelFunction
                                            com/hxy/miniscreen/tinylcd/OnWithScreenFunction
                                            com/hxy/miniscreen/tinylcd/ScheduleHomeFunction / ScheduleOffFunction
```

El APK **no contiene librerías nativas** (`lib/` ausente; solo `classes.dex`..`classes13.dex`,
`resources.arsc`, `AndroidManifest.xml`).

### 4.2 `com.android.hxyservice` (contraparte privilegiada)

| Atributo | Valor |
|---|---|
| `android:sharedUserId` | **`android.uid.system`** |
| `android:name` | `com.android.hxyservice.ServiceApplication` |
| `android:persistent` | **`true`** |

| Componente | Tipo | `exported` | Acción |
|---|---|---|---|
| `com.android.hxyservice.HxyService` | service | **`true`** (sin permiso) | `com.android.hxyservice.bindservice` + `category.DEFAULT` |
| `com.android.hxyservice.BootCompletedReceiver` | receiver | **`true`** | `BOOT_COMPLETED`, múltiples `SECRET_CODE`, `PTT.down/up`, **`com.hxy.aod_service_refresh`**, `mediatek.intent.action.BATTERY_WARNING`, `com.start.water_camera`, `com.trf.upanddown` |

Permisos destacados: `MANAGE_FINGERPRINT`, `CALL_PRIVILEGED`, `PREVENT_POWER_KEY`,
`READ_DREAM_STATE`, `WRITE_DREAM_STATE`, `SYSTEM_ALERT_WINDOW`,
`CONTROL_INCALL_EXPERIENCE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
Declara `com.android.hxyservice.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

**Hallazgo:** `HxyService` está **exportado sin permiso** y es bindeable con la acción
`com.android.hxyservice.bindservice`. Corre como `system` y persistente. La acción
`com.hxy.aod_service_refresh` liga este servicio al refresco del Always-On Display de la
pantalla pequeña. Es la superficie de IPC más prometedora, aunque su interfaz (AIDL/Messenger)
no está documentada y habría que deducirla del dex.

---

## 5. Nodos del sistema (solo lectura)

### 5.1 `/dev`

```
adb shell ls /dev
adb shell ls -lZ /dev/tiny_lcd /dev/SUB2AF
```
```
crwxrwxrwx 1 system system u:object_r:mini_device:s0    507,   0  /dev/tiny_lcd
crw-rw---- 1 system camera u:object_r:SUB2AF_device:s0  490,   0  /dev/SUB2AF
```

- **`/dev/tiny_lcd`** — el nodo de la pantalla trasera. Char device major **507** minor 0,
  dueño `system:system`, etiqueta SELinux **`mini_device`**, modo **`crwxrwxrwx`**
  (lectura/escritura para *cualquiera* a nivel DAC; SELinux sigue siendo el control efectivo).
- `/dev/SUB2AF` — **falso positivo**: actuador de autofoco de la cámara secundaria
  (`/sys/class/actuatordrv_sub2_af`), no tiene relación con la pantalla.
- `/dev/graphics` no existe (`No such file or directory`); no hay framebuffer legacy.

### 5.2 `/sys/class`

```
adb shell ls /sys/class | grep -iE 'lcd|disp|panel|screen|sub|mini|rear|second|tiny|backlight'
```
```
tiny_lcd_cls
backlight
actuatordrv_sub2_af
```
```
adb shell ls -lR /sys/class/tiny_lcd_cls/
```
```
/sys/class/tiny_lcd_cls:
lrwxrwxrwx 1 root root 0 tiny_lcd -> ../../devices/virtual/tiny_lcd_cls/tiny_lcd
```
```
adb shell ls -l /sys/devices/virtual/tiny_lcd_cls/tiny_lcd/
```
```
-????????? ? ? ? ? dev          # ilegible sin root
drwxr-xr-x 2 root root 0 power
lrwxrwxrwx 1 root root 0 subsystem -> ../../../../class/tiny_lcd_cls
-????????? ? ? ? ? uevent
```

**Hallazgo:** la clase `tiny_lcd_cls` **no expone atributos sysfs de control**
(no hay `brightness`, `enable`, `mode`, etc.), solo los ficheros estándar `dev`/`uevent`/`power`.
El control va por el char device, no por sysfs.

### 5.3 `service list`

```
adb shell service list          # 284 servicios
```

Filtrando por `screen|display|sub|mini|rear|second|lcd|panel|hxy`:

```
70  color_display: [android.hardware.display.IColorDisplayManager]
91  display:       [android.hardware.display.IDisplayManager]
126 iphonesubinfo: [com.android.internal.telephony.IPhoneSubInfo]
129 isub:          [com.android.internal.telephony.ISub]
```

**Hallazgo:** **no existe ningún servicio binder registrado** para la pantalla trasera
(`tiny_lcd`, `miniscreen`, `subscreen`...). Los 4 matches son servicios AOSP estándar
(`iphonesubinfo`/`isub` son telefonía, falsos positivos). El acceso **no** es vía
`ServiceManager`; es vía `bindService` a `com.android.hxyservice`.

### 5.4 Ruta de escritura al nodo

Ninguno de los dos APK referencia el nodo desde Java:

```
unzip -o apks/HxyMiniScreen.apk 'classes*.dex' -d dex/
grep -ao '/dev/[a-zA-Z0-9_]*' dex/*.dex     | sort -u   # -> solo /dev/cpuset
grep -ao 'tiny_lcd[a-zA-Z_/]*' dex/*.dex                # -> sin resultados
grep -ao 'tiny_lcd[a-zA-Z_/]*' dex_svc/*.dex            # -> sin resultados
```

Tampoco usan las APIs de captura o de display virtual:

| Cadena buscada | `HxyMiniScreen` | `HxyService` |
|---|---|---|
| `createVirtualDisplay` / `VirtualDisplay` | ausente | ausente |
| `SurfaceControl` | ausente | ausente |
| `ImageReader` | — | ausente |
| `ioctl` / `LocalSocket` / `nativeWrite` | ausente | — |
| `Presentation` | presente (clase AOSP en libs embebidas) | — |
| `DisplayManager` | presente | — |

**Hallazgo:** la escritura a `/dev/tiny_lcd` **no ocurre en las apps Java**. Sucede por debajo,
en el compositor/HWC del vendor o en el driver de kernel (`[mt_mini_top]`, etiqueta SELinux
`mini_device`). Las apps solo *pintan una ventana*; el firmware la desvía al panel pequeño.

### 5.5 La pantalla trasera tiene su propio digitalizador táctil

```
adb shell dumpsys input
```

Hay **dos controladores táctiles**, en buses distintos:

| Device | Nodo | Bus (`SysfsDevicePath`) | Ejes crudos | Qué es |
|---|---|---|---|---|
| `fts_ts` | `/dev/input/event3` | SPI3 (`11013000.spi3`) | X `0..1080`, Y `0..2400`, 10 slots | **Pantalla principal** (FocalTech) |
| `hyn_ts` | `/dev/input/event5` | I2C-6 (`6-005a`) | **X `0..360`, Y `0..360`**, 6 slots | **Pantalla trasera** (Hynitron) |

```
    6: hyn_ts
      Classes: KEYBOARD | TOUCH | TOUCH_MT | LIGHT
      Path: /dev/input/event5
      SysfsDevicePath: /sys/devices/platform/11db1000.i2c/i2c-6/6-005a
      Raw Touch Axes:
        X: min=0, max=360, ...
        Y: min=0, max=360, ...
        Slot: min=0, max=5, ...
      Viewport INTERNAL: displayId=100, uniqueId=local:0, port=0, orientation=0,
                         logicalFrame=[0, 0, 1080, 2400], physicalFrame=[0, 0, 1080, 2400],
                         deviceSize=[1080, 2400], isActive=[0]
```

**Hallazgos:**

1. Los ejes crudos de `hyn_ts` son **exactamente 360x360**, la misma geometría que la ventana
   `MiniScreenWin`. Es el táctil del panel trasero.
2. Su viewport dice **`displayId=100`** — pero **el display 100 no existe** (§2: solo hay el
   display `0`). Y aun así reporta `uniqueId=local:0` con `logicalFrame=[0,0,1080,2400]`.
   Es una **incoherencia deliberada del vendor**: se le asigna un id de display ficticio para
   que el evento no se entregue a las apps del display 0, mientras el `uniqueId` real lo mantiene
   atado a `local:0`. Refuerza que no hay un segundo display de verdad.
3. `EnableForInactiveViewport: false` en ambos: el táctil trasero está inactivo mientras el
   panel está apagado.

---

## 6. Activación: botón lateral y doble toque

Comportamiento observado por el usuario: **la pantalla trasera se activa con un botón, o dando
doble click a la pantalla**. Ambas rutas quedaron confirmadas.

### 6.1 Botón físico programable → `custom_key0`

```
adb shell dumpsys input
```
```
    7: custom_key0
      Classes: KEYBOARD | BATTERY
      Path: /dev/input/event0
      Descriptor: <DESCRIPTOR>
      KeyLayoutFile: /system/usr/keylayout/Generic.kl
  Device 4: custom_key0
    Sources: KEYBOARD | TOUCHSCREEN | STYLUS
    Listeners: 1 battery listeners
      0: pid=2060, monitored devices=[4]        # pid 2060 = com.android.systemui
```

La asignación de acciones del botón vive en `Settings.System`:

```
adb shell settings list system
```
```
prize_smart_link_key_single_click_left=miniscreen          <-- ACTIVA LA PANTALLA TRASERA
prize_smart_link_key_double_click_left=open_led
prize_smart_link_key_long_press_left=com.mediatek.camera
prize_smart_link_key_single_click=none
prize_smart_link_key_double_click=none
prize_smart_link_key_long_press=com.mediatek.camera
```

Quien despacha esas acciones es `HxyService`, cuyo dex contiene las claves y los tipos de gesto:

```
grep -aoh 'prize_smart_link[a-z_]*' dex_svc/*.dex | sort -u
```
```
prize_smart_link_key_single_click       prize_smart_link_key_double_click
prize_smart_link_key_single_click_left  prize_smart_link_key_double_click_left
prize_smart_link_key_long_press         prize_smart_link_key_long_press_left
prize_smart_link_key_selected / _unselected / _item_background / _item_background_press
```
```
grep -aoh 'KEYCODE_[A-Z0-9_]*' dex_svc/*.dex | sort -u
```
```
KEYCODE_MINISCREEN_ON               <-- keycode propietario
KEYCODE_ACTION_TYPE_CLICK
KEYCODE_ACTION_TYPE_DOUBLECLICK
KEYCODE_ACTION_TYPE_LONGPRESS
KEYCODE_ACTION_TYPE_LONGPRESS_DONE
KEYCODE_ACTION_TYPE_NONE
KEYCODE_FLASHLIGHT
```

**Hallazgo:** existe un keycode propietario **`KEYCODE_MINISCREEN_ON`** y una máquina de estados
propia de click / doble click / pulsación larga, implementada en `com.android.hxyservice`
(UID `system`, `persistent`), que además tiene el permiso **`android.permission.PREVENT_POWER_KEY`**
(§4.2) para interceptar teclas antes que el framework. El valor por defecto
`prize_smart_link_key_single_click_left=miniscreen` es literalmente la acción "encender la
pantalla trasera".

### 6.2 Doble toque → `OnWithDoubleClick`

Las clases del gesto están en `com.hxy.miniscreen.tinylcd` (§4.1):

| Clase | Disparador de encendido |
|---|---|
| `com.hxy.miniscreen.tinylcd.OnWithDoubleClick` | **doble toque** |
| `com.hxy.miniscreen.tinylcd.FlipTurnOnFunction` | al girar el teléfono |
| `com.hxy.miniscreen.tinylcd.OnWithNotification` | al llegar una notificación |
| `com.hxy.miniscreen.tinylcd.OnWithScreenFunction` | junto con la pantalla principal |
| `com.hxy.miniscreen.tinylcd.ScheduleHomeFunction` / `ScheduleOffFunction` | por horario |
| `com.hxy.miniscreen.tinylcd.LuxLevelFunction` | brillo según sensor de luz |

El doble toque físico entra, con toda probabilidad, por el digitalizador trasero `hyn_ts`
(`/dev/input/event5`, 360x360,
§5.5). El ajuste equivalente de la pantalla principal es independiente y sí es AOSP estándar:

```
adb shell settings list secure
```
```
double_tap_to_wake=1        # pantalla PRINCIPAL, AOSP
```

### 6.3 Estado persistido de la pantalla trasera → `Settings.Global`

```
adb shell settings list global
```
```
back_screen_on=0                        <-- 0 = pantalla trasera APAGADA
subscreen_current_clock=13
subscreen_take_photo_voice_enable=0
yft_miniscreen_test=0
aod_enabled=0
```

Las tres primeras claves aparecen como cadenas dentro de los dex:

```
grep -aoh 'back_screen_on\|subscreen_current_clock\|yft_miniscreen_test' dex/*.dex dex_svc/*.dex | sort -u
```
```
back_screen_on
subscreen_current_clock
yft_miniscreen_test
```

**Hallazgo:** el encendido/apagado de la pantalla trasera está reflejado en
**`Settings.Global.back_screen_on`**. Su valor `0` es coherente con lo observado en §7: la capa
`MiniScreenWin#113` está marcada `invisible` y el viewport de `hyn_ts` tiene `isActive=[0]`.

> **Corrección (probado en §9):** inicialmente se interpretó esta clave como la *palanca* de
> encendido. **Es falso.** La prueba empírica demostró que es un **espejo de estado**: se puede
> escribir, el valor persiste unos segundos, pero **el panel no se enciende** y el sistema acaba
> revirtiéndola a `0`. Ver [§8](#8-pruebas-de-escritura-qué-funciona-de-verdad).

---

## 7. Conclusión: **Escenario B**

**La pantalla trasera es un servicio propietario, no un Display estándar de Android.**

El mecanismo real quedó identificado: la app dibuja una **ventana overlay de 360x360 sobre el
display 0**, y el compositor modificado del vendor la redirige al panel trasero.

```
adb shell dumpsys window windows
```
```
Window #2 Window{259e3a2 u0 MiniScreenWin}:
  mDisplayId=0 rootTaskId=1 mSession=Session{ac62060 19794:u0a10076}
  mOwnerUid=10076 showForAllUsers=true package=com.hxy.miniscreen appop=NONE
  mAttrs={(0,0)(360x360) gr=TOP START CENTER ty=DISPLAY_OVERLAY fmt=RGBA_8888
  Frames: parent=[0,0][1080,2400] display=[0,0][1080,2400] frame=[0,0][360,360]
  mHasSurface=true isReadyForDisplay()=true mWindowRemovalAllowed=false
```
```
adb shell dumpsys SurfaceFlinger
```
```
Layer Hierarchy
 ROOT
 ├─ Display 0 name="Pantalla integrada"#43 pid=1689 uid=1000
 │  ├─ ...
 │  │              └─ WindowToken{9f62b6d type=2026 ...}#111 pid=1689 uid=1000
 │  │                 └─ 259e3a2 MiniScreenWin#112 pid=1689 uid=1000
 │  │                    └─ MiniScreenWin#113 pid=19794 uid=10076

  Layer [113] MiniScreenWin#113
    bounds={0,0,360,360}
    input{(NOT_FOCUSABLE | PREVENT_SPLITTING | TRUSTED_OVERLAY) touchableRegion={0,0,360,360}}

+ name:VRI[MiniScreenWin]#0(BLAST Consumer)0, size:548.00KiB, w/h:360x360, req fmt:1 (RGBA_8888)
```

### Evidencia que sostiene B

| # | Evidencia | Fuente |
|---|---|---|
| 1 | `Logical Displays: size=1`, `Display Devices: size=1`, `Display States: size=1` | §2 |
| 2 | Un solo display físico en SurfaceFlinger: `Display 0 (physical, "Primary display")`, 1080x2400, HWC display 0 | §2 |
| 3 | `MiniScreenWin` es hijo de **`Display 0`** en la jerarquía de capas, con `mDisplayId=0` y `parent=[0,0][1080,2400]` | §7 |
| 4 | Es una ventana `ty=DISPLAY_OVERLAY` (`type=2026`) de 360x360, `TRUSTED_OVERLAY`, buffer BLAST de 548 KiB a 360x360 RGBA_8888 | §7 |
| 5 | Ninguna app usa `createVirtualDisplay`, `VirtualDisplay` ni `SurfaceControl` | §5.4 |
| 6 | No hay servicio binder de subscreen en los 284 de `service list` | §5.3 |
| 7 | El panel está tras un char device propietario `/dev/tiny_lcd` (major 507, SELinux `mini_device`) con clase `tiny_lcd_cls` sin atributos de control | §5.1, §5.2 |
| 8 | El control lo ejercen dos apps de `/system/app` privilegiadas y `persistent`: `com.hxy.miniscreen` (UID de SystemUI) y `com.android.hxyservice` (UID `system`) | §3, §4 |
| 9 | Hilo de kernel dedicado `[mt_mini_top]` | §3 |
| 10 | El táctil trasero `hyn_ts` reporta **`displayId=100`, un display que no existe**, con `uniqueId=local:0` — mapeo ficticio del vendor en lugar de un display real | §5.5 |
| 11 | La activación pasa por un **keycode propietario `KEYCODE_MINISCREEN_ON`** y una máquina de gestos propia en `HxyService`, no por el framework | §6.1 |
| 12 | El estado del panel se persiste en **`Settings.Global.back_screen_on`**, no en el `DisplayManager` | §6.3 |

### Qué implica en la práctica

- **No funciona:** `DisplayManager.getDisplays()` no verá la pantalla trasera;
  `Presentation`, `ActivityOptions.setLaunchDisplayId()`, `adb shell am start --display 1`
  y las herramientas multi-display no tienen a dónde apuntar. Solo existe el display `0`.
- **No funciona desde una app normal:** `MiniScreenService` no está exportado.
  Escribir `/dev/tiny_lcd` requiere pasar SELinux (`mini_device`), no solo los permisos DAC
  que el `crwxrwxrwx` sugiere.
> **⚠️ Esta lista quedó desactualizada por §8.quater.** El broadcast `GC_SCREEN_ON` y la vía de
> notificaciones sí funcionan sin root.

- **Vías probadas y descartadas** (detalle en §8):
  - ❌ `Settings.Global.back_screen_on` — es un espejo de estado, no un actuador. Se escribe
    pero el panel no se enciende y el sistema lo revierte.
  - ❌ Broadcast `com.hxy.aod_service_refresh` — *protected broadcast*; ni `shell` lo puede enviar.
- **Vías que siguen abiertas**, en orden de viabilidad:
  1. **`bindService` a `com.android.hxyservice/.HxyService`** con la acción
     `com.android.hxyservice.bindservice` — está **exportado y sin permiso**. Hay que deducir
     el protocolo (AIDL o `Messenger`) desde `apks/HxyService.apk`. Es la vía con más alcance
     sin root: ahí vive `KEYCODE_MINISCREEN_ON` y el despacho de gestos.
  2. **`ContentProvider` `com.hxy.miniscreen.clock.db.SubscreenClock`** — ✅ confirmado
     accesible sin permiso (§8), aunque la tabla `clocks` está vacía. Alcance limitado al reloj.
  3. **Notificaciones:** `OnWithNotification` indica que la pantalla reacciona a notificaciones
     de apps seleccionadas en `NofiAppsSelectActivity`. Es la única ruta *soportada* para que
     una app de terceros muestre algo atrás. Sin verificar.
  4. **Root vía desbloqueo de OEM** (§9) — la única que da acceso al nodo y por tanto a dibujar
     contenido arbitrario.
- Cualquier ruta que implique escribir el nodo o suplantar a `MiniScreenService` exige
  firmware modificado o root; el build es `user`/`release-keys`.

---

## 8. Pruebas de escritura: qué funciona de verdad

Ejecutadas con `subscreen.py` (nivel de privilegio `shell`, sin root). Esta sección **corrige**
las expectativas de §7 y §6.3.

| Intento | Comando | Resultado |
|---|---|---|
| Escribir `back_screen_on=1` | `settings put global back_screen_on 1` | ✅ escribe, ✔ persiste (leído a 0s/1s/4s = `1`) — ❌ **el panel NO se enciende**: `MiniScreenWin` sigue `invisible` y `hyn_ts` sigue `isActive=[0]`. El sistema lo revierte a `0` al rato. |
| Broadcast de refresco AOD | `am broadcast -a com.hxy.aod_service_refresh` | ❌ **`SecurityException: not allowed to send broadcast ... from uid=2000`** — es un *protected broadcast* del vendor; ni `shell` puede enviarlo |
| Leer el provider exportado | `content query --uri content://com.hxy.miniscreen.clock.db.SubscreenClock/clocks` | ✅ **accesible sin permiso** (responde `No result found`: la tabla existe pero está vacía) |
| Cambiar `subscreen_current_clock` | `settings put global subscreen_current_clock N` | ✅ escribe, pero sin panel encendido no es observable |

> **⚠️ CORRECCIÓN (§8.quater):** lo que sigue en esta sección es **incorrecto**. `back_screen_on`
> sí está observado por un `ContentObserver`, pero detrás de condiciones; y el nivel `shell` **no**
> está cerrado: el broadcast `com.yft.miniscreen.action.GC_SCREEN_ON` enciende el panel.
> El error de fondo fue medir con la capa de SurfaceFlinger, que es un indicador inválido.

**Conclusión de las pruebas (ERRÓNEA, ver §8.quater):** `back_screen_on` es un **espejo de estado**, no un actuador.
Lo escribe el dueño del panel (`MiniScreen`/`HxyService`) cuando el panel cambia de estado, y
algo lo re-sincroniza con la realidad del hardware. Escribirlo desde fuera no dispara nada
porque **nadie escucha esa clave como orden**.

Con eso, el nivel de privilegio `shell` (ADB) queda cerrado:

- No hay servicio binder al que llamar (§5.3).
- El broadcast del vendor está protegido.
- La clave de Settings es solo lectura en la práctica.
- `/dev/tiny_lcd` requiere el dominio SELinux correcto, y `shell` no lo tiene.

Dato extra capturado en `logcat` al escribir la clave: el proceso 1689 emite con tag `hxy` la
línea `pkg:::android---name::::android.hardware.type.watch`, es decir la implementación del
vendor consulta el *form factor* de reloj. Pista de que el panel trasero se modela internamente
como un dispositivo tipo watch.

---

## 8.bis. Decompilado de HxyService (jadx) — la interfaz real

Con `jadx-gui-1.5.6-with-jre` (trae su propio JRE; no hizo falta JDK) se decompiló el servicio.
Artefactos en `jadx-out/`.

**El binder que devuelve `onBind` implementa `com.mediatek.hxymanager.IHxyService`** con 4 métodos,
y **ninguno verifica el permiso del llamador** — combinado con que el servicio está exportado
sin permiso (§4.2), **una app de terceros puede bindearlo y llamarlos**:

| Método AIDL | Qué hace por dentro (`ServiceManager`) |
|---|---|
| `handleIntent(Intent)` | `HandleIntent` → sólo actúa si la acción es `INTENT_SECRET_CODE`; el resto se ignora |
| `getServiceInfo(String)` | lee de un `HashMap` en memoria (`mServiceInfoes`) |
| `updateServiceInfo(String,String)` | escribe en ese `HashMap`; **no toca el panel** |
| `handleKeycode(int keycode, int action)` | `checkKeycode` + `sendRequest(128, keycode, action)` → enruta la tecla por el framework interno |

**`MiniscreenFunction` (la pieza del panel dentro de HxyService) es un shim de 45 líneas:**

```java
public boolean isSupported() {
    return !TextUtils.isEmpty(SystemProperties.get("ro.hxy_support_tinylcd", ""));
}
public void init() {
    requestKeycode(134, 1);   // se suscribe al keycode 134 = KEYCODE_MINISCREEN_ON
    requestKeycode(134, 2);
}
public void onKeycode(int keycode, int type) {
    if (getGlobalValue("back_screen_on", 0) == 0)
        putGlobalValue("back_screen_on", 1);   // <-- lo UNICO que hace: poner la clave a 1
}
```

Y la prop existe en tu equipo:
```
adb shell getprop ro.hxy_support_tinylcd
[ro.hxy_support_tinylcd]: [tlsc6x_touch,hyn_ts]
```

**Conclusión del decompilado (cierra el misterio de §8):**

1. `HxyService` **no dibuja nada ni abre `/dev/tiny_lcd`**. Ante el keycode 134, su única acción
   es poner `back_screen_on=1`. Es un intermediario de estado.
2. El **dibujo y el encendido físico** viven en `com.hxy.miniscreen` (UID de SystemUI) + el
   HWC/kernel. Esa app también referencia `back_screen_on` y `134` en su dex.
3. Por eso escribir `back_screen_on=1` desde fuera (§8) no encendió nada: el que dibuja **no
   reacciona a la clave**, reacciona al **evento de tecla/entrada**, y además re-sincroniza la
   clave con el estado real del hardware.
4. **La única palanca sin root que queda sin probar** es llamar `handleKeycode(134, …)` por el
   binder: eso mete el keycode en el despacho interno vía `sendRequest(128,…)`, que es más
   parecido a lo que hace el botón físico que escribir la clave a pelo. Puede funcionar o no;
   hay que probarlo con una app.

---

## 8.ter. App cliente propia — prueba en vivo

Se construyó una app (`com.example.subscreenpoke`, "Subscreen Poke") **sin Android Studio ni
Gradle** — cadena `javac → d8 → aapt2 → apksigner` a mano (ver `C:\Users\<USUARIO>\subscreen-app`).
Bindea a `com.android.hxyservice/.HxyService` y llama la interfaz AIDL del vendor.

**Detalle técnico que costó y vale documentar:** reusar la clase
`com.mediatek.hxymanager.IHxyService` **falla** — esa clase existe en `framework.jar` (boot
classpath), gana sobre la copia de la app, y su `asInterface()` es **hidden-API bloqueada**
(`NoSuchMethodError: ... api=blocked ... denied`). La solución fue hablar el binder **crudo** con
`IBinder.transact()` (API pública), escribiendo el descriptor y los transaction codes a mano
(clase `HxyClient`).

**Resultado en el teléfono (log de la propia app):**
```
bindService(com.android.hxyservice.bindservice) -> true
CONECTADO a com.android.hxyservice/.HxyService
binder = android.os.BinderProxy@...  (descriptor=com.mediatek.hxymanager.IHxyService)
handleKeycode(134, 1) -> handled=true
back_screen_on: 0 -> 0
handleKeycode(134, 2) -> handled=true
back_screen_on: 0 -> 0
getServiceInfo(back_screen_on) = null
```

| Comprobado | Resultado |
|---|---|
| Una app de terceros puede **bindear** el servicio | ✅ `bindService → true`, `onServiceConnected` disparado |
| El descriptor del binder es el correcto | ✅ `com.mediatek.hxymanager.IHxyService` |
| La llamada AIDL **llega y retorna** | ✅ `handleKeycode(134,1) → handled=true` |
| ¿Enciende el panel? | ❌ **No.** `back_screen_on` sigue `0`, capa `invisible` |

> **⚠️ CORRECCIÓN (§8.quater):** la conclusión de abajo decía que no había forma de encender el
> panel. **Es falsa.** Lo correcto: `handleKeycode` efectivamente no lo enciende, pero el broadcast
> `GC_SCREEN_ON` sí, y las notificaciones permiten además mostrar texto propio.

**Conclusión (PARCIALMENTE ERRÓNEA, ver §8.quater):**
`handleKeycode` **sí se ejecuta** en el servicio (`handled=true` = el keycode 134 está
registrado), pero **no enciende la pantalla**. Coherente con §8.bis: `HxyService` sólo voltea un
flag espejo; el encendido y el dibujo reales viven en `com.hxy.miniscreen` (UID de SystemUI) +
HWC/kernel, que reaccionan al **evento de tecla del hardware**, no a esta llamada IPC.

**Lo que esto demuestra y lo que no:**
- ✅ Sí se puede escribir una app propia que **hable con el servicio del panel** por su interfaz
  real. Eso funciona hoy, sin root.
- ❌ No se puede, desde ahí, **encender ni dibujar** en el panel. Ese control no está expuesto a
  apps por ningún canal (ni Settings §8, ni broadcast §8, ni este AIDL §8.ter).
- ~~Para dibujar contenido propio no queda más camino que el de root/OEM (§9).~~
  **Corregido:** sólo el dibujo de *píxeles arbitrarios* requiere root. Encender el panel y
  mostrar **texto propio** sí se puede sin root — ver §8.quater.

---

## 8.quater. El mecanismo real (correcciones)

Esta sección **sustituye** las conclusiones de §8 y §8.ter. Se obtuvo decompilando la app que
**de verdad dibuja**, `com.hxy.miniscreen` — que es lo que faltaba: hasta aquí sólo se había
analizado `HxyService`, que es un intermediario.

### 8.quater.1 Los tres errores del análisis previo

| Error | Por qué estaba mal |
|---|---|
| **1. No se decompiló `com.hxy.miniscreen`** | Es la app que renderiza. Todo el mecanismo vive ahí. Fue la causa raíz. |
| **2. Indicador equivocado** | Se usó "capa `MiniScreenWin` visible en SurfaceFlinger" como señal de panel encendido. **Es una señal inválida:** el panel **no se compone por SurfaceFlinger**. La vista se rasteriza a un `Bitmap` y se empuja por JNI. La capa figura `invisible` incluso con el panel encendido y pintando. |
| **3. Palanca equivocada** | Se escribía `Settings.Global.back_screen_on` directamente. El disparador real es un **broadcast**. |

### 8.quater.2 Cómo se pinta el panel: `libBackScreen2.so`

`TinylcdFunction` mantiene un bucle de refresco a 30 fps (8 fps en ahorro de energía):

```java
// com.hxy.miniscreen.tinylcd.TinylcdFunction
Bitmap tempBitmap = BitmapUtil.loadBitmapFromView(mShowView, MINISCREEN_WIDTH, MINISCREEN_HEIGHT);
mShowBitmap = PhotoUtils.Rgb565Bitmap(tempBitmap);            // <- RGB565
BackScreenNative.native_refreshScreen(mShowBitmap, BackScreenNative.SCREENCONTENT_MODULE_LENGTH);
```

```java
// com.freeme.backscreen.BackScreenNative
public static native boolean native_isScreenOn();
public static native int native_refreshScreen(Bitmap bitmap, int length);
public static native int native_screenOff();
public static native int native_screenOn();
public static native int native_setBrightnessScreen(int level);
static { System.loadLibrary("BackScreen2"); }
```

Confirmado en vivo por `logcat`:
```
BackScreenNative: refreshScreen, start
BackScreenNative: refreshScreen--- end, ret = 259200
```

**`259200 = 360 × 360 × 2`** → framebuffer **RGB565 de 360×360**. Esto valida la geometría
deducida en §7 a partir de la ventana overlay, y da el formato exacto.

La librería está en `/system/lib64/libBackScreen2.so` (`system_lib_file`, `-rw-r--r--`).

### 8.quater.3 ✅ ENCENDER/APAGAR sin root — broadcast exportado

`TinylcdFunction.initReceiver()` registra un receiver **explícitamente exportado**
(`registerReceiverAsUser(..., UserHandle.ALL, filter, null, null, 2)` — el `2` es
`RECEIVER_EXPORTED`), con dos acciones **no protegidas**:

```java
private static final String GC_SCREEN_ON  = "com.yft.miniscreen.action.GC_SCREEN_ON";
private static final String GC_SCREEN_OFF = "com.yft.miniscreen.action.GC_SCREEN_OFF";
// GC_SCREEN_ON  -> mUi.sendRequest(106)  -> wakelock + startFlush + turnScreenOn
// GC_SCREEN_OFF -> mUi.sendRequest(107)  -> turnScreenOff
```

Probado:
```
$ adb shell am broadcast -a com.yft.miniscreen.action.GC_SCREEN_ON
Broadcasting: Intent { act=com.yft.miniscreen.action.GC_SCREEN_ON flg=0x400000 }
Broadcast completed: result=0
```
```
back_screen_on: 0 -> 1
Frames al panel (259200 B RGB565): SI, fluyendo
```

A diferencia de `com.hxy.aod_service_refresh` (§8), **este broadcast no está protegido** y
cualquier app o `adb shell` lo puede enviar. **Esta es la palanca de encendido.**

### 8.quater.4 ✅ TEXTO PROPIO en el panel — vía notificaciones

`back_screen_on` **sí está observado** — pero con condiciones. `OnWithDoubleClick` registra un
`ContentObserver` sobre `Settings.Global.getUriFor("back_screen_on")` (clase `HxyGlobalSetting`)
y sólo actúa si el gesto de doble toque está activo. Por eso escribir la clave a pelo no bastaba.

La vía que **sí** permite contenido propio es la de notificaciones:

```java
// com.hxy.miniscreen.monitor.notification.NotificationMonitor
mListener.registerAsSystemService(mContext, mComponentName, -1);   // API oculta, sólo apps de sistema
...
private boolean isShowNotiApp(String pkgName) {
    String notiApps = Settings.Global.getStringForUser(cr, "subscreen_selected_noti_apps", -2);
    if (TextUtils.isEmpty(notiApps)) notiApps = "com.android.incall:com.kugou.android:...";
    return notiApps.contains(pkgName);
}
// muestra EXTRA_TITLE y EXTRA_TEXT de la notificación
```
```java
// com.hxy.miniscreen.tinylcd.OnWithNotification
mIsOn = addSettingListener("subscreen_noti_screenon", getInt(R.integer.default_notification_on)) == 1;
// onMessage(102): if (mIsOn && arg1 == 1) mUi.sendRequest(106);   // enciende el panel
```

**Las dos claves que lo bloqueaban**, ambas en `Settings.Global` y escribibles por ADB:

| Clave | Valor de fábrica | Necesario |
|---|---|---|
| `subscreen_noti_screenon` | `null` → default del recurso **`0`** | `1` |
| `subscreen_selected_noti_apps` | `null` → lista por defecto sin tu app | añadir el paquete (separador `:`) |

Con eso, una app **normal** (no de sistema) que postee una notificación hace que el panel
**se encienda solo** y muestre su título y texto. Verificado: panel apagado → la app postea →
`back_screen_on: 0 → 1`, frames fluyendo, y el texto visible en el panel.

### 8.quater.5 ❌ Dibujo libre de píxeles — sigue necesitando root

Se intentó cargar la librería del vendor desde una app normal, declarando la clase puente con el
nombre exacto que exigen los símbolos JNI (`com.freeme.backscreen.BackScreenNative`):

```
E BackScreenNative: loadLibrary fallo: java.lang.UnsatisfiedLinkError:
  dlopen failed: library "/system/lib64/libBackScreen2.so" needed or dlopened by
  "/apex/com.android.art/lib64/libnativeloader.so" is not accessible for the namespace "clns-7"
```

**No es SELinux:** es el aislamiento de *namespaces* del linker de Android. La librería no figura
en `public.libraries.txt` (comprobado: no aparece en `/system/etc/` ni `/vendor/etc/`), así que
sólo las apps de `/system/app` — que corren en el namespace del sistema — pueden cargarla.
Confirmado que tampoco hay conflicto de shadowing aquí: `BackScreenNative` vive sólo en el dex
de la app del vendor, no en `framework.jar`.

### 8.quater.6 Resumen de lo que se puede y no se puede, sin root

| Objetivo | ¿Sin root? | Cómo |
|---|---|---|
| Encender / apagar el panel | ✅ | broadcast `GC_SCREEN_ON` / `GC_SCREEN_OFF` |
| Mostrar **texto propio** | ✅ | notificación + `subscreen_noti_screenon=1` + paquete en `subscreen_selected_noti_apps` |
| Cambiar la carátula del reloj | ✅ | `Settings.Global.subscreen_current_clock` |
| Saber si está encendido | ✅ | contar `ret = 259200` en logcat de `BackScreenNative` |
| Hablar con `HxyService` por AIDL | ✅ | §8.ter (funciona, pero no enciende el panel) |
| **Dibujar píxeles arbitrarios** | ❌ | requiere app de sistema → firmware/root (§9) |

### 8.quater.7 Herramienta

`subscreen.py` quedó reescrito sobre el mecanismo correcto:

```
python subscreen.py setup              # habilita la vía de notificaciones (una vez)
python subscreen.py on | off | toggle  # broadcast real
python subscreen.py status             # cuenta frames, indicador válido
python subscreen.py text "Hola mundo"  # texto propio en el panel
python subscreen.py clock N
python subscreen.py dump
```

La app cliente (`C:\Users\<USUARIO>\subscreen-app`) acepta pruebas por Intent sin tocar la
pantalla, y necesita `android:launchMode="singleTop"` para que `am start` entregue intents
repetidos a una instancia ya viva:

```
adb shell am start -n com.example.subscreenpoke/.MainActivity --es test noti --es msg "Hola"
adb shell am start -n com.example.subscreenpoke/.MainActivity --es test state   # prueba la nativa
```

---

## 9. La vía OEM: desbloquear el bootloader

El teléfono es propiedad del usuario, así que ésta es una opción legítima. Estado actual:

```
adb shell getprop <propiedad>
adb shell getenforce
```

| Propiedad | Valor | Significa |
|---|---|---|
| `ro.boot.flash.locked` | `1` | bootloader **bloqueado** |
| `ro.boot.verifiedbootstate` | `green` | arranque verificado intacto |
| `ro.boot.veritymode` | `enforcing` | dm-verity activo |
| `ro.boot.vbmeta.device_state` | `locked` | vbmeta bloqueado |
| `ro.secure` / `ro.debuggable` | `1` / `0` | build de producción |
| `sys.oem_unlock_allowed` | `0` | **el switch "Desbloqueo de OEM" está APAGADO** en Opciones de desarrollador |
| `ro.oem_unlock_supported` | *(vacío)* | el OEM no lo anuncia; no concluyente |
| `getenforce` | `Enforcing` | SELinux activo |

Pero la infraestructura necesaria **sí está presente**:

```
adb shell service list | grep -i 'oem_lock\|persistent_data_block'
adb shell ls /dev/block/by-name/ | grep -iE 'frp|seccfg'
```
```
177  oem_lock:              [android.service.oemlock.IOemLockService]
190  persistent_data_block: [android.service.persistentdata.IPersistentDataBlockService]
frp
seccfg
```

`oem_lock` + `persistent_data_block` + las particiones `frp` y `seccfg` (la de MediaTek que
guarda el estado de bloqueo) indican que **el camino existe**. `fastboot.exe` ya está instalado
junto a `adb`.

### Ruta y qué gana cada paso

| Paso | Acción | Qué habilita |
|---|---|---|
| 1 | Activar "Desbloqueo de OEM" en Opciones de desarrollador | pone `sys.oem_unlock_allowed=1` |
| 2 | `fastboot flashing unlock` | **borra todos los datos**; `verifiedbootstate` pasa a `orange` |
| 3 | Parchear `boot.img` con Magisk y flashearlo | root |
| 4 | SELinux permissive o política propia | acceso real a `/dev/tiny_lcd` |
| 5 | Descubrir el formato del framebuffer | dibujar contenido propio |

### Lo que hay que tener claro

- **Root te da el nodo, no el protocolo.** Ninguno de los dos APK escribe `/dev/tiny_lcd` (§5.4):
  la escritura vive en código nativo del vendor (HWC) o en el driver. Con root abres el nodo,
  pero sigues sin saber qué bytes mandar.
- **El paso 5 es empírico y muy viable.** El buffer de `MiniScreenWin` es `360x360 RGBA_8888`
  (548 KiB con padding; 360×360×4 = 518 400 B). Con root se prueba a escribir patrones de
  360×360 en RGB565 (259 200 B) y en RGBA8888 (518 400 B) y se mira qué aparece. Es tanteo, pero
  el espacio de búsqueda es pequeño.
- **Costos reales:** borrado total de datos; Play Integrity roto (banca, Google Wallet, algunos
  juegos); pantalla de advertencia en cada arranque; se pierde OTA.
- **Reversible:** `fastboot flashing lock` vuelve al estado original, y borra los datos otra vez.
- **Riesgo de brick bajo:** al ser MediaTek (mt6855) existe rescate por modo BROM con SP Flash
  Tool o `mtkclient`, que es una red de seguridad que los Qualcomm no dan. Conviene tener el
  firmware de stock **antes** de empezar.
- **Incógnita:** Cubot a veces envía el bootloader sin permitir el desbloqueo pese a tener la
  infraestructura. Sólo se sabe intentando el paso 2.

---

## 10. Siguiente paso recomendado

Descartado el nivel `shell` (§8), quedan dos frentes, y conviene hacerlos en este orden:

1. **Sin root — descompilar `apks/HxyService.apk`** (p. ej. con `jadx`) y buscar el `onBind` de
   `com.android.hxyservice.HxyService` para recuperar su interfaz AIDL/Messenger, más el
   manejador de `KEYCODE_MINISCREEN_ON`. El servicio está exportado y sin permiso, así que si la
   interfaz permite "enciende el panel" o "muestra este texto", se puede llamar desde una app
   normal. Es gratis en términos de riesgo y puede resolverlo todo.
2. **Con root — desbloquear OEM (§9)** si el punto 1 no da fruto. Con root el objetivo inmediato
   no es dibujar, sino **capturar el protocolo**: tracear qué escribe el HWC del vendor en
   `/dev/tiny_lcd` mientras el panel está encendido (por ejemplo con `strace` sobre el proceso
   que lo abre, o leyendo el buffer). Una vez conocido el formato, escribir un framebuffer propio
   de 360×360 es trivial desde Python.

**Observación pendiente que no requiere nada:** encender el panel a mano (botón lateral o doble
toque) y ejecutar `python subscreen.py dump`. Eso captura `dumpsys` con la capa `MiniScreenWin`
**visible** y permite comparar contra la línea base ya guardada con el panel apagado. Es la
comparación más informativa que queda disponible sin root.

---

## Artefactos generados (en `C:\Users\<USUARIO>\diag-kingkong11`)

| Fichero | Contenido |
|---|---|
| `apks/HxyMiniScreen.apk` | APK extraído de `/system/app/HxyMiniScreen/` |
| `apks/HxyService.apk` | APK extraído de `/system/app/HxyService/` |
| `raw_display.txt` | `dumpsys display` completo (74 416 B) |
| `raw_sf.txt` | `dumpsys SurfaceFlinger` completo (62 435 B) |
| `raw_packages.txt` | `pm list packages -f` (260 líneas) |
| `raw_services.txt` | `service list` (284 líneas) |
| `raw_input.txt` | `dumpsys input` completo (dispositivos táctiles y botón) |
| `manifest_tree.txt` | `aapt2 dump xmltree` de HxyMiniScreen |
| `manifest_hxyservice.txt` | `aapt2 dump xmltree` de HxyService |
| `badging.txt` | `aapt2 dump badging` de HxyMiniScreen |
| `dex/`, `dex_svc/` | `classes*.dex` extraídos para búsqueda de cadenas |
| `subscreen.py` | Control del panel por ADB: `status`, `on`, `off`, `toggle`, `clock N`, `dump` |
| `dump-<fecha>-back_screen_on_<N>/` | Capturas de `dumpsys` etiquetadas con el estado del panel |

## Herramientas instaladas en la PC

| Herramienta | Versión | Ruta |
|---|---|---|
| Android Platform-Tools (`adb`) | 1.0.41 / 37.0.1 | `C:\Users\<USUARIO>\adb-tools\platform-tools` (agregada al PATH de usuario) |
| Android Build-Tools (`aapt`, `aapt2`) | r34 | `C:\Users\<USUARIO>\adb-tools\build-tools\android-14` |
