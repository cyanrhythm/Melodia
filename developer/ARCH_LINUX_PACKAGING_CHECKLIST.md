# Melodia Arch Linux 适配执行清单

> 目标：让 Melodia 桌面端在 Arch Linux 上**可构建、可运行、可打包为 `pkg.tar.zst`**（供本地安装或提交 AUR）。
> 本清单基于对当前仓库的实际代码审计（`:app` / `:shared` / `:desktopApp`）得出，标注了精确行号。
>
> 关联文档：`docs/LINUX_PORT_CHECKLIST.md`（通用 Linux 移植清单，本文档聚焦 Arch 打包与发行）。

---

## 0. 总览与依赖关系

Arch 适配 = **Linux 移植（让程序能跑）** + **打包适配（让程序能装）**。前者是后者的前提。

```mermaid
flowchart LR
    A["<b>阶段 A · 代码平台化</b><br/><i>能 ./gradlew run</i>"]
    B["<b>阶段 B · 构建产物</b><br/><i>免安装目录 + tar.zst</i>"]
    C["<b>阶段 C · Arch 打包</b><br/><i>PKGBUILD + makepkg</i>"]

    A -->|LibMpv / 路径 / 系统集成| B
    B -->|去 Windows 假设| C
    C -->|.desktop / 图标 / 依赖声明| D(["可安装的<br/>pkg.tar.zst"])

    D -.->|增强| D1["阶段 D · 系统集成<br/>MPRIS / 自启 / 热键"]
    D -.->|收尾| E["阶段 E · CI 与 AUR"]

    style A fill:#7f1d1d,stroke:#fca5a5,color:#fff
    style B fill:#7f1d1d,stroke:#fca5a5,color:#fff
    style C fill:#7f1d1d,stroke:#fca5a5,color:#fff
    style D fill:#14532d,stroke:#86efac,color:#fff
    style D1 fill:#78350f,stroke:#fcd34d,color:#fff
    style E fill:#78350f,stroke:#fcd34d,color:#fff
```

| 阶段 | 目标 | 阻塞级别 |
|---|---|---|
| A | Linux 上能启动并播放 | 🔴 必须 |
| B | 产出可复用的免安装目录与归档 | 🔴 必须 |
| C | `makepkg` 出包并安装运行 | 🔴 必须 |
| D | 系统集成（MPRIS/自启/热键） | 🟡 增强 |
| E | AUR 提交流程 | 🟢 收尾 |

---

## 阶段 A：代码平台化（让程序能跑）

### A1. libmpv 加载平台化 —— 🔴 最高优先级

**文件**：`desktopApp/src/main/kotlin/com/lin0721/linmusic/desktop/player/mpv/LibMpv.kt`

- [ ] **A1.1** 第 36 行 `LIBRARY_NAME = "libmpv-2"` 硬编码，需按平台区分：
  - Windows：`libmpv-2.dll`（JNA 会自动补后缀）
  - Linux：`libmpv.so.2`（Arch 的 `mpv` 包提供 `/usr/lib/libmpv.so.2`）
  - 实现方式二选一：
    - (a) `expect/actual`：`desktopMain` 内用 `System.getProperty("os.name")` 分支
    - (b) 用 `Native.load` 前先探测可用库名列表，逐个尝试
- [ ] **A1.2** 第 40-44 行 `compose.application.resources.dir` 搜索路径保留（自带的 `.so` 场景），
      **但需追加系统库回退**：先找打包自带 `.so`，找不到再让 JNA 走系统 `LD_LIBRARY_PATH`
- [ ] **A1.3** 加载失败时的错误处理：当前若 `Native.load` 抛 `UnsatisfiedLinkError`/`LinkageError`，
      需给出**可读提示**（如「未找到 libmpv，请安装 mpv 包」），不能直接崩

### A2. XDG 路径适配 —— 🔴 必须

**文件**：`desktopApp/src/main/kotlin/com/lin0721/linmusic/desktop/platform/DesktopPaths.kt`

- [ ] **A2.1** 第 8 行 `APPDATA` → Linux 无此变量，当前回退 `user.home` 会落到 `~/Melodia`，**不符合 XDG**
      改为：`$XDG_DATA_HOME/Melodia`（默认 `~/.local/share/Melodia`）
- [ ] **A2.2** 第 16 行 `LOCALAPPDATA` → 改为 `$XDG_CACHE_HOME/Melodia`（默认 `~/.cache/Melodia`）
- [ ] **A2.3** **保留 Windows 现有路径不变**，避免破坏已安装用户的升级（用 `os.name` 分支）
- [ ] **A2.4** 第 30 行 `defaultDownloadDir = ~/Music/Melodia` ✅ 无需改

