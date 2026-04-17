# Product Strategy — Accounts, Cloud, and Monetization

Companion to `LAUNCH_PLAN.md`. Where the war plan is about shipping the current
app safely, this document is about **what comes next** — the strategic moves
that turn the app from "a solid local editor" into "a product people pay for".

The app today is an offline-only, on-device editor. Everything lives in Room
+ local files + a single Strava integration. This is a good launching pad,
but for the experience to feel premium and scale into a business we need:

1. A **re-imagined home screen** that communicates value immediately.
2. A **user identity layer** (accounts, login/logout, sync).
3. **Cloud-backed templates** so creators can share / discover / monetize designs.
4. A **monetization model** that is fair, defensible, and aligned with endurance-athlete buyer psychology.

---

## 1. Home Screen Refactor

### 1.1 What's wrong with today's home

Current screen (`feature/home/HomeScreen.kt`) is a flat `LazyColumn` of
`ProjectCard`s with a search bar, an FAB for "+ new project", and a settings
icon. It's functional but:

- **No emotional payoff** — nothing here screams "this app makes gorgeous
  videos". A first-time user sees an empty state and a plus button.
- **No discovery surface** — templates are only visible _inside_ a project,
  after importing clips. The app's best asset (the templates) is hidden.
- **No creator-account signal** — there is no login, no profile, no history
  other than local project rows.
- **No merchandising of premium** — no place to show what Pro unlocks.
- **Weak retention loop** — users who finish one video have no reason to
  come back besides starting from scratch.

### 1.2 Proposed information architecture

Replace the flat list with a **tabbed / sectioned** home backed by a
`HomeTab` sealed type:

```
┌─────────────────────────────────────────────────┐
│  GPX Story                 [avatar]  [⚙]        │
├─────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────┐    │
│  │  FEATURED TEMPLATE OF THE WEEK           │    │  ← hero card, taps
│  │  "Horizon"  by @gpxstory                 │    │    into template preview
│  │  [ Try it ]                              │    │
│  └─────────────────────────────────────────┘    │
│                                                 │
│  ── Continue editing ─────────── See all →      │  ← last 3 projects
│  [p1] [p2] [p3]                                 │
│                                                 │
│  ── Templates for you ─────────── Browse →      │  ← personalized row
│  [t1] [t2] [t3] [t4] [t5]                       │
│                                                 │
│  ── Start from an activity ─────────────────    │  ← Strava shortcut
│  [ Import from Strava ]  [ Upload GPX ]         │
│                                                 │
│  ── From the community ──────── See all →       │  ← user-shared videos
│  [card] [card] [card]                           │
│                                                 │
│  [ + New project ]        (FAB, always on top)  │
└─────────────────────────────────────────────────┘
```

Each section is a module that can be feature-flagged and rolled out
independently. Build order:

1. Hero + "Continue editing" + "Templates for you" (ships with cloud templates)
2. "Start from an activity" (improves Strava funnel conversion)
3. "From the community" (post-social launch — see §3.4)

### 1.3 First-run vs returning-user

- **First run** — no projects yet: show onboarding carousel, then an expanded
  "Templates" section + a huge "Try a sample project" card (pre-packaged
  GPX + sample clip so the user can export a share-worthy video in 30 s).
  This dramatically improves day-1 retention.
- **Returning** — compress templates row, expand "Continue editing" and
  "Recents"; show a subtle streak counter or "3 videos exported" vanity stat.

### 1.4 Technical

- Introduce a `HomeRepository` that aggregates multiple flows: local projects,
  cloud templates, Strava linked state, featured content (remote config or
  a backend endpoint). Expose a single `HomeState` with nullable sections
  so the UI stays declarative.
- Skeleton loaders per section, not a whole-screen spinner.
- All new rows should be `LazyRow` nested in a `LazyColumn` with stable keys.

---

## 2. User Identity & Accounts

### 2.1 Why accounts now

Without identity we cannot: sync across devices, back up projects, monetize
(Play IAP is tied to a Google account but cross-device entitlement requires
our own identity), share templates, or build community features.

### 2.2 Auth options

