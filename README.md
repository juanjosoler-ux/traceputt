# TracePutt 1.0 Alpha — PuttVision v9 migration

Rebuild based on the full PuttVision v9 user-provided Kotlin / Compose project, **not** the earlier TracePutt v0.4 proof-of-concept.  Original ZIP is unchanged. The Kotlin namespace remains `com.puttvision` for safe migration, but the app ID is now `com.traceputt.next` and the label is TracePutt. No changes were made to TraceLine.

## Preserved from PuttVision

- CameraX capture, audio impact detection, pose recognition and color marker detector.
- Kotlin/Compose screens: onboarding, live, lab, statistics, history, settings.
- Room storage, session data, CSV and PDF exporters; 120 fps Camera2 experimental path.

## Crucial integrity changes

The legacy PuttVision v9 pipeline had **unvalidated** calculations:

- `tempoRatio` incorrectly labeled stroke-start-to-impact as backswing, used a timer for follow-through. The rebuilt engine deliberately reports tempo as unavailable until genuine backswing/downswing reversal detection exists.
- Face orientation used screen-space green/orange marker angle, not calibrated face-to-target at impact. The rebuilt engine does not publish a numeric impact face angle.
- Path used pixel OLS, with no homography or target line; it is not exposed as a fitted metric.
- Ball optical-flow ROI/fps assumptions were not validated; launch direction is not published.

Experimental algorithms and their source remain available for calibration and validation. The engine now marks these fields as missing rather than inventing precision.

## Build

GitHub Actions uses JDK 17, Gradle 8.2.1 (Android Gradle Plugin 8.2.2), SDK platform 34. Android debug APK has development signing only. Build/test checks must succeed before publishing an APK. The upstream PuttVision zip lacks gradle-wrapper.jar; the Actions workflow uses a managed Gradle distribution.

## Test plan

1. Compile and run unit tests.
2. Test CameraX + microphone simultaneous acquisition on actual Android hardware.
3. Print ArUco calibration markers and establish target-line + intrinsic/extrinsic camera parameters.
4. Record synchronized videos and audio from putts with both markers visible.
5. Benchmark impact/frame synchronization and ball launch against high-speed reference.
6. Only then enable face, path, tempo and fitting recommendations.

**Not a validated fitting device**; do not use its numerical output to make purchasing decisions yet.
