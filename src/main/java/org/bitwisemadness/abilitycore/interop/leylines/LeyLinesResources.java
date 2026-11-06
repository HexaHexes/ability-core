package org.bitwisemadness.abilitycore.interop.leylines;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Optional Sky's Ley Lines bridge entry point (issue #96, first part): declare
 * <b>one</b> Ley Lines-registered currency as an ability resource pair.
 *
 * <p><b>Agnosticism is the contract</b> (owner directive, recorded on #96):
 * core never enumerates, hardcodes, or discovers which energy types Ley Lines
 * registers - the adopter supplies the currency id, and the resulting resource
 * id <em>is</em> {@code currencyId.toString()} (e.g. a datapack costs entry
 * reads {@code "resource": "technomancy:aether"}). Nothing else in core knows
 * this bridge exists; the contract packages never reference upstream types
 * (the leak gate scans for {@code zone/moddev/mc/skysleylines/} there).</p>
 *
 * <p><b>Gate before touch:</b> all upstream-typed code lives in
 * {@link LeyLinesBridge}, which the JVM resolves only on the first active use
 * below - so when {@code skysleylines} is absent, no Ley Lines class ever
 * links, and the declaration skips with a debug line (the #92 auto-disable
 * chain owns downstream behaviour: {@code has()} reads false, abilities
 * costing it refuse {@code no_provider}).</p>
 *
 * <p><b>Timing</b> matches providers/consumers: declare at mod construction;
 * definitions register at {@code RegisterEvent} afterwards, so cost decisions
 * are order-safe. The halves map their API as pinned from
 * {@code docs/INTEGRATION.md}: {@code quoteAt} at the player's position is
 * the affordability read, {@code spendAt} post-ACTED is the charge.</p>
 */
public final class LeyLinesResources {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String REQUIRED_MOD_ID = "skysleylines";

    private LeyLinesResources() {
    }

    /**
     * Declares the provider/consumer pair for one Ley Lines currency.
     *
     * @param currencyId the namespaced currency id the adopter chose (their
     *                   paradigm registration must have created it)
     * @return true when the pair was declared, false when skipped (mod absent)
     * @throws IllegalArgumentException if {@code currencyId} is null
     */
    public static boolean declare(ResourceLocation currencyId) {
        return declare(currencyId, isLeyLinesLoaded());
    }

    /**
     * Presence-resolved seam (package-private, tests): the gate without
     * touching ModList - fixtures pass the resolved boolean directly, so no
     * test ever boots FML. {@code leyLinesPresent = true} reaches the bridge
     * (the pair registers; live quoting needs a world and stays a manual
     * dev-run check).
     */
    static boolean declare(ResourceLocation currencyId, boolean leyLinesPresent) {
        if (currencyId == null) {
            throw new IllegalArgumentException("currencyId must not be null");
        }
        if (!leyLinesPresent) {
            LOGGER.debug("Ley Lines currency '{}' skipped: {} is not loaded",
                    currencyId, REQUIRED_MOD_ID);
            return false;
        }
        // first active use: everything upstream-typed stays inside the bridge
        return LeyLinesBridge.declarePair(currencyId);
    }

    private static boolean isLeyLinesLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(REQUIRED_MOD_ID);
    }
}
