# Fix startup crash and channel loading

## Goal
Fix the Android app issue where it crashes a few seconds after launch and fails to display any channel list.

## Requirements
- Prevent app crash during startup flow after channel loading begins.
- Ensure channel list is shown when playlist fetch and parse succeed.
- Handle playlist fetch/parse/playback failures safely without app crash.
- Keep existing user-visible behavior unchanged except for stability and proper channel list display.

## Acceptance Criteria
- [ ] App no longer crashes after launch in normal startup flow.
- [ ] Category and channel list are visible when playlist is reachable and valid.
- [ ] If playlist loading fails, app stays alive and shows failure feedback.
- [ ] If first channel autoplay fails, app stays alive and user can still browse channels.
- [ ] Project lint/type/build checks pass.

## Technical Notes
- Focus on startup chain in MainActivity: loadChannels -> update UI -> autoplay first stream.
- Review ChannelRepository network error handling and M3uParser parse assumptions.
- Add defensive handling only at true boundaries (network/media parsing/playback callbacks).
