# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Retrofit + Gson model classes are read reflectively.
-keepattributes Signature
-keepattributes *Annotation*

# Keep model classes used with Gson (data classes deserialised from the API).
-keep class com.mrhabibi.usholli.wear.data.** { *; }
