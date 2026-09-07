package com.cypherbrain.app;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional compatibility service.
 *
 * It intentionally does not inspect window content, perform gestures, read text,
 * or react to accessibility events. When the user explicitly enables it in
 * Android settings, Android's audio-input policy may allow this app to keep
 * receiving microphone input while a communication app is active.
 */
public final class AudioCompatibilityAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty: Cypher Brain does not inspect accessibility events.
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt.
    }
}
