# HyperFrames v0.8.141 — Authoring Contract Reference

**Scope:** how to write a valid composition HTML file from scratch.
**Version verified:** `hyperframes@0.8.141` (matches the installed `node_modules/hyperframes`).
**Method:** every claim below was read from the installed package (docs, templates, skill files) or from the minified linter/runtime, and several were confirmed by running `hyperframes lint` against purpose-built probe compositions. Anything not verified is marked **UNVERIFIED**.

---

## 0. Where the authoritative material actually lives

The remote sources named in the task were **not reachable/usable** from this sandbox:

- `https://raw.githubusercontent.com/heygen-com/hyperframes/main/...` — returned HTTP 404 (the repo path does not exist as specified). The one fetch that returned 200 was not retrievable as a file.
- `https://hyperframes.heygen.com/introduction`, `/quickstart`, `/guides/gsap-animation` — **not fetched** (no evidence these exact paths exist; the docs' own machine-readable index is `https://hyperframes.heygen.com/llms.txt`).

**This does not matter**, because the full contract ships inside the CLI package on disk. These are the authoritative sources used:

| Source | Path (relative to `hyperframes-demo/node_modules/hyperframes/`) |
|---|---|
| CLI reference docs | `dist/docs/{data-attributes,compositions,gsap,rendering,troubleshooting,examples}.md` |
| Real working scaffolds | `dist/templates/{blank,from-file,warm-grain}/` |
| Project agent rules (6 Key Rules) | `dist/templates/_shared/AGENTS.md` |
| **Full technical contract** | `~/.agents/skills/hyperframes-core/references/*.md` (installed by `skills update`) |
| Lint rule codes, severities, messages | `dist/chunk-WY5OODN5.js` (the linter) |
| Runtime adapter behavior | `dist/hyperframe-runtime.js` |
| Font tables | `dist/chunk-HBBJFK6I.js` |

`bunx hyperframes skills update` (with `HOME=hyperframes-demo/.home`, `PUPPETEER_CACHE_DIR=hyperframes-demo/.chrome`) **succeeded** and installed 10 skills into `.home/.agents/skills/` and `.home/.claude/skills/`:
`hyperframes`, `hyperframes-animation`, `hyperframes-audio`, `hyperframes-cli`, `hyperframes-core`, `hyperframes-creative`, `hyperframes-keyframes`, `hyperframes-registry`, `hyperframes-studio`, `media-use`.

Richer content than the CLI docs also lives in `dist/skills/` (shipped with the package). Prefer the installed `hyperframes-core` skill — the CLI docs are a condensed subset.

Local docs topic command (no network): `npx hyperframes docs <topic>` where topic ∈ `data-attributes`, `gsap`, `compositions`, `rendering`, `examples`, `troubleshooting`.

---

## 1. Root composition element

### The minimal working skeleton

Verbatim shape, matching `dist/templates/blank/index.html` and the `minimal-composition.md` reference:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <title>Minimal HyperFrames Composition</title>
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
    <style>
      body {
        margin: 0;
        background: #0b0f14;
        color: white;
        font-family: Inter, system-ui, sans-serif;
      }
      #root {
        position: relative;
        width: 100%;
        height: 100%;
        overflow: hidden;
      }
      .clip {
        position: absolute;
        inset: 0;
        display: grid;
        place-items: center;
      }
      h1 {
        margin: 0;
        font-size: 96px;
      }
    </style>
  </head>
  <body>
    <div
      id="root"
      data-composition-id="main"
      data-start="0"
      data-width="1920"
      data-height="1080"
      data-duration="5"
    >
      <section id="title-card" class="clip" data-start="0" data-duration="5">
        <h1 id="title">Hello HyperFrames</h1>
      </section>
    </div>
    <script>
      const tl = gsap.timeline({ paused: true });
      tl.from("#title", { y: 48, opacity: 0, duration: 0.6, ease: "power3.out" }, 0.2);
      window.__timelines["main"] = tl;
    </script>
  </body>
</html>
```

### Root attributes — exact table

| Attribute | Required | Semantics |
|---|---|---|
| `data-composition-id` | **Yes** | Unique ID. **Must match the `window.__timelines` key.** Lint error `root_missing_composition_id` if absent. |
| `data-width` / `data-height` | **Yes** | Pixel frame size. Common: `1920x1080`, `1080x1920`, `1080x1080`. Lint error `root_missing_dimensions`. |
| `data-start` | **No** | Root start. **Written above by convention and every shipped block has it, but the runtime stamps it when absent.** Docs: "Root `data-start="0"` is written above by convention and every shipped block has it, but the runtime stamps it when absent, so it is not required." |
| `data-duration` | **Conditional** | Render duration in **seconds** (total length), *not* the GSAP timeline length. See below. |
| `data-fps` | No | Optional frame-rate hint; CLI `--fps` overrides. |
| `data-composition-variables` | No | JSON **array of declarations**, on the `<html>` element. |
| `data-root="true"` | No | Names the composition root explicitly. Without it the runtime picks the outermost `[data-composition-id]`, which is right for almost every file. |
| `data-no-timeline` | No | Marks a composition intentionally without a timeline; silences `missing_data_no_timeline`. |
| `id="root"` | No | Template convention so CSS can target the root. Not required by the runtime. |

### `data-duration` is OPTIONAL when duration is inferable

This is the most commonly over-stated requirement. **Exact rule:** `data-duration` is optional whenever the runtime can auto-infer duration from:

- a registered GSAP timeline,
- a finite CSS animation,
- a finite WAAPI `element.animate()`,
- a registered Lottie animation,
- or timed clips (root then ends at its latest clip end; lint only *warns* `root_composition_duration_derived`).

It is **required** for:
- Three.js (no auto-inference),
- infinite/unbounded CSS or WAAPI animations **when nothing else supplies a finite length**,
- any composition with no GSAP timeline, no animation signal, and no timed clip with a length.

`lint` enforces this with error `root_composition_missing_duration_source`.

**Empirically pinned (two probe runs):**

- **Regression case — passes with 0 errors:** a root with **no** `data-duration` that has a GSAP timeline *and* an `animation: spin 2s linear infinite` CSS rule. The GSAP timeline supplies the duration source, so the infinite CSS animation does **not** error here.
- **Failing case — 1 error:** a root with **no** `data-duration`, `data-no-timeline`, and **only** an infinite CSS animation. Exact message:
  > "Root composition uses a CSS animation with animation-iteration-count: infinite and no data-duration, so the intended total length is ambiguous. **If a finite animation is also present the runtime infers that length; with no finite source the render fails with "Composition has zero duration".** Declare the intended length explicitly."

  It also raised warning `timeline_element_missing_timing`: `<div id="s1"> is a timeline element without data-duration, so the timeline cannot draw where it ends.`

**Practical rule:** always write root `data-duration` when you use any `infinite` animation, and give every timed `<div>` an explicit `data-duration`.

### ⚠ `data-duration` is read ONCE at compile time

For the **root**, `data-duration` is locked before scripts run — exactly like `data-width`/`data-height`. Consequences:

- `root.setAttribute("data-duration", ...)` from a script **cannot** change render length.
- A `--variables`-driven root duration **cannot** change render length.
- To vary length per render, author the root `data-duration` directly.
- Only when the root **omits** `data-duration` does the renderer probe the live DOM/timeline for total length.
- A **clip's** `data-duration` is different: it *is* re-read from the live DOM, so scripts/variables can drive clip lengths.

### Root layout rules

- The root should be `position: relative`, sized, and `overflow: hidden` (unless intentionally composing outside the frame).
- Author `width`/`height: 100%` on the standalone root — **do not hardcode `1920px`/`1080px` on `#root`**; the runtime stamps the `data-width`/`data-height` pixels onto it. (The `blank` template's `html, body { width: 1920px; height: 1080px }` is the older style; the current `minimal-composition.md` reference uses `width/height: 100%` and explicitly warns against hardcoding.)
- **Root-level clips get automatic layout:** for *direct children* of the root that carry `data-start`, the runtime forces `position: absolute` and anchors at `top:0; left:0`, sizing to 100% when they have no computed size. Nested timed elements get **no** automatic layout — give them their own positioning.
- Elements **without** `data-start` are skipped entirely: an untimed full-bleed background needs its own `position: absolute; inset: 0` or it collapses to zero height.
- A full-screen fill **on the composition root** is fine on a normal render, but is dropped on the layered-composite path (HDR content, or shader transitions), where every composition root is forced transparent. If you use shader transitions or HDR media, put the fill on a full-bleed **child**.
- `zoom` on the root is an **error** (`root_zoom_rescales_a_fixed_canvas`).

