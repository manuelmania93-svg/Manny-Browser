package app.mannybrowser.data

/** One tab as it survives a process restart: where it points and what it was called. */
data class SessionTab(val url: String?, val title: String)

/** A named tab group with members as indices into the session's tab list (ids are per-process). */
data class SessionGroup(val name: String, val colorIndex: Int, val members: List<Int>)

/**
 * The browser's restorable state: open tabs in order, manual tab groups, and the active tab.
 * Pure data + mapping logic so it is unit-testable on the JVM; JSON encoding lives in Stores.
 */
data class Session(
    val tabs: List<SessionTab>,
    val groups: List<SessionGroup>,
    val current: Int
) {
    /** Drops out-of-range members and now-empty groups, and clamps the active index. */
    fun normalized(): Session {
        val groups = groups.map { g -> g.copy(members = g.members.filter { it in tabs.indices }.distinct()) }
            .filter { it.members.isNotEmpty() }
        val current = when {
            tabs.isEmpty() -> -1
            else -> current.coerceIn(0, tabs.size - 1)
        }
        return Session(tabs, groups, current)
    }

    companion object {
        /**
         * Builds a session from live state. Group membership arrives as per-process tab ids and is
         * rewritten to tab indices; members whose tab is gone drop out, empty groups drop out.
         */
        fun of(
            tabs: List<SessionTab>,
            tabIds: List<Long>,
            groups: List<Triple<String, Int, List<Long>>>,
            current: Int
        ): Session {
            val indexOf = HashMap<Long, Int>(tabIds.size)
            tabIds.forEachIndexed { i, id -> indexOf[id] = i }
            val mapped = groups.map { (name, color, members) ->
                SessionGroup(name, color, members.mapNotNull { indexOf[it] })
            }
            return Session(tabs, mapped, current).normalized()
        }
    }
}
