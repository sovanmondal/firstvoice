# FirstVoice ProGuard Rules
# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.firstvoice.app.**$$serializer { *; }
-keepclassmembers class com.firstvoice.app.** { *** Companion; }
-keepclasseswithmembers class com.firstvoice.app.** { kotlinx.serialization.KSerializer serializer(...); }
