[2026-01-06 21:04] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "lazy loading buffer",
    "EXPECTATION": "User wants smoother fast scrolling by beginning loads earlier and keeping a larger preloaded buffer.",
    "NEW INSTRUCTION": "WHEN implementing lazy-loading or infinite scroll THEN preload earlier and expand the buffer size"
}

[2026-01-06 22:21] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "UI alignment",
    "EXPECTATION": "The login button should be vertically centered within its container/page.",
    "NEW INSTRUCTION": "WHEN designing login screens THEN vertically center the primary login button"
}

[2026-01-09 13:25] - Updated by Junie
{
    "TYPE": "correction",
    "CATEGORY": "NotFound handling",
    "EXPECTATION": "Service/repository should use nullable returns for missing resources; avoid throwing NotFound internally.",
    "NEW INSTRUCTION": "WHEN resource lookup may fail THEN return null; map NotFound in routes"
}

