# Shelf 0.4 — motion for a television

The brief is a smoother, more Apple-like transition feel on Xiaomi S Pro Mini LED / HyperOS 3. Existing Shelf colours, typography and information layout are retained. The distinctive change is continuity: a selected item gives the next page its zoom origin, and Back restores the prior focus/scroll position.

## Primary references

- Apple HIG Motion: https://developer.apple.com/design/human-interface-guidelines/motion
- Apple HIG Designing for tvOS: https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos
- Apple WWDC23 Animate with springs: https://developer.apple.com/videos/play/wwdc2023/10158/
- Apple iPhone Motion settings (iOS 27): https://support.apple.com/en-mide/guide/iphone/iph0b691d3ed/27/ios/27
- AndroidX DynamicAnimation: https://developer.android.com/jetpack/androidx/releases/dynamicanimation

These sources support purposeful, brief, interruptible motion, consistent direction, gentle TV focus emphasis and reduced-motion support. Shelf does not use Apple's private animation code or claim exact iOS 27 spring parameters. Android TV is operated at viewing distance with a D-pad, so movement is deliberately modest.

## Implementation

- A persistent SceneHost replaces repeated whole-window setContentView calls. A navigation transition has a maximum of two live pages; stale completions cannot delete the current page. The transition can be replaced by another navigation action at any time.
- Forward: incoming page starts at 0.965 scale, 28 dp lateral offset, anchored at the outgoing focus. Back uses the opposite direction and a restrained 1.018 initial scale. Settings paths use 24 dp vertical movement. Pages use a critically damped spring (stiffness 230, damping 1); these are Shelf's tuning values, not Apple constants.
- Outgoing focus feedback is settled before compositing, avoiding repeated redraw of a full page just to animate an old button. Incoming default focus is settled before the page entrance; subsequent D-pad movement remains live.
- Focus: artwork grows 6.5%, lifts 2.5 dp, and gains a fine rim and shadow. Spring stiffness 440 / damping 0.92 preserves velocity during reversals. Confirmation-key press has a separate, critically damped spring. Detachment cancels springs.
- Navigation remembers focused item identity plus scroll positions for up to 32 routes. Same-page refresh restores these without replaying an entrance.
- Dialogs retain native selection/cancel/accessibility behaviour, with a 260 ms entrance and 170 ms exit. Late-loaded artwork fades in over 220 ms.
- The playback surface is never scaled or faded as a page texture. The previous page remains as a loading cover until Media3 renders its first video frame, then fades away. Audio-only readiness also releases the cover. Back/error can dismiss the pending cover.
- Controls use an interruptible spring, 18 dp of travel and alpha. Subtitle bottom padding follows the same progress, avoiding a sudden jump when controls appear/disappear.
- Reduced motion disables page/focus transforms and dialog animation; focus remains visible through colour/rim changes. Android's global disabled-animator state is respected. No continuously running decorative animation, real-time background blur, or video texture capture was added.

## Known bounds

This adapts motion only; it does not replace Shelf's entire visual layout. Exact shared-poster morphing and interactive touch/swipe navigation are not implemented. Per-route positions are in-memory for this activity; playback/library persistence remains the existing storage implementation. Device performance figures in VALIDATION.md are samples, not a claim of perfect frame pacing on every library or TV.
