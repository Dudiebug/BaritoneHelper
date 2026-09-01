package dev.dudie.baritonehelper.internal.baritone.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Policy-neutral target observations shared by workers in one dimension. */
public final class TargetCoverageLedger {
   private final Map<String, Map<Long, ChunkKnowledge>> targets = new HashMap<>();
   private final Map<Long, Long> chunkRevisions = new HashMap<>();
   private final Runnable dirtyListener;
   private long nextScanLease = 1L;

   public TargetCoverageLedger() {
      this(() -> {});
   }

   public TargetCoverageLedger(Runnable dirtyListener) {
      this.dirtyListener = dirtyListener == null ? () -> {} : dirtyListener;
   }

   // ponytail: one lock is enough for two scanner threads; shard by target only if profiling proves contention.
   public synchronized CoverageState state(String target, long chunk) {
      ChunkKnowledge knowledge = target(target, false).get(chunk);
      return knowledge == null ? CoverageState.UNKNOWN : knowledge.state;
   }

   public synchronized boolean beginScan(String target, long chunk) {
      return this.beginScanRevision(target, chunk) >= 0L;
   }

   public synchronized long beginScanRevision(String target, long chunk) {
      ChunkKnowledge knowledge = target(target, true).computeIfAbsent(chunk, ignored -> new ChunkKnowledge());
      if (knowledge.state == CoverageState.SCANNING || knowledge.state == CoverageState.SCANNED) return -1L;
      knowledge.state = CoverageState.SCANNING;
      knowledge.scanRevision = this.chunkRevisions.getOrDefault(chunk, 0L);
      knowledge.scanLease = nextScanLease();
      dirtyListener.run();
      return knowledge.scanLease;
   }

   public synchronized void publish(String target, long chunk, Collection<Long> locations) {
      ChunkKnowledge knowledge = target(target, true).computeIfAbsent(chunk, ignored -> new ChunkKnowledge());
      replaceLocations(knowledge, locations);
      knowledge.state = CoverageState.SCANNED;
      knowledge.scanLease = 0L;
      dirtyListener.run();
   }

   public synchronized boolean publishIfRevision(
         String target, long chunk, long expectedLease, Collection<Long> locations) {
      ChunkKnowledge knowledge = target(target, false).get(chunk);
      if (!ownsCurrentScan(knowledge, chunk, expectedLease)) return false;
      replaceLocations(knowledge, locations);
      knowledge.state = CoverageState.SCANNED;
      knowledge.scanLease = 0L;
      dirtyListener.run();
      return true;
   }

   public synchronized void addLocation(String target, long chunk, long location) {
      target(target, true).computeIfAbsent(chunk, ignored -> new ChunkKnowledge()).locations.add(location);
   }

   public synchronized void markScanned(String target, long chunk) {
      ChunkKnowledge knowledge = target(target, true).computeIfAbsent(chunk, ignored -> new ChunkKnowledge());
      knowledge.state = CoverageState.SCANNED;
      knowledge.scanLease = 0L;
      dirtyListener.run();
   }

   public synchronized void abortScan(String target, long chunk) {
      ChunkKnowledge knowledge = target(target, false).get(chunk);
      if (knowledge != null) this.abortScan(target, chunk, knowledge.scanLease);
   }

   public synchronized void abortScan(String target, long chunk, long expectedLease) {
      ChunkKnowledge knowledge = target(target, false).get(chunk);
      if (ownsCurrentScan(knowledge, chunk, expectedLease)) {
         knowledge.state = CoverageState.DIRTY;
         knowledge.scanLease = 0L;
         dirtyListener.run();
      }
   }

   public synchronized void markDirty(long chunk) {
      boolean tracked = targets.values().stream().anyMatch(chunks -> chunks.containsKey(chunk));
      if (!tracked) return;
      this.chunkRevisions.merge(chunk, 1L, Long::sum);
      boolean changed = false;
      for (Map<Long, ChunkKnowledge> chunks : targets.values()) {
         ChunkKnowledge knowledge = chunks.get(chunk);
         if (knowledge != null) {
            changed |= knowledge.state != CoverageState.DIRTY || !knowledge.locations.isEmpty();
            knowledge.state = CoverageState.DIRTY;
            knowledge.scanLease = 0L;
            knowledge.locations.clear();
         }
      }
      if (changed) dirtyListener.run();
   }

