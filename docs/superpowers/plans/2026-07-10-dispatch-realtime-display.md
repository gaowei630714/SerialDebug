# Dispatch Real-Time Display — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make received serial data push to the HEX/ASCII view in real time on the main IO tab — decoupled from whether the waveform chart tab is open.

**Architecture:** `pipeline.dispatch()` is currently called from exactly one production site: the `AnimationTimer` inside `SessionTabContent.startWaveform()`, which only runs while the waveform chart sub-tab is active. The fix introduces a second, always-on `AnimationTimer` (`ioPump`) owned by the **port-session lifecycle** (start on port-open, stop on port-close) and removes `dispatch()` from the waveform timer (which then only redraws the canvas). Produce side (jSerialComm thread → `publish`) and consume side (FX thread → `ioPump` → `dispatch`) stay on their existing threads — no threading model change.

**Tech Stack:** Java 17, JavaFX 21 (`AnimationTimer`), SLF4J 2.x + Logback (already added this session).

**Reference findings (systematic-debugging, this session):**
- `pipeline.dispatch()` single production caller: `SessionTabContent.java:541` (grep-confirmed, no other production call sites).
- `SessionDataPipeline` Javadoc already documents the single-dispatch-point risk and the overflow-WARN canary added this session.
- `DisplayController.onDataReceived` is dead code (never registered) — do not wire it up; the active path is `TextConsumer` via the pipeline.

---

## Global Constraints

- Project requires JDK 17+ (`JAVA_HOME` → `D:\soft\java\jdk17`); system default is JDK 11 and will fail the build.
- Build: `mvn clean compile` (from repo root). Run app: `mvn javafx:run -pl serial-debug-app`.
- UI module has no TestFX; UI-wiring changes are verified by compile + manual `javafx:run`, not unit tests. Core logic changes are verified by JUnit 5 in `serial-debug-core`.
- Follow existing naming/style: `AnimationTimer` field + `startX()/stopX()` pair, SLF4J logger per class, `volatile` for shared counters.
- No `Date.now()` / `Math.random()` in implementation code (workflow-script rule does not apply here, but keep timestamps from `System.nanoTime()` as the pipeline already does).

---

## Task 1: Lock the dispatch contract with a failing test (TDD)

**Files:**
- Test: `serial-debug-core/src/test/java/io/github/serialdebug/core/chart/SessionDataPipelineTest.java`

The new `ioPump` relies on one contract: calling `dispatch()` drains **all** currently-queued packets and returns the exact count. The existing tests exercise publish→dispatch but don't assert the **returned count** or the **multi-pump drain-until-empty** pattern the pump uses. Lock it.

- [ ] **Step 1: Write the failing test**

Add to `SessionDataPipelineTest.java` (imports already present: `SessionDataPipeline`, `RawPacket`, `Direction`, `List`, `ArrayList`):

```java
@Test
void shouldReturnExactCountAndDrainAllPackets() {
    SessionDataPipeline p = new SessionDataPipeline();
    List<String> received = new ArrayList<>();
    p.register(pkt -> received.add(new String(pkt.data(), pkt.offset(), pkt.length())));

    p.publish("one".getBytes(), 0, 3, Direction.RX);
    p.publish("two".getBytes(), 0, 3, Direction.RX);
    p.publish("three".getBytes(), 0, 5, Direction.RX);

    int drained = p.dispatch();

    assertEquals(3, drained, "dispatch() must return the number of packets drained");
    assertEquals(List.of("one", "two", "three"), received);
    assertTrue(p.isEmpty(), "buffer must be empty after a full drain");
}

@Test
void shouldDrainIncrementallyAcrossMultiplePumps() {
    SessionDataPipeline p = new SessionDataPipeline();
    List<String> received = new ArrayList<>();
    p.register(pkt -> received.add(new String(pkt.data(), pkt.offset(), pkt.length())));

    // First burst
    p.publish("a".getBytes(), 0, 1, Direction.RX);
    p.publish("b".getBytes(), 0, 1, Direction.RX);
    assertEquals(2, p.dispatch());

    // Second burst arrives after the first pump
    p.publish("c".getBytes(), 0, 1, Direction.RX);
    assertEquals(1, p.dispatch());

    assertEquals(List.of("a", "b", "c"), received);
    assertTrue(p.isEmpty());
}
```

- [ ] **Step 2: Run test to verify it passes**

> **Note:** `dispatch()` already returns `int` (confirmed in `SessionDataPipeline.java:95`, added earlier this session), so these tests **compile and pass** rather than fail. There is no genuine "red" here — the value of Task 1 is to *lock the drain-count contract* (assert the returned count + multi-pump drain-until-empty), which the pre-existing tests never did. Do NOT expect a compile failure.

