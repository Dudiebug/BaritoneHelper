package dev.dudie.baritonehelper.internal.baritone.cache;

/** Completeness of one target's observations in one chunk. */
public enum CoverageState {
   UNKNOWN,
   SCANNING,
   SCANNED,
   DIRTY
}
