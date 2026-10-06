# MARAN release rules.
# Retrofit/Gson DTO field names must remain stable because the backend contract uses
# their Kotlin property names directly.
-keepattributes Signature,*Annotation*
-keep class ai.maran.app.data.** { *; }
-keep class com.google.gson.** { *; }

# Keep Retrofit service method annotations and interface metadata.
-keep interface ai.maran.app.data.MaranApi
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
