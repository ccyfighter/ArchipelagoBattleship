# Debug/Release builds both keep minify disabled in this project.
# These rules are kept for the day R8 is enabled.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclasseswithmembers class com.archipelago.battleship.** { kotlinx.serialization.KSerializer serializer(...); }
