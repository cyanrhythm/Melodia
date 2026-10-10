package com.lin0721.linmusic.desktop.platform.linux

import com.lin0721.linmusic.desktop.platform.native.TrayMenuModel
import com.lin0721.linmusic.desktop.platform.native.TrayMenuNode
import com.lin0721.linmusic.desktop.platform.native.linux.sni.DbusMenu
import com.lin0721.linmusic.desktop.platform.native.linux.sni.MenuLayoutItem
import com.lin0721.linmusic.desktop.platform.native.linux.sni.MenuLayoutReply
import com.lin0721.linmusic.desktop.platform.native.linux.sni.SniDbusMenu
import com.lin0721.linmusic.desktop.platform.native.linux.sni.buildMenuLayout
import org.freedesktop.dbus.Marshalling
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Type

// DBusMenu 布局与多返回值机制的单元测试。
// dbus-java 的返回多值约定：实现 DBusSerializable，serialize() 给出各 out 参数值，
// deserialize() 的形参类型用于推导 out 签名（u + (ia{sv}av)）。
class MenuLayoutTest {

    @Test
    fun `头部是不可点行并带分隔线`() {
        val built = buildMenuLayout(model(header = "正在播放: 测试_歌曲", nodes = listOf(action("播放"))))
        val children = built.root.children.map { it.value as MenuLayoutItem }

        assertEquals(3, children.size)
        assertEquals(false, children[0].properties.getValue(DbusMenu.ITEM_ENABLED).value)
        // 字面下划线转义成两个（DBusMenu 用下划线声明助记符）
        assertEquals("正在播放: 测试__歌曲", children[0].properties.getValue(DbusMenu.ITEM_LABEL).value)
        assertEquals(DbusMenu.TYPE_SEPARATOR, children[1].properties.getValue(DbusMenu.ITEM_TYPE).value)
    }

    @Test
    fun `动作与勾选项的属性与回调`() {
        var played = false
        var toggled = false
        val built = buildMenuLayout(
            model(
                nodes = listOf(
                    action("播放") { played = true },
                    TrayMenuNode.Toggle("锁定桌面歌词", checked = true) { toggled = true },
                    TrayMenuNode.Separator,
                )
            )
        )
        val children = built.root.children.map { it.value as MenuLayoutItem }

        assertEquals(listOf(1, 2, 3), children.map { it.id })
        assertEquals("播放", children[0].properties.getValue(DbusMenu.ITEM_LABEL).value)
        assertEquals(true, children[0].properties.getValue(DbusMenu.ITEM_ENABLED).value)
        assertEquals(DbusMenu.TOGGLE_CHECKMARK, children[1].properties.getValue(DbusMenu.ITEM_TOGGLE_TYPE).value)
        assertEquals(1, children[1].properties.getValue(DbusMenu.ITEM_TOGGLE_STATE).value)
        assertEquals(DbusMenu.TYPE_SEPARATOR, children[2].properties.getValue(DbusMenu.ITEM_TYPE).value)

        built.callbacks.getValue(1).invoke()
        built.callbacks.getValue(2).invoke()
        assertTrue(played)
        assertTrue(toggled)
    }

    @Test
    fun `服务端按 id 回调并推进版本`() {
        val server = SniDbusMenu()
        var clicked = 0
        val revision = server.update(model(nodes = listOf(action("显示主窗口") { clicked++ })))

        server.Event(1, DbusMenu.EVENT_CLICKED, Variant(0, "i"), UInt32(0))
        assertEquals(1, clicked)
        // 未登记的 id（如分隔线）不回调
        server.Event(99, DbusMenu.EVENT_CLICKED, Variant(0, "i"), UInt32(0))
        assertEquals(1, clicked)

        val layout = server.GetLayout(0, -1, emptyList()).serialize()
        assertEquals(revision, layout[0])
        assertEquals(0, (layout[1] as MenuLayoutItem).id)
    }

    @Test
    fun `多返回值签名推导为 u 与菜单结构`() {
        // dbus-java 用 deserialize 的形参推导 out 参数
        assertEquals(listOf("u", "(ia{sv}av)"), Marshalling.getDBusType(MenuLayoutReply::class.java).toList())

        // 序列化时按 serialize() 展开成 revision 与布局两项
        val reply = MenuLayoutReply(UInt32(2), buildMenuLayout(model(nodes = listOf(action("退出")))).root)
        val params = Marshalling.convertParameters(arrayOf(reply), arrayOf<Type>(MenuLayoutReply::class.java), null)
        assertEquals(2, params.size)
        assertEquals(2L, (params[0] as UInt32).toLong())
    }

    private fun model(header: String? = null, nodes: List<TrayMenuNode>) = TrayMenuModel(header, nodes)

    private fun action(label: String, onClick: () -> Unit = {}) = TrayMenuNode.Action(label, onSelect = onClick)
}
