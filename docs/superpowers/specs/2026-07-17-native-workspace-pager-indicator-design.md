# Native Workspace Pager Indicator Design

## Scope

The native Android workspace keeps one stable horizontal order:

1. Weekly dashboard
2. Session home
3. Global graph
4. Community

Session home remains the initial page. Challenge remains a vertical page above session home. The graph keeps its existing edge-only horizontal paging behavior.

## Motion

The bottom workspace indicator is a fixed dark pill with four stable anchors. The active marker is a short white capsule whose center follows the continuous pager position. Inactive markers remain circles and use a small scale and opacity handoff as the active capsule approaches them.

Dragging reads directly from `PagerState.currentPage + currentPageOffsetFraction`; there is no independent keyframe animation. Pager cancellation and settling therefore remain interruptible and inherit the pager's short, non-bouncing return behavior. The indicator does not animate on initial composition.

## Composition

`WorkspacePagerHost` owns the shared indicator because it owns the horizontal `PagerState`. The existing home and weekly two-page indicators remain available to isolated Figma previews, but the production workspace disables them to prevent duplicate chrome.

The shared indicator is suppressed while the challenge page, the home new-session sheet, or a graph node sheet owns the bottom layer. Material modal sheets continue to render above the workspace host through their existing modal layer.

The home-state indicator keeps the existing weekly-dashboard tap behavior. No new labels, instructions, or page-selection interactions are added.

## Verification

- JVM tests cover the four-page order, home default, continuous position calculation, and clamping.
- Android instrumentation covers weekly to home to graph to community navigation and graph center/edge gesture ownership.
- Huawei device verification records the continuous indicator during drag, canceled drag return, challenge vertical paging, graph center drag, and both graph edge exits.
