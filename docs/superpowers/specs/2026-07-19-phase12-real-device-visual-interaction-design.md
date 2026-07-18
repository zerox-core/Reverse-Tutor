# Phase 12 Real-device Visual and Interaction Design

## Objective

Correct the Android real-device presentation and interaction defects identified on Huawei Mate 60 while preserving the existing product structure. This pass focuses on visual hierarchy, readable type, gesture ownership, navigation affordances, and local UI state. Repository mutations, durable editor persistence, export payload generation, and backend protocol contracts remain deferred.

## Design Direction

Use a neutral iOS-style grouped surface system while preserving Reverse Tutor's blue identity:

- Background: neutral grouped gray near `#F2F2F7`.
- Primary surfaces: white.
- Secondary surfaces: restrained neutral gray near `#F7F7FA`.
- Primary text: near-black neutral (`#1C1C1E`).
- Secondary and tertiary text: neutral grays (`#6E6E73`, `#8E8E93`).
- Brand blue remains for primary actions, selection, progress, and links.
- Soft blue/green/orange fills are limited to selected states, status chips, and illustration areas.
- Ordinary cards use luminance separation and restrained neutral borders instead of pervasive blue outlines or large shadows.
- Challenge detail and announcement dialogs keep their clean, frame-free material treatment.

## Typography and Density

Size text for normal handheld viewing distance on a 520 dpi phone without changing the user's system font settings:

- Avoid 8-10sp body copy on primary product surfaces.
- Use approximately 14-16sp for primary body text, 12-13sp for metadata, and 16-20sp for compact section/card titles.
- Preserve explicit line heights and zero letter spacing.
- Rebalance card height and padding after increasing type so content stays readable without oversized containers.
- Reduce the preset identity card from its current 154dp height while retaining title, learner identity, schedule, and avatar hierarchy.

## Home Surface

### Spatial Indicators

- Both the top vertical challenge indicator and bottom horizontal workspace indicator are hidden at rest.
- They fade in and highlight while the relevant pager is being dragged.
- After settling, they remain briefly visible and then fade out.
- Their appearance must not shift surrounding layout.

### Public-interest Card

- Provide clear available, loading, offline, pressed, and navigation states.
- Available content remains navigable to the article route.
- Offline content communicates why it cannot open without appearing like a dead control.
- No backend content protocol changes in this pass.

### Session Rows

- Add a stable avatar frame while preserving readable title and summary width.
- Add press and long-press feedback.
- Long press opens a bottom action sheet with rename, pin/unpin, export, and delete.
- Delete uses destructive color and a confirmation presentation.
- Action execution against repositories and export generation are deferred; this pass implements the interaction shell and local presentation states.

## Preset Detail and Custom World Tree

### Preset Detail

- Strengthen text contrast and increase undersized type.
- Reduce the oversized identity card.
- Replace the static episode card and decorative dots with a horizontal pager whose active indicator follows the page.
- Use local presentation data for episode pages; no backend episode protocol changes.
- Add visible edit affordances for learner identity, goal, scope, story, and sources.

### Shared Editor Presentation

- Preset detail and custom world-tree fields use the same editor route and visual language.
- Tapping a field opens a focused editor screen with a clear title, field controls, cancel/back, and save action.
- Changes may update an in-memory draft for visual validation, but no repository or backend persistence is added in this pass.
- The custom-tree name, every field row, add-field control, and preview action must be real clickable UI rather than decorative surfaces.

## Challenge Join Flow

- After a successful challenge join, close the detail route, return to the home session surface, and open a challenge-context new-session sheet.
- Pass the challenge context as a one-shot UI navigation event.
- Do not introduce a new backend challenge/session protocol in this pass.

## Chat Surface

### Header Navigation

- The world-tree/settings icon opens the active session settings stack rather than the global context hub.
- The overflow icon has a real menu presentation and no empty callback.
- Both controls have distinct labels, pressed states, and predictable back behavior.

### Composer

- Rebalance composer height, send-button size, trailing inset, and navigation-bar spacing.
- The send button remains centered and fully contained; its shadow must not be clipped or produce a double halo.
- Preserve a stable layout between empty, typed, focused, image-preview, and quoted-reply states.

## Global Graph and Workspace Gestures

- Add a top-left back control.
- Default to workspace page-swipe mode, including when the graph is empty.
- An explicit tap on the canvas enters graph pan/zoom mode.
- Graph mode has a visible state and a clear exit action.
- Only graph mode may consume canvas drag and pinch gestures.
- Direct swipes outside graph mode move between workspace pages.
- Empty graph state must not mount an invisible full-screen gesture interceptor.

## Home-to-challenge Vertical Gesture

- Increase the pull distance and pager positional threshold so short downward drags rebound to home.
- Increase the return-to-home capture range.
- Tune distance and velocity together on the Mate 60; do not modify device density or display size.

## Deferred Backend and Persistence Work

- Persisting shared editor drafts.
- Repository mutations for rename, pin/unpin, and delete.
- Export payload creation and system sharing.
- New public-content backend behavior.
- New challenge/session protocol contracts.
- Durable story episode models.

## Verification

- Unit tests for layout tokens, transient indicator state, graph gesture-mode state, and one-shot challenge navigation state.
- Compose/device tests for episode swiping, editor entry navigation, long-press action sheet, session settings navigation, and composer bounds.
- Build and install a Debug APK on Huawei Mate 60 at its native `1216x2688 / 520 dpi` configuration.
- Capture screenshots for home, preset detail, custom tree editor, chat composer, challenge join result, and global graph in both page and canvas modes.
- Confirm no clipping, overlap, persistent pager indicators, or invisible gesture interception.
