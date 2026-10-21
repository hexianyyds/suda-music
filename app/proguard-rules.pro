# ==================== 基础 ====================
# 保留注解、签名与内部类信息（kotlinx.serialization / 反射依赖）
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault, EnclosingMethod, SourceFile, LineNumberTable

# ==================== kotlinx.serialization ====================
# 生成的 serializer 是通过 Companion 反射拿到的，必须保留
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}

-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}

# 业务模型（队列 / 收藏 / 搜索历史 / 播放列表都靠它序列化）
-keepclassmembers class com.sodamusic.app.data.model.** { *** Companion; }
-keepclasseswithmembers class com.sodamusic.app.data.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.sodamusic.app.data.model.** { *; }

# ==================== Media3 / ExoPlayer ====================
# Media3 自带 consumer rules，这里只屏蔽可选依赖的告警
-dontwarn androidx.media3.**
-dontwarn androidx.media3.ui.**

# ==================== OkHttp / Okio ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ==================== 协程 ====================
-dontwarn kotlinx.coroutines.**

# ==================== Compose / Haze / Coil ====================
-dontwarn androidx.compose.**
-dontwarn dev.chrisbanes.haze.**
-dontwarn coil.**
