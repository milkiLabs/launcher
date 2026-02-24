# Direct Call and Recent Contacts Feature

This document explains the implementation of two related features:
1. **Direct Call** - Tapping the dial icon on a contact to make a direct call
2. **Recent Contacts** - Showing recently called contacts when using the "c" prefix without a query

## Overview

### Feature 1: Direct Call from Dial Icon

When the user searches for contacts using the "c" prefix, each contact result shows:
- A **dial icon** (phone icon) - Tapping this makes a DIRECT call (requires CALL_PHONE permission)
- The **contact item itself** - Tapping this opens the dialer with the number pre-filled

This separation allows users to:
- Quickly call frequently contacted people with one tap (dial icon)
- Preview the number before calling (tap the item to open dialer)

### Feature 2: Recent Contacts on Empty Query

When the user types "c " (c followed by space) without a search query:
- Shows the 8 most recently called contacts
- Similar to how recent apps are shown on empty search
- Provides quick access to frequently called contacts

## Architecture

### Data Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           User Interface                                 │
│                                                                          │
│  ┌──────────────────────┐     ┌──────────────────────┐                 │
│  │   Contact Item       │     │    Dial Icon         │                 │
│  │   (tap → dialer)     │     │  (tap → direct call) │                 │
│  └──────────┬───────────┘     └──────────┬───────────┘                 │
│             │                            │                              │
│             │ onClick                    │ onDialClick                  │
│             ▼                            ▼                              │
│  ┌──────────────────────────────────────────────────────────┐          │
│  │                      SearchViewModel                      │          │
│  │                                                           │          │
│  │  - onResultClick() → CallContact (dialer)                │          │
│  │  - onDialClick() → check permission                      │          │
│  │      - If granted → CallContactDirect                    │          │
│  │      - If not → store pending, request permission        │          │
│  └──────────────────────────────────────────────────────────┘          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Permission Flow for Direct Call

```
┌─────────────────┐
│ User taps dial  │
│     icon        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     Yes      ┌─────────────────┐
│ Has CALL_PHONE  │─────────────►│ Make direct call│
│  permission?    │              │ (ACTION_CALL)   │
└────────┬────────┘              └─────────────────┘
         │ No
         ▼
┌─────────────────┐
│ Store pending   │
│     call        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Request CALL_   │
│ PHONE permission│
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│           Permission Result              │
│                                         │
│  ┌──────────────┐      ┌──────────────┐ │
│  │   Granted    │      │    Denied    │ │
│  │              │      │              │ │
│  │ Execute      │      │ Clear pending│ │
│  │ pending call │      │    call      │ │
│  └──────────────┘      └──────────────┘ │
└─────────────────────────────────────────┘
```

## Key Files

### Permission Handling

| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | Declares `CALL_PHONE` permission |
| `PermissionHandler.kt` | Handles CALL_PHONE permission requests |
| `SearchUiState.kt` | Contains `hasCallPermission` and `pendingDirectCall` state |

### Actions

| File | Purpose |
|------|---------|
| `SearchAction.kt` | Defines `CallContact`, `CallContactDirect`, `RequestCallPermission` actions |
| `ActionHandler.kt` | Executes call actions (ACTION_DIAL vs ACTION_CALL) |

### UI Components

| File | Purpose |
|------|---------|
| `SearchResultItems.kt` | `ContactSearchResultItem` with separate `onDialClick` callback |
| `SearchResultsList.kt` | Passes `onDialClick` through the component hierarchy |
| `AppSearchDialog.kt` | Top-level dialog that receives `onDialClick` |
| `LauncherScreen.kt` | Connects ViewModel's `onDialClick` to the dialog |
| `MainActivity.kt` | Handles `RequestCallPermission` action |

### Data Layer

| File | Purpose |
|------|---------|
| `ContactsRepository.kt` | Interface with `saveRecentContact()`, `getRecentContacts()`, `getContactByPhoneNumber()` |
| `ContactsRepositoryImpl.kt` | Implementation using DataStore for recent contacts |
| `ContactsSearchProvider.kt` | Returns recent contacts on empty query |

## Implementation Details

### 1. Storing Recent Contacts

Recent contacts are stored in DataStore as a comma-separated string of phone numbers:

```kotlin
// Storage format: "1234567890,0987654321,..."
private val recentContactsKey = stringPreferencesKey("recent_contacts")
```

**Why store phone numbers only?**
- Minimal storage
- Contact names can be looked up from the contacts provider
- Phone number is all that's needed to make a call

**Limit: 8 contacts**
- Matches the recent apps limit
- Provides enough for quick access
- Keeps the list manageable

### 2. Looking Up Contact Info

When displaying recent contacts, we look up the contact name:

```kotlin
suspend fun getContactByPhoneNumber(phoneNumber: String): Contact?
```

This queries the `ContactsContract.CommonDataKinds.Phone` table to find a matching contact.

**Fallback behavior:**
If a phone number is in recent contacts but no longer matches a contact (deleted), we display just the phone number.

### 3. Two Click Actions on Contacts

The `ContactSearchResultItem` composable accepts two callbacks:

```kotlin
@Composable
fun ContactSearchResultItem(
    result: ContactSearchResult,
    accentColor: Color?,
    onClick: () -> Unit,           // Opens dialer
    onDialClick: (() -> Unit)?     // Makes direct call
)
```

The dial icon is a separate clickable area with a larger touch target (32dp) for better accessibility.

### 4. Permission Request Timing

CALL_PHONE permission is requested **only when needed**:
- User must first tap the dial icon
- If permission not granted, request dialog appears
- Permission result triggers the pending call

This is better than requesting permission upfront because:
- User understands why permission is needed (they just tried to call)
- Permission is only requested for users who need it
- Follows Android best practices for runtime permissions

## Usage Examples

### Direct Call Flow

1. User types "c john" to search contacts
2. Contact result appears with dial icon
3. User taps dial icon
4. If permission granted → call is made directly
5. If permission not granted → permission dialog appears
6. Phone number is saved to recent contacts

### Recent Contacts Flow

1. User types "c " (c followed by space)
2. Recent contacts list appears (up to 8)
3. User can tap item (dialer) or dial icon (direct call)
4. Calling updates the recent contacts order

## Security Considerations

- `CALL_PHONE` is a dangerous permission that must be granted at runtime
- We use `ACTION_CALL` for direct calls (requires permission)
- We use `ACTION_DIAL` for opening dialer (no permission needed)
- The app never makes calls without user interaction
- Phone numbers are stored locally on device only

## Future Improvements

1. **Call log sync**: Instead of (or in addition to) manual recent tracking, read from the system call log (requires `READ_CALL_LOG` permission)

2. **Frequent contacts**: Show contacts called most often, not just most recently

3. **Contact photos**: Display contact photos in recent contacts list

4. **Multiple numbers**: Allow selecting which number to call for contacts with multiple phones
