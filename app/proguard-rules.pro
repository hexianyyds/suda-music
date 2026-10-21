# ==================== 基础 ====================
# 保留注解、签名与内部类信息（kotlinx.serialization / 反射依赖）
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault, EnclosingMethod, SourceFile, LineNumberTable

# ==================== kotlinx.serialization ====================
# 只保留我们自己的模型类：
#   - 模型字段名不能被混淆（否则持久化的队列/收藏/历史读不出来）
#   - Companion 里的 serializer() 是反序列化入口
# 注意：不要使用 <1>$<3> 这类通配规则 —— 它会要求 R8 扫描每一个
# Serializable 类（含 kotlinx.serialization 自身），成倍拖慢混淆。
-keep,includedescriptorclasses class com.sodamusic.app.data.model.** { *; }
-keepclassmembers class com.sodamusic.app.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.sodamusic.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

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