> ⚠️ 若签名/移植阶段想先跑通、后规范化，A2 可临时跳过（回退到 `~/Melodia` 也能跑），
> 但**提交 AUR 前必须完成**，否则会在用户家目录留下不规范目录。

### A3. 构建配置去 Windows 硬假设 —— 🔴 必须（仅需 `run` 时）

**文件**：`desktopApp/build.gradle.kts`

- [ ] **A3.1** 第 84-90 行 `buildSmtc` 任务已有 `onlyIf { os.name.startsWith("Windows") }` ✅ 无需改
- [ ] **A3.2** 第 93-99 行 `prepareNativeResources` 的 `include("*.dll")`：
      Linux 需 `include("*.so*")`，目标目录从 `appResources/windows` 改为 `appResources/linux-x64`
- [ ] **A3.3** 第 96 行目录名 `appResources/windows` 是 CMP 的**平台资源约定名**，
      Linux 下必须用 `linux-x64`（CMP 会在运行时按当前平台查找），否则自带 `.so` 不会被加载
- [ ] **A3.4** `packagingJavaHome`（第 117-120 行）声明 `ADOPTIUM` 21 ✅ 跨平台可用

### A4. Windows 专属 API 的运行时守卫 —— 🔴 必须（防崩溃）

以下文件在 Linux 上会因 `Native.load("kernel32"/"user32"/"dwmapi")` 抛 `UnsatisfiedLinkError`，
**必须全部加平台守卫或短路**：

- [ ] **A4.1** `platform/GlobalHotkeys.kt`
  - 第 84 行用 `Kernel32.GetCurrentThreadId` + `User32.RegisterHotKey`
  - 方案：Linux 下 `start()` 直接返回，`failed` 置空；或整体替换为"不支持"实现
- [ ] **A4.2** `platform/win/WindowChrome.kt`
  - 第 23 行已有 `isWindows` 判断 + 第 107 行早返回 ✅ **已安全**
- [ ] **A4.3** `platform/win/SystemAccent.kt` + `platform/RegistryCli.kt`
  - 依赖 `reg.exe`。Linux 下 `ProcessBuilder("reg")` 会抛异常，
    但 `RegistryCli.run` 已有 `try/catch` 返回 null ✅，`SystemAccent` 会返回 null ✅ **已安全**，
    仍建议加显式平台判断以免无谓的系统调用
- [ ] **A4.4** `ui/lyrics/DesktopLyricWindow.kt`
  - 第 131-139 行 `Native.getWindowPointer` + `User32` 设置点击穿透
  - 方案：Linux 下跳过该段，退化为「置顶但不穿透」
- [ ] **A4.5** `platform/smtc/SmtcLibrary.kt` + `SmtcSession.kt`
  - 第 56-60 行已有 `catch (e: LinkageError)` 回退 ✅ **不会崩**
  - 但 Linux 下永远是「不可用」，需确认 `SettingsPage` 文案合理
- [ ] **A4.6** `platform/AutoStart.kt`
  - 依赖注册表。`RegistryCli` 有 try/catch ✅，`isEnabled` 返回 false ✅ **已安全**
  - Linux 应改为 XDG autostart（见阶段 D）

### A5. 音频设备过滤放宽 —— 🟡 影响体验

**文件**：`desktopApp/src/main/kotlin/com/lin0721/linmusic/desktop/player/AudioDevice.kt`

- [ ] **A5.1** 第 41、66 行 `WASAPI_PREFIX = "wasapi/"` 过滤会**误杀 Linux 设备**
      （Linux 的 mpv 后端是 `alsa/`、`pulse/`、`pipewire/`、`jack/`）
  - 方案：Linux 下**不过滤**，仅将 `"auto"` 系统默认项置顶
- [ ] **A5.2** 第 46 行 `classifyAudioDevice` 的关键词表基于 Windows 描述文本，
      Linux 描述格式不同（如 `"Built-in Audio Analog Stereo"`），图标分类可能不准 → **非阻塞，二期优化**

### A6. 单测平台化 —— 🔴 CI 必过

