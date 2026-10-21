# ======================================================================
# 苏打音乐 · R8 混淆规则
#
# 策略：只对业务代码（com.sodamusic.app）做混淆与优化，
#       第三方库整体保留 —— R8 无需逐类分析它们，混淆速度提升一个量级。
# ======================================================================

# ---------- 基础：保留注解 / 签名 / 内部类信息 ----------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations, AnnotationDefault, EnclosingMethod, SourceFile, LineNumberTable

# ======================================================================
# 一、第三方库整体保留（不重命名、不裁剪、不优化）
# ======================================================================
-keep class androidx.** { *; }
-keep class androidx.compose.** { *; }
-keep class com.google.android.** { *; }
-keep class com.google.common.** { *; }
-keep class com.google.guava.** { *; }
-keep class kotlinx.** { *; }
-keep class coil.** { *; }
-keep class dev.chrisbanes.haze.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.json.** { *; }
-keep class org.jetbrains.** { *; }

# ======================================================================
# 二、业务代码：正常混淆
#    com.sodamusic.app 下的界面 / 逻辑类会被重命名（防反编译）
# ======================================================================

# 数据模型必须保留：序列化的字段名一旦被混淆，
# 持久化的队列 / 收藏 / 播放历史就再也读不出来了
-keep,includedescriptorclasses class com.sodamusic.app.data.model.** { *; }
-keepclassmembers class com.sodamusic.app.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.sodamusic.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Manifest 里声明的组件（Activity / Service / Receiver / Provider）由系统反射实例化
-keep class com.sodamusic.app.MainActivity { *; }
-keep class com.sodamusic.app.SodaMusicApp { *; }
-keep class com.sodamusic.app.playback.PlaybackService { *; }
-keep class com.sodamusic.app.ui.lyric.DesktopLyricService { *; }

# ======================================================================
# 三、屏蔽可选依赖的告警
# ======================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.media3.**
-dontwarn androidx.media3.ui.**
-dontwarn dev.chrisbanes.haze.**
-dontwarn coil.**
