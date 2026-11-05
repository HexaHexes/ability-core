package org.bitwisemadness.abilitycore;

import java.util.ArrayList;
import java.util.List;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;
import org.bitwisemadness.abilitycore.api.ResourceProviders;

/**
 * Test-support declarations for the shared test resource pool. Centralised so
 * cooldown/charge suites can swap provider behaviour (sufficient / throwing
 * check) without duplicating declaration cleanup logic. Declares the provider
 * first, exactly as the pairing rule (issue #88) requires.
 */
public final class ResourceConsumersDeclarations {

    public static final String RESOURCE = "test:pool";
    private static final List<String> EVENTS = new ArrayList<>();

    private ResourceConsumersDeclarations() {
    }

    public static List<String> events() {
        return EVENTS;
    }

    /** Sufficient check, no-op charge. */
    public static void sufficient() {
        reset();
        ResourceProviders.declare(RESOURCE, (abilityId, player, amount) -> {
            EVENTS.add("check");
            return Affordability.sufficient(RESOURCE, amount);
        });
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player, Affordability snapshot) {
                EVENTS.add("charge");
            }
        });
    }

    /** Sufficient check, throwing charge (post-activation failure). */
    public static void throwing() {
        reset();
        ResourceProviders.declare(RESOURCE, (abilityId, player, amount) -> {
            EVENTS.add("check");
            return Affordability.sufficient(RESOURCE, amount);
        });
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player, Affordability snapshot) {
                EVENTS.add("charge-failed");
                throw new IllegalStateException("charge backend down");
            }
        });
    }

    public static void reset() {
        ResourceConsumers.remove(RESOURCE);
        ResourceProviders.remove(RESOURCE);
        EVENTS.clear();
    }
}
