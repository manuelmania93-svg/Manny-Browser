package app.mannybrowser

import app.mannybrowser.data.Session
import app.mannybrowser.data.SessionGroup
import app.mannybrowser.data.SessionTab
import org.junit.Assert.*
import org.junit.Test

class SessionSnapshotTest {

    @Test fun groupMembershipIsRewrittenFromTabIdsToIndices() {
        val s = Session.of(
            tabs = listOf(SessionTab("https://a.example", "A"), SessionTab("https://b.example", "B"), SessionTab(null, "")),
            tabIds = listOf(10L, 20L, 30L),
            groups = listOf(Triple("Work", 2, listOf(20L, 10L))),
            current = 1
        )
        assertEquals(listOf(SessionGroup("Work", 2, listOf(1, 0))), s.groups)
        assertEquals(1, s.current)
    }

    @Test fun membersPointingAtClosedTabsDropOutAndEmptyGroupsVanish() {
        val s = Session.of(
            tabs = listOf(SessionTab("https://a.example", "A")),
            tabIds = listOf(10L),
            groups = listOf(
                Triple("Gone", 0, listOf(99L)),
                Triple("Kept", 1, listOf(99L, 10L))
            ),
            current = 0
        )
        assertEquals(listOf(SessionGroup("Kept", 1, listOf(0))), s.groups)
    }

    @Test fun currentIndexIsClampedIntoRange() {
        val s = Session(
            tabs = listOf(SessionTab("https://a.example", "A"), SessionTab("https://b.example", "B")),
            groups = emptyList(),
            current = 7
        ).normalized()
        assertEquals(1, s.current)
    }

    @Test fun duplicateMembersCollapse() {
        val s = Session(
            tabs = listOf(SessionTab("https://a.example", "A")),
            groups = listOf(SessionGroup("G", 0, listOf(0, 0, 0))),
            current = 0
        ).normalized()
        assertEquals(listOf(0), s.groups[0].members)
    }

    @Test fun startPageTabsSurviveWithNullUrl() {
        val s = Session.of(
            tabs = listOf(SessionTab(null, ""), SessionTab("https://a.example", "A")),
            tabIds = listOf(1L, 2L),
            groups = emptyList(),
            current = 0
        )
        assertNull(s.tabs[0].url)
        assertEquals("https://a.example", s.tabs[1].url)
    }

    @Test fun emptySessionNormalizesToNoSelection() {
        val s = Session(emptyList(), listOf(SessionGroup("G", 0, listOf(3))), 4).normalized()
        assertEquals(-1, s.current)
        assertTrue(s.groups.isEmpty())
    }
}
