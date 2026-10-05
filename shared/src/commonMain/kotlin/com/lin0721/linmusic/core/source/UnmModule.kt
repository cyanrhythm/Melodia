package com.lin0721.linmusic.core.source

// 客户端本地直连音源子模块定义
enum class UnmModule(
    val key: String,
    val displayName: String,
    val description: String
) {
    BYFUNS("byfuns", "Byfuns", "网易云高品质/无损直连解析"),
    DDYR("ddyr", "Ddyr", "网易云高品质直连解析"),
    GDMUSIC("gdmusic", "Gdmusic", "GD 音乐台直连解析"),
    OI("oi", "Oi", "OiAPI 网易云直链解析"),
    QIJIEYA("qijieya", "Qijieya", "七街 Meting 网易云解析"),
    MSLS("msls", "Msls", "Msls 聚合直连解析");

    companion object {
        val ALL_KEYS: List<String> = entries.map { it.key }
        fun fromKey(key: String): UnmModule? = entries.find { it.key.equals(key, ignoreCase = true) }
    }
}
