package com.intellij.remoterobot.fixtures

import com.google.gson.Gson
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.search.locators.byXpath
import org.assertj.swing.core.MouseButton
import java.awt.Point

class TextEditorFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    ContainerFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath("//div[@class='PsiAwareTextEditorComponent']")
    }

    val gutter: GutterFixture
        get() = find(GutterFixture.locator)

    val editor: EditorFixture
        get() = find(EditorFixture.locator)

    val statusButton
        get() = find<ComponentFixture>(byXpath("//div[@class='StatusButton']"))
}


class GutterIcon(private val gutter: GutterFixture, private val gutterIconInfo: GutterIconInfo) {
    val lineNumber = gutterIconInfo.lineNumber
    val description = gutterIconInfo.description
    val point = gutterIconInfo.point
    fun moveMouse() {
        gutter.runJs(
            """
                const point = new Point(${point.x}, ${point.y})
                robot.moveMouse(component, point) 
            """
        )
    }

    fun click() {
        gutter.click(point)
    }

    fun rightClick() {
        gutter.runJs(
            """
           robot.click(component, new Point(${point.x}, ${point.y}), MouseButton.RIGHT_BUTTON, 1) 
        """
        )
    }

    override fun toString(): String {
        return "$lineNumber: $point $description"
    }
}

data class GutterIconInfo(val description: String, val lineNumber: Int, val point: Point)
class GutterFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    ComponentFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath("//div[@class='EditorGutterComponentImpl']")
    }

    fun getIcons(): List<GutterIcon> {
        val icons = callJs<ArrayList<String>>(
            """
            const iconsArray = new ArrayList()
            const method = component.getClass().getDeclaredMethod('processGutterRenderers')
            method.setAccessible(true)
            try {
                const iteratable = method.invoke(component)
                const iterator = iteratable.iterator() 
                while(iterator.hasNext()) {
                    let lineInfo = iterator.next()
                    let renderers = lineInfo.getValue()
                    for (let i = 0; i < renderers.size(); i++) {
                        let icon = {
                            point: {}
                        }
                        let renderer = renderers.get(i)
                        let point = component.getCenterPoint(renderer)
                        icon.description = new String(renderer.getIcon().toString())
                        icon.point.x = point.x
                        icon.point.y = point.y
                        icon.lineNumber = parseInt(lineInfo.getKey() + 1)
                        iconsArray.add(JSON.stringify(icon))
                    }
                }
            } finally {
              method.setAccessible(false)
            }
            iconsArray
        """, true
        )
        return icons.map { Gson().fromJson(it, GutterIconInfo::class.java) }.map { GutterIcon(this, it) }
    }
}

class EditorFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) :
    ContainerFixture(remoteRobot, remoteComponent) {
    companion object {
        val locator = byXpath("//div[@class='EditorComponentImpl']")
    }

    init {
        runJs(
            """
            local.put('editor', component.getEditor())
            local.put('document', component.getEditor().getDocument())
        """
        )
    }

    val text: String
        get() = callJs("local.get('document').getText()")

    val selectedText: String
        get() = callJs(
            """
            let selectedText = local.get('editor').getSelectionModel().getSelectedText()
            if (!selectedText) { selectedText = "" }
            selectedText
        """
        )

    val caretOffset: Int
        get() = callJs("local.get('editor').getCaretModel().getOffset()", true)

    val fileName: String
        get() = callJs("local.get('editor').getVirtualFile().getName()", true)

    val filePath: String
        get() = callJs("local.get('editor').getVirtualFile().getPath()", true)

    fun clickOnOffset(offset: Int, mouseButton: MouseButton = MouseButton.LEFT_BUTTON, times: Int = 1) {
        scrollToOffset(offset)
        runJs(
            """
            const editor = local.get('editor')
            const visualPosition = editor.offsetToVisualPosition(${offset})
            const clickPoint = editor.visualPositionToXY(visualPosition)
            robot.click(component, new Point(clickPoint.x, clickPoint.y + 5), MouseButton.${mouseButton}, ${times})
        """, true
        )
    }

    fun scrollToOffset(offset: Int) {
        runJs(
            """
                const editor = local.get('editor')
                local.put('scrollingInProgress', true)
                editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(${offset}), com.intellij.openapi.editor.ScrollType.CENTER)
            """, true
        )
        Thread.sleep(500)
    }
}