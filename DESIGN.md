---
name: Precision Transfer
colors:
  surface: '#faf8ff'
  surface-dim: '#d2d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f3ff'
  surface-container: '#eaedff'
  surface-container-high: '#e2e7ff'
  surface-container-highest: '#dae2fd'
  on-surface: '#131b2e'
  on-surface-variant: '#464555'
  inverse-surface: '#283044'
  inverse-on-surface: '#eef0ff'
  outline: '#777587'
  outline-variant: '#c7c4d8'
  surface-tint: '#4c42e9'
  primary: '#493ee5'
  on-primary: '#ffffff'
  primary-container: '#635bff'
  on-primary-container: '#fefaff'
  inverse-primary: '#c3c0ff'
  secondary: '#6b38d4'
  on-secondary: '#ffffff'
  secondary-container: '#8455ef'
  on-secondary-container: '#fffbff'
  tertiary: '#006a2c'
  on-tertiary: '#ffffff'
  tertiary-container: '#008639'
  on-tertiary-container: '#f2ffee'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#321ed2'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d0bcff'
  on-secondary-fixed: '#23005c'
  on-secondary-fixed-variant: '#5516be'
  tertiary-fixed: '#7ffc97'
  tertiary-fixed-dim: '#62df7d'
  on-tertiary-fixed: '#002109'
  on-tertiary-fixed-variant: '#005320'
  background: '#faf8ff'
  on-background: '#131b2e'
  surface-variant: '#dae2fd'
typography:
  display:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.03em
  display-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.025em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: -0.011em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: -0.006em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0em
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
    letterSpacing: -0.005em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.04em
  code-inline:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-sm: 1rem
  gutter-lg: 2rem
  margin: 2rem
  margin-sm: 1rem
  margin-lg: 4rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes an exacting, developer-grade file and text exchange environment inspired by modern operational tools. The brand persona blends the engineering rigor and speed of Linear with the refined, trustworthy finish of Stripe. The interface speaks to developers, designers, and high-velocity product teams who demand friction-free sharing without visual clutter.

### Aesthetic Paradigm
- **Linear-meets-Stripe Precision:** Pristine layout density, microscopic spatial alignment, and deliberate typography.
- **Atmospheric Clarity:** Surfaces rely on high-purity off-whites and true whites, letting vibrant indigo accents guide attention to core transfer actions.
- **Engineered Micro-Interactions:** Snappy hover states, subtle border illuminations, and soft kinetic transitions convey responsiveness and reliability.
- **Utilitarian Elegance:** Information is scannable at a glance through crisp tabular structures, monochrome metadata, and intentional pill indicators.

## Colors

The palette is tuned for extreme contrast, legibility, and focused task execution:

- **Canvas & Surface:** Base canvas runs on ultra-clean Slate 50 (`#F8FAFC`), while interactive panels, modal views, and drop zones use pure White (`#FFFFFF`). Subsurface panels use Slate 100 (`#F1F5F9`).
- **Primary Accent (`#635BFF`):** Vibrant Stripe-grade indigo for primary actions, active upload strokes, and key focus rings.
- **Secondary Accent (`#8B5CF6`):** Electric violet for multi-file gradients, progress fills, and supplementary interactive states.
- **Neutrals:**
  - `slate-900` (`#0F172A`): Primary headings, critical labels, and dark interactive elements.
  - `slate-700` (`#334155`): Secondary body text and active icon fills.
  - `slate-500` (`#64748B`): Timestamps, file size metadata, and placeholder copy.
  - `slate-200` (`#E2E8F0`): Subtle structural borders (`rgba(226, 232, 240, 0.8)`).
- **Semantics:**
  - Success (`#16A34A`): Completed uploads, copied-to-clipboard badges, active links.
  - Error (`#EF4444`): Transfer timeouts, file size rejections, expired links.
  - Warning (`#F59E0B`): Pending revocations and auto-destruction countdowns.

## Typography

The typographic hierarchy pairs Plus Jakarta Sans for confident, modern, geometric display moments with Inter for dense, neutral utility in tabular listings, input panels, and transfers. JetBrains Mono serves technical snippets, access tokens, hash sums, and ephemeral URLs.

- **Display & Headings:** Tight tracking (`-0.03em` to `-0.015em`) with compact vertical metrics creates a razor-sharp, editorial tone.
- **Body & Data:** Neutral vertical height (`1.4`–`1.5`) maximizes legibility across file queues, expiration options, and metadata streams.
- **Metadata Labels:** `label-sm` utilizes subtle uppercase treatment with positive tracking (`0.04em`) for status flags and column table headers.

## Layout & Spacing

