-keep class dev.brahmkshatriya.echo.extension.** { *; }
-keep class dev.brahmkshatriya.echo.common.** { *; }
-keep @interface * { *; }
-keepnames class dev.brahmkshatriya.echo.common.** { *; }
-keep interface dev.brahmkshatriya.echo.common.clients.TrackerClient
-keep interface dev.brahmkshatriya.echo.common.clients.LoginClient
-keep interface dev.brahmkshatriya.echo.common.clients.ExtensionClient
-keep class dev.brahmkshatriya.echo.common.models.** { *; }
-keepclassmembers class dev.brahmkshatriya.echo.common.** {
    @dev.brahmkshatriya.echo.common.* <fields>;
}
-keep interface dev.brahmkshatriya.echo.common.clients.TrackerClient {
    <methods>;
}
-keep class dev.brahmkshatriya.echo.common.models.EchoMediaItem {
    <methods>;
}
-keep class dev.brahmkshatriya.echo.common.models.Track {
    <methods>;
}
-keepnames class kotlin.coroutines.Continuation { *; }

-keep class kotlin.jvm.functions.Function* { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.brahmkshatriya.echo.extension.** { *; }
-keep,includedescriptorclasses class dev.brahmkshatriya.echo.extension.Message.** { *; }
-keepclassmembers class your.package.name.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlin.reflect.KClass { *; }

-keepattributes *Annotation*, InnerClasses
-keep class kotlin.Unit { *; }
-keepclassmembers class kotlin.Unit { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }
-dontnote kotlinx.serialization.AnnotationsKt
# okhttp
-dontwarn com.oracle.svm.core.annotate.Delete
-dontwarn com.oracle.svm.core.annotate.Substitute
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn java.lang.Module
-dontwarn org.graalvm.nativeimage.hosted.Feature$BeforeAnalysisAccess
-dontwarn org.graalvm.nativeimage.hosted.Feature
-dontwarn org.graalvm.nativeimage.hosted.RuntimeResourceAccess
-keep class okhttp3.** { *; }

-dontwarn org.jspecify.annotations.NullMarked

# Add these rules to your proguard-rules.pro file

# Keep Kotlin Coroutines
-keepclassmembernames class kotlin.coroutines.** {
    *;
}
-keepclassmembernames class kotlin.coroutines.CoroutineContext { *; }
-keepclassmembernames class kotlin.coroutines.CoroutineContext$* { *; }
-keepclassmembernames class kotlin.coroutines.EmptyCoroutineContext { *; }
-keepclassmembernames class kotlin.coroutines.CombinedContext { *; }

# Keep Kotlin Coroutine Core
-keep class kotlinx.coroutines.** { *; }
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.Continuation {
    *;
}

# Keep specific coroutine interfaces and their implementations
-keep interface kotlin.coroutines.CoroutineContext { *; }
-keep interface kotlin.coroutines.CoroutineContext$Element { *; }
-keep interface kotlin.coroutines.CoroutineContext$Key { *; }
-keep class * implements kotlin.coroutines.CoroutineContext { *; }
-keep class * implements kotlin.coroutines.CoroutineContext$Element { *; }

# Keep OkHttp WebSocket related classes
-keepclassmembers class okhttp3.internal.ws.** { *; }
-keepclassmembers class okhttp3.WebSocket { *; }
-keepclassmembers class okhttp3.WebSocketListener { *; }

# Keep RemoteControl class and its coroutine usage
-keep class dev.brahmkshatriya.echo.extension.RemoteControl { *; }
-keepclassmembers class dev.brahmkshatriya.echo.extension.RemoteControl {
    void handleMessage(...);
}

# Keep CoroutineScope and its methods
-keepclassmembernames interface kotlinx.coroutines.CoroutineScope {
    kotlinx.coroutines.CoroutineContextKt *;
}

# Keep suspension functions
-keepclasseswithmembers class * {
    @kotlin.coroutines.jvm.* <methods>;
}

# Keep R8/ProGuard from stripping interface information
-keep interface * extends kotlin.coroutines.Continuation
-keep class * implements kotlin.coroutines.Continuation { *; }

# Additional Kotlin metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes SourceFile,LineNumberTable

# If you're using Kotlin reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# Keep Function types (especially important for coroutines)
-keep class kotlin.jvm.functions.Function0 { *; }
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class kotlin.jvm.functions.Function2 { *; }