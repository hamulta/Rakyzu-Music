# Rakyzu Music — Android design system

Status: **normative for Android 0.8.8.2 and later**

This document is the visual and interaction source of truth for Rakyzu Music. Welcome, Sign In,
Log In, Sign Up, Home, Explore, and every future Android screen must use this system. A screen may
extend the system with a documented component, but it must not invent a second visual language.

The system is inspired by the clarity and content hierarchy of modern music products. It does not
copy Spotify source code, proprietary assets, trademarks, or private infrastructure.

## Source of truth

Use the shared Compose theme before adding a screen-local style:

- Tokens: `apps/android/core/designsystem/src/main/java/my/id/rakyzumusic/core/designsystem/theme/`
- Welcome implementation: `feature/auth/WelcomeScreen.kt`
- Auth gateway and forms: `feature/auth/SignInGatewayScreen.kt` and `AuthScreen.kt`
- Home implementation: `feature/home/HomeScreen.kt`
- Explore implementation: `feature/search/ExplorePage.kt` and `SearchScreen.kt`
- Reusable model and interaction contracts: `core:model` and feature ViewModels

Feature modules should consume `RakyzuMusicTheme`, `MaterialTheme.colorScheme`,
`RakyzuTypography`, and named Rakyzu color tokens. Do not place a new raw color, typography scale,
corner radius, or shadow in a feature module unless the token is first added here and to
`core:designsystem`.

## Brand direction

Rakyzu is a dark, energetic, editorial music experience. The dark shell keeps artwork and controls
focused; aqua communicates the primary action; purple provides secondary emphasis; artwork supplies
controlled variation. Surfaces are layered, never pure black-on-black, and text hierarchy is always
maintained without relying on color alone.

### Color tokens

| Token | Hex | Role |
| --- | --- | --- |
| `RakyzuBlack` | `#08080D` | App background and dark primary text on bright surfaces |
| `RakyzuSurface` | `#14141E` | Cards, sheets, and primary dark containers |
| `RakyzuSurfaceRaised` | `#20202C` | Raised cards, fields, dialogs, and pressed elevation |
| `RakyzuOnSurface` | `#F7F5FF` | Primary text and high-emphasis icons |
| `RakyzuOnSurfaceMuted` | `#B8B4C5` | Supporting text, metadata, and inactive navigation |
| `RakyzuAqua` | `#00E5D4` | Primary action, active navigation, progress, and focus accent |
| `RakyzuPurple` | `#9B7BFF` | Secondary action, gradients, and editorial emphasis |
| `RakyzuPurpleSoft` | `#BFA7FF` | Secondary emphasis on dark surfaces |
| Welcome background | `#41C3D6` | First-run Welcome canvas only |
| Welcome panel | `#121111` | Welcome lower content panel |
| Welcome action | `#059FB4` | Welcome Get Started action |

The Welcome canvas is intentionally the one bright entry point. Its black bubbles use
`BlendMode.Softlight` over `#41C3D6`; they must never be rendered as opaque black circles. The
transparent PNG listener artwork is the approved asset and has descriptive content text.

Material light-theme values remain available for system accessibility settings, but new product
surfaces are designed and reviewed against the dark Rakyzu shell first. Error, success, and warning
states must use semantic Material colors plus text or an icon; color alone never communicates state.

### Type scale

`RakyzuTypography` is the baseline:

| Role | Size / line height | Weight | Usage |
| --- | --- | --- | --- |
| Display small | 32 / 36sp | Black | Gateway headline and major entry title |
| Headline small | 24 / 29sp | Bold | Screen and section headings |
| Title large | 20 / 24sp | Bold | Group/detail titles |
| Title medium | 16 / 20sp | SemiBold | Card titles and form labels |
| Body medium | 14 / 20sp | Regular | Descriptions and metadata |
| Label large | 14 / 18sp | Bold | Buttons, filters, and compact controls |

Use sentence case, short labels, and visible field labels. Body text must remain readable at system
font scaling; never solve overflow by silently shrinking below the system type scale.

## Layout and component rules

- Use a 4dp base grid; normal spacing increments are 8, 12, 16, 24, and 32dp.
- Every interactive target is at least 48dp in both dimensions with at least 8dp separation.
- Respect status, navigation, and gesture-bar insets using Compose safe-area modifiers.
- Use a bounded content width (normally 420dp for auth and 520dp for Welcome artwork/panel).
- Use rounded containers consistently: pill shapes for primary actions and compact filters; rounded
  cards/sheets for content; do not mix arbitrary corner radii in one surface.
- Use one icon family and accessible content descriptions. Decorative icons are excluded from the
  semantics tree; icon-only actions always have a spoken name and state.
- Use artwork aspect ratios and reserved bounds before loading images to prevent layout shifts.
- Horizontal shelves may scroll, but the main page remains vertically scrollable and never relies on
  a hidden gesture for a critical action.
- Use a single clear primary action per surface. Destructive actions are separated and confirmed.

### Shared controls

Buttons, text fields, filter chips, dialogs, bottom sheets, cards, loading indicators, and snackbars
inherit Material 3 state layers and the Rakyzu palette. Pressed, focused, disabled, loading, error,
and success states must all be visible and testable. Async actions disable duplicate submission and
show progress without removing the user's context.

