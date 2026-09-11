# Friend Request System Walkthrough

I have implemented a full friend request system that allows users to connect, see each other's online status, and eventually chat.

## Key Features

### 1. Searching and Adding Friends
In the **Messages** screen, there is now an "Add Friend" icon in the top right.
- Clicking it opens a search dialog where you can type a username.
- Matching users appear in a list with an "Add" button.
- Sending a request creates a `friend_requests` document in Firestore and sends a notification to the target user.

### 2. Handling Requests
In the **Notifications** screen, friend requests appear with a distinct icon.
- Users can click **Accept** or **Reject** directly within the notification card.
- **Accepting**: Updates both users' `friends` lists in their Firestore documents.
- **Rejecting**: Simply marks the request as rejected.

### 3. Online Status Visibility
The "Online Now" row in the Message Screen has been updated to be more private.
- It now **only shows people who are in your friend list**.
- This ensures users only see the status of people they have explicitly connected with.

## Implementation Details

### Data Layer
- **`ChatModels.kt`**: Added `FriendRequest` and `User` models.
- **`ChatRepository.kt`**: Added logic for searching users using Firestore string range queries and handling the friend request lifecycle.

### Logic
- **`ChatViewModel.kt`**: Manages the search results and sending requests. It now filters the online users list to include only friends.

### UI
- **`MessageScreen.kt`**: Added the `SearchUserDialog` and the top bar action button.
- **`NotificationScreen.kt`**: Added a new style for friend requests and the interactive action buttons.

## Verification Summary
- **Database**: Verified Firestore collection structure and queries.
- **UI**: Verified layout and button interactions.
- **Privacy**: Confirmed online status filtering logic correctly references the current user's `friends` array.
