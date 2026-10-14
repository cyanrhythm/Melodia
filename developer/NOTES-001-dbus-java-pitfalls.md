# dbus-java 使用要点：MPRIS 实现踩坑记录

> 场景：`desktopApp` 的 `MprisSession` 用 MPRIS 2 over D-Bus 实现系统媒体控制
> 库：`com.github.hypfvieh:dbus-java-core:5.1.1` + `dbus-java-transport-native-unixsocket:5.1.1`
> 关联代码：`platform/linux/MprisSession.kt` 及 `platform/linux/mpris/`

---

## 1. 选型说明

`dbus-java` 用**纯 Java 的 unixsocket 传输**，**不需要原生 `libdbus`**。
对本项目很重要：jpackage 打包时不必引入系统 `.so` 依赖。

已实测确认（session bus 连通、导出对象、读写属性、调用方法全部通过）。

---

## 2. 四个必踩的坑（均已在本项目修复并回归测试覆盖）

### 2.1 `Variant` 包装数组必须显式给签名

```kotlin
// ❌ 运行时抛 IllegalArgumentException:
//    Can't wrap class java.util.Collections$SingletonList in an unqualified Variant
Variant(listOf(artist))

// ✅ 显式声明 D-Bus 签名 "as"（array of strings）
Variant(arrayOf(artist), "as")
```

MPRIS 规范中 `xesam:artist` 是**字符串数组**，必须用 `Array` + 显式签名。

### 2.2 `Variant` 不能用 Kotlin 的 `buildMap` / `mapOf` 构建的 Map

```kotlin
// ❌ Can't wrap class kotlin.collections.builders.MapBuilder
buildMap { put(...) }

// ✅ 标准 LinkedHashMap
LinkedHashMap<String, Variant<*>>().apply { this["k"] = Variant(...) }
```

**原因**：dbus-java 反射检查容器的实现类，`MapBuilder` 等 Kotlin 内部类型不被识别。

### 2.3 `Get` 返回 `a{sv}` 属性时必须显式包装 `Variant`

```kotlin
// ❌ 返回裸 Map → Can't wrap class java.util.LinkedHashMap in an unqualified Variant
override fun <A : Any?> Get(iface: String, prop: String): A =
    metadata as A

// ✅ 带签名包装
override fun <A : Any?> Get(iface: String, prop: String): A =
    Variant(metadata, "a{sv}") as A
```

`Metadata` 属性签名是 `a{sv}`，dbus-java 在序列化返回值时不会自动推断容器签名。

### 2.4 `DBusSignal` 子类必须嵌套在 `DBusInterface` 内，且要走 8 参构造器

**第一层问题**：信号类不能是独立顶层类。

```
DBusException: Signals must be declared as a member of a class
implementing DBusInterface which is the member of a package.
```

→ 必须写成 `MprisPlayer` 的**嵌套类**。

**第二层问题**：构造器重载选错会导致接口名错位。

dbus-java 的可用重载：

```java
DBusSignal(String path, Object... args)                          // 短重载，靠反射推断
DBusSignal(byte endianess, String source, String path,
           String iface, String member, String sig, Object... args)  // 完整重载
```

Kotlin 调用 `DBusSignal(path, iface, name, sig, body)` 这类 5 参写法会**解析到短重载**，
把 `iface` 当作 body 参数，导致信号的接口名变成 body 里的值。

**必须显式走完整重载**：

```kotlin
class PropertiesChanged(
    objectPath: String,
    changedProperties: Map<String, Variant<*>>,
    invalidatedProperties: List<String> = emptyList(),
) : DBusSignal(
    0,                    // endianess
    null,                 // source
    objectPath,
    PROPERTIES_INTERFACE, // iface = org.freedesktop.DBus.Properties
    "PropertiesChanged",  // member
    "sa{sv}as",           // 完整签名
    PLAYER_INTERFACE,     // body 首字段：变更所属接口（签名 s）
    changedProperties,
    invalidatedProperties,
)
```

> 验证方式：单测断言 `signal.getInterface()` 为 `org.freedesktop.DBus.Properties`。
> 这个断言当初正是靠测试才发现接口名错位的。

---

## 3. 验证手段

### 3.1 单测（`MprisMetadataTest`，9 例）
覆盖元数据字段、签名、容器类型、信号构造。**不依赖真实 D-Bus**，可在 CI 跑。

### 3.2 实测（`dbus-send`）
需要真实 session bus：

```bash
# 服务名是否注册
dbus-send --session --print-reply --dest=org.freedesktop.DBus \
  /org/freedesktop/DBus org.freedesktop.DBus.ListNames | grep melodia

# 读属性
dbus-send --session --print-reply --dest=org.mpris.MediaPlayer2.melodia \
  /org/mpris/MediaPlayer2 org.freedesktop.DBus.Properties.Get \
  string:org.mpris.MediaPlayer2.Player string:Metadata

# 调方法
dbus-send --session --dest=org.mpris.MediaPlayer2.melodia \
  /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player.PlayPause
```

**实测结论**（本项目已验证）：
- 服务名注册成功
- `PlaybackStatus` 读到 `"Playing"`
- `Metadata` 完整返回，`mpris:trackid` 为 object path、`xesam:artist` 为 array、`mpris:length` 为 int64
- `GetAll` 正常
- `PlayPause` / `Seek(int64)` 均返回 `method return`

---

## 4. 部署注意

- **运行时依赖**：`dbus-java-core` + `dbus-java-transport-native-unixsocket` 两个 jar
- **jpackage**：需确认 `modules(...)` 覆盖 `java.base` 的 unixsocket 相关类（通常无需额外模块）
- **无 session bus 时**：`DBusConnectionBuilder.forSessionBus().build()` 抛异常，
  `start()` 已捕获并返回 `false`，应用降级为无系统媒体控制，不影响其他功能

---

## 5. 参考

- MPRIS 2.2 规范：https://specifications.freedesktop.org/mpris-spec/2.2/
- dbus-java：https://github.com/hypfvieh/dbus-java
