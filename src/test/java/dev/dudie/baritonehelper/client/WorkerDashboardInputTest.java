package dev.dudie.baritonehelper.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class WorkerDashboardInputTest {
    @Test
    void signedCoordinateFilterAcceptsOnlyIncompleteOrValidIntegers() {
        assertTrue(DashboardInput.isSignedInteger(""));
        assertTrue(DashboardInput.isSignedInteger("-"));
        assertTrue(DashboardInput.isSignedInteger("0"));
        assertTrue(DashboardInput.isSignedInteger("-512"));
        assertFalse(DashboardInput.isSignedInteger("--1"));
        assertFalse(DashboardInput.isSignedInteger("-abc"));
        assertFalse(DashboardInput.isSignedInteger("12-3"));
        assertFalse(DashboardInput.isSignedInteger(" 1"));
    }
}
