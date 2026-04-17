# Launch War Plan — GPX Video Producer

Version assessed: `0.1.0` (versionCode `1`) · Target: Google Play public release

This document is a practical, prioritized checklist for taking the app from
its current internal build to a store-ready 1.0 release. It covers
functionality, security, UI, privacy, performance, and rollout.

See also [`PRODUCT_STRATEGY.md`](./PRODUCT_STRATEGY.md) for the post-1.0
roadmap (accounts, cloud templates, monetization).

Priorities:
- 🔴 **P0 — must fix before any release** (blocker, legal, security, crash)
- 🟠 **P1 — should ship with 1.0** (user-visible quality, store-required)
- 🟡 **P2 — nice to have / fast-follow**

Status legend: ✅ done · ☐ open

---

## 1. Functionality Review

### What works well
- The `core/overlay-renderer` (SVG) pipeline is shared by both preview and
  export — WYSIWYG parity is structurally guaranteed.
- Media3 Transformer is the path forward; the legacy `lib/ffmpeg` module has
  been removed.
- The template system (`metric_N_value/label/unit`, chart/route slots,
  per-template fonts) is clean and extensible.
- Capability detection (`hasChartSlot` / `hasRouteMapSlot`) auto-disables
  chart/route settings per template.
- GPX/TCX parser is self-contained and unit-tested.

### Gaps / risks

| # | Area | Issue | Priority | Status |
|---|---|---|---|---|
| 1.1 | Timeline actions | `TimelineEditorTab` with empty TODO handlers was fully dead code — file removed. | 🔴 P0 | ✅ |
| 1.2 | Export formats | `OutputSettings` advertises H.264/H.265/VP9 but only H.264 is verified on all the AVD/real-device paths we've tested. Audit on real hardware. | 🟠 P1 | ☐ |
| 1.3 | Sync modes | `GPX_TIMESTAMP` mode depends on video EXIF creation timestamps — many sources (WhatsApp, re-encodes) strip them. Failure path needs a clear UX. | 🟠 P1 | ☐ |
| 1.4 | Undo/redo | `UndoManager` exists in `TimelineViewModel` but UI coverage is incomplete. Decide: ship everywhere or scope to MVP (clip trim/reorder only) and document. | 🟠 P1 | ☐ |
| 1.5 | Large GPX files | Statistics computed in-memory; >50k-point tracks could stutter during sync. Downsampling exists in parser — confirm it's applied in all code paths. | 🟡 P2 | ☐ |
| 1.6 | Background export | `ExportService` runs as `foregroundServiceType="dataSync"` — verify it survives Doze on OEM-skinned Androids (Xiaomi, OPPO). | 🟠 P1 | ☐ |
| 1.7 | Strava import | OAuth flow exists but error paths (revoked token, network loss mid-import) need review. | 🟠 P1 | ☐ |

---

## 2. Security & Privacy

### 🔴 P0 — Secrets in source — ✅ DONE

`lib/strava/.../StravaConfig.kt` used to hard-code `CLIENT_SECRET`. Fixed:

- Secrets moved to `local.properties` (`strava.clientId`, `strava.clientSecret`).
- `lib/strava/build.gradle.kts` reads them and injects via `BuildConfig`.
- `StravaConfig` now reads from `BuildConfig.STRAVA_CLIENT_SECRET`.
- `local.properties` is already in `.gitignore`.
- `StravaConfig.isConfigured` flag lets the app disable Strava features when
  the build lacks credentials (e.g. CI / contributor clones).

**⚠️ Still to do manually:**
1. **Rotate the previously-committed secret at <https://www.strava.com/settings/api>** — it is in git history and on the public internet as of this rewrite. A `git filter-repo` pass over the repo history is also worth considering.
2. For the long term, move the token exchange to a backend proxy so the secret never ships in the APK at all. Tracked in `PRODUCT_STRATEGY.md` §2.5.

### 🔴 P0 — ProGuard / R8 rules — ✅ DONE

`app/proguard-rules.pro` rewritten with explicit keep rules for Room,
kotlinx-serialization, Hilt/Dagger, Media3 (Transformer/Effect/ExoPlayer),
AndroidSVG, Coil 3, OkHttp, and all `@Serializable` / Room entity classes.
Stale `com.arthenica.**` (ffmpeg-kit) rule removed.

Verified with `./gradlew :app:assembleRelease` — **release build passes
minifyReleaseWithR8 + shrinkReleaseRes** cleanly. Full end-to-end smoke test
on a signed release APK is still pending real-device QA.

### 🟠 P1 — Permissions audit

Current manifest requests:

