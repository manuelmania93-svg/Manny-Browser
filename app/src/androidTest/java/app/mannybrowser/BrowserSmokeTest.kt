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
    private val device = UiDevice.getInstance(instrumentation)
    private fun shot(name: String) {
        val dir = File("/sdcard/manny-evidence").apply { mkdirs() }
        device.takeScreenshot(File(dir, "$name.png"))
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }
    private fun visible(text: String) = device.wait(Until.hasObject(By.textContains(text)), 20000)
    @Test fun startPageTabsMenuAndRealWebPage() {
        val context = instrumentation.targetContext
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue("Manny activity not foreground", device.wait(Until.hasObject(By.pkg("app.mannybrowser")), 15000))
        try {
            shot("01-start")
            assertTrue("No shortcut control", device.wait(Until.hasObject(By.desc("Add shortcut")), 10000))
            device.findObject(By.desc("Tabs")).click()
            assertTrue("Tab overview missing New tab", visible("New tab"))
            shot("02-tabs")
            device.findObject(By.text("New tab")).click()
            device.findObject(By.desc("Menu")).click()
            assertTrue("History action missing", visible("History"))
            assertTrue("Find action missing", visible("Find in page"))
            shot("03-menu")
            device.pressBack()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            assertTrue("WebView did not render Example Domain", visible("Example Domain"))
            shot("04-loaded")
            device.findObject(By.desc("Tabs")).click()
            assertTrue("Loaded page preview missing", device.wait(Until.hasObject(By.desc("Page preview")), 10000))
            shot("06-page-preview")
        } finally { shot("05-final") }
    }
}
