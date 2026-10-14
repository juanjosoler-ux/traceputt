# TracePutt Lab Android v0.4.0-alpha

Standalone native Android prototype. **Independent of TraceLine.** Processes short imported or camera-recorded videos locally, tracks a bright magenta marker fixed near the putter head, draws an apparent pixel-space trajectory and exports measurement samples as JSON.

This is an EXPERIMENTAL tracker: no impact, face angle, physical millimetres, fitting, or putter recommendations. No network permission. Place the camera outside the golfer's line of sight and the stroke.

## Building
GitHub Actions installs Android SDK 35 and Gradle 8.9, builds a debug APK, verifies its signature and ZIP integrity, uploads the artifact and publishes a prerelease. Requires Actions and write permissions enabled.

Manual build: Gradle 8.9, JDK 17 and SDK 35; run `gradle :app:assembleDebug`. Debug builds are not intended for commercial distribution.

## Limitations
Uses `MediaMetadataRetriever` at 10 samples/sec and downscales videos. Requires a colored marker and good lighting. Output units are pixels of the video, not mm. 8-second windows are selectable. This is a technical alpha, not a validated professional instrument.