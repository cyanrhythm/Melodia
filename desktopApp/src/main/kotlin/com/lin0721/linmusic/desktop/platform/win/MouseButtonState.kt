package com.lin0721.linmusic.desktop.platform.win

private const val VK_LBUTTON = 0x01
private const val VK_RBUTTON = 0x02
private const val KEY_DOWN_MASK = 0x8000

// 直接读系统的鼠标键状态：窗口外松开时 AWT 收不到抬起事件，事件流里的按键状态不可信
internal object MouseButtonState {

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    // GetAsyncKeyState 返回物理键位，左右键互换时主键对应物理右键
    private val primaryVirtualKey: Int? by lazy {
        if (!isWindows) return@lazy null
        try {
            if (User32.INSTANCE.GetSystemMetrics(User32.SM_SWAPBUTTON) != 0) VK_RBUTTON else VK_LBUTTON
        } catch (_: LinkageError) {
            null
        }
    }

    // 非 Windows 或调用失败返回 null，调用方应退回事件里的按键状态
    fun isPrimaryDown(): Boolean? {
        val key = primaryVirtualKey ?: return null
        return try {
            (User32.INSTANCE.GetAsyncKeyState(key).toInt() and KEY_DOWN_MASK) != 0
        } catch (_: LinkageError) {
            null
        }
    }
}
