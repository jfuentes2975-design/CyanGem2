package com.cyangem.ui

// =============================================================================
// HC-018 — Deprecation stub.
//
// The HC-016 / HC-017 types (RecentMedia, MediaQueryResult, MediaType,
// MediaStoreQuery, formatMediaTimestamp) have been replaced by:
//
//   CyanMediaItem.kt          — CyanMediaItem, CyanMediaType, CyanMediaResult, formatCyanTimestamp
//   MediaBridgeRepository.kt   — MediaBridgeRepository (replaces MediaStoreQuery object)
//
// New names match the HC-018 technical fix-forward document.
//
// This file is intentionally empty (apart from this comment). It exists in
// the patch package to overwrite the HC-017 file with a clean replacement
// — preventing duplicate-symbol compile errors.
//
// If future code still imports `com.cyangem.ui.MediaStoreQuery` or
// `com.cyangem.ui.RecentMedia`, the build will fail with a clear
// "Unresolved reference" pointing the developer to the new types. That is
// the desired behavior — HC-018 callers should use the new names directly.
// =============================================================================
