package com.lin0721.linmusic.core.source

// 音源平台枚举
enum class MusicPlatform(val key: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    KUGOU("kugou", "酷狗音乐"),
    MIGU("migu", "咪咕音乐"),
    KUWO("kuwo", "酷我音乐"),
    QQ("qq", "QQ音乐"),
    LX("lx", "自定义插件");

    // LX Music 源脚本使用的平台代号映射
    val lxKey: String
        get() = when (this) {
            NETEASE -> "wy"
            QQ -> "tx"
            KUGOU -> "kg"
            KUWO -> "kw"
            MIGU -> "mg"
            LX -> "lx"
        }

    companion object {
        fun fromKey(key: String): MusicPlatform? = entries.find { it.key == key }
        fun fromLxKey(lxKey: String): MusicPlatform? = entries.find { it.lxKey == lxKey }
    }
}
