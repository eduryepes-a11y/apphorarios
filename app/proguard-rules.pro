# ── Reglas de ProGuard / R8 para Horaria ──────────────────────────────
# Activado para cumplir los requisitos de calidad de Google Play (feb 2027):
#   ≥ 25 % de ofuscación, optimización y reducción.

# ── Room ──────────────────────────────────────────────────────────────
# Room genera código en tiempo de compilación (KSP), pero las entidades
# y DAOs se referencian por reflexión en la base de datos.
-keep class com.eduardo.horarios.data.** { *; }

# ── Compose ───────────────────────────────────────────────────────────
# El compilador de Compose ya inyecta las anotaciones @Keep necesarias.
# Estas reglas extra previenen problemas con reflection en previews y
# restauración de estado.
-keepclassmembers class * implements androidx.compose.runtime.Composer { *; }

# ── Glance (widget) ──────────────────────────────────────────────────
-keep class com.eduardo.horarios.widget.** { *; }

# ── BroadcastReceivers (referenciados en AndroidManifest) ─────────────
-keep class com.eduardo.horarios.alarm.** { *; }

# ── Application class ────────────────────────────────────────────────
-keep class com.eduardo.horarios.HorariosApp { *; }

# ── Play Billing ──────────────────────────────────────────────────────
# La biblioteca de billing usa AIDL y reflexión internamente.
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ── Kotlin ────────────────────────────────────────────────────────────
# Evitar warnings de metadata de Kotlin
-dontwarn kotlin.**
-dontwarn kotlinx.**
-keep class kotlin.Metadata { *; }

# ── Enums ─────────────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ────────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Serializable ──────────────────────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}