### Standalone vs sub-composition — two root forms (NOT interchangeable)

| Form | Shape | Rule |
|---|---|---|
| **Standalone** (`index.html`) | root `<div data-composition-id>` directly in `<body>`, **no `<template>`** | Wrapping it in `<template>` is lint **error** `standalone_composition_wrapped_in_template`. |
| **Sub-composition** (loaded via `data-composition-src`) | root wrapped in `<template>` | The `<template>` is the **transport container** — see §4. |

---

## 2. `class="clip"` and all `data-*` timing/track attributes

### The single most important correction

> **`data-start` is what makes an element a clip.**
> The runtime collects `[data-start]` and drives visibility off that attribute.
> **`class="clip"` is a convention, not a requirement — the runtime never reads it.**

`data-track-index` is likewise **not read by the render**. You can author a composition with no `class="clip"` and no `data-track-index` at all and it renders fine (lint warns about the missing class, but only as a warning; a composition with no clips at all renders fine).

### Full clip attribute table

| Attribute | Required | Semantics |
|---|---|---|
| `id` | **Yes on `<video>`/`<audio>`** | Lint **error** `media_missing_id`: "The renderer requires id to discover media elements — this audio will be SILENT / this video will be FROZEN in renders." Elsewhere a warning (`studio_missing_editable_id`). |
| `data-start` | **Yes** | Start time in **seconds**, or a supported clip-time reference (§ below). This attribute is what marks the element as timed. |
| `data-duration` | Required for `div` and sub-compositions | Duration in **seconds**. `img` defaults to **3 s**; video/audio default to source length (less playback offset, over the rate) once known; an authored value trims. Without any resolvable duration the element has no end and stays visible for the rest of the composition. |
| `data-track-index` | No | **Studio timeline lane, display only. The render never reads it.** Absent, the parser defaults it to `0` and Studio lays out one lane per clip. |
| `data-media-start` | No | Offset **into the media source**, in seconds (trim point). |
| `data-volume` | No | Static audio/video gain. Default `1` (0 dB). `0` = silence. Values above `1` **boost up to `3.98` (+12 dB)**. |
| `data-fade-in` / `data-fade-out` | No | Clip-edge fades in **seconds**. Linear gain ramps anchored to the clip's start and end, **multiplied on top of** `data-volume` and any volume lane. |
| `data-has-audio` | **Required on a timed `<video>` unless `muted`** | `"true"` keeps the file's sound on this clip (default for footage with sound). Silent footage uses `muted` instead. Lint error `video_missing_muted` if neither. |
| `data-playback-rate` | No | Constant speed multiplier, normalized `0.1..10`. |
| `data-link` | No | Studio editing contract only; render ignores it. |
| `data-sync-origin` | No | Studio sync contract only. |
| `data-automation` | No | JSON envelope lanes (volume/rate/fx). See §5. |
| `data-hidden` | No | Hides the element in **BOTH** preview and render, overriding its time window. Non-destructive/reversible; toggled by Studio's timeline eye icon. |

`<video>`/`<audio>` also accept `playsinline` (used throughout the shipped templates).

### Units: SECONDS, not frames

Every timing attribute is in **seconds** (floats allowed, e.g. `data-start="1.8"`, `data-duration="2.5"`). Frame rate is a separate render concern (`data-fps` / `--fps`). `data-media-start` is seconds into the source. `data-automation` `t` values are seconds **from the clip's own start**.

### The visibility window is HALF-OPEN: `[start, start + duration)`

A clip shows while `start ≤ t < start + duration` and is **hidden at exactly `t = start + duration`**.

> **Land an animation's resolved end state slightly *before* `data-duration`, not on it, or its last frame is never rendered.**

Two clips can be authored back to back (`b.start === a.start + a.duration`) with no overlapping frame.

### Overlapping clips on the same track — fully legal

- **Two clips on the same track may overlap in time.** Nothing rejects it and the render is well defined: **both are visible, painted in CSS order.**
- Visual layering (front/back) is controlled by **CSS `z-index`**, not by track index. *"A clip on track `5` is not 'above' a clip on track `1`."*
- Overlapping clips do **not** need different tracks.
- The **one** place the value carries meaning: two `<audio>` elements that share a track index **and** overlap in time raise lint **warning** `duplicate_audio_track` (a nudge that you are about to double up a bed).
- Adding a clip: you do **not** need to find a free lane, and you never need to renumber tracks after a retime.

### Relative timing (`data-start` as a clip-ID reference)

`data-start` accepts a **clip ID** meaning "start when that clip ends", with `+ N` / `- N` offsets (negative = overlap, useful for crossfades):