   /**
    * Applies an exact single-position mutation. Unaffected scanned targets keep
    * their coverage, while in-flight scans are fenced by the chunk revision.
    */
   public synchronized void recordBlockChange(
         long chunk, long position, String beforeTarget, String afterTarget) {
      boolean tracked = targets.values().stream().anyMatch(chunks -> chunks.containsKey(chunk));
      if (!tracked) return;
      this.chunkRevisions.merge(chunk, 1L, Long::sum);
      boolean changed = false;
      for (Map.Entry<String, Map<Long, ChunkKnowledge>> entry : targets.entrySet()) {
         ChunkKnowledge knowledge = entry.getValue().get(chunk);
         if (knowledge == null) continue;
         boolean affected = entry.getKey().equals(beforeTarget) || entry.getKey().equals(afterTarget);
         if (knowledge.state == CoverageState.SCANNING || affected) {
            changed |= knowledge.state != CoverageState.DIRTY;
            knowledge.state = CoverageState.DIRTY;
            knowledge.scanLease = 0L;
         }
         if (affected) {
            changed |= knowledge.locations.remove(position);
            if (entry.getKey().equals(afterTarget)) changed |= knowledge.locations.add(position);
         }
      }
      if (changed) dirtyListener.run();
   }

   public synchronized Set<Long> locations(String target, long chunk) {
      ChunkKnowledge knowledge = target(target, false).get(chunk);
      return knowledge == null
            || knowledge.state != CoverageState.SCANNED && knowledge.state != CoverageState.DIRTY
            ? Set.of() : Set.copyOf(knowledge.locations);
   }

   public synchronized Set<Long> allLocations(String target) {
      Map<Long, ChunkKnowledge> chunks = target(target, false);
      if (chunks.isEmpty()) return Set.of();
      Set<Long> result = new HashSet<>();
      chunks.values().stream()
            .filter(knowledge -> knowledge.state == CoverageState.SCANNED
                  || knowledge.state == CoverageState.DIRTY)
            .forEach(knowledge -> result.addAll(knowledge.locations));
      return Set.copyOf(result);
   }

   public synchronized int count(String target, CoverageState state) {
      return (int) target(target, false).values().stream().filter(value -> value.state == state).count();
   }

   public synchronized int locationCount(String target) {
      return target(target, false).values().stream()
            .filter(value -> value.state == CoverageState.SCANNED)
            .mapToInt(value -> value.locations.size())
            .sum();
   }

   public synchronized boolean anyScanned(long chunk) {
      return targets.values().stream()
            .map(chunks -> chunks.get(chunk))
            .anyMatch(knowledge -> knowledge != null && knowledge.state == CoverageState.SCANNED);
   }

   public synchronized List<ChunkSnapshot> snapshot() {
      List<ChunkSnapshot> result = new ArrayList<>();
      targets.forEach((target, chunks) -> chunks.forEach((chunk, knowledge) -> result.add(new ChunkSnapshot(
            target,
            chunk,
            knowledge.state == CoverageState.SCANNING ? CoverageState.DIRTY : knowledge.state,
            Set.copyOf(knowledge.locations)))));
      return List.copyOf(result);
   }

   public synchronized void restore(Collection<ChunkSnapshot> snapshots) {
      targets.clear();
      chunkRevisions.clear();
      nextScanLease = 1L;
      if (snapshots == null) return;
      for (ChunkSnapshot snapshot : snapshots) {
         if (snapshot == null || snapshot.target() == null || snapshot.target().isBlank()) continue;
         ChunkKnowledge knowledge = new ChunkKnowledge();
         knowledge.state = snapshot.state() == CoverageState.SCANNING
               ? CoverageState.DIRTY : snapshot.state();
         if (knowledge.state == null || knowledge.state == CoverageState.UNKNOWN) continue;
         knowledge.locations.addAll(snapshot.locations());
         target(snapshot.target(), true).put(snapshot.chunk(), knowledge);
      }
   }

   private Map<Long, ChunkKnowledge> target(String target, boolean create) {
      if (target == null || target.isBlank()) return Map.of();
      if (create) return targets.computeIfAbsent(target, ignored -> new HashMap<>());
      return targets.getOrDefault(target, Map.of());
   }

   private boolean ownsCurrentScan(ChunkKnowledge knowledge, long chunk, long expectedLease) {
      return knowledge != null
            && knowledge.state == CoverageState.SCANNING
            && knowledge.scanLease == expectedLease
            && knowledge.scanRevision == this.chunkRevisions.getOrDefault(chunk, 0L);
   }

   private long nextScanLease() {
      long lease = nextScanLease++;
      if (nextScanLease < 1L) nextScanLease = 1L;
      return lease;
   }

   private static void replaceLocations(ChunkKnowledge knowledge, Collection<Long> locations) {
      knowledge.locations.clear();
      if (locations != null) knowledge.locations.addAll(locations);
   }

   public record ChunkSnapshot(String target, long chunk, CoverageState state, Set<Long> locations) {
      public ChunkSnapshot {
         locations = locations == null ? Set.of() : Set.copyOf(locations);
      }
   }

   private static final class ChunkKnowledge {
      private CoverageState state = CoverageState.UNKNOWN;
      private final Set<Long> locations = new HashSet<>();
      private long scanRevision;
      private long scanLease;
   }
}
