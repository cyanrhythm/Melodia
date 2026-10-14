package com.lin0721.linmusic.desktop.platform.linux

// 单个输出的物理模式尺寸与合成器逻辑矩形
// （逻辑坐标来自 xdg_output：分数缩放下不仅尺寸不同，原点也可能带小数取整误差）
internal data class WaylandOutputInfo(
    val physicalWidth: Int,
    val physicalHeight: Int,
    val logicalX: Int,
    val logicalY: Int,
    val logicalWidth: Int,
    val logicalHeight: Int,
)

// 运行 wayland-info 并返回原始输出（无 Wayland 会话或未安装 wayland-utils 时为 null）；
// 缩放探测与托盘坐标换算共用
internal fun readWaylandInfo(): String? = runCatching {
    if (System.getenv("WAYLAND_DISPLAY").isNullOrBlank()) return null
    val process = ProcessBuilder("wayland-info").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
    output
}.getOrNull()

// 解析 wayland-info 输出：wl_output 的当前模式宽高 + xdg_output 的逻辑位置/宽高（按对象 id 配对）
internal fun parseWaylandInfo(text: String): List<WaylandOutputInfo> {
    val physicalById = WL_OUTPUT_BLOCK.findAll(text).mapNotNull { match ->
        val body = match.groupValues[2]
        val size = CURRENT_MODE_SIZE.find(body) ?: ANY_MODE_SIZE.find(body)
        val width = size?.groupValues?.get(1)?.toIntOrNull()
        val height = size?.groupValues?.get(2)?.toIntOrNull()
        if (width == null || height == null) null else match.groupValues[1] to PhysicalSize(width, height)
    }.toMap()
    val logicalById = XDG_OUTPUT_BLOCK.findAll(text).mapNotNull { match ->
        val body = match.groupValues[2]
        // 原点缺省视为 0：老版本 wayland-info 不一定输出 logical_x/y
        val x = LOGICAL_X.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val y = LOGICAL_Y.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val width = LOGICAL_WIDTH.find(body)?.groupValues?.get(1)?.toIntOrNull()
        val height = LOGICAL_HEIGHT.find(body)?.groupValues?.get(1)?.toIntOrNull()
        if (width == null || height == null) {
            null
        } else {
            match.groupValues[1] to LogicalRect(x, y, width, height)
        }
    }.toMap()

    return physicalById.mapNotNull { (id, physical) ->
        val logical = logicalById[id] ?: return@mapNotNull null
        WaylandOutputInfo(
            physicalWidth = physical.width,
            physicalHeight = physical.height,
            logicalX = logical.x,
            logicalY = logical.y,
            logicalWidth = logical.width,
            logicalHeight = logical.height,
        )
    }
}

private data class PhysicalSize(val width: Int, val height: Int)

private data class LogicalRect(val x: Int, val y: Int, val width: Int, val height: Int)

private val WL_OUTPUT_BLOCK = Regex("interface: 'wl_output'[^\\n]*name:\\s*(\\d+)([\\s\\S]*?)(?=interface:|\\z)")
private val XDG_OUTPUT_BLOCK = Regex("output:\\s*(\\d+)([\\s\\S]*?)(?=xdg_output_v1|interface:|\\z)")
private val CURRENT_MODE_SIZE = Regex("width:\\s*(\\d+)\\s*px,\\s*height:\\s*(\\d+)\\s*px[^\\n]*\\n\\s*flags:\\s*current")
private val ANY_MODE_SIZE = Regex("^\\s*width:\\s*(\\d+)\\s*px,\\s*height:\\s*(\\d+)\\s*px", RegexOption.MULTILINE)
private val LOGICAL_X = Regex("logical_x:\\s*(-?\\d+)")
private val LOGICAL_Y = Regex("logical_y:\\s*(-?\\d+)")
private val LOGICAL_WIDTH = Regex("logical_width:\\s*(\\d+)")
private val LOGICAL_HEIGHT = Regex("logical_height:\\s*(\\d+)")
