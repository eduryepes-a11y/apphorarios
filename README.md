# Horarios 📅

App Android (Kotlin + Jetpack Compose + Material 3) para crear horarios semanales, activar uno y recibir avisos.

**📲 Descargar:** ve a [Releases](https://github.com/eduryepes-a11y/apphorarios/releases/latest) y descarga el archivo `Horarios-vX.Y.Z.apk`.
Una vez instalada, la app te avisa sola cuando hay versión nueva y se actualiza con un toque.

## Funciones
- Varios horarios (p. ej. «Semana normal», «Vacaciones»): crear, editar, duplicar, borrar y **activar** uno.
- Actividades con icono, color, días, hora de inicio/fin y notas.
- Avisos: X minutos antes y/o **al empezar** cada actividad. Pantalla 🔔 de diagnóstico con historial.
- Vista **Día** (línea de tiempo) y **Semana** (cuadrícula L–D).
- **Widget** con todas las actividades del día.
- **Marcar como hecha** (en la lista o desde la notificación) y **estadísticas**: % cumplido, racha y horas por actividad.
- **Copia de seguridad** (exportar/importar) y **compartir horarios** con otras personas como archivo.
- **Temas**: claro, oscuro o del sistema, y 8 colores para la app.
- **Idiomas**: español e inglés (se cambia dentro de la app).
- **Actualizaciones automáticas** desde GitHub Releases.

## Desarrollo
1. Android Studio → **File › Open** → carpeta del proyecto.
2. Espera al *Gradle Sync* y pulsa ▶ **Run**.

Cada push compila la app en GitHub Actions (pestaña **Actions**) para detectar errores.

## Publicar una versión

### Una sola vez: la llave de firma
Todas las versiones tienen que ir firmadas con **la misma llave**; si no, no se pueden instalar encima.

1. En Android Studio: **Build › Generate Signed App Bundle or APK… › APK › Create new…** y crea `horarios-key.jks`
   (apunta la contraseña y el alias). Guárdala en un sitio seguro: **si la pierdes no podrás publicar actualizaciones**.
2. Copia la llave en base64 (PowerShell):
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\ruta\horarios-key.jks")) | Set-Clipboard
   ```
3. En GitHub: **Settings › Secrets and variables › Actions › New repository secret** y crea estos 4:

   | Nombre | Valor |
   |---|---|
   | `KEYSTORE_BASE64` | lo que has copiado en el paso 2 |
   | `KEYSTORE_PASSWORD` | contraseña del keystore |
   | `KEY_ALIAS` | alias (p. ej. `horarios`) |
   | `KEY_PASSWORD` | contraseña de la key |

### Cada versión nueva
1. Sube tus cambios a `main`.
2. En GitHub: **Releases › Draft a new release**.
3. En *Choose a tag* escribe la versión nueva, p. ej. `v1.1.0` (siempre mayor que la anterior) → *Create new tag*.
4. Escribe las novedades en la descripción (la app las enseña) y pulsa **Publish release**.
5. En unos 5 minutos GitHub Actions compila el APK firmado y lo adjunta a la Release.
   Desde ese momento, todos los que tengan la app verán «🎉 Nueva versión» al abrirla.

## Estructura
- `data/` → Room (horarios y actividades) y repositorio.
- `alarm/` → AlarmManager, avisos, historial y notificaciones.
- `widget/` → widget con Jetpack Glance.
- `update/` → comprobación, descarga e instalación de actualizaciones.
- `ui/` → pantallas Compose (inicio, semana, horarios, editor, avisos, ajustes, estadísticas) y tema.
- `res/values` y `res/values-en` → textos en español e inglés.
