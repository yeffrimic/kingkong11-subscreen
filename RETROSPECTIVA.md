# Retrospectiva: qué funcionó, qué no, y qué haría distinto

Notas de método sobre la ingeniería inversa de la pantalla trasera del Cubot KINGKONG 11.
Escrito para quien quiera repetir este tipo de investigación en otro dispositivo — el valor
está tanto en los aciertos como en los errores, que costaron varias horas y llevaron a
conclusiones publicadas que luego resultaron falsas.

Los resultados técnicos están en [DIAGNOSTICO.md](DIAGNOSTICO.md). Aquí va el proceso.

---

## ✅ Lo que sí funcionó

### 1. Enumeración de solo lectura, sistemática, antes de tocar nada
`getprop` → `dumpsys display` → `dumpsys SurfaceFlinger` → `pm list packages` → `service list`
→ `/dev` → `/sys/class` → `dumpsys input`. Esto dio la respuesta estructural correcta a la
primera: la pantalla **no** es un Display lógico de Android. `Logical Displays: size=1` cerró
el caso sin ambigüedad, y esa conclusión sobrevivió intacta a todo lo demás.

**Generalizable:** separar "¿qué es esto arquitectónicamente?" de "¿cómo lo controlo?". La
primera pregunta se responde con enumeración pasiva y es barata. La segunda necesita el código.

### 2. `grep` sobre los `.dex` antes de decompilar
Buscar cadenas en los dex crudos (`grep -aoh 'patrón' classes*.dex`) localizó nombres de clases,
claves de `Settings` y rutas en segundos, sin esperar una decompilación de 4000 clases. Sirvió
para decidir **qué** decompilar.

### 3. Sacar el AIDL del `framework.jar` real en vez de adivinarlo
Los `transaction codes` de un binder dependen del orden de declaración en el `.aidl`. En vez de
suponerlo, se extrajo `/system/framework/framework.jar` del teléfono y se decompiló el `Stub`
verdadero:

```java
static final int TRANSACTION_handleIntent = 1;
static final int TRANSACTION_updateServiceInfo = 2;
static final int TRANSACTION_getServiceInfo = 3;
static final int TRANSACTION_handleKeycode = 4;
```

Mi suposición previa tenía el orden 2 y 3 invertidos. Adivinar habría producido un fallo
silencioso y confuso (llamar a `getServiceInfo` creyendo llamar a `updateServiceInfo`).

### 4. `IBinder.transact()` crudo para esquivar dos obstáculos a la vez
Reusar la clase `com.mediatek.hxymanager.IHxyService` falló porque vive en el *boot classpath*,
gana sobre la copia de la app, y sus métodos son *hidden API* bloqueada. La solución fue no usar
la clase en absoluto: escribir el descriptor como literal y hablar el protocolo a mano.

```java
data.writeInterfaceToken("com.mediatek.hxymanager.IHxyService");
data.writeInt(keyCode); data.writeInt(action);
binder.transact(4, data, reply, 0);
```
`IBinder.transact` es API pública. Esta es **la** técnica para servicios ocultos.

### 5. Compilar el APK sin Gradle ni Android Studio
`javac → d8 → aapt2 link → jar → zipalign → apksigner`, con un JDK portable y un `android.jar`.
Total ~450 MB de descargas y un script de 40 líneas, contra varios GB de Android Studio. Para una
app de prueba de 3 clases fue claramente la decisión correcta y el ciclo de iteración fue de
segundos.

### 6. Pruebas por Intent en vez de automatizar toques
Pelear con `input tap` + `uiautomator dump` fue una pérdida de tiempo: la pantalla se bloqueaba,
la sombra de notificaciones robaba el foco, las coordenadas cambiaban. Añadir un
`getStringExtra("test")` a la Activity y disparar con

```
adb shell am start -n <pkg>/.MainActivity --es test noti --es msg "Hola"
```

hizo las pruebas deterministas y sin depender del estado de la pantalla. **Instrumentar la app
para que sea manejable por ADB es mucho más barato que automatizar su UI.**

### 7. `logcat` como fuente de verdad
La pista decisiva de todo el proyecto fue una línea de log:

```
BackScreenNative: refreshScreen--- end, ret = 259200
```

`259200 = 360 × 360 × 2` → framebuffer RGB565. Una sola línea dio el formato exacto, la
geometría, y la confirmación de que el panel estaba activo. Ningún `dumpsys` lo habría dicho.

### 8. Pedir confirmación visual al usuario
El estado físico del panel era el único dato que no se podía obtener por software. Preguntarlo
explícitamente fue lo que finalmente rompió el bucle de conclusiones erróneas.

---

## ❌ Lo que no funcionó

### 1. Decompilar solo la mitad del sistema — el error raíz
Se decompiló `com.android.hxyservice` y se construyó toda una teoría sobre él. Resultó ser un
intermediario de 45 líneas que no dibuja nada. La app que **realmente** renderiza,
`com.hxy.miniscreen`, no se abrió hasta muy tarde. Todo el mecanismo estaba ahí: el bucle de
refresco, la librería nativa, y el receiver exportado que enciende el panel.

**Coste:** varias horas y tres conclusiones publicadas como definitivas que eran falsas.

### 2. Un indicador inválido, usado como si fuera un hecho
Se tomó "la capa `MiniScreenWin` figura `invisible` en `dumpsys SurfaceFlinger`" como prueba de
que el panel estaba apagado. **Era una señal sin relación con la realidad:** el panel no se
compone por SurfaceFlinger. La vista se rasteriza a un `Bitmap` y se empuja por JNI, así que la
capa aparece `invisible` incluso con el panel encendido y pintando a 30 fps.

