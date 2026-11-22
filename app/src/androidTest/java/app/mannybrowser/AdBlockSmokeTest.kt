package app.mannybrowser

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Ad & popup blocker evidence: local fixture pages (ci/fixture.py), seeded
 * filter files (no network dependency), then UI-driven assertions:
 *  - ad request blocked, cosmetic rule hides the ad container
 *  - per-site pause lets the ad through, resume blocks it again
 *  - gestureless popup is swallowed, real tap still opens a tab
 */
@RunWith(AndroidJUnit4::class)
class AdBlockSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun str(id: Int) = context.getString(id)
    private fun shot(name: String) {
        val language = context.resources.configuration.locales[0].language
        val dir = File(context.getExternalFilesDir(null), "manny-evidence").apply { mkdirs() }
        device.takeScreenshot(File(dir, "$language-$name.png"))
    }
    private fun visible(text: String, ms: Long = 20000) = device.wait(Until.hasObject(By.textContains(text)), ms)
    private fun click(text: String) {
        assertTrue("Missing $text", visible(text))
        repeat(3) {
            try { device.findObject(By.textContains(text))?.let { it.click(); device.waitForIdle(); Thread.sleep(350); return } }
            catch (_: androidx.test.uiautomator.StaleObjectException) { }
            Thread.sleep(250)
        }
        throw AssertionError("Could not click $text")
    }
    private fun clickDesc(description: String) {
        assertTrue("Missing control: $description", device.wait(Until.hasObject(By.desc(description)), 15000))
        repeat(3) {
            try { device.findObject(By.desc(description))?.let { it.click(); device.waitForIdle(); Thread.sleep(350); return } }
            catch (_: androidx.test.uiautomator.StaleObjectException) { }
            Thread.sleep(250)
        }
        throw AssertionError("Could not click control: $description")
    }

    /** Seed tiny deterministic filter lists so no network download is needed. */
    private fun seedFilters() {
        val dir = File(context.filesDir, "filters").apply { mkdirs() }
        File(dir, "easylist.txt").writeText("||pagead2.googlesyndication.com^\n##.ad-banner\n")
        File(dir, "easyprivacy.txt").writeText("! fixture\n")
        File(dir, "updated-at").writeText(System.currentTimeMillis().toString())
    }

    private fun launchFresh() {
        context.getSharedPreferences("manny", 0).edit().clear().commit()
        seedFilters()
        var up = false
        repeat(4) {
            if (up) return@repeat
            context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            device.waitForIdle()
            // Emulator flake: Pixel Launcher ANRs on the first launch after install
            // and swallows the intent; dismiss the dialog and retry (BrowserSmokeTest
            // handles the same case).
            if (device.hasObject(By.text("Pixel Launcher isn't responding"))) {
                device.findObject(By.text("Close app"))?.click()
                device.waitForIdle()
                return@repeat
            }
            up = device.wait(Until.hasObject(By.pkg("app.mannybrowser")), 15000)
            if (!up) {
                device.pressHome()
                device.waitForIdle()
            }
        }
        assertTrue("Manny activity not foreground", up)
        Thread.sleep(3000) // filter engine load + page settle
    }

    /** Tab count shown next to the Tabs button (text lives in a child node of the described control). */
    private fun tabCount(): String? {
        val btn = device.wait(Until.findObject(By.desc("Tabs")), 5000) ?: return null
        btn.text?.let { if (it.isNotBlank()) return it }
        val b = btn.visibleBounds
        return device.findObjects(By.text(java.util.regex.Pattern.compile("[0-9]+")))
            .firstOrNull { b.contains(it.visibleCenter.x, it.visibleCenter.y) }?.text
    }

    /** Load [url] until [marker] shows, reloading a few times so a slow first engine compile cannot fail the test. */
    private fun openUntil(url: String, marker: String) {
        repeat(4) {
            open(url)
            if (visible(marker, 8000)) return
            Thread.sleep(2000)
        }
    }

    private fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.waitForIdle()
    }

    @Test
    fun adBlockingCosmeticAndSitePause() {
        launchFresh()
        try {
            openUntil("http://localhost:8765/adblock-test", "AD-BLOCKED")
            assertTrue("blocked ad script must fire onerror", visible("AD-BLOCKED", 3000))
            Thread.sleep(2500) // second cosmetic injection pass
            shot("30-adblock-blocked")

            // Per-site pause: ad script loads after the toggle + reload
            clickDesc("Menu")
            click(str(R.string.ad_blocker_pause_site))
            assertTrue("paused page must load the ad script", visible("AD-LOADED"))
            assertTrue("localhost must be paused", app.mannybrowser.data.Stores(context).blockerPausedSites().contains("localhost"))
            shot("31-adblock-paused")

            // Resume: blocked again
            clickDesc("Menu")
            click(str(R.string.ad_blocker_resume_site))
            assertTrue("resumed page must block again", visible("AD-BLOCKED"))
            shot("32-adblock-resumed")

            // Global toggle off: ad loads
            clickDesc("Menu")
            click(str(R.string.ad_blocker_on))
            assertTrue("disabled blocker must let the ad through", visible("AD-LOADED"))
            assertEquals(false, app.mannybrowser.data.Stores(context).adBlockEnabled)
            shot("33-adblock-disabled")
        } finally { shot("39-adblock-final") }
    }

    @Test
    fun popupBlockingKeepsGestures() {
        launchFresh()
        try {
            open("http://localhost:8765/popup-test")
            assertTrue("popup fixture must load", visible("Popup fixture"))
            Thread.sleep(2500) // give the gestureless window.open its chance
            val before = tabCount()
            assertNotNull("tab count must be readable", before)
            // baseline after open() is the start tab plus the VIEW-intent tab
            assertEquals("gestureless popup must not open a tab", "2", before)
            shot("40-popup-blocked")

            click("Open with gesture")
            Thread.sleep(1500)
            val countAfter = tabCount()
            assertEquals("real tap must open the tab", "3", countAfter)
            shot("41-popup-gesture-allowed")
        } finally { shot("49-popup-final") }
    }
}
