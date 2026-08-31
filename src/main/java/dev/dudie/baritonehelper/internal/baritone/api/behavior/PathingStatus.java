package dev.dudie.baritonehelper.internal.baritone.api.behavior;

/** Observable state of one embedded Baritone path request. */
public enum PathingStatus {
   IDLE,
   CALCULATING,
   PATH_FOUND,
   EXECUTING,
   ARRIVED,
   NO_PATH,
   CANCELLED,
   FAILED
}
