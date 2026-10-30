package org.bitwisemadness.abilitycore.activation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.bitwisemadness.abilitycore.api.ChargingModel;

/**
 * Per-ability activation handlers plus their charging declarations
 * (REQ-CORE-02's ADAPTER presence check, REQ-CORE-04's chargingModel
 * exclusivity - ADR-002).
 *
 * <p>One declaration per ability id: handler + charging model. The model is
 * informational-plus-enforced: {@link ChargingModel#EXTERNAL} abilities must
 * declare a cost on their definition (enforced by the RESOURCE phase before
 * activation, which routes the single core charge site -
 * {@code ResourceConsumer#charge} - and nothing else can run), NATIVE
 * abilities get no core charge at all. Duplicate handler registration for one
 * id fails loudly (two adapters claiming one ability is the conflict class
 * the brief forbids).</p>
 */
public final class ActivationHandlerRegistry {

    /** One ability's activation + charging declaration. */
    public record Declaration(ActivationHandler handler, ChargingModel model) {

        public Declaration {
            if (handler == null || model == null) {
                throw new IllegalArgumentException("handler and model must not be null");
            }
        }
    }

    private final Map<ResourceLocation, Declaration> declarations = new ConcurrentHashMap<>();

    /** Registers a natively-charged ability (the default: providers charge themselves). */
    public void register(ResourceLocation abilityId, ActivationHandler handler) {
        register(abilityId, handler, ChargingModel.NATIVE);
    }

    /**
     * Registers an ability with its explicit charging model.
     *
     * @throws IllegalStateException on duplicate registration (loud conflict,
     *         never silent overwrite)
     */
    public void register(ResourceLocation abilityId, ActivationHandler handler, ChargingModel model) {
        Declaration declaration = new Declaration(handler, model);
        Declaration previous = declarations.putIfAbsent(abilityId, declaration);
        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate activation declaration for ability " + abilityId + " - two adapters claim one ability");
        }
    }

    public Optional<Declaration> declaration(ResourceLocation abilityId) {
        return Optional.ofNullable(declarations.get(abilityId));
    }

    public Optional<ActivationHandler> get(ResourceLocation abilityId) {
        return declaration(abilityId).map(Declaration::handler);
    }

    public boolean has(ResourceLocation abilityId) {
        return declarations.containsKey(abilityId);
    }

    public ChargingModel modelOf(ResourceLocation abilityId) {
        Declaration declaration = declarations.get(abilityId);
        // fail-closed default for unregistered ids: treat as NATIVE (RESOLVE/ADAPTER
        // gates refuse before any model matters)
        return declaration == null ? ChargingModel.NATIVE : declaration.model();
    }

    public int size() {
        return declarations.size();
    }

    /**
     * Removes the declaration for an id (test isolation and future mod-reload
     * paths; providers register once per load, so normal flows never call this).
     *
     * @return true if a declaration was present
     */
    public boolean remove(ResourceLocation abilityId) {
        return declarations.remove(abilityId) != null;
    }
}