Este error contaminó todas las pruebas posteriores. Varias veces se reportó "no se encendió"
cuando probablemente sí se había encendido.

### 3. Confundir correlación con causalidad
`back_screen_on` coincidía perfectamente con el estado del panel, así que se asumió que era la
palanca. Era un reflejo del estado. Peor: cuando escribirlo no produjo el efecto esperado, en vez
de dudar del **indicador** se concluyó que la clave "no era un actuador" — una conclusión
construida sobre la medición defectuosa.

### 4. Decir "definitivo" con dos fuentes que compartían el mismo punto ciego
Se escribió *"confirmado por dos vías independientes: decompilado + prueba en vivo"*. No eran
independientes: ambas dependían del mismo indicador roto y de haber leído solo la mitad del
código. **Dos observaciones que comparten una suposición no se corroboran entre sí.**

### 5. Analizar solo los manifests
Se enumeraron cuidadosamente los componentes declarados en `AndroidManifest.xml` y se concluyó
qué era alcanzable desde fuera. Pero el receiver que enciende el panel **no está en el manifest**:
se registra en tiempo de ejecución.

```java
mContext.registerReceiverAsUser(receiver, UserHandle.ALL, filter, null, null, 2);  // 2 = RECEIVER_EXPORTED
```

Es la superficie de ataque más importante del sistema y el análisis estático de manifests la
pasó por alto por completo.

### 6. No ejecutar el experimento de control que yo mismo propuse
Dos veces se sugirió "encender el panel a mano con el botón y capturar los `dumpsys` para
comparar". Nunca se hizo. **Ese experimento habría revelado el error del indicador de inmediato:**
con el panel visiblemente encendido, `MiniScreenWin` habría seguido `invisible`, y la
contradicción habría sido imposible de ignorar.

### 7. Ignorar los valores por defecto de los recursos
La vía de notificaciones estaba bloqueada por `subscreen_noti_screenon`, cuyo valor era `null` y
por tanto tomaba el default del recurso: **`0`**. Eso estuvo visible en
`aapt2 dump resources` desde el principio. No se miró hasta el final.

### 8. Fricciones técnicas que costaron tiempo
- **`d8` 8.2.2 falla** al dexar ciertas clases anónimas de `javac` moderno
  (`NullPointerException: String.length()`). Se resolvió reescribiendo sin clases anónimas.
- **`launchMode` por defecto**: `am start` sobre una instancia viva no entrega el intent.
  Necesita `singleTop`. Provocó un falso negativo desconcertante.
- **Git Bash mangla rutas Unix** al pasarlas a `adb.exe`. Necesita `MSYS_NO_PATHCONV=1`.
- **`apksigner.bat` exige `JAVA_HOME`**, no basta con tener `java` en el `PATH`.
- No se registró el valor original de `screen_off_timeout` antes de cambiarlo.

---

## 🔧 Qué haría distinto

### 1. Establecer un oráculo de verdad *antes* de concluir nada
Antes de cualquier hipótesis, encontrar una señal que diga inequívocamente "el hardware está
haciendo lo que quiero". Aquí era `logcat -s BackScreenNative`. Se encontró al final; debió ser
el primer paso. **Sin un oráculo válido, cada prueba posterior es ruido.**

### 2. Validar el oráculo con un control conocido
Si hay una forma que **ya se sabe que funciona** (el botón físico), usarla primero para ver qué
aspecto tiene el estado "encendido". Diferenciar contra el estado "apagado". Eso calibra el
instrumento antes de medir con él. Es el paso que se omitió y habría ahorrado todo.

### 3. Decompilar todo el conjunto relevante antes de teorizar
Ante varios paquetes de vendor relacionados, decompilarlos todos primero. `jadx` tardó 40
segundos en la app grande. Teorizar sobre la mitad del código costó horas.

### 4. Buscar receivers registrados en runtime, no solo los del manifest
Complementar el análisis de manifests con un grep sobre el código decompilado:

```
grep -rnE 'registerReceiver(AsUser)?\(' .
grep -rhoE '"[a-z0-9.]+\.action\.[A-Z_]+"' .
```

Y comprobar en cada uno si pasa `RECEIVER_EXPORTED`. Los manifests solo cuentan la mitad.

### 5. Volcar los recursos y revisar los defaults al principio
`aapt2 dump resources` sobre la app del vendor, y revisar todos los `bool/config_*` e
`integer/default_*`. Ahí están las puertas que el OEM deja cerradas de fábrica. Son gratis de
leer y explican muchos "no funciona".

### 6. Instrumentar la app de prueba desde el minuto uno
Intent extras + `Log.i` a logcat, nunca automatización de UI. Y en un dispositivo real:
`svc power stayon true` y subir `screen_off_timeout` **antes** de empezar, tras anotar los
valores originales.

### 7. Ser más cuidadoso con el lenguaje de certeza
Reservar "definitivo" para cuando las fuentes de evidencia no comparten suposiciones. Cuando una
predicción falla, la primera sospecha debe recaer sobre el instrumento de medida, no sobre el
modelo del mundo. Aquí la frase *"no se puede sin root"* se afirmó tres veces y era falsa.

### 8. Anotar el estado original antes de escribir
Un `settings list global > antes.txt` al empezar cuesta un segundo y hace la limpieza trivial y
exacta. Sin eso, restaurar se convierte en adivinar.

---

## Resumen en una línea

El análisis estructural salió bien porque fue pasivo y sistemático. El análisis de control salió
mal porque se concluyó con la mitad del código leído y un instrumento de medida sin calibrar —
y lo segundo fue peor que lo primero, porque hizo que la evidencia contradictoria pareciera
confirmación.
