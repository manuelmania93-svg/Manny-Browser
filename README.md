# Manny Browser 0.1 development

Fresh Kotlin/Compose interface around Android WebView. Work in progress, not a release. Separate branch, no merge into the Fulguris baseline.

Builds are debug-signed GitHub Actions artifacts. APK identity and actual UI still need validation before handoff. No extension support or full browser-engine parity is claimed.

Implemented surface: start-page shortcuts, URL/search entry, tabs, bookmarks, history, DownloadManager integration, share, find, dark mode and fullscreen. Each feature requires runtime testing. Downloads on authenticated sites and long-lived tab restoration remain unverified.

See THIRD_PARTY_NOTICES.md for reused build components and libraries.

Tab previews are temporary in-memory bitmaps, never written to storage. Closing a tab or all tabs drops their preview references; no incognito mode is claimed in this version. CI screenshots use only synthetic/public test pages, not personal browsing.
