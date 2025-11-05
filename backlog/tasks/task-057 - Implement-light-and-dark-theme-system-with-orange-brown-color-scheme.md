---
id: task-057
title: Implement light and dark theme system with orange/brown color scheme
status: Done
assignee:
  - '@assistant'
created_date: '2025-11-05 21:06'
updated_date: '2025-11-05 21:16'
labels:
  - frontend
  - ui
  - theme
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add support for light and dark themes with a toggle. Dark theme should use orange colors for fonts and UI details, and dark brown colors for backgrounds and components.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Dark theme uses orange (#f97316 or similar) for text and UI accents
- [x] #2 Dark theme uses dark brown (#3e2723 or similar) for backgrounds and components
- [x] #3 Theme toggle component is implemented and accessible
- [x] #4 Theme preference is persisted to localStorage
- [x] #5 Theme changes apply smoothly across all pages and components
- [x] #6 System preference (prefers-color-scheme) is respected on first load
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Update index.css with orange/dark brown color scheme for dark theme
2. Create a ThemeContext and ThemeProvider to manage theme state
3. Add localStorage persistence and system preference detection
4. Create a ThemeToggle component (sun/moon icon button)
5. Integrate ThemeProvider in App.tsx
6. Add ThemeToggle to Layout component navigation bar
7. Test theme switching across all pages
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented complete light/dark theme system with the following components:

**Color Scheme (index.css)**
- Dark theme uses very dark backgrounds (almost black with brown tint #1a1412)
- Light gray text (#ebebeb) for main content
- Orange (#f97316) used sparingly for primary buttons and accents only
- Much darker overall appearance per user requirements

**ThemeContext (contexts/ThemeContext.tsx)**
- Created React context with theme state management
- Automatic localStorage persistence
- System preference detection (prefers-color-scheme)
- Theme applied to document root with CSS class

**ThemeToggle Component (components/ThemeToggle.tsx)**
- Sun/Moon icon button using lucide-react
- Accessible with aria-label and title
- Integrated into Layout navigation bar

**Integration**
- ThemeProvider wraps entire app in App.tsx
- Theme toggle visible in navigation bar next to logout button
- Works seamlessly across all pages (Dashboard, Operations, Batches)

The theme persists across page reloads and respects the user's system preference on first visit. Dark theme is very dark with orange used only as accent color for interactive elements.
<!-- SECTION:NOTES:END -->
