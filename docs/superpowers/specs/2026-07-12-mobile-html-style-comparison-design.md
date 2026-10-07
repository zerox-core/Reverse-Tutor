# Reverse Tutor Mobile HTML Style Comparison

## Goal

Create two small, interactive HTML prototypes for a 390px mobile viewport. The prototypes compare visual direction only; they use identical content, information architecture, and interaction behavior.

## Prototype A: Knowledge Archive

- Warm paper-white background with ink-blue text.
- Fine dividers, index labels, restrained folder and document metaphors.
- Amber communicates challenge progress; lavender communicates AI and system state.
- Low shadow usage and clear editorial hierarchy.

## Prototype B: Apple Spatial

- Cool white background with lightly translucent surfaces.
- Generous spacing, soft elevation, floating bottom actions, and sheet-like transitions.
- Blue-violet is reserved for primary actions; other colors communicate status only.
- Native-feeling controls with concise labels and clear feedback.

## Shared Screens

### Home

- Continue-learning summary with the current subject and next action.
- Recent sessions with title, short summary, and time.
- Entry points for starting a new session and opening the challenge.

### Challenge

- Current 21-day learning challenge, progress, daily task, and reward.
- Join state changes to an active state without a backend.
- A clear route back to Home and forward to Session.

### Session

- AI prompt, learner response, source citation, and composer.
- Sending a message appends it to the conversation locally.
- Composer remains reachable above the mobile safe area.

## Interaction Model

- A stable three-item navigation switches between Home, Challenge, and Session.
- Buttons have default, pressed, focus, and disabled states.
- Challenge participation and sent messages are stored only in page memory.
- Motion uses short opacity, transform, and sheet transitions and respects reduced-motion preferences.

## Frontend Constraints

- Build as standalone HTML previews that open without a backend.
- Target 390px width while remaining usable from 360px to 430px.
- Minimum touch target is 44px.
- Dynamic text wraps without overlap; long session titles truncate intentionally.
- Use semantic HTML, visible keyboard focus, sufficient contrast, and ARIA labels for icon-only controls.
- Avoid nested cards, excessive rounded rectangles, decorative gradients, and generic dashboard composition.

## Deliverables

- One HTML preview for Knowledge Archive.
- One HTML preview for Apple Spatial.
- Shared sample content so the visual comparison is fair.
- Browser verification at 390x844 and 360x800, including navigation, challenge state, and message sending.

## Acceptance Criteria

- Both previews expose exactly the same three screens and actions.
- The two visual directions are immediately distinguishable without changing product behavior.
- No clipped text, incoherent overlap, blank content, or layout shift during interaction.
- Each preview can later be translated into Figma components and the existing WebView or Compose frontend.
