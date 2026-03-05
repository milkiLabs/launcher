
## 2) Contacts lookup still performs nested queries in batch path (**P1**)
### Evidence
- `getContactsByPhoneNumbers` uses an IN query, but still calls `getPhoneNumbersForContact(contactId)` per contact.

### Impact
- Partial N+1 query behavior remains under larger result sets.

### Fix
- Return sufficient phone fields in one query and group in-memory.

---

## 3) File search logs excessively in normal path (**P2**)
### Evidence
- `FilesRepositoryImpl` emits many debug logs per query/file.

### Impact
- Potential log overhead and noisy diagnostics in production builds.

### Fix
- Gate verbose logs by build type or sampling.
- Keep error logs; reduce per-row debug logs.

---

## 4) `animateScrollToItem(0)` on every results change can feel jumpy (**P2**)
### Evidence
- `SearchResultsList` scrolls to top whenever `results` list changes.

### Impact
- Can interrupt user scanning while typing quickly.

### Fix
- Only auto-scroll when query changes meaningfully or when mode changes.
- Prefer `scrollToItem(0)` for non-animated quick reset in rapid updates.

---

## 5) URL/browser resolution cache may stale after app install/uninstall (**P3**)
### Evidence
- `UrlHandlerResolver` caches browser package set with no invalidation.

### Impact
- Minor mismatch until process restart.

### Fix
- Invalidate cache on package change broadcasts or time-based refresh.

---

## 6) Generic `catch (Exception) -> emptyList()` hides real outages (**P1**)
### Evidence
- Search provider execution and repositories use broad fallback.

### Impact
- Functional bugs appear as "no results" with no telemetry.

### Fix
- Distinguish permission denial, query failure, and provider crash.
- Surface recoverable diagnostics in debug mode and structured logs.
