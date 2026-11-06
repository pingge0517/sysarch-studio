/**
 * Polarion push/pull and consistency checks.
 *
 * <p>T7: /sync/push and /sync/pull (idempotent, all-or-nothing, rate-limited, audited), see {@link com.example.sysarch.sync.SyncService}.
 * TODO(T8): consistency rules R1-R5, /consistency/report and /consistency/repair, webhooks.
 */
package com.example.sysarch.sync;
