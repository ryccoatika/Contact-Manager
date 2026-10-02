# Changelog

All notable changes to Contact Manager are documented here. This project adheres
to [Keep a Changelog](https://keepachangelog.com/) and
[Semantic Versioning](https://semver.org/).

## [1.2] - 2026-10-02
- Updated the crash-reporting and analytics libraries.

## [1.1] - 2026-09-17
- Export contacts to a file (vCard): a whole account, several accounts at
  once (as one combined file or one file per account), or just the contacts
  you've selected — from Settings › Accounts or the contacts list.
- Import contacts from a file into one or more accounts, including SIM
  (name and first number only) — choose exactly which contacts to
  bring in, and see what will be skipped or lost, before it starts.
- Copy contacts without moving them: "Copy to…" for selected contacts and
  "Copy all contacts to…" on any account chip or in Settings › Accounts —
  works on app-managed accounts too (e.g. WhatsApp), whose contacts can now
  be copied out even though they can't be moved.
- Deleting a selection that spans several accounts now asks which accounts to
  delete from, so you can clear a contact's Google entry while keeping its
  SIM copy.
- The search box now tucks away while you scroll down the contact list and
  glides back on scroll-up — or tap the new search icon in the top bar to
  bring it back with the keyboard ready.
- New Settings screen (from the Contacts top bar): Accounts, Theme, a "Rate this
  app" shortcut to Google Play, Contact developer, and About.
- Light, Dark, or System theme, chosen from Settings and remembered.
- Contact developer form — choose a category, write a message; the developer
  email is tappable to compose or long-press to copy.
- About screen with app info, description, and a Privacy Policy link.
- Fixed a crash when the contacts permission is revoked while the app runs.
- Fixed the permission screen title being unreadable in dark mode.
- Automatic in-app updates via Google Play.
- Smoother open animations for a contact's details and the add/edit screen.
- Smoother fast scrolling of the contact list.
- Reworked swipe-to-delete: a short swipe reveals a tappable delete button, a
  long swipe (with a strong buzz) asks to confirm right away.
- Long-press and drag to select (or deselect) many contacts in one sweep, with
  edge auto-scroll.
- The add button tucks away while scrolling down and returns when scrolling up.
- Long-press an account chip to hide it or move all its contacts, right from
  the contact list.
- New splash screen that follows the device's dark mode; app launch no longer
  flashes white in the dark theme.
- A friendly screen appears if the app ever crashes, with a one-tap restart;
  the error is reported automatically.

## [1.0] - 2026-08-07
- Initial feature
