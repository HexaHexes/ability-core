package org.bitwisemadness.abilitycore;

import java.util.ArrayList;
import java.util.List;
import org.bitwisemadness.abilitycore.api.Affordability;
import org.bitwisemadness.abilitycore.api.ResourceConsumer;
import org.bitwisemadness.abilitycore.api.ResourceConsumers;

/**
 * Test-support declarations for the shared test resource pool. Centralised so
 * cooldown/charge suites can swap consumer behaviour (sufficient / throwing)
 * without duplicating declaration cleanup logic.
 */
public final class ResourceConsumersDeclarations {

    public static final String RESOURCE = "test:pool";
    private static final List<String> EVENTS = new ArrayList<>();

    private ResourceConsumersDeclarations() {
    }

    public static List<String> events() {
        return EVENTS;
    }

    /** Sufficient validate, no-op charge. */
    public static void sufficient() {
        reset();
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public Affordability validate(net.minecraft.resources.ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player) {
                EVENTS.add("validate");
                return Affordability.sufficient(RESOURCE, 10.0);
            }

            @Override
            public void charge(net.minecraft.resources.ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player, Affordability snapshot) {
                EVENTS.add("charge");
            }
        });
    }

    /** Sufficient validate, throwing charge (post-activation failure). */
    public static void throwing() {
        reset();
        ResourceConsumers.declare(RESOURCE, new ResourceConsumer() {
            @Override
            public Affordability validate(net.minecraft.resources.ResourceLocation abilityId,
                    net.minecraft.server.level.ServerPlayer player) {
                EVENTS.add("validate");
                return Affordability.sufficient(RESOURCE, 10.0);
            }

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
        EVENTS.clear();
    }
}
