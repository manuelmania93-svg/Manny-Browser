package app.mannybrowser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    private val sampleList = """
        [Adblock Plus 2.0]
        ! Comment line
        ||doubleclick.net^
        ||ads.example.com^$third-party
        ||restricted-ads.com^$third-party,domain=foo.com
        ||pagead2.googlesyndication.com^
        ||tracker.io^$script,third-party
        ##.ad-banner
        ##ad_container
        ##.complex > .selector
        example.com##.site-specific
        ||example.com/path/only.js
        ||wild*.example.org^
        /substring/rule/
        @@||whitelisted-ads.com^
    """.trimIndent()

    private fun engine(): AdBlocker = AdBlocker().apply { load(listOf(sampleList)) }

    @Test
    fun hostRulesBlockAndCount() {
        val e = engine()
        assertTrue(e.isReady())
        assertTrue(e.shouldBlock("https://www.somepage.com/", "https://doubleclick.net/ad.js"))
        assertTrue(e.shouldBlock("https://www.somepage.com/", "https://sub.doubleclick.net/ad.js"))
        assertEquals(2, e.blockedCount)
    }

    @Test
    fun unlistedHostsPass() {
        val e = engine()
        assertFalse(e.shouldBlock("https://www.somepage.com/", "https://www.somepage.com/app.js"))
        assertFalse(e.shouldBlock("https://www.somepage.com/", "https://cdn.wikipedia.org/w.js"))
        assertEquals(0, e.blockedCount)
    }

    @Test
    fun thirdPartyOptionHonored() {
        val e = engine()
        // tracker.io is third-party-only: blocked from a foreign page, allowed first-party
        assertTrue(e.shouldBlock("https://www.somepage.com/", "https://tracker.io/t.js"))
        assertFalse(e.shouldBlock("https://tracker.io/", "https://tracker.io/t.js"))
        // doubleclick.net has no third-party restriction: blocked even first-party
        assertTrue(e.shouldBlock("https://doubleclick.net/", "https://doubleclick.net/ad.js"))
    }

    @Test
    fun exceptionsWin() {
        val e = engine()
        assertFalse(e.shouldBlock("https://www.somepage.com/", "https://whitelisted-ads.com/a.js"))
    }

    @Test
    fun unsupportedRuleFormsSkipped() {
        val e = engine()
        val c = e.compiled!!
        // path rules, wildcard rules and substring rules must not be in the host sets
        assertFalse(c.blocked.contains("example.com"))
        assertFalse(c.blocked.contains("example.org"))
        // domain-restricted rules are skipped entirely (we don't model domain=)
        assertFalse(c.blockedThirdPartyOnly.contains("restricted-ads.com"))
        assertFalse(c.blocked.contains("restricted-ads.com"))
        assertTrue(c.blockedThirdPartyOnly.contains("ads.example.com"))
    }

    @Test
    fun cosmeticSelectorsFiltered() {
        val e = engine()
        val c = e.compiled!!
        assertTrue(c.cssSelectors.contains(".ad-banner"))
        assertTrue(c.cssSelectors.contains("#ad_container"))
        // complex and site-specific selectors are skipped
        assertFalse(c.cssSelectors.any { it.contains('>') })
        assertFalse(c.cssSelectors.any { it.contains("site-specific") })
        val js = e.cosmeticJs()
        assertNotNull(js)
        assertTrue(js!!.contains("display:none !important"))
    }

    @Test
    fun emptyEngineBlocksNothing() {
        val e = AdBlocker()
        assertFalse(e.shouldBlock("https://a.com/", "https://doubleclick.net/x"))
        assertNull(e.cosmeticJs())
    }

    @Test
    fun hostParsing() {
        assertEquals("example.com", AdBlocker.hostOf("https://example.com/path?q=1"))
        assertEquals("sub.example.com", AdBlocker.hostOf("https://user@sub.example.com:8443/x"))
        assertEquals("example.com", AdBlocker.hostOf("https://example.com./"))
        assertNull(AdBlocker.hostOf("about:blank"))
        assertNull(AdBlocker.hostOf(null))
    }

    @Test
    fun thirdPartyDetection() {
        assertTrue(AdBlocker.isThirdParty("example.com", "ads.other.com"))
        assertFalse(AdBlocker.isThirdParty("www.example.com", "cdn.example.com"))
        assertFalse(AdBlocker.isThirdParty("example.co.uk", "cdn.example.co.uk"))
        assertTrue(AdBlocker.isThirdParty(null, "anything.com"))
    }

    @Test
    fun realEasyListHeaderParses() {
        // Real-world EasyList header + typical lines must not confuse the parser
        val realistic = """
            [Adblock Plus 2.0]
            ! Version: 202611200001
            ! Title: EasyList
            ! Expires: 4 days
            ||0-9wm.com^
            ||001.0x1f4b0.com^
            ||032.cfd^
            ||adnxs.com^
            @@||imasdk.googleapis.com/js/sdkloader/ima3.js
            ##.text-ad
        """.trimIndent()
        val e = AdBlocker()
        e.load(listOf(realistic))
        assertTrue(e.shouldBlock("https://news.site/", "https://adnxs.com/px"))
        assertTrue(e.shouldBlock("https://news.site/", "https://v.032.cfd/x"))
        assertFalse(e.shouldBlock("https://news.site/", "https://imasdk.googleapis.com/js/sdkloader/ima3.js"))
    }
}