Run: `mvn test -pl serial-debug-core -Dtest=SessionDataPipelineTest#shouldReturnExactCountAndDrainAllPackets+shouldDrainIncrementallyAcrossMultiplePumps`
Expected: PASS (both new tests). If it fails to compile, `dispatch()` was reverted to `void` — restore the `int` signature below before continuing.

- [ ] **Step 3: Confirm `dispatch()` returns the count (no change needed unless reverted)**

`SessionDataPipeline.java` already returns `int` from `dispatch()` (line 95). Confirm the signature is `public int dispatch()` and that it returns `count`. If it was reverted to `void`, restore:

```java
public int dispatch() {
    int count = 0;
    RawPacket pkt;
    while ((pkt = buffer.poll()) != null) {
        for (PayloadConsumer c : consumers) c.onPacket(pkt);
        count++;
    }
    if (count > 0) {
        LOG.debug("dispatched {} packet(s) to {} consumer(s)", count, consumers.size());
    }
    return count;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl serial-debug-core -Dtest=SessionDataPipelineTest`
Expected: ALL PASS (including the two new tests and all pre-existing tests).

- [ ] **Step 5: Commit**

```bash
git add serial-debug-core/src/test/java/io/github/serialdebug/core/chart/SessionDataPipelineTest.java \
        serial-debug-core/src/main/java/io/github/serialdebug/core/chart/SessionDataPipeline.java
git commit -m "test(core): lock dispatch() drain-count contract for ioPump"
```

---

## Task 2: Add the always-on `ioPump` to SessionTabContent

**Files:**
- Modify: `serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java`

Introduce a second `AnimationTimer` that drains the pipeline on every FX pulse, started/stopped by the port-open/close lifecycle — independent of the waveform tab.

- [ ] **Step 1: Add the logger field and import**

At top of file, add import (alongside other `io.github.serialdebug` imports):
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

In the class body (near `private AnimationTimer waveTimer;`), add:
```java
private static final Logger LOG = LoggerFactory.getLogger(SessionTabContent.class);

/**
 * Always-on dispatch pump. Drains {@code session.getPipeline()} on every FX pulse
 * so received bytes reach the HEX/ASCII view in real time on the main IO tab.
 *
 * <p>Owned by the <em>port-session lifecycle</em> (start on port-open, stop on
 * port-close), NOT by the waveform chart tab. Before this fix, dispatch() was
 * only called from {@link #startWaveform()}, so data silently piled up in the
 * ring buffer unless the user happened to have the chart tab open.</p>
 */
private AnimationTimer ioPump;
```

- [ ] **Step 2: Add `startIoPump()` / `stopIoPump()` methods**

Place immediately after `stopWaveform()` (after line ~553):

```java
/**
 * Start the always-on dispatch pump. Safe to call repeatedly — if the pump is
 * already running this is a no-op. Must be called on the FX Application Thread
 * (it is, from the port-state-change callback).
 */
private void startIoPump() {
    if (ioPump != null) return;
    ioPump = new AnimationTimer() {
        @Override
        public void handle(long now) {
            int n = session.getPipeline().dispatch();
            if (n > 0) {
                LOG.debug("ioPump dispatched {} packet(s)", n);
            }
        }
    };
    ioPump.start();
    LOG.debug("ioPump started");
}

private void stopIoPump() {
    if (ioPump != null) {
        ioPump.stop();
        ioPump = null;
        LOG.debug("ioPump stopped");
    }
}
```

- [ ] **Step 3: Wire start/stop into the port-state-change callback**

In `buildUI()`, inside `toolbarController.setOnPortStateChange(...)` (lines 167-179), add the pump control right after the `connected` flag is computed:

```java
toolbarController.setOnPortStateChange((connected, config) -> {
    boolean isConnected = connected != null && connected;
    sendController.setPortOpen(isConnected);
    statusBarController.updateConnectionStatus(isConnected, config);
    // Drive the always-on dispatch pump from the port lifecycle, so the HEX/ASCII
    // view updates in real time regardless of which sub-tab is active.
    if (isConnected) {
        startIoPump();
    } else {
        stopIoPump();
    }
    if (!isConnected) {
        displayController.resetRateCalcs();
        statusBarController.resetRateLabels();
    }
    if (session.getTab() != null) {
        session.getTab().setText(isConnected && config != null
                ? config.getPortName() : Messages.get("io.tab.disconnected"));
    }
});
```

- [ ] **Step 4: Remove `dispatch()` from the waveform timer**

In `startWaveform()` (lines 536-546), strip the dispatch call so the waveform timer only redraws the canvas:

```java
private void startWaveform() {
    if (waveTimer != null) return;
    waveTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            // dispatch() is now owned by ioPump (port-open lifecycle); the
            // waveform tab only needs to redraw its canvas each pulse.
            waveCanvas.redraw();
        }
    };
    waveTimer.start();
}
```