| Option | Pros | Cons | Recommendation |
|---|---|---|---|
| **Firebase Auth** (Google, Apple, Email) | Free tier, first-party Android SDK, solid email/password + social | Vendor lock-in, no fine-grained RBAC | ✅ **Recommended for 1.1** |
| **Supabase Auth** | Open-source, Postgres-backed, cheaper at scale, row-level security for templates | Smaller Android SDK, you'll hand-roll more UI | Good alt if we want all-in-one backend |
| **Auth0 / Clerk** | Nicest DX | Cost curve gets steep | ❌ Overkill |
| **Apple/Google Sign-In only** | Zero password friction | Loses users without either (rare in endurance demo) | Ship as the _default_ sign-in methods on top of Firebase/Supabase |
| **Custom JWT backend** | Max control | Weeks of engineering, auth bugs are scary | ❌ Not yet |

**Decision:** Firebase Auth with Google + Apple + Email as the three sign-in
methods. Email allows recovery; Google/Apple are frictionless.

### 2.3 Sign-in UX principles

1. **Sign-in is optional.** The app must remain fully usable offline with no
   account — editing and exporting a single video never requires an account.
   This protects our App Store story ("no account required for core use").
2. **Soft prompts.** After the first export, a subtle "Save your project to
   the cloud? Sign in to sync across devices" banner.
3. **Deferred account creation.** Use Firebase _anonymous auth_ immediately
   on first launch so a stable user ID exists; upgrade to full account when
   the user signs in later. This preserves local data.
4. **Logout = scorched earth.** On logout, clear all cloud caches and
   offer ("Keep local projects on this device?" Yes/No). Never lose a user's
   offline work silently.
