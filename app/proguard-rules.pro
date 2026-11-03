# R8 rules for the release build. Libraries ship their own consumer rules (Hilt, Ktor,
# kotlinx.serialization, Compose, Credentials); these cover what they leave to the app.

# Readable crash reports in Play Console: keep file names and line numbers (the mapping.txt
# inside the .aab restores the class and method names).
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,EnclosingMethod,Signature
-renamesourcefileattribute SourceFile

# The API bodies are decoded by name: kotlinx.serialization generated serializers must survive.
-keepclassmembers class com.atmosferast.orbita.data.remote.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.atmosferast.orbita.data.remote.**$$serializer { *; }

# Ktor's OkHttp engine logs through SLF4J, which Android does not ship.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