- [ ] **A6.1** 4 个测试文件硬编码 `File("native/libmpv-2.dll")`：
  - `MpvPlaybackControllerCrossfadeTest.kt`（第 41 行）
  - `MpvEnginePairTest.kt`（第 44 行）
  - `MpvRecordingTest.kt`（第 51 行）
  - `MpvPlaybackControllerCacheTest.kt`（第 51 行）
  - 改为按平台取库名（`.so.2` / `.dll`），或 `assumeTrue` 条件放宽 → **否则 Linux 上 fail 而非 skip**
- [ ] **A6.2** 确认 `DesktopPaths` 相关测试不硬编码 Windows 路径

### ✅ 阶段 A 验收标准（DoD）

- [ ] `./gradlew :desktopApp:run` 在 Arch 上**能启动窗口**
- [ ] 能**扫码/Cookie 登录**（网络层在 `shared`，跨平台 ✅）
- [ ] 能**搜索并播放一首歌**（验证 libmpv 出声）
- [ ] `./gradlew :shared:desktopTest :desktopApp:test` 全绿
- [ ] `~/.local/share/Melodia` 与 `~/.cache/Melodia` 正确生成

---

## 阶段 B：构建产物（不要走 deb！）

### B1. 使用 `createReleaseDistributable` 而非平台包格式

- [ ] **B1.1** **明确决策：不产出 deb/rpm/msi，只产出免安装程序目录**
  - 原因：PKGBUILD 应从"上游产物"构建，套一层 deb 再拆包是反模式
  - 复用现有资产：`packageReleaseZip`（第 169-173 行）已依赖
    `compose/binaries/main-release/app` ✅ 正是需要的目录

- [ ] **B1.2** 第 135 行 `targetFormats(TargetFormat.Msi)` **保持不动**
  （因为不使用平台包格式；但若不改动而直接调 `createReleaseDistributable`，
  该配置不影响 —— CMP 中 `createDistributable` 与 `package*` 是独立任务）
  - ⚠️ 需实测确认：`targetFormats` 是否会导致 `createReleaseDistributable` 在 Linux 上报错

- [ ] **B1.3** 第 143-153 行 `windows { ... }` 块：
  仅被 `package*` 任务消费，`createDistributable` 不读 → 理论上可用
  **但为清晰起见**建议用 `if (isWindows) windows { ... }` 包裹

- [ ] **B1.4** 第 156-157 行 `iconFile.set(icons/melodia.ico)`：Linux 打包需要 **PNG**
  - ✅ **已存在可用资源**：`desktopApp/src/main/resources/melodia.png`（实测 **256×256 RGBA**）
  - 需将其复制/引用到 `desktopApp/icons/melodia.png`，或在 PKGBUILD 中直接从这里取
  - `desktopApp/icons/` 下当前**只有 `.ico`**，无 PNG，故需补一份

### B2. 新增 Linux 归档任务

- [ ] **B2.1** 新增 `packageReleaseTarball` 任务（参考现有 `packageReleaseZip`），
      产出 `Melodia-<version>-linux-x64.tar.zst`（或 `.tar.gz`）
  - 源目录：`build/compose/binaries/main-release/app`
  - ⚠️ `tar.zst` 需 Gradle 侧支持 zstd（可用自定义 `Exec` 调 `tar --zstd`，或先出 `.tar.gz`）
- [ ] **B2.2** 确认归档内**包含 `libmpv.so`**（经 `prepareNativeResources` 同步进 appResources）
- [ ] **B2.3** 确认归档**包含 `lib/runtime/`（jlink 裁剪 JRE）**，这是"免安装"的前提

### ✅ 阶段 B 验收标准（DoD）

- [ ] `./gradlew :desktopApp:createReleaseDistributable` 成功
- [ ] `app/Melodia/bin/Melodia` 在**未安装 JDK** 的 Arch 环境能启动（验证内置 runtime）
- [ ] 产出 `Melodia-<version>-linux-x64.tar.zst`，解压后可直接运行

---

## 阶段 C：Arch 打包（PKGBUILD）

### C1. 目录与文件

- [ ] **C1.1** 新建 `packaging/arch/` 目录，存放：
  - `PKGBUILD`
  - `melodia.desktop`
  - `melodia.install`（可选，用于 post_install 提示）
  - `.SRCINFO`（`makepkg --printsrcinfo` 生成，AUR 需要）

### C2. PKGBUILD 要点

- [ ] **C2.1** `pkgname=melodia`，`arch=('x86_64')`，`license=('MIT')`
- [ ] **C2.2** `depends`：至少 `libpulse`、`alsa-lib`（libmpv 的音频后端依赖）
      - 若**自带 libmpv.so**：不需声明 `mpv`
      - 若**用系统 mpv**：声明 `mpv`，并需先完成 A1.2 的系统库查找