| Permission | Justified? |
|---|---|
| `INTERNET` | ✅ Strava API |
| `READ_MEDIA_VIDEO` / `READ_MEDIA_IMAGES` | ✅ media picker on Android 13+ |
| `READ_EXTERNAL_STORAGE` (≤API 32) | ✅ legacy gallery access |
| `WRITE_EXTERNAL_STORAGE` (≤API 29) | ✅ save export before scoped storage |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | ✅ export pipeline |
| `POST_NOTIFICATIONS` | ✅ export progress |

All justifiable. Document each in the Play Store privacy form. Prefer the
photo picker (`ACTION_PICK_IMAGES`) on Android 13+ to avoid needing
`READ_MEDIA_*` at all for media selection.

### 🟠 P1 — Privacy policy & data flow

- GPX files contain precise GPS traces and (often) heart-rate data — this is
  sensitive personal data under GDPR.
- Videos are processed fully on-device (good) — make that explicit in the
  privacy policy.
- Strava data: access & refresh tokens now stored in
  **`EncryptedSharedPreferences`** (AES-256-GCM, Android Keystore-backed).
  `StravaTokenStore` was migrated from plain DataStore. ✅
- Publish a privacy policy URL (required by Play) that covers: on-device
  processing, no analytics (or disclose what you collect), Strava OAuth data
  retention, crash reporting (if added).

### 🟠 P1 — Network security config — ✅ DONE

Added `app/src/main/res/xml/network_security_config.xml` (cleartext
disabled, system CAs only) and wired via `android:networkSecurityConfig`
in the manifest. Strava uses HTTPS so no per-domain overrides needed.

### 🟡 P2 — Backup policy — ✅ DONE

Added `backup_rules.xml` (≤ Android 11) and `data_extraction_rules.xml`
(Android 12+) to exclude the Strava token store from cloud backup and
device-to-device transfer. `allowBackup=true` is preserved so that user
projects still back up — only sensitive OAuth material is excluded.

---

## 3. UI / UX Review

### 🟠 P1 — Readability & polish

- Overlay templates were just overhauled for real-video legibility (stroked
  text, bigger values). Walk through all 6 templates on: dark footage, bright
  footage, low-contrast landscapes (snow, beach). Capture screenshots for
  the store listing while doing it.
- Settings sheet: confirm disabled chart/route rows read as "intentionally
  off" and not as a bug. The "— not available in this template" label helps;
  add a tooltip/info icon for explicitly telling the user to swap template.
- Empty states: onboarding, empty project list, empty timeline, failed GPX
  import. Each needs an illustration + actionable CTA.

### 🟠 P1 — Error handling

- `ErrorHandler` + `ErrorSnackbar` exist but coverage is thin. Ensure every
  ViewModel surfaces failures: import parse errors, missing EXIF, transcode
  failure, storage full, OAuth denial.
- Export: currently returns `ExportTaskResult.Error(message, code, logs)` —
  don't leak raw logs to users; map codes to human copy + a "copy details"
  affordance for support.

### 🟠 P1 — Accessibility

- Run Accessibility Scanner on each screen.
- Every icon-only button needs `contentDescription`.
- Check text contrast on overlays against the template preview background
  (currently the bright green sample).
- Support Dynamic Type / large font scales — audit timeline tile labels.

### 🟡 P2 — Aspect-ratio switching

Confirmed working in overlay layout. Add a subtle animation when canvas
resizes so users don't think the preview crashed.

### 🟡 P2 — Localization

App is currently English-only (`res/values/strings.xml`). Not blocking for
launch but: externalize every string now (no hardcoded literals in Composables)
to avoid a painful retrofit.

---

## 4. Performance & Stability

### 🟠 P1 — Export reliability matrix

Build a matrix and run it before release:

| Source | Length | Ratio | Codec | Device |
|---|---|---|---|---|
| Phone camera 4K60 | 30s | 9:16 | H.264 | Pixel 9 / Samsung S23 / Xiaomi 13 |
| WhatsApp re-encode | 15s | 1:1 | H.264 | ↑ |
| GoPro HEVC | 60s | 16:9 | H.265 | ↑ |
| Multi-clip (3×20s) with crossfade | 60s | 9:16 | H.264 | ↑ |
| Live sync with 10k-point GPX | 60s | 9:16 | H.264 | ↑ |

Track: export duration, output file size, visual parity with preview, RAM
peak, battery drain. Fix any crashes; document known-bad device/codec combos.

### 🟠 P1 — Crash reporting

No crash reporter detected. Before public release, add Firebase Crashlytics
(or Sentry). Gate it behind an opt-in to satisfy GDPR. Without this, bug
triage in production is blind.

