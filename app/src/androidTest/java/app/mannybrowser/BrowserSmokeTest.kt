package app.mannybrowser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun str(id: Int) = context.getString(id)
    private fun shot(name: String) {
        val language = context.resources.configuration.locales[0].language
        val dir = File(context.getExternalFilesDir(null), "manny-evidence").apply { mkdirs() }
        device.takeScreenshot(File(dir, "$language-$name.png"))
        device.dumpWindowHierarchy(File(dir, "$language-$name.xml"))
    }
    private fun visible(text: String) = device.wait(Until.hasObject(By.textContains(text)), 20000)
    private fun click(text: String) { assertTrue("Missing $text", visible(text)); device.findObject(By.text(text)).click(); device.waitForIdle() }
    private fun clickDesc(description: String) {
        val target = device.wait(Until.findObject(By.desc(description)), 10000)
        assertNotNull("Missing control: $description", target)
        target!!.click()
        device.waitForIdle()
    }
    private fun launch() {
        context.getSharedPreferences("manny",0).edit().clear().commit()
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        device.waitForIdle()
        shot("00-launch-diagnostic")
        if (device.hasObject(By.text("Pixel Launcher isn't responding"))) {
            device.findObject(By.text("Close app"))?.click()
            device.waitForIdle()
        }
        assertTrue("Manny activity not foreground", device.wait(Until.hasObject(By.pkg("app.mannybrowser")), 15000))
    }
    @Test fun shortcutsFormsAndPersistence() {
        context.getSharedPreferences("manny",0).edit().clear().commit()
        launch()
        try {
            assertTrue(device.wait(Until.hasObject(By.desc(str(R.string.add_shortcut))),10000))
            clickDesc(str(R.string.add_shortcut))
            assertTrue(visible(str(R.string.name_label)))
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            fields[fields.size-2].text = "Example"
            fields.last().text = "https://example.com"
            shot("01-shortcut-form")
            click(str(R.string.save))
            assertTrue("Shortcut not shown", visible("Example"))
            assertEquals("Shortcut not persisted", "https://example.com", app.mannybrowser.data.Stores(context).shortcuts().single().url)
            device.findObject(By.text("Example")).longClick()
            assertTrue(visible(str(R.string.edit_shortcut)))
            val edit = device.findObjects(By.clazz("android.widget.EditText"))
            edit[edit.size-2].text = "Example edited"
            click(str(R.string.save))
            val editDeadline = System.currentTimeMillis() + 5000
            while (app.mannybrowser.data.Stores(context).shortcuts().single().name != "Example edited" && System.currentTimeMillis() < editDeadline) Thread.sleep(50)
            assertEquals("Edit not persisted", "Example edited", app.mannybrowser.data.Stores(context).shortcuts().single().name)
            context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue("Saved shortcut absent after activity restart", visible("Example edited"))
            shot("02-populated-start")
        } finally { shot("03-shortcut-final") }
    }
    @Test fun navigationAndDownload() {
        launch()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8765"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Fixture not loaded", visible("Manny fixture"))
            click("Second page")
            assertTrue("Second page not loaded", visible("Second fixture"))
            clickDesc("Back")
            assertTrue("Back did not return", visible("Manny fixture"))
            clickDesc("Forward")
            assertTrue("Forward did not return", visible("Second fixture"))
            click("Download fixture")
            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var downloaded = false
            val until = System.currentTimeMillis() + 15000
            while (!downloaded && System.currentTimeMillis() < until) {
                dm.query(android.app.DownloadManager.Query()).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TITLE)).startsWith("manny-test") && cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)) == android.app.DownloadManager.STATUS_SUCCESSFUL) downloaded = true
                    }
                }
                if (!downloaded) Thread.sleep(500)
            }
            assertTrue("DownloadManager did not complete fixture", downloaded)
            clickDesc("Menu")
            click(str(R.string.downloads))
            assertTrue("Completed download missing in app", visible("manny-test"))
            shot("15-download-complete")
        } finally { shot("16-navigation-final") }
    }
    @Test fun pageBookmarksHistoryFindThemeFullscreenAndTabs() {
        launch()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("WebView did not render Example Domain", visible("Example Domain"))
            shot("04-loaded")
            // Top star toggles current page, bottom star opens list.
            device.findObjects(By.desc(str(R.string.bookmarks))).first().click()
            assertTrue("Bookmark was not persisted", app.mannybrowser.data.Stores(context).bookmarks().any { it.url.startsWith("https://example.com") })
            device.findObjects(By.desc(str(R.string.bookmarks))).last().click()
            assertTrue("Bookmark list missing page", visible("Example Domain"))
            shot("05-bookmarks")
            device.pressBack()
            clickDesc("Menu")
            shot("06-menu")
            click(str(R.string.history))
            assertTrue("History missing page", visible("example.com"))
            shot("07-history")
            device.pressBack()
            clickDesc("Menu")
            click(str(R.string.find_in_page))
            val fields = device.findObjects(By.clazz("android.widget.EditText"))
            fields.last().text = "domain"
            shot("08-find")
            device.pressBack()
            clickDesc("Menu")
            click(str(R.string.dark_mode))
            assertTrue("Dark-mode override missing", context.getSharedPreferences("manny",0).contains("dark"))
            shot("09-theme")
            clickDesc("Menu")
            click(str(R.string.fullscreen))
            assertTrue("Fullscreen controls still visible", device.wait(Until.gone(By.desc("Menu")), 5000))
            shot("10-fullscreen")
            // Android first-use immersive tutorial intercepts Back. Dismiss only that system overlay.
            val gotIt = device.wait(Until.findObject(By.res("android", "ok")), 2500)
            if (gotIt != null) gotIt.click()
            else device.wait(Until.findObject(By.text("Got it")), 1000)?.click()
            device.waitForIdle()
            device.pressBack()
            assertTrue("Could not exit fullscreen", device.wait(Until.hasObject(By.desc("Menu")), 5000))
            clickDesc("Menu")
            click(str(R.string.downloads))
            assertTrue("Download screen absent", visible(str(R.string.downloads)))
            shot("11-downloads-empty")
            device.pressBack()
            clickDesc("Menu")
            click(str(R.string.share))
            assertTrue("Share chooser not opened", device.wait(Until.hasObject(By.pkg("com.android.intentresolver")), 5000))
            shot("12-share-chooser")
            device.pressBack()
            clickDesc("Tabs")
            assertTrue("Loaded page preview missing", device.wait(Until.hasObject(By.desc("Page preview")), 10000))
            shot("13-page-preview")
            click(str(R.string.new_tab))
            assertTrue(device.wait(Until.hasObject(By.desc(str(R.string.add_shortcut))),5000))
        } finally { shot("14-final") }
    }
    @Test fun fullscreenBackStandalone() {
        launch()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8765"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Fixture not loaded", visible("Manny fixture"))
            clickDesc("Menu")
            click(str(R.string.fullscreen))
            assertTrue("Fullscreen controls still visible", device.wait(Until.gone(By.desc("Menu")), 5000))
            val gotIt = device.wait(Until.findObject(By.res("android", "ok")), 2500)
            if (gotIt != null) gotIt.click()
            else device.wait(Until.findObject(By.text("Got it")), 1000)?.click()
            device.waitForIdle()
            shot("20-fullscreen-standalone")
            device.pressBack()
            assertTrue("Back did not exit fullscreen", device.wait(Until.hasObject(By.desc("Menu")), 10000))
            shot("21-fullscreen-exited")
        } finally { shot("22-fullscreen-final") }
    }
    @Test fun shareAndTabPreviewStandalone() {
        launch()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:8765"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("Fixture not loaded", visible("Manny fixture"))
            clickDesc("Menu")
            click(str(R.string.share))
            assertTrue("Share chooser absent", device.wait(Until.hasObject(By.pkg("com.android.intentresolver")), 10000))
            shot("23-share-standalone")
            device.pressBack()
            clickDesc("Tabs")
            assertTrue("Actual page preview absent", device.wait(Until.hasObject(By.desc("Page preview")), 10000))
            shot("24-tabs-standalone")
            click(str(R.string.new_tab))
            assertTrue("New tab start page absent", device.wait(Until.hasObject(By.desc(str(R.string.add_shortcut))),10000))
        } finally { shot("25-share-tabs-final") }
    }

}
