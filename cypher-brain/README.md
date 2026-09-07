# CYPHER BRAIN — Android MVP

Cypher Brain es un copiloto de entrenamiento para freestyle. Está diseñado para un solo Android y no necesita un bot de Discord.

## Qué hace esta versión

- Burbuja flotante sobre otras apps.
- Botón **ESCUCHAR RIVAL** para activar reconocimiento de voz.
- Botón **MI TURNO** para cancelar inmediatamente el reconocimiento antes de que el usuario rapee.
- Procesamiento de resultados parciales para reducir latencia percibida.
- Motor local de asociaciones/puentes.
- 25 familias semánticas x 22 conceptos = **11,550 relaciones dirigidas potenciales**.
- Motor fonético con packs de 10 rimas compatibles.
- Muestra: frase detectada, concepto foco, patrón vocálico, 10 multis, asociaciones y cadenas de puentes.
- No escribe ni guarda archivos de audio.

## Arquitectura

`SpeechRecognizer -> concepto foco -> RhymeEngine + BridgeRepository -> overlay`

Los puentes y las familias de rimas viven en `app/src/main/assets/`, por lo que la consulta semántica y fonética es local. El servicio de reconocimiento usado por Android puede requerir conexión dependiendo del motor instalado en el dispositivo.

## Privacidad y uso

La app debe usarse en prácticas donde las personas participantes sepan que el audio será procesado. La app no intenta identificar hablantes ni grabar conversaciones. El usuario controla manualmente cuándo el reconocimiento está activo.

## Limitación Android importante

Android puede impedir que Discord y otra app capturen simultáneamente el mismo micrófono. El modo previsto para el MVP es reproducir al otro participante por altavoz durante `ESCUCHAR RIVAL` y cambiar a `MI TURNO` antes de hablar.

## Build local

Requisitos: JDK 17, Android SDK 35 y Gradle 8.10.2.

```bash
cd cypher-brain
gradle assembleDebug
```

APK esperado:

`app/build/outputs/apk/debug/app-debug.apk`

## Build en GitHub

El workflow está en `.github/workflows/cypher-brain-android.yml`. Cada push a la rama `cypher-brain-mvp` intenta generar el artifact `cypher-brain-debug-apk`.

## Próximas mejoras recomendadas

1. Sustituir `SpeechRecognizer` por un ASR streaming dedicado para menor latencia y mayor consistencia.
2. Añadir ranking de complejidad/nicho a cada puente.
3. Añadir más familias fonéticas y rimas multisilábicas de 2–5 palabras.
4. Cruzar familias semánticas compartiendo nodos para rutas multiuniverso.
5. Añadir historial efímero de las últimas 3 frases sin conservar audio.
