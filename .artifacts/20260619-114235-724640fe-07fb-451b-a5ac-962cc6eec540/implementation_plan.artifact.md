# Friend Request System Implementation Plan

Implement a friend request system where users can add each other by name, receive notifications, and only chat or see online status if they are friends.

## User Review Required

- **Chat Restriction**: Do you want to hide existing conversations with people who are NOT your friends yet, or just prevent starting NEW ones? (I will assume we only show friends in the online row and search results).
- **Search Method**: Searching by "name" will look at the `username` field in Firestore.

## Proposed Changes

### [Component: Data Models]

#### [ChatModels.kt](file:///C:/JONCEL/CAPSTONE PROJECT/James/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/ChatModels.kt)

- Add `FriendRequest` data class.
- Update `UserPresence` if needed (it already has enough for now).

### [Component: Repository]

#### [ChatRepository.kt](file:///C:/JONCEL/CAPSTONE PROJECT/James/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/ChatRepository.kt)

- Add `searchUsers(query: String)` to find potential friends.
- Add `sendFriendRequest(fromId: String, fromName: String, toId: String)` to create a request in Firestore and a notification.
- Add `respondToFriendRequest(requestId: String, accept: Boolean)` to handle the decision.
- Add `getOnlineFriends(userId: String)` to filter online users to only those in the user's `friends` list.

### [Component: ViewModel]

#### [ChatViewModel.kt](file:///C:/JONCEL/CAPSTONE PROJECT/James/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/ChatViewModel.kt)

- Add `searchResult` StateFlow.
- Add `sendFriendRequest` method.
- Update `onlineUsers` logic to fetch only friends.

### [Component: UI]

#### [MessageScreen.kt](file:///C:/JONCEL/CAPSTONE PROJECT/James/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/MessageScreen.kt)

- Add "Add Friend" button to `TopAppBar`.
- Create a `SearchUserDialog` to search and send requests.
- Filter the `LazyColumn` and `OnlineRidersRow` based on friendship.

#### [NotificationScreen.kt](file:///C:/JONCEL/CAPSTONE PROJECT/James/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/NotificationScreen.kt)

- Add `friend_request` type to `notifStyle`.
- Implement "Accept" and "Reject" button logic for friend requests.
- When "Accept" is clicked, update Firestore `users` documents to add to `friends` array.

## Verification Plan

### Automated Tests
- I will verify the Firestore queries via code analysis.

### Manual Verification
1.  **Search**: Open Message Screen, click "Add Friend", type a name, verify results appear.
2.  **Send**: Click "Add" on a user, verify a `friend_requests` document is created in Firestore.
3.  **Notification**: Check Notifications screen as the recipient, verify "Accept/Reject" buttons appear.
4.  **Accept**: Click "Accept", verify both users have each other in their `friends` list.
5.  **Visibility**: Verify the new friend appears in the "Online Now" row in Message Screen.
