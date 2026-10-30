package org.bitwisemadness.abilitycore.sync;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.bitwisemadness.abilitycore.AbilityCore;

/**
 * Network channel registration (REQ-CORE-08, ADR-003).
 *
 * <p>One registrar, {@code versioned("1").optional()} - the org-idiomatic
 * pattern verified against ISS and Better Spellcasting. {@code optional()}
 * tolerates channel <em>absence</em> (mod not installed on one side) but
 * never <em>version skew</em> - codec changes require a version bump shipped
 * to both sides together.</p>
 *
 * <p>Payload inventory (current):</p>
 * <ul>
 *   <li>{@link ActivateAbilityPayload} - playToServer (client intent, server
 *       resolves through the pipeline)</li>
 * </ul>
 *
 * <p>Consumers add their own payloads (bar snapshots, deltas, assignment
 * requests) on the same channel version or their own channels - the
 * infrastructure (versioned/optional registrar) is shared; the payload
 * inventory is per-consumer.</p>
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NetworkRegistration {

    private NetworkRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AbilityCore.MOD_ID)
                .versioned("1")
                .optional();

        registrar.playToServer(
                ActivateAbilityPayload.TYPE,
                ActivateAbilityPayload.STREAM_CODEC,
                ActivateAbilityPayload::handle
        );
    }
}