- [ ] **Step 5: Stop the pump in `shutdown()`**

In `shutdown()` (lines 563-569), add `stopIoPump()` as the first line so a closed tab releases the pump even if the port-close callback already ran:

```java
public void shutdown() {
    stopIoPump();
    stopWaveform();
    if (toolbarController != null && toolbarController.isOpen()) toolbarController.closePort();
    if (sendController != null) sendController.shutdown();
    if (fileSendController != null) fileSendController.shutdown();
    if (statusBarController != null) statusBarController.shutdown();
}
```

- [ ] **Step 6: Verify compilation**

Run: `mvn clean compile`
Expected: BUILD SUCCESS (all four modules). The new `ioPump` is pure JavaFX wiring; if it compiles, the threading contract is intact (FX-thread consume, jSerialComm-thread produce — unchanged from before).

- [ ] **Step 7: Commit**

```bash
git add serial-debug-ui/src/main/java/io/github/serialdebug/ui/session/SessionTabContent.java
git commit -m "fix(ui): drive dispatch() from port lifecycle so RX shows in real time on IO tab

dispatch() was only called from the waveform chart tab's AnimationTimer, so
received bytes piled up in the ring buffer (and eventually overflowed silently)
unless the user had the chart tab open. Adds an always-on ioPump owned by the
port-open/close lifecycle and removes dispatch() from the waveform timer."
```

---

## Task 3: Manual runtime verification (UI has no TestFX)

**Files:** none (verification only).

The bug is in UI wiring that can't be unit-tested without TestFX. Verify by running the app and observing the two canaries added this session.

- [ ] **Step 1: Run the app**

```bash
mvn javafx:run -pl serial-debug-app
```

- [ ] **Step 2: Reproduce the OLD behavior is gone**

1. Open a real or virtual COM port (the main IO tab is shown by default — do NOT open the waveform chart tab).
2. Have the device send data (or type into a connected terminal).
3. **Expected (after fix):** HEX/ASCII view on the main IO tab updates in real time. Status-bar RX counter advances. No need to touch the chart tab.

- [ ] **Step 3: Confirm the overflow canary stays quiet**

With the pump running, the ring buffer should never fill. Check the file log:
```bash
tail -f ~/.serialdebug/logs/serialdebug.log | grep -i "FULL\|ioPump\|dispatched"
```

> **Logback level caveat:** `logback.xml` sets `io.github.serialdebug.ui` and `io.github.serialdebug.core.chart` to **INFO** (lines 36–37), so the `ioPump dispatched …` and `dispatched …` **DEBUG** lines are suppressed by default and will NOT appear. To observe them during verification, temporarily lower those two loggers to `DEBUG` in `serial-debug-app/src/main/resources/logback.xml` (revert afterwards). The reliable signal is the **absence** of the `ring buffer FULL` WARN — that works regardless of level.

**Expected:** **no** `ring buffer FULL` WARN lines during traffic (this is the primary canary). If you lowered the loggers to DEBUG, you will additionally see `ioPump dispatched N packet(s)` / `dispatched N packet(s)` lines. If a `FULL` WARN appears, the pump is not draining — stop and report.

- [ ] **Step 4: Confirm the chart tab still works**

Open the waveform chart tab — the waveform should draw. Close it — the IO tab should **keep** updating (this is the regression guard: before the fix, closing the chart tab froze the display).

- [ ] **Step 5: Confirm port close/reopen releases and re-arms the pump**

Close the port (toolbar button), reopen it. The IO tab should resume updating on the first new packet after reopen. No duplicate-pump warning in the log (the `if (ioPump != null) return;` guard prevents double-start).

---

## Task 4: Final verification + full test pass

- [ ] **Step 1: Full compile**

Run: `mvn clean compile`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Full test suite (core)**

Run: `mvn test -pl serial-debug-core`
Expected: ALL PASS — the two new dispatch-count tests plus all pre-existing `SessionDataPipelineTest`, `RingBufferTest`, `JSerialCommServiceTest`, parser tests.

- [ ] **Step 3: Confirm no stray `dispatch()` production callers were introduced**

Run: `grep -rn "\.dispatch()" serial-debug-ui/src/main/ serial-debug-core/src/main/`
Expected: only `ioPump` (SessionTabContent) and the pipeline's own `public int dispatch()` definition. The waveform timer must no longer call it.

- [ ] **Step 4: Final commit (if any fixes from verification)**

If Steps 1-3 surfaced issues, fix, re-verify, then:
```bash
git add -A && git commit -m "fix: address verification findings for ioPump dispatch fix"
```
Otherwise, the work is complete.
