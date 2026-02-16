# Contacts Search Optimization

## Overview

This document describes the performance optimizations implemented in the contacts search feature. The search uses a **single JOIN query** approach combined with **priority-based sorting** to provide fast and relevant results.

---

## The Problem: N+1 Query Issue

### Original Implementation

The original implementation suffered from the classic **N+1 query problem**:

```
1 query: Find contacts matching search query
N queries: Get phone numbers for each contact (1 per contact)
N queries: Get email addresses for each contact (1 per contact)
---------------------------------------------------
Total: 2N + 1 queries
```

### Example Impact

For a search returning 50 contacts:
- **Original**: 1 + 50 + 50 = **101 database queries**
- **Optimized**: **1 database query**

This resulted in slow search times (200-500ms) especially for users with many contacts.

---

## The Solution: Single JOIN Query

### How It Works

Instead of querying multiple tables separately, we use Android's `ContactsContract.Data` table which automatically joins:
- Contacts table (basic contact info)
- Data table (phone numbers, emails)
- Mimetypes table (data type definitions)

### Query Structure

```kotlin
// Query the Data table with a selection that filters:
// 1. Display name matches search query (LIKE %query%)
// 2. Mimetype is Phone, Email, or StructuredName

val selection = """
    ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? 
    AND ${ContactsContract.Data.MIMETYPE} IN (?, ?, ?)
"""

val selectionArgs = arrayOf(
    "%$queryLower%",
    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
)
```

### Result Grouping

The Data table returns **one row per data item** (not per contact). For example:

| CONTACT_ID | DISPLAY_NAME | MIMETYPE | PHONE_NUMBER | EMAIL_ADDRESS |
|------------|--------------|----------|--------------|---------------|
| 1 | John Doe | Phone | +1234567890 | NULL |
| 1 | John Doe | Email | NULL | john@email.com |
| 2 | Jane Smith | StructuredName | NULL | NULL |
| 2 | Jane Smith | Phone | +9876543210 | NULL |

We group these rows by `CONTACT_ID` in Kotlin code:

```kotlin
// Map to accumulate contact data
val contactsMap = mutableMapOf<Long, ContactBuilder>()

while (cursor.moveToNext()) {
    val contactId = cursor.getLong(contactIdIndex)
    
    // Get or create a builder for this contact
    val builder = contactsMap.getOrPut(contactId) {
        ContactBuilder(
            id = contactId,
            displayName = cursor.getString(displayNameIndex),
            photoUri = cursor.getString(photoUriIndex),
            lookupKey = cursor.getString(lookupKeyIndex)
        )
    }
    
    // Add phone/email based on mimetype
    when (cursor.getString(mimetypeIndex)) {
        Phone.CONTENT_ITEM_TYPE -> builder.phoneNumbers.add(phone)
        Email.CONTENT_ITEM_TYPE -> builder.emails.add(email)
    }
}
```

---

## Priority-Based Sorting

### Match Types

To provide the best user experience, results are sorted by match quality (same algorithm as app search):

| Priority | Match Type | Description | Example |
|----------|------------|-------------|---------|
| 1 | EXACT | Name equals query exactly | Query: "John" → "John" |
| 2 | STARTS_WITH | Name starts with query | Query: "Jo" → "John", "Joseph" |
| 3 | CONTAINS | Name contains query anywhere | Query: "oh" → "John", "Rohan" |

### Implementation

```kotlin
private enum class MatchType {
    EXACT,       // Highest priority
    STARTS_WITH,
    CONTAINS     // Lowest priority
}

private fun getMatchType(displayName: String, queryLower: String): MatchType {
    val nameLower = displayName.lowercase()
    
    return when {
        nameLower == queryLower -> MatchType.EXACT
        nameLower.startsWith(queryLower) -> MatchType.STARTS_WITH
        else -> MatchType.CONTAINS
    }
}
```

### Sorting Results

```kotlin
// Sort by match priority, then take top 50
return unsortedContacts
    .sortedBy { contact ->
        getMatchType(contact.displayName, queryLower).ordinal
    }
    .take(50)
```

---

## Performance Limits

### Why LIMIT 150 then take 50?

We fetch 150 rows from the database, then take 50 after sorting:

1. **SQL LIMIT 150**: Prevents too much data from being loaded
2. **Kotlin take(50)**: Returns only top 50 after priority sorting

This ensures:
- We have enough results to sort properly
- Memory usage stays reasonable
- UI stays responsive

### Why Include StructuredName Mimetype?

The StructuredName mimetype is included in the query to capture contacts that have **no phone numbers or emails**. Without this, such contacts would be excluded from search results.

---

## ContactBuilder Pattern

### Why a Builder?

Since the Data table returns multiple rows per contact, we need to **accumulate data** before creating the final `Contact` object.

```kotlin
private data class ContactBuilder(
    val id: Long,
    val displayName: String,
    val photoUri: String?,
    val lookupKey: String
) {
    // Mutable lists to accumulate data
    val phoneNumbers: MutableList<String> = mutableListOf()
    val emails: MutableList<String> = mutableListOf()
    
    // Build immutable Contact at the end
    fun build(): Contact = Contact(
        id = id,
        displayName = displayName,
        phoneNumbers = phoneNumbers.toList(),
        emails = emails.toList(),
        photoUri = photoUri,
        lookupKey = lookupKey
    )
}
```

---

## Performance Comparison

| Metric | Before | After |
|--------|--------|-------|
| Queries per search | 2N + 1 | 1 |
| 50 contacts search | 101 queries | 1 query |
| Estimated time | 200-500ms | 20-50ms |
| Result sorting | Alphabetical | Priority-based |

---

## Files Modified

- `ContactsRepositoryImpl.kt` - Complete rewrite of `searchContacts()` method

---

## Related Documentation

- [Architecture.md](Architecture.md) - Overall app architecture
- [multi-mode-search.md](multi-mode-search.md) - Multi-mode search feature