5. **Delete account** must be one tap away from Settings (legal requirement
   in multiple jurisdictions, and Play Console's Data Safety form).

### 2.4 Profile model

Minimum viable profile document (stored in Firestore or Supabase):

```kotlin
data class UserProfile(
    val uid: String,
    val displayName: String,
    val handle: String,          // @gpxstory — unique, for sharing
    val avatarUrl: String?,
    val createdAt: Instant,
    val tier: Tier,              // Free / Pro / Team
    val linkedStravaId: Long?,
    val publicProfile: Boolean,
    val preferences: Preferences // units, default sport, locale
)
```

`Tier` drives entitlement checks; `handle` backs social features; Strava link
lets us show "imported from Strava" badges on shared content.

### 2.5 Session & token hygiene

- Store Firebase ID token in memory only; refresh token in
  `EncryptedSharedPreferences` (same pattern we just applied to Strava).
- Never ship a long-lived bearer token to Firestore/Supabase clients — use
  the SDK's built-in refresh.
- Backend calls (§3) go through an authorized HTTP client that attaches
  `Authorization: Bearer <id_token>`.

---

## 3. Cloud Templates

### 3.1 Today's limitation

Templates live in `app/src/main/assets/templates/` — static, baked into the
APK, updated only by shipping a new build. A creator authoring a new Figma
SVG has no way to get it to users without a release.

### 3.2 Desired state

- **Pull templates at runtime** from a CDN. The APK ships with a small
  "seed" set (current 6) for offline bootstrap.
- **User-uploaded templates.** Creators export an SVG + meta.json bundle,
  upload it via the app or a companion web tool, and it becomes visible in
  the template browser (public or private).
- **Marketplace.** Top creators can charge (see §4.3).
- **Versioning.** Each template has `id`, `version`, `authorId`. When a
  creator publishes a v2, existing projects keep referencing v1 (by content
  hash) so old videos don't silently change.

### 3.3 Storage architecture

```
┌────────────────────────────────────────────┐
│  Android client                            │
│   ├── local assets/ (seed templates)       │
│   ├── app cache/templates/<id>/<version>/  │ ← downloaded on demand
│   └── SvgTemplateLoader (reads from both)  │
└────────────────────────────────────────────┘
        │  HTTPS + Auth header
        ▼
┌────────────────────────────────────────────┐
│  Cloud Storage (bucket)                    │
│   /templates/{id}/{version}/               │
│     ├── meta.json                          │
│     ├── *_9x16.svg  (etc.)                 │
│     └── fonts/*.ttf                        │
└────────────────────────────────────────────┘
        ▲
┌────────────────────────────────────────────┐
│  Metadata DB (Firestore / Postgres)        │
│   templates: id, authorId, title,          │
│              tags, price, visibility,      │
│              downloads, avgRating          │
└────────────────────────────────────────────┘
```

**Vendor pick:** Firebase Storage + Firestore if we went Firebase Auth;
Supabase Storage + Postgres if we went Supabase. Same shape either way.

### 3.4 Offline semantics

- Any template the user has used in a project is **pinned** to local cache
  — it's never evicted. Projects remain openable offline forever.
- Lazy-fetch everything else. Show a "download (1.2 MB)" CTA in the template
  picker rather than auto-downloading the whole catalog.
- `SvgTemplateLoader` gets a pluggable `TemplateSource` interface with two
  implementations: `BundledSource` (assets/) and `CloudSource` (cache + net).

### 3.5 Authoring pipeline

- **Web tool** (low priority): a Figma plugin or React+Konva page that
  drops placeholders, preview against sample GPX, and uploads. Single dev
  can build this in ~2 weeks once the API exists.
- **CLI** (day 1): existing `tools/convert_template.py` and
  `tools/finalize_template.py` grow a `--upload` flag that pushes to the
  creator's account.
- **In-app** (day 1): the "Save as template" button on the Style screen
  that bundles the current custom layout and posts it.

### 3.6 Moderation

User-generated content requires moderation. MVP approach:

- All uploads are **private by default** — only the creator sees them.
- "Publish to marketplace" is a distinct action that triggers human review
  (you, initially; automated checks later: SVG validator, font license
  check, profanity filter on title/description).
- A "Report" button on every public template. 3 reports → auto-hide
  pending review.
- Clear **Terms of Service** for creators (no trademarked logos, no hate
  symbols, original work only).

---

## 4. Monetization

### 4.1 Market framing

Competitor benchmarks:

| App | Model | Ballpark |
|---|---|---|
| Relive | Freemium — Pro required for HD, longer videos, advanced stats | €39.99/yr |
| Kinomap | Subscription, content-heavy | €9/mo |
| CapCut | Freemium → Pro for effects | $7.99/mo / $74.99/yr |
| VN Video Editor | Free, no premium | $0 |
| Wondershare Filmora | Paid pro tool | $49.99/yr |

Endurance athletes are **used to paying** for Strava, TrainingPeaks, Garmin
Connect IQ — pricing power is higher than a generic video editor. Aim for
the Relive band ($30–50/year) with aggressive yearly discount.

### 4.2 Free vs Pro split — guiding principle

> **Free must be enough to post a decent video**, or users uninstall. Pro
> must be enough to feel premium on every meaningful frame.

Proposed split:

| Feature | Free | Pro |
|---|---|---|
| Video imports | ✓ unlimited | ✓ |
| GPX / TCX import | ✓ | ✓ |
| Strava link | ✓ (read-only) | ✓ |
| Templates | 3 seed templates | **all 6+ built-in + entire marketplace** |
| Custom fonts | ✗ | ✓ |
| Aspect ratios | 9:16 only | **all 4** |
| Export resolution | 720p | **1080p / 1440p / 4K** |
| Export length | ≤60 s | **unlimited** |
| Watermark | Small "Made with GPX Story" lower-right | ✗ |
| Live sync | ✗ | ✓ |
| Cloud project sync | ✗ | ✓ |
| Template uploads (sell) | ✗ | ✓ (revenue share, see §4.3) |
| Ad-free | ✓ (no ads anywhere) | ✓ |

Rationale:
- Watermark is the **single strongest conversion lever** in video editors —
  it's visible on every exported video, which every free user will post.
- 720p cap is soft enough that Pro feels justified, generous enough that
  free users don't feel crippled.
- Length cap aligns with Reels/TikTok norms anyway.
- Unlimited templates + 4 aspect ratios are aspirational-but-obvious Pro perks.

### 4.3 Creator revenue share (marketplace)

When cloud templates (§3) open to paid listings:

- Price tiers set by creator: Free / $2.99 / $4.99 / $9.99.
- Platform takes **30% first year, then 15%** (mirrors Apple's post-2020
  policy, widely accepted).
- Creator must be Pro tier to sell.
- Payouts via Stripe Connect once a creator reaches $25.
- Templates are always previewable (one watermarked test export) before
  purchase.

### 4.4 Pricing

- **Pro Monthly:** $7.99 / €7.99
- **Pro Yearly:** $39.99 / €39.99 (**58% off**, banner-worthy)
- **Lifetime:** $99.99 one-time (availability-limited: first-year customers only)

Lifetime is a powerful early-adopter conversion tool. Remove after year 1.

### 4.5 Billing stack

- Google Play Billing Library v7 for the Android side.
- **RevenueCat** as the abstraction layer on top. Non-negotiable:
  - Handles subscription state, restore purchases, refunds, grace periods,
    introductory offers, and cross-platform entitlements in one place.
  - Free up to $10 k MRR, then 1% — cheap for the value.
  - Without it, entitlement bugs will eat 10% of your engineering time.
- Server-side receipt verification via RevenueCat webhooks → our Firestore.

### 4.6 Promo & trial strategy

- **7-day free trial** on yearly. Play's 1-week intro offer is frictionless
  and dramatically lifts conversion.
- **First export gate** — users see the paywall at the moment of highest
  emotional investment (just finished their video, about to export).
  Option to "Export with watermark" keeps free cohort happy.
- **Strava-linked discount** — if `linkedStravaId != null`, show a 20% off
  first-year banner. Rewards the engaged cohort.
- **Seasonal campaigns** — winter (treadmill / Zwift videos) and spring
  (marathon training).

### 4.7 Regulatory

- Auto-renew disclosure verbatim required by Apple and increasingly Google.
- EU (Digital Markets Act): must surface in-app price clearly, allow
  un-subscribe from inside the app.
- Must display restore-purchases button on paywall (Apple / Play rule).

---

## 5. Phasing

Keep the offline-first promise — every phase must leave the app fully
functional without a network.

### Phase 1 (1.0 → 1.1): Foundations
- Home screen redesign (without cloud sections — use local data only)
- Firebase Auth with anonymous-upgrade flow, optional sign-in
- Settings → Profile, Sign out, Delete account
- RevenueCat SDK integration and paywall with **free + Pro tiers only**
  (watermark, 720p cap, aspect-ratio cap, length cap as the first 4 gates)

### Phase 2 (1.2): Cloud templates (consumption)
- `TemplateSource` abstraction, cloud fetch, local cache pinning
- Home screen "Templates for you" section goes live
- Built-in templates served from CDN (remote-updateable without a store release)

### Phase 3 (1.3): Cloud projects & personal templates
- Cloud project sync (Pro only)
- "Save as template" personal uploads (private-only, no marketplace yet)

### Phase 4 (2.0): Marketplace & community
- Public template submissions + moderation
- Paid templates with revenue share
- Community feed in home screen
- Creator analytics (downloads, ratings, earnings)

---

## 6. Risks and answers

| Risk | Mitigation |
|---|---|
| Accounts scare offline users | Fully optional; anonymous auth bootstraps silently; no feature _requires_ account for single-device use except cloud sync. |
| Cloud bill surprises at scale | Firestore/Storage quotas with alerts at 50%/80%. Templates are small SVGs, worst case a few GB. Video is never stored cloud-side. |
| Marketplace legal (copyrighted logos, brand misuse) | Clear TOS, human-reviewed publish gate, DMCA contact, 3-strike takedown policy. |
| Payment regional differences | RevenueCat + Play localized pricing = one switch. |
| Moderation throughput as we grow | Start human, move to SVG static analysis (flagged fonts, external URLs, script tags) + trust score per creator. |
| Firebase lock-in | Abstract behind repositories. The overlay-renderer and GPX parser are the valuable IP — both stay portable. |
| Competitors bundling this into Strava natively | Strava's video features are shallow. Our moat is template quality + desktop-grade editing on mobile. Keep shipping templates. |

---

## 7. North-star metrics

- **W1 retention** ≥ 35% (first-export completion drives it)
- **Free → Pro conversion** ≥ 4% in the first 30 days
- **Template engagement** — median user opens ≥ 5 templates per project
- **Creator participation** (post phase 4) — 50 published templates per month within 6 months of marketplace launch
- **Crash-free sessions** ≥ 99.5%

Each feature proposed above should be defended by which of these it moves.