The structural framework is built on an 8pt spatial grid with an underlying 4pt subgrid for precise component alignment.

### Grid Framework
- **Desktop (>= 1280px):** 12-column fluid grid, maximum content container width `1200px`, `margin-lg` (64px) outer margin, `gutter-lg` (32px) columns. Primary transfer hubs sit center-stage spanning 8 columns or fixed at 640px.
- **Tablet (768px – 1279px):** 8-column layout, `margin` (32px), `gutter` (24px).
- **Mobile (< 768px):** 4-column layout, `margin-sm` (16px), `gutter-sm` (16px). Cards span 100% width with sticky bottom action anchors.

### Content Arrangement
- **Transfer Canvas:** Focused, distraction-free central zone flanked by generous breathing room. Secondary data (audit logs, previous drops) rests in compact side drawers or underneath the primary workspace card.

## Elevation & Depth

This system shuns heavy drop shadows in favor of ambient, multi-stop diffusion paired with hairline borders.

- **Baseline Cards:** Border `1px solid rgba(226, 232, 240, 0.8)` layered with a dual-step shadow:
  - `0 1px 2px rgba(15, 23, 42, 0.04), 0 4px 12px rgba(15, 23, 42, 0.03)`
- **Hovered Elements:** Micro-elevation with indigo tinting:
  - `0 2px 4px rgba(15, 23, 42, 0.04), 0 12px 24px -4px rgba(99, 91, 255, 0.08)`
  - Border transition: `rgba(99, 91, 255, 0.25)`
- **Modals & Flyouts:** High diffusion with deep anchor:
  - `0 8px 30px rgba(15, 23, 42, 0.08), 0 0 1px rgba(15, 23, 42, 0.12)`
- **Drop Zones (Active Drag):** Border switches to dashed `1.5px solid #635BFF` with an inner ring glow:
  - `0 0 0 4px rgba(99, 91, 255, 0.08)`

## Shapes

The geometry balances soft industrial curves with tight architectural clarity. A roundedness factor of `2` provides standard radii of `8px` (`0.5rem`), transitioning up to `16px` (`1rem`) for larger surface cards.

- **Cards & Modals:** `rounded-lg` (16px) creates self-contained, welcoming focal points.
- **Inputs & Interactive Controls:** `rounded` (8px) for buttons, text areas, and segment bars.
- **Badges & Pills:** Full curvature (`rounded-full` / 9999px) for status indicators, tag metadata, and link pills.

## Components

### Buttons
- **Primary:** Solid `#635BFF` background, `#FFFFFF` text, subtle top inner highlight (`inset 0 1px 0 rgba(255,255,255,0.2)`), hover background `#5248E5`, active transform `scale(0.985)`.
- **Secondary / Ghost:** White background, border `1px solid rgba(226, 232, 240, 0.8)`, text `#0F172A`, hover background `#F8FAFC`.
- **Destructive:** White background with red text (`#EF4444`), hover border `#FCA5A5`, hover background `#FEF2F2`.

### Cards & Tabbed Containers
- **Container Structure:** Pure white canvas with hairline slate borders. Header tabs operate within an inset pill dock (`#F1F5F9` background, `4px` padding) where active tabs lift via crisp white cards and micro-shadows.
- **Drop Zone Component:** Centered inside the main card with subtle dashed lines, featuring drag-over transitions that shift background from `#FFFFFF` to `#F8FAFC` and border to `#635BFF`.

### Input Fields & Secret Text Areas
- **Text Inputs:** Monospace-friendly, `#FFFFFF` background, border `1px solid #CBD5E1`. On focus: border `#635BFF`, ring `3px solid rgba(99, 91, 255, 0.12)`.
- **Snippet Area:** Dedicated dark-tinted or clean monospace box with one-click copy accessory docked to the top right.

### Badges & Status Pills
- **Success (Uploaded/Verified):** Pill container with `#F0FDF4` background, `#16A34A` text, and a `6px` solid dot indicator.
- **Expiring/Counter:** `#FEF2F2` background, `#EF4444` text, displaying countdown clocks or hit limits.
- **Format Tag:** Slate-100 container (`#F1F5F9`), `#475569` text, `font-mono`, `text-[11px]`.

### Checkboxes & Radios
- Crisp `16px` boxes with `4px` corner radii. Checked state fills `#635BFF` with an embedded white check mark icon. Radio controls follow matching accent states with a centered circular dot.

### Transfer Progress List
- Individual rows feature file icon badges, truncated file names (`#0F172A`), live percentage indicators (`#64748B`), and an ultra-thin `3px` bottom or embedded progress track driven by `#635BFF`.