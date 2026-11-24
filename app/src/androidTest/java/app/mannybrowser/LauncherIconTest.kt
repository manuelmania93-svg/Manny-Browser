package app.mannybrowser

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.BeforeClass
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun disableSessionPersistence() {
            BrowserState.sessionPersistenceEnabled = false
        }
    }
    @Test fun newIconAppearsInLauncherAndKeepsReleasedSigningIdentity() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val device = UiDevice.getInstance(inst)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = MessageDigest.getInstance("SHA-256").digest(info.signingInfo!!.apkContentsSigners.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("app.mannybrowser", context.packageName)
        assertTrue("release versionName is semantic, was ${info.versionName}",
            (info.versionName ?: "").matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertEquals("824edd962eaea53159fdd906ffac900d71c396c1b0964ee3fd6d8209a1ffc54f", cert)
        device.pressHome()
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 9 / 10,
            device.displayWidth / 2, device.displayHeight / 5, 30)
        val label = device.wait(Until.findObject(By.text("Manny Browser")), 15000)
        assertNotNull("Manny Browser visible in launcher app drawer", label)
        val dir = File(context.getExternalFilesDir(null), "manny-evidence").apply { mkdirs() }
        val lang = context.resources.configuration.locales[0].language
        device.takeScreenshot(File(dir, "$lang-launcher-new-logo.png"))
        device.dumpWindowHierarchy(File(dir, "$lang-launcher-new-logo.xml"))
        label!!.click()
        assertTrue("Launcher opens Manny Browser", device.wait(Until.hasObject(By.pkg(context.packageName)), 15000))
    }
}
