package org.bitwisemadness.abilitycore.sync;

import net.neoforged.bus.api.Event;

/**
 * Posted on the client when a {@link MasteryQueryResponse} arrives
 * (REQ-CORE-12): display code (menus, tabs, overlays) reads the snapshot
 * from here. Fired only for an explicit request - core never pushes
 * mastery state (docs/sync.md).
 */
public final class MasteryQueryAnsweredEvent extends Event {

    private final MasteryQueryResponse response;

    public MasteryQueryAnsweredEvent(MasteryQueryResponse response) {
        this.response = response;
    }

    /** The arrived snapshot. */
    public MasteryQueryResponse response() {
        return response;
    }
}