### 🟡 P2 — Memory

Per-frame bitmap rendering for dynamic overlays can spike RAM on long
exports. Profile with Android Profiler on a 2-minute export; confirm bitmap
reuse in `OverlayFrameRenderer` is happening.

### 🟡 P2 — Start-up time

Launch cold on a mid-range device (e.g., Pixel 6a) and target <1.5 s to
first interactive frame. Baseline profile (`androidx.profileinstaller`) will
cut this significantly — worth the 30 min setup for a user-facing editor.

---

## 5. Code Quality

Recent cleanup removed:
- `lib/ffmpeg/` module (FfmpegResult migrated to `feature/export/ExportTaskResult`)
- `PreviewOverlayRenderer.kt`, `TransitionIndicator.kt`, `TemplateApplicator.kt`,
  `TimelineEditorTab.kt`, `AppResult` — all unused
- Unused `Double.formatDistance/Speed/Pace` extensions
- `ffmpegKit` and `ffmpeg-kit-full` entries from the version catalog
- Stale `refactor.md` PRD (superseded by current 2-screen flow)

Still outstanding:

- [ ] Consolidate formatters: `FormatUtils` object vs `Long.formatDuration`
      extension. Pick one.
- [ ] Private per-file `formatDistance/Duration` wrappers in
      `StravaActivityPickerSheet`, `StatsGrid`, `OverlayRenderer`,
      `DynamicOverlayRenderer` → use `FormatUtils` directly.
- [ ] Decide fate of `feature/overlays/OverlayRenderer.kt` +
      `DynamicOverlayRenderer.kt` — are they still used, or fully superseded
      by `core/overlay-renderer/SvgOverlayRenderer`? If superseded, delete.
- [ ] Tests: only `lib/gpx-parser` has unit tests + a single instrumentation
      test. Add at minimum: overlay-renderer snapshot tests, export smoke
      test, sync-engine unit tests.

---

## 6. Store Readiness

### 🔴 P0 — Metadata
- [ ] App name, short description (80 chars), full description (4000 chars)
- [ ] Screenshots: phone (min 2, ideally 5), 7" tablet, 10" tablet if
      advertised. One per feature pillar: import, edit, template, export.
- [ ] Feature graphic (1024×500)
- [ ] App icon (already present: `@mipmap/ic_launcher`)
- [ ] Category: *Video Players & Editors* (secondary: *Sports*)
- [ ] Content rating questionnaire
- [ ] Privacy policy URL
- [ ] Data safety form (on-device processing, Strava OAuth disclosure)

### 🟠 P1 — Build hygiene
- [ ] Bump `versionName` to `1.0.0`, `versionCode` to something ≥ 100 (leave
      room for pre-release builds).
- [ ] Sign release with a Play App Signing–enrolled upload keystore. Back up
      the upload key offsite.
- [ ] Enable Android App Bundle (AAB) output.
- [ ] Run `./gradlew bundleRelease`, upload to Play Console internal track.

### 🟠 P1 — Closed testing
- Internal testing track → closed track (20–30 testers) for at least a week.
- Collect crash logs, fix P0s, re-upload.
- Promote to production when crash-free rate ≥ 99.5% on closed track.

---

## 7. Day-0 Observability

Before hitting "Publish":

- [ ] Crashlytics / Sentry DSN configured
- [ ] Analytics events (even if minimal, GDPR-compliant): project created,
      GPX imported, template selected, export started, export completed,
      export failed (with error code).
- [ ] Remote kill-switch for the export pipeline in case of a bad codec path
      (Firebase Remote Config or a hand-rolled flag server).

---

## 8. Rollout Timeline (condensed)

Week 1 — Security & crash
- Rotate Strava secret, move it off-device.
- Wire Crashlytics, test a release build with R8.
- Run the export matrix on 3 devices.

Week 2 — UX polish & error paths
- Onboarding empty states, error copy pass, accessibility scanner pass.
- Finish or remove stubbed Timeline action handlers.

Week 3 — Store assets & closed testing
- Screenshots, descriptions, privacy policy, data safety form.
- Promote to closed track, monitor for a week.

Week 4 — Public launch
- Promote closed → production, 10% rollout.
- Monitor Crashlytics daily; bump to 100% after 48 h if crash-free ≥ 99.5%.

---

## 9. Post-launch backlog (P2)

- Cloud sync of projects
- Template marketplace / user-authored templates (sideload via SVG + meta.json)
- iOS port consideration — the SVG + Canvas renderer is the most portable
  piece; Media3 Transformer is not.
- Apple Watch / Garmin companion import
- More sport-specific default metric sets (swimming pace, triathlon multi-sport)
