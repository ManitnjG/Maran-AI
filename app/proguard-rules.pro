# MARAN release rules.
# Retrofit/Gson DTO field names must remain stable because the backend contract uses
# their Kotlin property names directly.
-keepattributes Signature,*Annotation*
-keep class ai.maran.app.data.** { *; }
