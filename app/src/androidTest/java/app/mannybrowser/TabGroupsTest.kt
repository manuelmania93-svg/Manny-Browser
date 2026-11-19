package app.mannybrowser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TabGroupsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private fun visible(text: String) = device.wait(Until.hasObject(By.text(text)), 15000)
    private fun boundsFromSnapshot(attribute: String, value: String): android.graphics.Rect? {
        val bytes = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(bytes)
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(bytes.toByteArray()))
        val nodes = xml.getElementsByTagName("node")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as org.w3c.dom.Element
            if (node.getAttribute(attribute) == value) {
                val n = Regex("[0-9]+").findAll(node.getAttribute("bounds")).map { it.value.toInt() }.toList()
                if (n.size == 4 && n[2] > n[0] && n[3] > n[1]) return android.graphics.Rect(n[0],n[1],n[2],n[3])
            }
        }
        return null
    }
    private fun click(text: String) {
        assertTrue("Missing $text", visible(text))
        val bounds = boundsFromSnapshot("text", text)
        assertNotNull("Snapshot contains $text", bounds)
        device.click(bounds!!.centerX(), bounds.centerY())
        device.waitForIdle(); Thread.sleep(500)
    }
    private fun closeFirstMember() {
        val bounds = boundsFromSnapshot("content-desc", context.getString(R.string.close_tab))
        if (bounds == null) shot("missing-close")
        assertNotNull("Snapshot contains member close", bounds)
        device.click(bounds!!.centerX(), bounds.centerY())
        device.waitForIdle(); Thread.sleep(500)
    }
    private fun tabs() {
        assertTrue("Tabs button in browser", device.wait(Until.hasObject(By.desc("Tabs")), 15000))
        val bounds = boundsFromSnapshot("content-desc", "Tabs")
        assertNotNull("Tabs snapshot", bounds)
        device.click(bounds!!.centerX(), bounds.centerY())
        device.waitForIdle(); Thread.sleep(750)
    }
    private fun shot(name: String) {
        val dir = File(context.getExternalFilesDir(null), "manny-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-group-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-group-$name.xml"))
    }
    private fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Page loaded for $url", device.wait(Until.hasObject(By.desc("Tabs")), 15000))
        Thread.sleep(1200)
    }
    @Test fun groupingKeepsOrderIdsAndSafeHostBoundaries() {
        fun entry(id: Long, url: String?) = TabGroups.Entry(id, url)
        val g = TabGroups.group(listOf(entry(1, "https://WWW.Example.com/a"), entry(2, null),
            entry(3, "http://example.com/b?q=2"), entry(4, "https://shop.example.com"),
            entry(5, "https://example.com.evil.test"), entry(6, null), entry(7, "file:///tmp/x")))
        assertEquals(6, g.size)
        assertEquals(listOf(1L, 3L), g.first().tabIds)
        assertEquals("example.com", g.first().site)
        assertEquals(listOf(2L), g[1].tabIds)
        assertEquals(listOf(4L), g[2].tabIds)
        assertEquals(listOf(5L), g[3].tabIds)
        assertNull(TabGroups.site("about:blank"))
        assertNull(TabGroups.site("not a URL"))
        assertEquals(3, TabGroups.group(listOf(entry(1,null),entry(2,null),entry(3,null))).size)
        assertEquals(listOf(3L), TabGroups.group(listOf(entry(3,"https://example.com"))).single().tabIds)
        assertTrue(TabGroups.group(emptyList()).isEmpty())
    }
    @Test fun oneSiteCardOpensMembersSwitchesClosesAndBackReturns() {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        open("http://localhost:8765")
        open("http://localhost:8765/second")
        open("http://127.0.0.1:8765")
        tabs()
        assertEquals("One card for both localhost tabs", 1, device.findObjects(By.text("localhost")).size)
        assertTrue("Count visible", visible(context.getString(R.string.tab_count, 2)))
        shot("overview")
        click("localhost")
        assertTrue("Group details has Back to all", visible(context.getString(R.string.all_tabs)))
        assertEquals("One close per member", 2, device.findObjects(By.desc(context.getString(R.string.close_tab))).size)
        shot("members")
        device.pressBack()
        assertTrue("Back returns to grouped overview", visible("localhost"))
        click("localhost")
        val first = boundsFromSnapshot("content-desc", context.getString(R.string.close_tab))
        assertNotNull("Member card bounds available", first)
        // Select the first member body, not its close button.
        device.click((device.displayWidth / 4), first!!.bottom + 60)
        assertTrue("Member selected and overview dismissed", device.wait(Until.gone(By.text(context.getString(R.string.all_tabs))), 5000))
        assertTrue("Browser toolbar visible", device.wait(Until.hasObject(By.desc("Tabs")), 5000))
        tabs(); click("localhost")
        assertTrue("Member detail reopened before close", visible(context.getString(R.string.all_tabs)))
        shot("before-close")
        closeFirstMember()
        shot("immediate-after-close")
        assertTrue("One member left collapses to normal overview", device.wait(Until.gone(By.text(context.getString(R.string.all_tabs))), 5000))
        val hostGone = device.wait(Until.gone(By.text("localhost")), 10000)
        shot("settled-after-close")
        assertTrue("No grouped localhost card after closing member", hostGone)
        shot("after-close")
        click(context.getString(R.string.close_all))
        tabs()
        assertTrue("Fresh tab close button shown", device.wait(Until.hasObject(By.desc(context.getString(R.string.close_tab))), 10000))
        assertEquals("Close all leaves one fresh tab", 1, device.findObjects(By.desc(context.getString(R.string.close_tab))).size)
        shot("close-all")
    }
}
