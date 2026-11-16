# User-defined access profiles for study timer sessions

## Problem Statement

The three fixed access profiles do not let a person choose which installed Android apps remain available during a study timer session. Repeatedly choosing the same apps should be easy, and a session should keep a predictable set of available apps once it starts.

## Solution

On Android, let the user create and manage named access profiles from the currently installed, launchable apps. Before starting a study timer session, the user can choose one saved profile; if they choose none, the fixed Essentials profile applies. The selected apps remain available and other eligible apps are blocked. Show a warning before starting that the choice cannot change during the session.

## User Stories

1. As an Android user, I want to see the apps currently installed on my phone, so that I can decide which remain available while studying.
2. As an Android user, I want launchable system apps such as Messages and Settings in the picker, so that I can decide whether they remain available.
3. As an Android user, I want calls, emergency access, the home screen, keyboard, and Dimky to remain available, so that a profile cannot make essential phone functions inaccessible.
4. As an Android user, I want to create a named access profile, so that I can reuse my preferred app selection.
5. As an Android user, I want to select any combination of eligible apps, so that the access profile fits my study activity.
6. As an Android user, I want to save a profile with no selected optional apps, so that only permanently available apps remain accessible.
7. As an Android user, I want to see my saved profiles when starting a study timer session, so that I can choose one quickly.
8. As an Android user, I want Essentials to apply when I choose no saved profile, so that sessions have a predictable default.
9. As an Android user, I want to edit a saved profile outside an active session, so that I can adapt it when my needs change.
10. As an Android user, I want to delete an unused saved profile, so that obsolete choices do not clutter the list.
11. As an Android user, I want the app to prevent editing or deleting a profile used by an active session, so that the session's access rules remain stable.
12. As an Android user, I want a warning before starting that I cannot change the chosen profile during the session, so that I can check my choice first.
13. As an Android user, I want the chosen profile to remain fixed until the session ends, so that I cannot bypass the restriction by switching profiles.
14. As an Android user, I want apps uninstalled from my phone to disappear from saved profiles, so that profiles reflect apps I currently have.
15. As an Android user, I want a reinstalled app to require selection again, so that an old profile does not silently grant it access.
16. As an Android user, I want the fixed ChatGPT and Google Search choices removed, so that the session screen reflects the new profile model.
17. As a planner, I want planned blocks to describe intended work without choosing a phone access profile, so that planning and starting a study timer stay separate.
18. As a user starting a session from a browser in the future, I want to choose an Android-created access profile by name, so that the same phone rules apply regardless of where I start.

## Implementation Decisions

- Keep the term **Access profile** for a set of allowed Android apps. Selected apps are allowed; other eligible apps are blocked.
- Replace the three fixed choices with the fixed Essentials fallback and user-created named profiles. Essentials cannot be edited or deleted. The two other fixed choices are removed.
- Manage profiles on Android. The profile editor enumerates currently installed launchable apps, including launchable system apps. Permanently available phone functions are excluded from restrictions.
- Resolve saved profiles against the current installed-app inventory. Remove an app from a saved profile when it is uninstalled; reinstalling it does not restore its former selection.
- A study timer session chooses one saved profile or Essentials. There is no unsaved one-session selection and no automatic reuse of the previous session's choice.
- Persist the selected profile and the allowed package set needed to enforce an active session consistently. Prevent profile switching and all edits or deletion of its profile while that session is active. Release the lock when the session ends.
- Show a pre-start warning that the chosen access profile cannot change during the session.
- Planned blocks do not choose access profiles. Remove their existing profile association as part of this change while preserving their planning and desktop-site scheduling roles.
- Update session persistence and synchronization so a resumed session enforces the same allowed apps. Keep device-specific package identifiers meaningful to Android; browser surfaces use profile names for selection when browser work is implemented.
- Review the existing Android site filtering rules tied to the three fixed profiles so removing those profiles does not accidentally broaden or break site restrictions.

## Testing Decisions

- Test observable behavior: profile creation, selection, persistence, active-session locking, and which apps are available. Avoid tests of private implementation details.
- Use the existing Android flow test as the main seam, extending the flow from profile creation through study timer start and enforcement. Use focused policy tests for package-availability cases that the flow test cannot reliably exercise on an emulator.
- Cover Essentials fallback, a custom profile, zero optional apps, permanently available functions, uninstalled and reinstalled apps, and blocked edits, deletion, and switching during an active session.
- Follow the existing Android flow and focus policy test patterns already in the codebase.

## Out of Scope

- Creating or editing access profiles in a browser.
- Implementing profile selection in browser extensions or additional browsers now; record this as a future capability.
- Choosing apps while editing planned blocks.
- Unsaved per-session app selections or changing the selected profile after a session starts.

## Further Notes

The current Android implementation stores one of three access-profile enum values on study sessions and planned blocks, and its accessibility policy always allows a hard-coded set of essential packages. The change needs migration for existing stored values and separation of permanently available functions from optional apps currently grouped under Essentials. Existing synchronization decisions in ADR-0003 still apply to study session state.
