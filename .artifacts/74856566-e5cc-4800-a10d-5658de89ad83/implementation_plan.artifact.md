# Implementation Plan - SOS Notifications for Nearby Cyclists

Add a notification system that alerts nearby cyclists when an SOS signal is sent, ensuring they are notified even if their app is in the background.

## User Review Required

> [!IMPORTANT]
> This implementation relies on the existing `FirestoreNotificationService` (a foreground service) to deliver notifications while the app is in the background. If the user has manually killed the app and the foreground service is stopped by the OS, notifications may not be delivered. A more robust solution would involve Firebase Cloud Functions, but this plan works within the current client-side architecture.

## Proposed Changes

### Core Logic & Utilities

#### [NEW] [NotificationHelper.kt](file:///C:/JONCEL/CAPSTONE PROJECT/SYSTEM/UNZIPPED/8_23_26/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/utils/NotificationHelper.kt)
- Create a utility file to handle the logic of finding nearby cyclists and sending notifications to them via Firestore.
- Include the `haversineKm` distance formula.
- Implement `notifyNearbyCyclists` function.

### Location Tracking

#### [MODIFY] [HomeScreen.kt](file:///C:/JONCEL/CAPSTONE PROJECT/SYSTEM/UNZIPPED/8_23_26/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/HomeScreen.kt)
- Update the `publishLocation` function to include the user's Firebase UID (`userId`) in the `userLocations` collection. This allows other users to target them for notifications without extra lookups.

### SOS Alert Triggers

#### [MODIFY] [Sossheet.kt](file:///C:/JONCEL/CAPSTONE PROJECT/SYSTEM/UNZIPPED/8_23_26/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/Sossheet.kt)
- Call `NotificationHelper.notifyNearbyCyclists` after a manual SOS alert is successfully sent to Firestore.

#### [MODIFY] [FallDetectionService.kt](file:///C:/JONCEL/CAPSTONE PROJECT/SYSTEM/UNZIPPED/8_23_26/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/services/FallDetectionService.kt)
- Call `NotificationHelper.notifyNearbyCyclists` after an automatic fall-detection SOS alert is sent to Firestore.

## Verification Plan

### Automated Tests
- N/A (Manual verification on device/emulator is more appropriate for location-based features).

### Manual Verification
1.  **Preparation**: Deploy the app to two devices/emulators.
2.  **Location Sharing**: Ensure both devices have location sharing enabled and are "near" each other (within 3km) in their simulated locations.
3.  **Background Mode**: Put one device's app in the background.
4.  **Trigger SOS**: Send an SOS alert from the other device.
5.  **Verify Notification**: Confirm that the backgrounded device receives a system notification: "🚨 [Name] needs help! SOS alert nearby."
6.  **Distance Check**: Move one device far away (e.g., > 3km) and trigger SOS again. Verify no notification is received.
