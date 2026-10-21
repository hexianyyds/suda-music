# 苏打音乐 · Soda Music

> 纯 Kotlin + Jetpack Compose 打造的 **网易云 / QQ 音乐双音源** 客户端，液态玻璃视觉，**App 端直连官方接口**，不依赖任何自建服务端代理。

[![Release](https://img.shields.io/badge/release-v1.0.0-ff3b5c)](https://github.com/hexianyyds/suda-music/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7f52ff)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.4-3ddc84)](https://developer.android.com/build)
[![API](https://img.shields.io/badge/minSdk-26%20|%20targetSdk-36-3ddc84)](https://developer.android.com)

**[⬇ 下载最新版本](https://github.com/hexianyyds/suda-music/releases)**  ·  [v1.0.0](https://github.com/hexianyyds/suda-music/releases/tag/v1.0.0)

---

## 为什么它快

这个 App 从头到尾只用 Kotlin 写，UI 全靠 Compose，**没有一行 XML 布局、没有一次反射调用**。这带来的是实打实的速度：

| 手段 | 原理 | 收益 |
|---|---|---|
| **Compose 编译期生成** | Compose 编译器把 `@Composable` 在编译期转成普通函数，跳过 XML 解析、`findViewById` 与 View 层级反射 | 首帧渲染路径极短，冷启动快 |
| **ART Baseline Profile（AOT2 预编译）** | 启动、四个主页、播放页等热点路径写进 `baseline.prof`，装进 APK，由系统做 AOT2 提前编译 | 运行时免 JIT，启动即命中机器码 |
| **R8 混淆 + 资源裁剪** | 死代码剔除、类内合并、未引用资源剔除 | 安装包仅 **12.24 MB** |
| **协程并发** | 取直链、拉封面、解歌词、抽取续播队列全部 `suspend` 并发执行 | 主线程永不阻塞，滑动/切歌零卡顿 |
| **Media3 / ExoPlayer** | 硬件解码 + 相邻曲目直链预取 | 切歌无缝，缓冲极快 |
| **DataStore + kotlinx.serialization** | 异步增量存储，零 SQL 开销 | 收藏/历史/队列读写无感 |

**冷启动之所以特别快**，是因为它同时命中了三件事：Compose 没有 XML 解析开销、Baseline Profile 让启动路径在设备上提前 AOT 过、R8 把 dex 压到最小。

### 📊 实测数据

release 正式包（已开启 R8 混淆 + 资源裁剪 + Baseline Profile），Android 模拟器 API 36 实测：

| 指标 | 实测值 | 说明 |
|---|---|---|
| **冷启动** | **220 ~ 239 ms**（4 次，均值 ≈ 227 ms） | `am start -W` 的 TotalTime，含进程创建到首帧 |
| **热启动** | **84 ~ 90 ms**（均值 ≈ 87 ms） | 进程存活时的重新拉起 |
| **卡顿帧率** | **0.43%**（466 帧中仅 2 帧） | 乐库竖滑切歌 4 张卡片后统计 |
| **帧耗时分位** | 50% / 90% / 95% / 99% = **13 / 18 / 20 / 34 ms** | 60 fps 的帧预算是 16.67 ms，99 分位以内基本满帧 |
| **内存 PSS** | 198 MB（含 85 MB 图形显存） | Dalvik 堆 20 MB，Native 堆 31 MB |
| **安装包体积** | **12.24 MB** | 混淆 + 资源裁剪后 |

作为参照，传统 View 体系的 Android 应用冷启动通常在 300 ~ 600 ms，而未经裁剪的 Compose 应用普遍在 500 ms 以上。

---

## 功能

### 🎵 双音源直连
- **网易云音乐**、**QQ 音乐**两大音源，App 端直连官方公开接口
- 支持 **B 站 / 酷我 / JOOX** 作为备选音源，可在「我的 → 设置 → 音乐源」随时切换
- 搜索、播放直链、歌词、封面、歌单、榜单、歌手、专辑全部打通

### 🔁 自动续播队列
进入乐库自动从两个音源各抽一半、凑够 **30 首**；**播到第 25 首**时再抽 30 首补进队列，保证一直有歌可听，不用手动续。

### 💎 液态玻璃视觉
基于 [Haze 2.0](https://github.com/chrisbanes/haze) 实现真实折射：Fresnel 边缘光照、镜面高光、背景色彩透出。悬浮扁椭圆导航胶囊、播放页控制板与顶栏、搜索框、我的页卡片全部玻璃化。

### 👤 我的
- **个人主页**：头像、背景图从本地相册选取并存储在本机，昵称可编辑
- **听歌总时长 / 今日时长 / 收藏数**统计
- **收藏的歌**、**历史播放**、**下载**三个列表
- **下载功能**：歌曲下载到本地离线可播，播放时自动优先使用本地文件
- **主题**：深色 / 浅色 / 跟随系统

### 🎧 播放体验
- **桌面歌词**：悬浮窗显示当前歌词，支持 6 种颜色
- **锁屏播放**：通知栏与锁屏直接控制，支持单曲循环 / 列表循环 / 随机 / 顺序
- **评论区**：只读展示热评与评论数
- 逐句歌词滚动、封面采样真实像素驱动玻璃背景

---

## 构建

### 本地构建
```bash
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 正式包（R8 混淆 + 资源裁剪）
```

正式包需要签名，密钥通过 Gradle 属性或环境变量传入，**不入库**：

```properties
RELEASE_STORE_FILE=/path/to/release.jks
RELEASE_STORE_PASSWORD=***
RELEASE_KEY_ALIAS=soda
RELEASE_KEY_PASSWORD=***
```

### GitHub Actions 自动打包
推 tag 即自动构建并发布：

```bash
git tag v1.0.1
git push origin v1.0.1
```

工作流在 [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml)：装 SDK 37 → 恢复签名密钥 → `assembleRelease`（R8 混淆 + 资源裁剪 + Baseline Profile）→ 上传 Artifact / 创建 Release。

签名密钥通过仓库 Secrets 提供（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`）。

---

## 技术栈

```
Kotlin 2.x  ·  Jetpack Compose  ·  Material 3
Haze 2.0 (液态玻璃)  ·  Media3 ExoPlayer + MediaSession (锁屏播放)
OkHttp  ·  Coil  ·  DataStore  ·  kotlinx.serialization
R8 (full mode 可选)  ·  ART Baseline Profile  ·  GitHub Actions
```

```
app/src/main/java/com/sodamusic/app/
├─ data/        数据层：API（网易/QQ 直连）、仓库、存储、下载
├─ playback/    前台播放服务、MediaSession、播放总线
├─ ui/          界面：nav / home / search / mine / player / discover / lyric / library
└─ util/        LRC 解析等工具
```

---

## 免责声明

本应用**仅供学习与技术交流使用**，不提供任何商业服务。

- 应用内的歌曲、歌词、封面、评论等数据均来自第三方公开接口，**著作权归原作者及相关权利人所有**，本项目不主张拥有这些内容的任何权利。
- 播放地址由第三方接口动态生成，可能随时失效；部分曲目因版权限制仅提供试听片段或完全无法播放。
- 本应用**不存储、不上传、不转售、不分享**任何用户数据；昵称、头像、背景、听歌记录与下载内容仅保存在用户本机。
- 请勿将本应用或通过其获取的任何内容用于商业用途；因个人使用本应用而产生的任何纠纷与后果由使用者自行承担。
- 如权利人认为相关内容侵犯其合法权益，请通过 Issue 反馈，核实后我们会立即下线相关内容。

## License

[MIT](LICENSE) © hexian
