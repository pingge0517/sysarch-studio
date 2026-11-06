/**
 * Polarion push/pull and consistency checks.
 *
 * <p>T7: /sync/push and /sync/pull (idempotent, all-or-nothing, rate-limited, audited), see {@link com.example.sysarch.sync.SyncService}.
 * T8: consistency rules R1-R5, report, repair and webhooks in {@link com.example.sysarch.sync.consistency}.
 */
package com.example.sysarch.sync;
