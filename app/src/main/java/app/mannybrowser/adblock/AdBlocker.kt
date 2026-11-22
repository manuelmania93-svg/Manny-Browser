package app.mannybrowser.adblock

/**
 * Compact ABP-style filter engine (EasyList/EasyPrivacy subset).
 *
 * Supported rule forms:
 *  - ||host^            block requests to host (and subdomains)
 *  - ||host^$third-party  block only for third-party requests
 *  - @@||host^          exception (never block)
 *  - ##.class / ##id    generic cosmetic selectors (class/id only)
 *
 * Everything else (substring rules, regex, scriptlets, $removeparam,
 * domain-restricted rules, per-site cosmetic rules) is skipped on purpose:
 * better to under-block than to break pages with a half-correct parser.
 *
 * Pure Kotlin, no Android imports, so the JVM unit tests cover it.
 */
class AdBlocker {

    data class Compiled(
        val blocked: Set<String>,
        val blockedThirdPartyOnly: Set<String>,
        val exceptions: Set<String>,
        val cssSelectors: List<String>,
        val ruleCount: Int,
    )

    @Volatile
    var compiled: Compiled? = null
        private set

    @Volatile
    var blockedCount: Int = 0
        private set

    fun load(listTexts: List<String>) {
        compiled = parse(listTexts)
    }

    fun resetCount() {
        blockedCount = 0
    }

    fun isReady(): Boolean = compiled != null

    fun shouldBlock(pageUrl: String?, requestUrl: String): Boolean {
        val c = compiled ?: return false
        val reqHost = hostOf(requestUrl) ?: return false
        if (matches(c.exceptions, reqHost)) return false
        if (matches(c.blocked, reqHost)) {
            blockedCount++
            return true
        }
        if (matches(c.blockedThirdPartyOnly, reqHost) && isThirdParty(pageUrl?.let { hostOf(it) }, reqHost)) {
            blockedCount++
            return true
        }
        return false
    }

    /** One CSS blob of all generic cosmetic rules, or null when none loaded. */
    fun cosmeticCss(): String? {
        val selectors = compiled?.cssSelectors ?: return null
        if (selectors.isEmpty()) return null
        // Chunk the selector list: one giant selector list is slow to parse and
        // a single bad selector would kill the whole rule.
        return selectors.chunked(200).joinToString("\n") { chunk ->
            chunk.joinToString(",") + "{display:none !important;}"
        }
    }

    /** JS that injects the cosmetic CSS into the current document. */
    fun cosmeticJs(): String? {
        val css = cosmeticCss() ?: return null
        return "(function(){if(document.getElementById('manny-adblock'))return;" +
            "var s=document.createElement('style');s.id='manny-adblock';" +
            "s.textContent=" + jsQuote(css) + ";" +
            "(document.head||document.documentElement).appendChild(s);})();"
    }

    companion object {
        private val GENERIC_SELECTOR = Regex("^[.#][A-Za-z0-9_-]+$")

        fun parse(texts: List<String>): Compiled {
            val blocked = HashSet<String>()
            val blocked3p = HashSet<String>()
            val exceptions = HashSet<String>()
            val css = ArrayList<String>()
            var count = 0
            for (text in texts) {
                for (raw in text.lineSequence()) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue

                    // Generic cosmetic rules (class/id selectors only)
                    val cosmIdx = line.indexOf("##")
                    if (cosmIdx >= 0) {
                        val domains = line.substring(0, cosmIdx)
                        val selector = line.substring(cosmIdx + 2).trim()
                        if (domains.isEmpty() && GENERIC_SELECTOR.matches(selector)) {
                            css.add(selector)
                            count++
                        }
                        continue
                    }
                    // Advanced cosmetic forms we don't support
                    if (line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) continue

                    var rule = line
                    var exception = false
                    if (rule.startsWith("@@")) {
                        exception = true
                        rule = rule.substring(2)
                    }
                    // Host-anchored rules only
                    if (!rule.startsWith("||")) continue
                    rule = rule.substring(2)

                    val optIdx = rule.indexOf('$')
                    val options = if (optIdx >= 0) rule.substring(optIdx + 1).lowercase() else ""
                    val hostPart = if (optIdx >= 0) rule.substring(0, optIdx) else rule

                    // Pure host rules only: no paths, no wildcards
                    if (hostPart.contains('/') || hostPart.contains('*')) continue
                    val cut = hostPart.indexOf('^')
                    var host = (if (cut >= 0) hostPart.substring(0, cut) else hostPart).lowercase()
                    if (host.isEmpty() || !host.contains('.')) continue
                    // domain= page restrictions are not evaluated by this engine
                    if (options.contains("domain=")) continue
                    // first-party-only rules are rare and we don't model them
                    if (options.contains("~third-party")) continue

                    if (exception) {
                        exceptions.add(host)
                    } else if (options.split(',').any { it.trim() == "third-party" }) {
                        blocked3p.add(host)
                    } else {
                        blocked.add(host)
                    }
                    count++
                }
            }
            return Compiled(blocked, blocked3p, exceptions, css, count)
        }

        /** Host suffix match: walks up parent domains (a.b.c -> b.c -> c). */
        fun matches(set: Set<String>, host: String): Boolean {
            var h: String? = host
            while (h != null) {
                if (set.contains(h)) return true
                val dot = h.indexOf('.')
                h = if (dot >= 0) h.substring(dot + 1) else null
            }
            return false
        }

        /** Extract the host from a URL without android.net.Uri (JVM-testable). */
        fun hostOf(url: String?): String? {
            url ?: return null
            val schemeIdx = url.indexOf("://")
            if (schemeIdx < 0) return null
            var rest = url.substring(schemeIdx + 3)
            val atIdx = rest.lastIndexOf('@')
            if (atIdx >= 0) rest = rest.substring(atIdx + 1)
            val endIdx = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            var host = (if (endIdx >= 0) rest.substring(0, endIdx) else rest)
            val portIdx = host.indexOf(':')
            if (portIdx >= 0) host = host.substring(0, portIdx)
            host = host.trimEnd('.').lowercase()
            return host.ifEmpty { null }
        }

        // Common second-level public suffixes; good enough for a third-party heuristic.
        private val SECOND_LEVEL_SUFFIXES = setOf(
            "co.uk", "org.uk", "ac.uk", "co.at", "or.at", "ac.at", "com.au", "net.au",
            "co.jp", "or.jp", "com.br", "com.mx", "com.cn", "com.tr", "co.in", "co.nz"
        )

        /** Registrable-domain guess: last two labels, three for known SLDs. */
        fun registrableDomain(host: String?): String? {
            host ?: return null
            val labels = host.split('.')
            if (labels.size <= 2) return host
            val lastTwo = labels.takeLast(2).joinToString(".")
            return if (lastTwo in SECOND_LEVEL_SUFFIXES && labels.size >= 3) {
                labels.takeLast(3).joinToString(".")
            } else lastTwo
        }

        fun isThirdParty(pageHost: String?, requestHost: String): Boolean {
            pageHost ?: return true
            val pageReg = registrableDomain(pageHost) ?: return true
            val reqReg = registrableDomain(requestHost) ?: return true
            return pageReg != reqReg
        }

        private fun jsQuote(s: String): String {
            val out = StringBuilder(s.length + 2)
            out.append('"')
            for (ch in s) {
                when (ch) {
                    '"' -> out.append("\\\"")
                    '\\' -> out.append("\\\\")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    else -> out.append(ch)
                }
            }
            out.append('"')
            return out.toString()
        }
    }
}