```html
<video id="intro" data-start="0" data-duration="10" data-track-index="0" src="..."></video>
<video id="main"  data-start="intro"     data-duration="20" data-track-index="0" src="..."></video>
<video id="scene-a" data-start="intro + 2" data-duration="20" data-track-index="0" src="..."></video>
<video id="scene-b" data-start="intro - 0.5" data-duration="20" data-track-index="1" src="..."></video>
```

**Four silent failure modes — `lint` checks NONE of them:**

1. **Spaces around the operator are required.** `"intro - 0.5"` = 0.5 s before `intro` ends. **`"intro-0.5"` (no spaces) is parsed as a reference to an element whose id is literally `intro-0.5`**, which does not exist, so the clip silently starts at **0**.
2. **An unresolved reference resolves to 0**, it does not error. A typo'd id puts the clip at the start.
3. **If the target has no resolvable duration, the reference lands on the target's START, not its end.** So `data-start="hero"` where `hero` has no `data-duration` and no known media length silently means "same time as `hero`".
4. **A cycle resolves to 0** rather than erroring. `A → B → A` puts one of them at 0.

Additional rules: lookup is **document-wide** (`getElementById`, then `[data-composition-id]`) so a reference can reach another composition on the assembled page — keep referenced ids unique. A value that parses as a number is **always absolute seconds**. References can chain; keep chains under 3–4 levels. **Always snapshot a reference-timed composition to confirm placement.**

### Deprecated / removed attribute names (BOTH are lint ERRORS)

| Legacy | Use instead | Lint code |
|---|---|---|
| `data-layer` | `data-track-index` | `deprecated_data_layer` (**error**) |
| `data-end` | `data-duration` | `deprecated_data_end` (**error**) |

`data-end` is a **derived** attribute: the compiler regenerates it from `data-duration`. It is only *read* as a legacy fallback when `data-duration` is absent; writing both, or writing `data-end` alone, is an error. Exact messages:
- Disagreeing: `"<tag> has data-end that disagrees with data-duration. Remove the stale data-end; the compiler regenerates it from data-duration."`
- Alone: `"<tag> uses data-end without data-duration. Use data-duration in source HTML."`

### Nesting rules

- A timed element inside a wrapper is still timed, and **a timed ancestor clamps its descendants**: a child cannot be visible while its timed ancestor is hidden.
- **Never nest `<video data-start>` inside a plain element that also has `data-start`** — lint **error** `video_nested_in_timed_element`, and the failure is real: the frame extractor resolves the video's start from its own `data-start` **without** the wrapper's offset, while visibility uses the wrapper's window. The clip shows the wrong source frames and disappears partway through its slot. **Put the timing on the wrapper OR the media element, never both.** Sub-composition hosts are exempt.
- Keep every `id` unique across the **assembled** page (prefix sub-comp ids, e.g. `#intro-hero`).

---

## 3. The animation contract

### Wiring `window.__timelines`

```js
const tl = gsap.timeline({ paused: true });
tl.to("#title", { opacity: 1, duration: 0.5 }, 0);
window.__timelines["root"] = tl;   // key MUST equal root data-composition-id
```

Exact rules:

- **Each composition registers EXACTLY ONE** `gsap.timeline({ paused: true })` at `window.__timelines["<id>"]`, where the key is the root's `data-composition-id`.
- **You do NOT need `window.__timelines = window.__timelines || {}`.** The runtime creates the registry before your inline scripts evaluate, and `lint` no longer asks for it. (The `blank` template still writes it in `warm-grain`; it is harmless but unnecessary.)
- Supported GSAP methods per the CLI docs: **`set`, `to`, `from`, `fromTo`**.
- Position parameter (3rd arg) sets **absolute time in composition seconds**: `tl.to(el, vars, 1.5)`.
- **Render length is the root's `data-duration`, not the timeline's length.** A timeline that runs past it is cut off; one that ends early holds its last frame.
- **Do not** call `tl.play()` for render-critical motion.
- **Do not** create empty tweens only to set duration; use `data-duration` on the clip instead.
- **Do not manually nest sub-timelines into the host.** The runtime auto-nests registered child timelines; GSAP-nesting them causes double-seeks.
- **Scene timelines manually added to a root timeline must NOT be paused.** A paused child does not advance when the root is seeked. The runtime activates registered composition *siblings*, not arbitrary nested scene timelines.
- **Do not** add a scene-exit `tl.set(..., {visibility:"hidden"})`. The runtime already hides timed clips. (Caption "hard kills" are a different, deliberate rule.)

### Why `paused: true` is mandatory

HyperFrames **seeks** rather than plays. The renderer sets composition time and captures a frame; every frame must be reproducible from its time value alone. A playing timeline advances on wall-clock time, so it would drift from the seek target and produce different pixels per worker/run. `paused: true` makes the timeline a pure function of `tl.seek(t)`.

### Registration edge cases (silent bugs)

- **Mismatched key:** if the key does not match the root's `data-composition-id`, the runtime still binds it **when it is the only registered timeline**. With **two or more** registered, a mismatched key leaves the render **frozen at t=0**.
- **Async build:** building inside an async callback (`document.fonts.ready(...)`) **is supported**. What you must **not** do is **register the key before the build finishes** — an empty timeline registered early is treated as ready and nested empty, so the animation renders blank. Lint **error** `gsap_timeline_registered_before_async_build`:
  > "window.__timelines is assigned BEFORE the timeline is built inside document.fonts.ready. An empty timeline registered early gets nested empty when this composition is used as a sub-composition (the readiness gate treats 'key present' as ready)."
  
  Assign `window.__timelines[id] = tl` at the **end** of the callback, after tweens are added, and optionally call `window.__hfForceTimelineRebind()` right after.
- Lint error `gsap_timeline_not_registered`: "GSAP timeline is created but never registered in window.__timelines. The runtime discovers timelines from this registry — without registration, animations will not play during preview or render."
- Lint error `missing_gsap_script`: "Composition uses GSAP but no GSAP script is loaded. The animation will not run."

### Determinism rules — exact forbidden list

Rendered frames must be reproducible from the requested time: **same input time → same pixels.** Do NOT use any of the following for visual state:

- `Date.now()`, `performance.now()`, or any render-time clock.
- Unseeded `Math.random()`. Use a seeded PRNG.
- Render-time network fetches for required assets. Inline or pre-bundle them.
- Hover, scroll, pointer, or focus state. **The renderer has no input events.**
- Unbounded infinite loops. `repeat: -1` is allowed **only** when the root declares a finite `data-duration` — seeking and export clip to that explicit window (`gsap_infinite_repeat` demotes to a **warning**). Without a finite composition duration it stays a **hard error**.
- Animating the same property on the same element from multiple timelines at the same time — GSAP's overwrite behavior is order-dependent and can flip between renders (`overlapping_gsap_tweens`, warning).