### Rakyzu content cards

- **Song card:** compact artwork and title/artist metadata; tapping starts playback immediately.
- **Group card:** artwork, title, ambient artwork color, and a clear group affordance; tapping opens
  the real queue/detail surface.
- **Radio card:** same large visual treatment as a group card but tapping starts the station directly.
- **Private recommendation:** account-local, never fabricated. If no server-backed content exists,
  render an honest empty state rather than placeholder songs.
- **Global editorial card:** managed by authorized staff, bounded by server placement and membership
  limits. Long-press is an enhancement; visible edit actions remain available in the management UI.

Home and Explore keep card dimensions stable while content changes. Home compact song artwork is
54×54dp, Top Mix group cards are 150×150dp, and large group/radio cards are 182×182dp, matching the
approved visual references. Any new card size requires a documented reason and accessibility review.

## Screen contracts

### Welcome

Welcome is shown on first launch/first data state and can be revisited using the Sign In back arrow.
Signing out goes to Sign In, not Welcome. The screen uses the `#41C3D6` canvas, transparent listener
artwork, Soft Light bubbles, animated artwork/panel entry, a two-step indicator, and one `Get Started`
CTA. It must remain readable on short screens and at large font scales.

### Sign In gateway

The gateway is the dark branded bridge after Welcome. It includes the Rakyzu logo, native Google and
Facebook actions, the Apple coming-soon dialog, password Log In, and Sign Up navigation. The back
arrow returns to Welcome. Provider logos are the approved local assets, not remote image URLs.

### Log In and Sign Up

Both credential screens use the same dark shell, logo/provider treatment, visible labels, keyboard
types, password visibility control, loading state, inline error recovery, and 48dp targets. Log In
contains Remember Me and Forgot Password; Sign Up adds Confirm Password and omits Remember Me. The
Sign Up CTA remains available where specified by the approved design, and successful authentication
passes through the profile gate to Home. Recovery opens the dedicated New Password state rather than
the ordinary Log In form.

### Home

Home is an authenticated, offline-first feed with the Rakyzu dark shell, three-item bottom
navigation, profile/settings/analytics/notification actions, and dynamic section ordering. Its
sections use private recommendation cards for listener-specific content and global editorial cards
for governed content. No placeholder content is shown. Card long-press opens the authorized editor;
the server remains the authority for role checks and mutations. The notification primer appears only
after authentication reaches Home; storage/media permission prompts originate only from upload
actions.

### Explore

Explore is a discovery surface in the same shell, not a separate theme. Recommended For You is an
always-available private song shelf. Your Top Genres starts with four real groups and supports eight;
Browse All starts with six real groups and supports twelve. Group grids, artwork/ambient color,
empty/loading/error states, long-press editing, ordering, and detail queues follow the shared card and
interaction rules. Global ranking may reorder equal editorial positions from aggregate engagement,
while private recommendations remain account-scoped.

## Motion and interaction

- Entry transitions use short fade/translate motion (roughly 350–500ms) to establish hierarchy.
- Press feedback is immediate; use a subtle scale/state-layer change, never a blocking animation.
- Forward navigation moves into the surface; Back reverses direction and preserves scroll/state.
- Bottom sheets and dialogs animate from their source and always expose Back/cancel dismissal.
- Animations use transforms/opacity, are interruptible, and respect reduced-motion preferences.
- Never make swipe, long-press, color, or animation the only way to complete a critical task.

## Accessibility and responsive behavior

- Target WCAG-style contrast: at least 4.5:1 for normal text and 3:1 for large text or controls.
- Provide headings, logical traversal order, descriptive image text, live regions for loading/error
  status, and action labels such as “Play {title}” rather than generic “Button”.
- Preserve content and controls at 1.3× font scale and on widths below 360dp; use stacking or extra
  lines instead of clipping.
- Keep controls away from system insets and support Android 8–17 (min SDK 26 through target SDK 37).
- Verify touch targets, focus/semantics, empty states, and destructive confirmation in Compose
  accessibility tests for every new screen.

## Implementation and review checklist

Before merging a new screen or materially changing an existing one:

1. Start with `RakyzuMusicTheme` and named tokens; add missing tokens to `core:designsystem`.
2. Map the screen to one of the contracts above and reuse the closest existing component.
3. Define loading, empty, offline, error, disabled, pressed, and success states before styling the
   happy path.
4. Check 48dp targets, semantics/content descriptions, text scaling, contrast, safe-area insets,
   and reduced motion.
5. Add or update Compose accessibility/unit tests and confirm no placeholder/dead UI remains.
6. Update this document when a new component, token, screen contract, or intentional exception is
   introduced. Then run the repository's Android test, lint, and build gates before release.

## Non-negotiable anti-patterns

Do not copy Spotify assets or source; do not introduce a second palette; do not use raw hex colors in
feature screens; do not add remote provider logos; do not ship placeholder media as real content; do
not rely on long-press alone; do not hide errors in logs; do not bypass role/RLS checks; and never
put Supabase service keys, Cloudflare tokens, or R2 credentials in Android or this document.
