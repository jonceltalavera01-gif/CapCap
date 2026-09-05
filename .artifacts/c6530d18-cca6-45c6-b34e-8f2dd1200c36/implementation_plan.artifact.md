# Dynamic Polyline Trimming for Navigation

Implement dynamic polyline trimming where passed route segments are removed from the map in real-time as the user moves. This applies to both SOS responder navigation and POI navigation.

## User Review Required

> [!IMPORTANT]
> The arrival detection radius will be increased from 25m to 40m to match the user's request (30-50m range). This will trigger the "Arrived" message and clear the route slightly sooner.

> [!NOTE]
> We will leverage the existing `matchBreadcrumbProgress` algorithm to ensure smooth and forward-only progress matching, preventing the polyline from "jumping back" due to GPS jitter.

## Proposed Changes

### UI & Navigation Logic

#### [MODIFY] [HomeScreen.kt](file:///C:/JONCEL/CAPSTONE PROJECT/SYSTEM/UNZIPPED/9_4_26/CapCap-main/app/src/main/java/com/darkhorses/PedalConnect/ui/theme/HomeScreen.kt)
- Add `navigationLastMatchedIdx` state variable to track progress during turn-by-turn navigation.
- Initialize `navigationLastMatchedIdx = 0` in `fetchRoutes` success, `onImOnMyWay` success, and `startBreadcrumbRoute`.
- Update the `LocationListener` in `onLocationChanged` to:
    - Perform fine-grained matching of the current location against active route points using `matchBreadcrumbProgress`.
    - Trim the active `Polyline` by removing passed segments using `subList(matchedIdx, points.size)`.
    - Call `mapView.invalidate()` for smooth redraw.
- Increase `ARRIVAL_RADIUS_M` to `40.0`.

## Verification Plan

### Manual Verification
1. **Scenario 1 (SOS):**
    - Tap "Respond" to an alert.
    - Verify route is drawn.
    - Simulate movement along the route (or test live).
    - Observe that the polyline disappears behind the user.
    - Verify "Arrived" message appears and polyline clears when reaching the destination.
2. **Scenario 2 (POI):**
    - Search for a POI and tap "Navigate".
    - Verify the same trimming behavior.
    - Verify "Arrived" message at destination.
3. **Smoothness:**
    - Verify no flickering or full map redraws during trimming.