**The linter's exact detection patterns** (all severity **error**, code `non_deterministic_code`), extracted from `chunk-WY5OODN5.js`:

| Pattern | Label | Hint |
|---|---|---|
| `/Math\.random\s*\(/` | `Math.random()` | Use a seeded PRNG (e.g. mulberry32). |
| `/Date\.now\s*\(/` | `Date.now()` | Use GSAP timeline position instead of wall-clock time. |
| `/new\s+Date\s*\(\s*\)/` | `new Date()` | **Zero-arg only.** `new Date(<fixed timestamp>)` is deterministic and is explicitly allowed. |
| `/performance\.now\s*\(/` | `performance.now()` | Use GSAP timeline position. |
| `/crypto\.getRandomValues\s*\(/` | `crypto.getRandomValues()` | Use a seeded PRNG. |
| `/gsap\.utils\.random\s*\(/` | `gsap.utils.random()` | Each render worker initializes independently, so values diverge across chunks. |
| `/["'`](?:[+-]=)?random\(\s*[-\d[]/` | `"random(...)"` tween value | GSAP random string values re-roll at tween init. **This one scans string literals** (GSAP parses the string); the others match executable code only, so matches inside strings are inert and not reported. |

`requestAnimationFrame` is a **separate error** (`requestanimationframe_in_composition`):
> "`requestAnimationFrame` runs on wall-clock time, not the GSAP timeline. It will not sync with frame capture and may cause flickering or missed frames during rendering."
> Fix: "Use GSAP tweens or onUpdate callbacks instead."

**Also banned:** tweening `display`, raw `visibility`, or `autoAlpha` **on a clip element** (`gsap_animates_clip_element`, **error**) — HyperFrames timing owns a clip's visibility. Fade with `opacity`, or tween a child wrapper.

**Never tween `class="clip"`.**

**`repeat: -1` bounding math — get the rounding right:**
```js
repeat: Math.max(0, Math.floor(duration / cycleDuration) - 1)
```
Use **`floor`, not `ceil`** — `ceil` overshoots `data-duration` and trips `gsap_repeat_ceil_overshoot`; `max(0, …)` avoids a negative repeat (which means infinite).

**No property allowlist.** `lint` enforces a **denylist**, so `filter`, `clipPath`, `strokeDashoffset`, `width`, `height` are all legitimate targets. Prefer transforms and opacity for performance, not correctness.

**Runtime helpers available on `window.__hyperframes`:**
- `getVariables()` → merged variable object.
- `fitTextFontSize(text, { maxWidth, fontFamily, fontWeight })`.
- `pretext.prepare(text, font)` / `pretext.layout(prepared, maxWidth, lineHeight)` → `{ lineCount, height }` (measure off a canvas, no DOM reflow).
- `pretext.prepareWithSegments(...)` + `measureNaturalWidth(prepared)` / `measureLineStats(prepared, maxWidth)` for shrinkwrap sizing.
- `font` is a CSS font shorthand string, e.g. `"700 90px Inter"`.
- `clearCache` and `setLocale` are deliberately **not** exposed (they mutate state shared across compositions, which would make a render depend on what ran before it).

---

## 4. CSS animations vs GSAP

### Does the runtime seek CSS animations? **YES — for `@keyframes` animations.**

Verified two ways:

1. **The runtime code** (`dist/hyperframe-runtime.js`) walks `document.getAnimations()`, filters to instances of `CSSAnimation`, groups by `effect.target`, and reads `animation-duration` / `animation-delay` / `animation-name` to compute `cycleSeconds`, per-name delays, and `endSeconds` via `effect.getComputedTiming()`. It then seeks them. Documented as the `css` runtime adapter (`packages/core/src/runtime/adapters/css.ts`).
2. **Empirically:** a probe composition with `animation: spin 2s linear infinite` produced **no lint finding**, while a sibling with `transition: opacity 1s ease` produced warning `css_transition_used`.

Per the adapter (`hyperframes-animation/adapters/css-animations.md`):

> "The adapter discovers elements with computed `animation-name`, seeks their browser `Animation` handles when available, and **falls back to pausing with negative `animation-delay`**."

**Contract for CSS animations:**

- Put the animated element **in the DOM before runtime initialization finishes**.
- Give timed elements a **`data-start`** value so local animation time matches the clip. (The adapter resolves `element.closest("[data-start]")`.)
- Use **finite** `animation-duration` and `animation-iteration-count` — the negative-delay fallback cannot represent unbounded duration in environments without WAAPI-backed CSS animations.
- Prefer **`animation-fill-mode: both`** so seeked states hold before and after active motion.
- Avoid wall-clock JS, hover-triggered state, and class toggles that depend on user events.

Basic pattern (verbatim from the adapter doc):

```html
<div id="pulse-ring" class="clip pulse-ring" data-start="0" data-duration="4" data-track-index="2"></div>

<style>
  .pulse-ring {
    width: 280px; height: 280px;
    border: 4px solid rgba(255, 255, 255, 0.7);
    border-radius: 50%;
    animation-name: pulse-ring;
    animation-duration: 1200ms;
    animation-timing-function: cubic-bezier(0.2, 0, 0, 1);
    animation-iteration-count: 3;
    animation-fill-mode: both;
  }
  @keyframes pulse-ring {
    from { opacity: 0; transform: scale(0.82); }
    35%  { opacity: 1; }
    to   { opacity: 0; transform: scale(1.18); }
  }
</style>
```

Stagger via custom properties (`--i`) with `animation-delay: calc(var(--i) * 120ms)`.

**No extra required attributes on animated elements beyond `data-start`** (for clip-local time). `class="clip"` remains a convention.

### CSS `transition` is NOT seekable

Lint **warning** `css_transition_used`:
> "CSS declaration `transition: opacity 1s ease` runs on the browser clock and cannot be seeked deterministically across render workers."
> Fix: "Keep the class or attribute swap for state; put the visual change on the paused GSAP timeline."

The check ignores `transition: none`, `transition-property`, and zero-time `transition-duration`/`transition-delay`, but flags any non-zero time (and treats `var(...)` as non-zero). **Prefer GSAP for all choreography; use CSS keyframes for decorative single-element loops.**

### WAAPI (`element.animate`) is also seeked

Adapter `waapi`: calls `document.getAnimations()`, sets each animation's `currentTime` to HyperFrames time in ms, then pauses it. Contract: create animations **synchronously** during init; finite `duration` and `iterations`; `fill: "both"`; pause after creation. Casting `document.getAnimations()` is `Element.animate`-compatible.

### Duration inference per runtime

- **GSAP:** timeline reports duration automatically.
- **CSS-only:** infers from the longest running animation's computed end time (`animation-delay` + `animation-duration` × finite `animation-iteration-count`, per element with `data-start` added as offset). `data-duration` on the root is optional **iff every CSS animation on the page is finite**.
- **WAAPI-only:** infers from every animation's `effect.getComputedTiming().endTime`.
- **`animation-iteration-count: infinite` (or any unbounded animation) has no finite end**, so it cannot be auto-inferred — you **must** add root `data-duration`, or lint errors `root_composition_missing_duration_source`.
- **Three.js:** no auto-inference; always declare root `data-duration`.

---

## 5. Audio

### The `<audio>` element contract

```html
<audio
  id="music"
  src="assets/bed.mp3"
  data-start="0"
  data-duration="12"
  data-track-index="2"
  data-volume="0.4"
></audio>
```

Hard rules:

- **Every `<audio>` needs an `id`.** The mixer selects `audio[id][src]`, so an id-less `<audio>` is **never mixed and the render is SILENT**. Lint **error** `media_missing_id`. Same for `<video>` (else the video is **FROZEN**).
- **`data-start` is required** on media (`media_missing_data_start`, error): "HyperFrames cannot own playback for untimed media, so preview and render behavior can diverge."
- **Never add `crossorigin`** to `<video>`/`<audio>`. Lint **error** `media_crossorigin_breaks_preview`, **unconditional, no suppression** — even for canvas/WebGL/WebAudio readback. A media host without `Access-Control-Allow-Origin` fails silently in preview while renders still work, hiding the bug.
- **`preload="none"`** warns (`media_preload_none`); the compiler strips it for renders.
- **Base64 media is prohibited** (`base64_media_prohibited`, error).
- **Do not call** `video.play()`, `audio.play()`, pause, or seek in composition code (`imperative_media_control`, error). **HyperFrames owns playback.**
- `src` may not be changed at runtime without risk (`media_runtime_src_mutation`, warning).
- Tag/file kind must agree (`media_src_kind_mismatch`, error).

### `data-volume` and the three audio attributes

`data-volume` is the **static baseline** gain: default `1` (0 dB), `0` is silence, values above `1` boost up to `3.98` (+12 dB). Studio's fader writes it.

Two more clip-level audio attributes (from `hyperframes-audio/references/attributes.md` and the CLI docs):

- **`data-fade-in` / `data-fade-out`** (seconds) — linear gain ramps anchored to clip start/end, multiplied on top of `data-volume` and any volume lane.
- **`data-automation`** — JSON envelope lanes, the way to do fades/ducking/shaped envelopes:

```html
<audio id="bed" src="bed.mp3" data-start="0" data-duration="12"
  data-automation='{"version":1,"lanes":[{"target":"volume","points":[{"t":0,"v":0.1},{"t":10,"v":0.5}]}]}'></audio>
```

`t` is **seconds from the clip's own start** (`t:0` at composition time `data-start`). `v` is in the parameter's own unit (0..1 for volume, dB for a gain). A lane holds its first value backwards and last value forwards. `curve` (-1..1) bends the segment leaving a point; `viaX`/`viaY` supersede it. Max **512 points per lane**.

**One rule for volume over time:** use **either** a lane **or** a timeline `volume` tween, never both — when both exist **the lane wins and the tween is ignored** (`audio_volume_double_automation`). Note `data-volume` is the static level and **combines with nothing else you author** in the lane.

Also available: `data-fx-chain` (effect rack, signal order), `data-fx-carve` (ducking), and `<hf-audio-group>` buses. **Write these attributes double-quoted with JSON quotes as `&quot;`** — `scripts/carve.mjs` finds them with a `name="..."` regex, so a single-quoted attribute is invisible to it and a carve will quietly overwrite work it could not see.

### Video with sound

A video with sound keeps it on the `<video>` (`data-has-audio="true"`, **no `muted`**). Use a separate `<audio>` **only** for other sound (music, voiceover, replacement audio, J/L cuts) and mute the video it replaces.

- `data-has-audio="true"` **and** `muted` together is an **error** (`video_muted_with_declared_audio`).
- Same source on an unmuted `<video>` and an `<audio>` at the same time is an **error** (`video_audio_double_source`) — causes double playback and echo.

### How to control whether audio is included in the render

- **`data-volume="0"`** silences a clip.
- **`muted`** on a `<video>` removes its sound; then it needs no `data-has-audio`.
- **Omit the element**: there is **no render-time flag** to strip audio. `hyperframes render --help` for v0.8.141 lists **no** `--audio`/`--mute`/`--silent` option. (`--audio` exists only on **`hyperframes init`**, where it means "start from an audio file".) **UNVERIFIED:** whether any env var or `hyperframes.json` config key can drop the audio track — I found none, and I did not test a render.
- Practical consequence: **control audio by authoring.** Remove or mute the elements. Lint warning `audio_file_without_element` fires when audio files exist in the project with no `<audio>` element ("The rendered video will be silent"), and error `audio_src_not_found` when an `<audio>` points at a missing file.

---

## 6. Images / video: allowed sources

### External URLs / CDNs **do work**

Verified empirically: external `https://` sources are accepted by the linter. The only external-source rejections found are:

- **`placeholder_media_url`** (error) — known placeholder domains (the probe rejected `https://picsum.photos/...` and `https://cdn.example.com/...`).
- **`inaccessible_media_url`** (error) — a URL that actually returned a non-OK HTTP status: `"references a URL that returned HTTP ${status}"`.
- **`missing_local_asset`** (error) — a **local** file that does not exist: `"references local file(s) not found in the project"`. (Confirmed: `audio_src_not_found` fired for `missing.mp3`.)

The docs' determinism rule nonetheless says: **"Render-time network fetches for required assets. Inline or pre-bundle them."** So the *contract* favors local files even though remote URLs are not statically rejected. Remote assets also create render-time nondeterminism and can fail the request audit in `check`.

### Recommended: local files

Canonical form from the shipped `from-file` template:

```html
<video id="a-roll" class="clip" src="__VIDEO_SRC__" playsinline
  data-has-audio="true" data-start="0" data-duration="__VIDEO_DURATION__"
  data-track-index="0"
  style="position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover"></video>

<audio id="a-roll-audio" src="__AUDIO_SRC__" data-start="0"
  data-duration="__VIDEO_DURATION__" data-track-index="2" data-volume="1"></audio>
```

And from `variables-and-media.md`:

```html
<video id="a-roll" class="clip" src="assets/demo.mp4" playsinline
  data-has-audio="true" data-start="0" data-duration="12"
  data-track-index="0" data-volume="1"></video>
```

### Images

`<img>` with `data-start` defaults to **3 seconds**; write `data-duration` only for another length.

### Media placement and trimming

- **`<video>`/`<audio>` work at ANY nesting depth**, including inside a sub-composition `<template>` or a wrapper `<div>`. The runtime discovers media with a flat `document.querySelectorAll("video, audio")`, resolves the host composition via `element.closest("[data-composition-id]")`, and **rebases local `data-start` by the accumulated absolute start of every ancestor composition**. A host at `2` with child media at `2` starts that media at root time `4`, consistently in preview, snapshot, extraction, and render.
- **New compositions should always use scene-local `data-start`.** Legacy projects that deliberately authored global-time media starts must mark the element `data-hf-media-start-basis="global"`; never infer the basis from overlapping numbers.
- **Do not animate timed media element dimensions** — animate a non-timed wrapper instead.
- **Cutting one source into multiple ranges:** duplicate the same video source into multiple clip elements, each selecting its range with `data-media-start` + `data-duration` and placing it with `data-start`. Do not try to keyframe source cutting.
- **Codecs:** render decodes video via **FFmpeg** (frames pre-extracted and injected), so **HEVC/H.265 (8/10-bit) renders correctly everywhere**. Live preview auto-proxies browser-hostile assets (transcodes + caches an H.264 copy; opt out with `--no-proxy` or `media.autoProxy: false`); lint emits info `hevc_preview_codec`.
- **Media that uses `<source>` children instead of a `src` attribute is not stamped** with the document-unique `data-hf-render-id`, so unique ids matter more there.

---

## 7. Fonts — guaranteeing determinism

### The rule

Lint **error** `font_family_without_font_face`:
> "Font family used without @font-face declaration: `<name>`. These are not in the auto-resolved font list, so the renderer cannot supply them automatically. **Text will fall back to a generic font**, producing incorrect typography in the video."

Fix hint:
> "Add `@font-face { font-family: '...'; src: url('capture/assets/fonts/...woff2'); }` for each font family, pointing to the captured `.woff2` files. **For an OS-bundled system font (e.g. Hiragino Sans, Microsoft YaHei) that has no downloadable file, use `src: local('Exact Font Name')` instead — the declaration alone satisfies this check without needing a font file.**"

**A family passes the check if ANY of these is true:**
1. It has an in-file `@font-face` declaration (verified: a `@font-face` pointing at a **nonexistent** `file:///fonts/x.woff2` passed lint), **or**
2. it is in the **auto-resolved bundled font list**, **or**
3. it is a recognized **system-font alias** (then it warns `system_font_will_alias` instead), **or**
4. it is a generic family, or in `FONT_ALIAS_KEYS`' exception set (`-apple-system`, `blinkmacsystemfont`, `inherit`, `initial`, `unset`, `revert`).

### Auto-resolved / bundled canonical fonts (18)

Verified from `FONT_ALIAS_MAP` in `chunk-HBBJFK6I.js`. These are fetched and embedded automatically:

`Inter`, `Montserrat`, `Outfit`, `Nunito`, `Oswald`, `League Gothic`, `Archivo Black`, `Space Mono`, `IBM Plex Mono`, `JetBrains Mono`, `EB Garamond`, `Playfair Display`, `Source Code Pro`, `Noto Sans JP`, `Roboto`, `Open Sans`, `Lato`, `Poppins`.

### System-font aliases — substituted automatically, with a WARNING

These map onto a bundled font and pass the `@font-face` check, but lint warns `system_font_will_alias`:
> "Font ... will be substituted at render time: `'Helvetica' → Inter`. **In distributed/Lambda rendering system-font capture is disabled** — these fonts will fall back to OS defaults. Embed explicit @font-face declarations instead."

| Alias | Substituted with |
|---|---|
| `Helvetica Neue`, `Helvetica`, `Arial`, `Helvetica Bold`, `SF Pro`, `SF Pro Display`, `SF Pro Text`, `SF Pro Rounded`, `Avenir`, `Avenir Next`, `Lucida Grande`, `Geneva`, `Optima`, `Verdana`, `Tahoma`, `Trebuchet MS`, `Calibri`, `Candara`, `Corbel`, `Lucida Sans`, `Lucida Sans Unicode`, `Noto Sans`, `DejaVu Sans`, `Liberation Sans` | **Inter** |
| `Futura`, `DIN Alternate`, `Arial Black` | **Montserrat** |
| `Bebas Neue` | **League Gothic** |
| `Courier New`, `Courier`, `SF Mono`, `Menlo`, `Monaco`, `Consolas`, `Lucida Console`, `Lucida Sans Typewriter`, `Andale Mono`, `DejaVu Sans Mono`, `Liberation Mono` | **JetBrains Mono** |
| `Garamond`, `Georgia`, `Palatino`, `Palatino Linotype`, `Book Antiqua`, `Cambria`, `Times`, `Times New Roman`, `DejaVu Serif`, `Liberation Serif` | **EB Garamond** |
| `Noto Sans Japanese` | **Noto Sans JP** |

**The one that FAILS lint** (verified empirically): `Hiragino Sans`, `Microsoft YaHei`, and any other name not in the lists above. Those need an explicit `@font-face { src: local('Exact Font Name') }`.

### Practical guidance

- **Safest:** use `Inter` (or another bundled canonical) with no `@font-face` at all — it is fetched and embedded for you. Both shipped templates use `font-family: Inter, ui-sans-serif, system-ui, sans-serif`.
- **Deterministic and explicit:** ship a local `.woff2` and declare `@font-face { font-family: 'X'; src: url('assets/fonts/x.woff2') format('woff2'); }`.
- **Distributed/Lambda renders have system-font capture DISABLED** — system fonts fall back to OS defaults. Embed explicit `@font-face` for anything you care about.
- There is a `hyperframes-localize-fonts` binary (`bin/hyperframes-localize-fonts.mjs`) and `injectDeterministicFontFaces` in the CLI that rewrites font declarations to embedded/local files; the renderer stamps `hyperframes-font-compiler-version` / `hyperframes-font-localizer-version` meta tags.
- Docker mode (`--docker`) is documented as the way to get "deterministic output with exact Chrome version **and fonts**"; local renders "may differ due to font availability and Chrome version".

---

## 8. Gotchas / common lint failures

`lint` reports `error` (must fix), `warning` (should fix), and `info` (only with `--verbose`). **A lint error also switches off the layout and contrast audits** — `check` then reports `0 sample(s)` and `0/0 text checks`, which reads like a clean file but means nothing ran.

### Guaranteed first-build failures (lint DOES catch them)

| Code | Sev | Message / rule |
|---|---|---|
| `gsap_css_transform_conflict` | **error** | `"<sel>" has CSS transform: <v> and a GSAP tween animates <props>. GSAP will overwrite the full CSS transform, discarding any translateX(-50%) centering or CSS scale value.` **Never pair a CSS initial `transform` with a GSAP tween on the same property.** Set the initial state inside the tween: `gsap.fromTo(el, { x: -40 }, { x: 0 })`. |
| `media_crossorigin_breaks_preview` | **error** | Never put `crossorigin` on `<video>`/`<audio>`. No suppression exists. |
| `video_nested_in_timed_element` | **error** | `<video data-start>` inside an ancestor that also has `data-start`. Time the wrapper **or** the video. |
| `media_missing_id` | **error** | Every `<audio>`/`<video>` needs an `id`; an id-less `<audio>` is never mixed → **silent** render. |
| `gsap_animates_clip_element` | **error** | Never tween a `.clip` with `autoAlpha` or `visibility`. Animate a child instead. |
| `font_family_without_font_face` | **error** | Named family with no in-file `@font-face` and not auto-resolved. |
| `standalone_composition_wrapped_in_template` | **error** | Root `index.html` wrapped in `<template>`. Only sub-comps use `<template>`. |
| `gsap_timeline_registered_before_async_build` | **error** | Registered the key before the async build finished. |
| `media_preload_none` | warning | `preload="none"` prevents the renderer loading the media. |

### Other high-frequency errors

- `root_missing_composition_id`, `root_missing_dimensions`, `root_composition_missing_duration_source`.
- `multiple_root_compositions` — multiple root-level HTML files with `data-composition-id`; "causing duplicate audio playback".
- `html_dir_attribute_breaks_render` — `<html dir="...">` "renders correctly in preview/snapshot but produces a **fully blank/black video from render** — a confirmed, silent failure."
- `non_deterministic_code` (7 patterns, §3); `requestanimationframe_in_composition`.
- `imperative_media_control` — inline script calling play/pause/seek on managed media.
- `base64_media_prohibited`.
- `deprecated_data_layer`, `deprecated_data_end`.
- `missing_local_asset`, `audio_src_not_found`, `placeholder_media_url`, `inaccessible_media_url`.
- `media_missing_data_start`, `media_src_kind_mismatch`, `video_missing_muted`, `video_muted_with_declared_audio`, `video_audio_double_source`, `duplicate_media_id`, `media_crossorigin_breaks_preview`.
- `missing_gsap_script`, `gsap_timeline_not_registered`, `missing_timeline_registry`.
- `host_missing_composition_id` — a `data-composition-src` host without `data-composition-id`.
- `subcomposition_root_styled_by_class` — style a sub-comp root with `#root`, not the host class.
- `invalid_inline_script_syntax`, `css_parse_error`, `unclosed_tag_swallowed_element`, `visible_markup_comment` ("CSS/JS block comment syntax `/* ... */` appears in visible HTML markup").
- `root_zoom_rescales_a_fixed_canvas`, `repeated_id_descendant_selector`, `runtime_hidden_style_opacity`, `gsap_undefined_css_variable`.
- `media_variable_src_no_fallback` (warning) — media with `data-var-src` needs a real fallback `src`.

### Warnings worth clearing

- `timed_element_missing_clip_class` — **not an error**; the runtime drives visibility off the `data-start` **attribute**, not the class. The scaffold's `.clip { position:absolute; inset:0 }` is what gives a scene its full-frame box.
- `clip_ends_past_root_duration` — a clip running past root `data-duration` is cut off. Extend the root duration in the same edit.
- `duplicate_audio_track` — two overlapping audible elements on one track index.
- `overlapping_gsap_tweens` — same target + same property from two tweens in an overlapping window.
- `system_font_will_alias`; `linked_clips_out_of_sync`; `linked_clip_orphan`.
- `nested_structure_needs_subcomposition` — a timeline element containing a nested element; "the timeline shows one row per top-level element, and the root composition is built only from sub-compositions."
- `root_composition_duration_derived`; `subcomposition_blanks_before_host`; `timeline_track_too_dense`; `composition_file_too_large`.
- `gsap_infinite_repeat` — warning with a finite root duration, **error** without.
- `gsap_repeat_ceil_overshoot`, `gsap_repeat_floor_unclamped`, `gsap_repeat_refresh_relative_value`, `gsap_relative_value_second_writer`, `gsap_cold_seek_hidden_fromto_missing_reveal`, `gsap_from_opacity_noop`, `gsap_fullscreen_overlay_starts_visible`, `gsap_function_value_hazard`, `gsap_callback_dom_measurement`, `gsap_group_selector_keyframes`, `gsap_timeline_return_used_as_tween`, `gsap_timeline_set_initial_hide`.

### Sub-composition pitfalls (pass static checks, break at render)

1. **`<style>`/`<script>` in `<head>` instead of inside `<template>`** — the assembler drops the templated file's own `<head>` `<style>`/`<script>` (the `hasTemplate` gate in `compositionAssembly.ts`). Symptom: checks pass, render completes, but text is tiny/unstyled and SVGs blow up to canvas size. `<link>` is hoisted either way.
2. **Host `data-composition-id` ≠ inner template `data-composition-id`** — lint passes (each file is individually valid) but the render logs `Sub-composition timelines not registered after 45000ms: <host-id>`, waits 45 s per scene, then captures static initial frames. **Give the host slot, the inner template root, and the `window.__timelines` key the SAME id.**
3. **Styling the sub-comp root by a class** — `subcomposition_root_styled_by_class`, error. Use `#root`.
4. Sub-comp root uses `width`/`height: 100%` (or `inset: 0`), **not** hardcoded `1920px`/`1080px`.
5. Prefer `gsap.fromTo()` over `gsap.from()` for entrance tweens — the host re-seeks the sub-comp every time its clip becomes visible, and `from()` records its start state at registration and can desync on seek-back.
6. A sub-comp timeline **cannot reach or animate host elements** — neither `document.querySelector("#host-id")` nor a gsap selector string resolves across the boundary. Drive host-root media from the **main** timeline at **global** time.
7. The host clip's `data-duration` is the slot's visible window and **takes precedence** over the sub-comp's internal timeline length. A shorter internal timeline holds its last frame; an elapsed slot goes blank. Leaving a single full-bleed sub-comp shorter than the composition is flagged `subcomposition_blanks_before_host`.
8. **Do not** manually `master.add(child)` a sub-comp timeline into the host — causes double-seeks.

### Layout / text (silent; automated gates may miss)

- **Do not use `<br>` in body text.** Forced breaks ignore the rendered font width and produce an extra break when a line already wraps naturally → overlap. Exception: short display titles with each word deliberately on its own line.
- **Transformed elements must be block-level + sized.** `transform`/`scaleX`/`scaleY` is a no-op on an inline `<span>`, and scaling an auto-width (0px) element shows nothing. Give them `display: block`/`inline-block`/flex-item **and** a real `width`/`height`.
- **Absolutely-positioned decoratives that pulse or overshoot** (`yoyo` scale, `back.out`) need clearance at their **peak** size and must not straddle an `overflow: hidden` edge.
- **Scene containers** should fill with `width:100%; height:100%; box-sizing:border-box`.
- **Center with flex/`inset`, not CSS `transform: translate(-50%,-50%)`** on a node you then GSAP `x`/`y` (that is the `gsap_css_transform_conflict` trap). Use `fromTo` or `xPercent`/`yPercent`.

### `check`-level gates (not lint)

- **`sweep_static`** — if a 3 s+ composition shows no visible change across every sample, `check` **fails**: "a frozen timeline makes every green verdict unreliable." Opacity-only reveals count as motion only while still in flight. **The classic trap:** a reveal that completes early and then holds static for the rest of the duration — every sample lands on the settled state. Spread the reveal across the timeline or keep one continuously animated element alive. A composition meant to be still takes `data-no-timeline` on its root.
- **Contrast** failures are **errors** (WCAG AA: 4.5:1 normal, 3:1 large ≥24px or ≥19px bold).
- **Escape hatches** (mark intent in HTML, then re-run): `data-layout-allow-overflow`, `data-layout-allow-overlap`, `data-layout-allow-occlusion`, `data-layout-allow-caption-zone`, `data-layout-ignore`. **`data-layout-allow-overflow` inherits down the subtree and silences more than the overflow audit** — prefer the narrowest decorative wrapper.

### Variable gotchas

- `data-composition-variables` is an **array of declarations** (schema) on `<html>`: `[{id, type, label, default}]`. `--variables` and `data-variable-values` are **objects keyed by id** (values). Easy to confuse.
- The declaration is invalid without all of `id`, `type`, `label`, `default`; `enum` additionally **requires** `options: [{value,label}]`.
- `data-var-src` substitutes `src`; `data-var-text` substitutes the element's own text (children preserved). Every scalar variable is auto-applied as a `--{id}` CSS custom property on the composition root.

---

## 9. Sub-composition wiring (complete, copy-pasteable)

**Host** (in `index.html`):

```html
<div
  id="chart"
  data-composition-id="data-chart"
  data-composition-src="compositions/data-chart.html"
  data-start="2"
  data-duration="8"
  data-track-index="2"
  data-width="1920"
  data-height="1080"
  data-variable-values='{"title":"Q4 Revenue","accent":"#66d9ef"}'
></div>
```

**Sub-composition file** (`compositions/data-chart.html`):

```html
<!doctype html>
<html>
  <head>
    <meta charset="UTF-8" />
    <!-- head is metadata for the source file only; the runtime ignores it -->
  </head>
  <body>
    <template>
      <!-- EVERYTHING the runtime needs goes here: styles, markup, scripts -->
      <style>
        #root { position: absolute; inset: 0; color: #fff; }
      </style>

      <div id="root" data-composition-id="data-chart" data-width="1920" data-height="1080">
        <h1 id="title">Fallback</h1>
      </div>

      <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
      <script>
        const tl = gsap.timeline({ paused: true });
        tl.fromTo("#title", { opacity: 0, y: 24 }, { opacity: 1, y: 0, duration: 0.6 }, 0);
        window.__timelines["data-chart"] = tl;
      </script>
    </template>
  </body>
</html>
```

Declaring variables on the sub-composition's `<html>`:

```html
<html data-composition-variables='[
  {"id":"title","type":"string","label":"Title","default":"Hello"},
  {"id":"color","type":"color","label":"Color","default":"#111827"}
]'>
```

CLI render override: `npx hyperframes render --variables '{"title":"Q4 Report"}'`, or `--variables-file`, plus `--strict-variables` in CI.

---

## 10. Verification workflow

```bash
npx hyperframes lint                 # fast static feedback while iterating
npx hyperframes lint --verbose       # include info-level findings
npx hyperframes lint --json          # machine-readable
npx hyperframes check                # REQUIRED final gate: lint + runtime + layout + motion + contrast
npx hyperframes check --snapshots    # annotated overview frames + per-finding crops
npx hyperframes snapshot --at <t1>,<t2>,<t3>   # sub-composition midpoint smoke test
npx hyperframes timeline --json      # what is actually on the timeline (tracks/clips/absStart/absEnd)
npx hyperframes preview --background # Studio handoff; verify it returns HTTP 200
npx hyperframes render --quality looks --output out.mp4
ffprobe -v error -show_format -show_streams out.mp4   # compare duration to root data-duration
```

- `validate`, `inspect`, `layout` are **deprecated aliases** for `check`; do not use them in new scripts.
- Render qualities: `draft` (iterate), **`looks` (the default, CRF 16)**, `delivery` (final). (`standard`/`high` are legacy aliases.)
- After a render, read the summary's second line: `beginframe` vs `screenshot`, GPU mode, stage timings. `screenshot` + `software gpu` on Linux is the slow path.
- Optional `*.motion.json` sidecar next to the composition lets `check` verify motion intent: `appearsBy`, `before`, `staysInFrame`, `keepsMoving`.

---

## 11. Explicitly UNVERIFIED

- Whether `hyperframes.heygen.com/introduction`, `/quickstart`, `/guides/gsap-animation` exist or match the installed v0.8.141 contract — **not fetched**. The docs' own index is `https://hyperframes.heygen.com/llms.txt`.
- The upstream GitHub source tree (`packages/core/src`) — **not reachable** (404). All parser/types claims here come from the bundled/minified equivalents (`chunk-TQFG7HTI.js` exposes `COMPOSITION_ATTRIBUTES`, `readClipTiming`, `readDuration`, `readTrack`) and the shipped skill docs.
- Whether any env var or `hyperframes.json` key suppresses the audio track at render time. No CLI flag was found.
- Local (non-Docker) vs Docker font determinism was not empirically compared; the docs only assert Docker is deterministic.
- Whether remote `https://` media actually survives the full `check` request audit and render — I verified only that lint does not statically reject non-placeholder remote URLs.
- `data-hf-media-start-basis="global"`, `data-no-pick`, `data-hyperframes-ignore`, and other Studio-internal `data-*` names appear in the runtime bundle but are **not documented authoring surface**; treat as internal.