- [ ] **C2.3** `makedepends`：`jdk21-openjdk`、`git`（若自带 libmpv 还需 `cmake` 等）
- [ ] **C2.4** ⚠️ **`options=('!strip')` 必须设置**
  - 内置 jlink runtime 的 `.so` 被 strip 后会破坏 JVM → 运行崩溃
- [ ] **C2.5** `source=()` 指向 GitHub tag 的 tarball；`sha256sums` 填实际校验值
- [ ] **C2.6** `build()` 内执行：
  ```bash
  ./gradlew :desktopApp:createReleaseDistributable --no-daemon
  ```
- [ ] **C2.7** `package()` 内安装布局（Arch 规定**禁止装到 `/opt`**）：
  - 应用主体 → `/usr/lib/melodia/`
  - 入口符号链接 → `/usr/bin/melodia`（指向 `/usr/lib/melodia/bin/Melodia`）
  - 桌面项 → `/usr/share/applications/melodia.desktop`
  - 图标 → `/usr/share/icons/hicolor/256x256/apps/melodia.png`
  - 许可证 → `/usr/share/licenses/melodia/LICENSE`

### C3. .desktop 文件

- [ ] **C3.1** 关键字段：
  ```ini
  [Desktop Entry]
  Type=Application
  Name=Melodia
  Exec=/usr/bin/melodia
  Icon=melodia
  Terminal=false
  Categories=AudioVideo;Audio;Player;
  StartupWMClass=Melodia
  ```
- [ ] **C3.2** 确认 `StartupWMClass` 与应用实际窗口类名一致
  - 应用窗口标题为 `"Melodia"`（`Main.kt` 第 193 行），AWT/Compose Desktop 默认 WM_CLASS 通常即为该值
  - ⚠️ 需在 Arch 上实测 `xprop WM_CLASS` 核对，否则任务栏会出现**两个图标**

### C4. 校验

- [ ] **C4.1** `makepkg -si` 能安装
- [ ] **C4.2** `namcap melodia-*.pkg.tar.zst` 无 ERROR（WARNING 逐条评估）
- [ ] **C4.3** 从应用菜单能启动，图标正确
- [ ] **C4.4** 卸载 `pacman -R melodia` 干净
- [ ] **C4.5** 用户数据目录遵循 XDG（验证 A2 完成）

### ✅ 阶段 C 验收标准（DoD）

- [ ] Arch 上 `makepkg -si` 一把过
- [ ] `namcap` 无 ERROR
- [ ] 安装后能播放、登录、退出

---

## 阶段 D：系统集成（增强，可后置）

### D1. MPRIS 替代 SMTC —— 🟡 推荐

- [ ] **D1.1** 新增 `platform/mpris/MprisLibrary.kt`：D-Bus 绑定（`dbus-java` 或 JNA 绑 `libdbus-1`）
- [ ] **D1.2** 新增 `platform/mpris/MprisSession.kt`：实现 `org.mpris.MediaPlayer2` + `.Player`
- [ ] **D1.3** 提取公共接口（如 `SystemMediaSession`），`Main.kt`（第 92-94 行）与 `SettingsPage` 按平台取实现
- [ ] **D1.4** `mpris:artUrl` 需 `file://` URL → 封面需落盘临时文件（当前 SMTC 直接传 URL，处理不同）
- [ ] **D1.5** 依赖新增 `dbus`（运行时，D-Bus 本就是系统基础组件）

### D2. 开机自启走 XDG —— 🟡 推荐

- [ ] **D2.1** `platform/AutoStart.kt` 平台分支：Linux 写 `~/.config/autostart/melodia.desktop`
- [ ] **D2.2** `exePath` 取自 `jpackage.app-path`，Linux 下同样注入 ✅
- [ ] **D2.3** 自启的 `Exec` 应指向 `/usr/bin/melodia` 还是应用自身路径，需明确

### D3. 全局热键 —— 🟢 可标记"不支持"

- [ ] **D3.1** X11 可用 `XGrabKey`（JNA 绑 `libX11`）；**Wayland 无全局热键 API**
- [ ] **D3.2** 推荐策略：Linux 下依赖 MPRIS/桌面媒体键，自定义热键在设置页**灰显**
- [ ] **D3.3** `SettingsPage.kt` 第 106 行 Win32 虚拟键码映射表 Linux 不适用

### D4. 桌面歌词点击穿透 —— 🟢 可降级

