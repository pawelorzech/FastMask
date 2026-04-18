# Generic signatures must be preserved globally — Retrofit + kotlinx.serialization
# both rely on reflection over generic types (Continuation<T>, KSerializer<T>).
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations

# ----- kotlinx.serialization -----
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable classes and their generated $$serializer
-keep,includedescriptorclasses class com.fastmask.**$$serializer { *; }
-keepclassmembers class com.fastmask.** {
    *** Companion;
}
-keepclasseswithmembers class com.fastmask.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Serializer/companion of every class annotated as @Serializable, anywhere
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** {
    *** Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$$serializer INSTANCE;
}

# ----- Retrofit + suspend -----
# Suspend functions encode the response type in Continuation<T>'s generic.
# R8 must keep the signatures and the bridge classes.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-dontwarn retrofit2.Platform$Java8

# Keep our own JMAP DTOs untouched — paranoid backup for serialization rules
-keep class com.fastmask.data.api.** { *; }
-keep class com.fastmask.domain.model.** { *; }

# ----- OkHttp -----
-dontwarn okhttp3.**
-dontwarn okio.**

# ----- Google Tink (security-crypto) -----
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.api.client.http.**
-dontwarn com.google.errorprone.annotations.InlineMe
-dontwarn org.joda.time.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
