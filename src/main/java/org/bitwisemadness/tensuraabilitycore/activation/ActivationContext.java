package org.bitwisemadness.tensuraabilitycore.activation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import org.bitwisemadness.tensuraabilitycore.api.ActivationRequest;
import org.jetbrains.annotations.Nullable;

/**
 * Per-request scratch state flowing through the pipeline (REQ-CORE-04).
 *
 * <p>Steps read earlier steps' outputs (e.g. the RESOURCE step stashes the
 * {@code Affordability} snapshot that the CHARGE action consumes) without
 * widening the {@link ActivationPipeline.Step} contract into result- carrying
 * protocols. Keys are contract strings owned by core modules; values are
 * immutable snapshots.</p>
 */
public final class ActivationContext {

    private final ActivationRequest request;
    @Nullable
    private final ServerPlayer player;
    private final Map<Object, Object> attributes = new HashMap<>();

    public ActivationContext(ActivationRequest request, @Nullable ServerPlayer player) {
        this.request = request;
        this.player = player;
    }

    public ActivationRequest request() {
        return request;
    }

    /** May be null only in unit-test fixtures (see pipeline javadoc). */
    @Nullable
    public ServerPlayer player() {
        return player;
    }

    public void put(Object key, Object value) {
        attributes.put(key, value);
    }

    public <T> Optional<T> get(Object key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException(
                    "Context attribute " + key + " is " + value.getClass().getName() + ", expected " + type.getName());
        }
        return Optional.of(type.cast(value));
    }

    public boolean has(Object key) {
        return attributes.containsKey(key);
    }
}
