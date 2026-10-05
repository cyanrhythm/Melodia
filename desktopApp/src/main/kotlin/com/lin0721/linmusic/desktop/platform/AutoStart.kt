package com.lin0721.linmusic.desktop.platform

private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val VALUE_NAME = "Melodia"

// 开机自启以注册表 Run 项为准；阻塞调用，需在 IO 线程执行
object AutoStart {

    // jpackage 启动器注入的 exe 路径，开发环境运行时为空
    val exePath: String? = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

    val isSupported: Boolean get() = exePath != null

    fun isEnabled(): Boolean {
        if (!isSupported) return false
        val result = RegistryCli.run("query", RUN_KEY, "/v", VALUE_NAME) ?: return false
        return result.exitCode == 0 && result.output.contains(exePath!!, ignoreCase = true)
    }

    fun setEnabled(enabled: Boolean): Boolean {
        val path = exePath ?: return false
        val result = if (enabled) {
            RegistryCli.run("add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", "\"$path\"", "/f")
        } else {
            RegistryCli.run("delete", RUN_KEY, "/v", VALUE_NAME, "/f")
        }
        return result?.exitCode == 0
    }
}
