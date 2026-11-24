package app.mannybrowser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.BeforeClass
import java.io.File

@RunWith(AndroidJUnit4::class)
class TabScreenTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun disableSessionPersistence() {
            BrowserState.sessionPersistenceEnabled = false
        }
    }
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun visible(text: String) = device.wait(Until.hasObject(By.text(text)), 15000)
    private fun goneDesc(desc: String) = device.wait(Until.gone(By.desc(desc)), 8000)
    private fun boundsFromSnapshot(attribute: String, value: String, clickableParent: Boolean = false): android.graphics.Rect? {
        val bytes = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(bytes)
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(bytes.toByteArray()))
        val nodes = xml.getElementsByTagName("node")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as org.w3c.dom.Element
            if (node.getAttribute(attribute) == value) {
                var target = node
                if (clickableParent) {
                    while (target.getAttribute("clickable") != "true") {
                        target = target.parentNode as? org.w3c.dom.Element ?: break
                    }
                    if (target.getAttribute("clickable") != "true") continue
                }
                val n = Regex("[0-9]+").findAll(target.getAttribute("bounds")).map { it.value.toInt() }.toList()
                if (n.size == 4 && n[2] > n[0] && n[3] > n[1]) return android.graphics.Rect(n[0], n[1], n[2], n[3])
            }
        }
        return null
    }
    private fun clickAttr(attribute: String, value: String, clickableParent: Boolean = true) {
        val deadline = System.currentTimeMillis() + 10000
        var bounds: android.graphics.Rect? = null
        do {
            bounds = boundsFromSnapshot(attribute, value, clickableParent)
            if (bounds != null) break
            Thread.sleep(150)
        } while (System.currentTimeMillis() < deadline)
        if (bounds == null) shot("missing-$value")
        assertNotNull("Snapshot contains $value", bounds)
        device.click(bounds!!.centerX(), bounds.centerY())
        device.waitForIdle(); Thread.sleep(600)
    }
    private fun clickText(text: String) {
        assertTrue("Missing $text", visible(text))
        clickAttr("text", text)
    }
    private fun clickDesc(desc: String) {
        assertTrue("Missing $desc", device.wait(Until.hasObject(By.desc(desc)), 15000))
        clickAttr("content-desc", desc, clickableParent = true)
    }
    private fun fillDialogField(value: String) {
        if (device.wait(Until.findObject(By.focused(true)), 2500) == null) {
            clickAttr("class", "android.widget.EditText", clickableParent = false)
        }
        val edit = device.wait(Until.findObject(By.focused(true)), 5000)
        assertNotNull("Dialog field focused", edit)
        edit!!.text = value
        device.waitForIdle()
    }
    private fun tabs() {
        assertTrue("Tabs button in browser", device.wait(Until.hasObject(By.desc("Tabs")), 15000))
        clickAttr("content-desc", "Tabs", clickableParent = true)
        Thread.sleep(500)
    }
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "manny-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-tabscreen-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-tabscreen-$name.xml"))
    }
    private fun launch() {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.desc("Tabs")), 20000))
    }
    private fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Page loaded for $url", device.wait(Until.hasObject(By.desc("Tabs")), 15000))
        Thread.sleep(1500)
    }

    @Test fun headerSearchAndLayoutToggle() {
        launch()
        open("http://localhost:8765")
        open("http://localhost:8765/second")
        tabs()
        assertTrue("Options button", device.wait(Until.hasObject(By.desc(str(R.string.tab_options))), 10000))
        assertTrue("Layout toggle", device.wait(Until.hasObject(By.desc(str(R.string.toggle_layout))), 10000))
        assertTrue("New tab tile", device.wait(Until.hasObject(By.desc(str(R.string.new_tab))), 10000))
        assertTrue("Search bar hint", visible(str(R.string.search_tabs)))
        shot("header")

        // Search flattens the grid and filters cards by title or URL.
        clickAttr("text", str(R.string.search_tabs), clickableParent = false)
        val field = device.wait(Until.findObject(By.focused(true)), 5000)
        assertNotNull("Search field focused", field)
        field!!.text = "Manny"
        device.waitForIdle(); Thread.sleep(1000)
        shot("search-filtered")
        assertTrue("Matching card visible", device.wait(Until.hasObject(By.text("Manny fixture")), 5000))
        assertTrue("Non-matching card filtered out",
            device.wait(Until.gone(By.text("Second fixture")), 5000))

        // Clear search, then switch to list mode and back.
        field.text = ""
        device.waitForIdle(); Thread.sleep(800)
        clickDesc(str(R.string.toggle_layout))
        shot("list-mode")
        assertTrue("List shows site row", device.wait(Until.hasObject(By.text("localhost")), 5000))
        clickDesc(str(R.string.toggle_layout))
        assertTrue("Back in grid: group card count", visible(str(R.string.tab_count, 2)))
        shot("grid-mode")
    }

    @Test fun manualGroupCreateRenameRecolorClose() {
        launch()
        open("http://localhost:8765")
        open("http://localhost:8765/second")
        tabs()
        // Both localhost tabs sit on one automatic group card; select it and form a manual group.
        clickDesc(str(R.string.tab_options))
        clickText(str(R.string.select_tabs))
        shot("selecting")
        clickDesc(str(R.string.select_tab, "localhost"))
        shot("selected")
        clickText(str(R.string.new_group_action, 2))
        assertTrue("Name dialog", visible(str(R.string.edit_group_name)))
        fillDialogField("MyGroup")
        clickText(str(R.string.save))
        shot("group-created")
        assertTrue("Group card with name", visible("MyGroup"))
        assertTrue("Group card count", visible(str(R.string.tab_count, 2)))

        // Rename it.
        clickDesc(str(R.string.group_options))
        clickText(str(R.string.edit_group_name))
        fillDialogField("Renamed")
        clickText(str(R.string.save))
        shot("group-renamed")
        assertTrue("Renamed group card", visible("Renamed"))
        assertTrue("Old name gone", device.wait(Until.gone(By.text("MyGroup")), 5000))

        // Recolor it (visual change, exercise the dialog and persist the pick).
        clickDesc(str(R.string.group_options))
        clickText(str(R.string.edit_group_color))
        assertTrue("Color dialog", visible(str(R.string.edit_group_color)))
        clickDesc("color_2")
        Thread.sleep(500)
        shot("group-recolored")

        // Close the group: its tabs close with it.
        clickDesc(str(R.string.group_options))
        clickText(str(R.string.close_group))
        shot("group-closed")
        assertTrue("Group card gone", device.wait(Until.gone(By.text("Renamed")), 5000))
        assertEquals("Only the fresh tab remains", 1,
            device.findObjects(By.desc(str(R.string.close_tab))).size)
    }

    @Test fun deleteGroupKeepsTabs() {
        launch()
        open("http://localhost:8765")
        open("http://localhost:8765/second")
        tabs()
        clickDesc(str(R.string.tab_options))
        clickText(str(R.string.select_tabs))
        clickDesc(str(R.string.select_tab, "localhost"))
        clickText(str(R.string.new_group_action, 2))
        fillDialogField("KeepTabs")
        clickText(str(R.string.save))
        assertTrue("Group card with name", visible("KeepTabs"))
        clickDesc(str(R.string.group_options))
        clickText(str(R.string.delete_group))
        shot("group-deleted")
        assertTrue("Group card gone", device.wait(Until.gone(By.text("KeepTabs")), 5000))
        // The two tabs fall back to their automatic same-site card.
        assertTrue("Automatic site card back", visible("localhost"))
        assertTrue("Two tabs still there", visible(str(R.string.tab_count, 2)))
    }
}