- [ ] **D4.1** X11 可用 `XShapeCombineRectangles`；**Wayland 无点击穿透 API**
- [ ] **D4.2** 建议 Linux 下退化为「置顶不穿透」，并在设置页说明

### D5. 托盘兼容性 —— 🟢 提示即可

- [ ] **D5.1** AWT `SystemTray` 跨平台 ✅，但 GNOME 默认不显示 StatusNotifier
- [ ] **D5.2** 增加环境检测提示，或在无托盘时隐藏相关设置项

---

## 阶段 E：CI 与 AUR 发布（收尾）

### E1. CI 扩展

- [ ] **E1.1** `.github/workflows/ci.yml` 第 37 行已有 `:desktopApp:compileKotlin` 跑在 ubuntu ✅
- [ ] **E1.2** 新增 `:desktopApp:createReleaseDistributable` 构建验证
- [ ] **E1.3** CI 需预置 Linux libmpv（`apt install libmpv-dev` 或放入自带 `.so`）
- [ ] **E1.4** 新增 `:desktopApp:test` 到测试步骤（阶段 A6 完成后）
- [ ] **E1.5** 可新增 Arch 容器（`archlinux:latest`）内跑 `makepkg` 的验证 job

### E2. 发布产物

- [ ] **E2.1** Release workflow 增加 Linux tarball 上传
- [ ] **E2.2** 按平台区分产物命名：`Melodia-<ver>-windows-x64.zip` / `Melodia-<ver>-linux-x64.tar.zst`

### E3. AUR 提交

- [ ] **E3.1** 创建 AUR 仓库（`ssh://aur@aur.archlinux.org/melodia.git`）
- [ ] **E3.2** 生成 `.SRCINFO`：`makepkg --printsrcinfo > .SRCINFO`
- [ ] **E3.3** 遵循 AUR 命名/许可规范（`$pkgname.install` 慎用，避免脚本逻辑）
- [ ] **E3.4** ⚠️ Gradle 离线构建问题：`build()` 内 Gradle 会下载依赖，
      与 Arch「可重复、可离线」原则冲突。缓解方案：
  - 预先把依赖缓存打进 source tarball
  - 或在 `prepare()` 阶段用 vendored 依赖
  - 这是 Gradle 项目打 Arch 包的**通用痛点**，需在 PKGBUILD 注释中说明

### E4. 文档

- [ ] **E4.1** `README.md` 第 111-114 行「桌面端环境要求」补 Linux/Arch 章节
- [ ] **E4.2** 说明 libmpv 在 Arch 上的获取方式（系统 `mpv` 包 vs 自带 `.so`）
- [ ] **E4.3** 说明 Wayland 下热键/点击穿透的限制
- [ ] **E4.4** 新增 `docs/ARCH_PACKAGING.md` 记录打包流程

---

## 附：关键风险与决策点

| # | 风险 | 决策 | 建议 |
|---|---|---|---|
| R1 | **libmpv 来源**：自带 `.so` vs 系统 `mpv` 包 | 二选一 | 初期**自带**（简单），AUR 稳定后切**系统 mpv**（规范） |
| R2 | **`!strip` 遗漏**导致 JVM 崩溃 | 必须设 | PKGBUILD 里 `options=('!strip')`，并写进验收 |
| R3 | **Wayland** 无全局热键/点击穿透 | 无法绕过 | 明确标注"受限"，不强行实现 |
| R4 | **Gradle 离线构建**与 Arch 规范冲突 | 需权衡 | 初期注释说明，后期做依赖 vendoring |
| R5 | **图标缺 PNG** | 必须先补 | 从 `src/main/resources/melodia.png` 提取/重制 |
| R6 | **A2 XDG 路径**若跳过，会污染家目录 | 必须完成 | 提交 AUR 前的硬门槛 |

---

## 附：推荐执行顺序（最小可交付路径）

```
1. A1 LibMpv 平台化  +  A4 守卫             → 能在 Arch 上 run（出声）
2. A6 测试平台化                              → CI 绿
3. A2 XDG 路径                                → 符合规范
4. B1/B2 产出免安装目录 + tar.zst             → 有可分发产物
5. C  PKGBUILD + .desktop + PNG 图标          → makepkg 出包
6. E CI 扩展 + AUR 提交                       → 可发布
7. D MPRIS / 自启 / 热键（增强，可后置）      → 完善体验
```

**最小可安装版本**：仅需完成 1–5 步，即可产出可用的 Arch 包。
**风险最高项**：A1（libmpv 加载）+ R2（strip）+ R4（Gradle 离线）。
