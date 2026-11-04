package org.bitwisemadness.abilitycore.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Core's reference configuration (REQ-CFG-01/02, issue #9) - the small but
 * real SERVER + CLIENT pair that exercises the shared contract
 * ({@code docs/config.md}). Client-preference <em>keys</em> stay per-repo;
 * core ships the pattern and the helpers, not anybody's display settings.
 *
 * <p><b>SERVER</b> ({@code ability_core-server.toml}): auto-synced, the only
 * authority channel, per-world overridable via
 * {@code <world>/serverconfig/}. Core's one key reconfigures the eligibility
 * refusal log (the once-per-key spam guard shipped with REQ-CORE-07 AC5):
 * operators debugging a stuck activation switch it to {@code EVERY}, noisy
 * servers to {@code OFF}.</p>
 *
 * <p><b>CLIENT</b> ({@code ability_core-client.toml}): display-only, never
 * synced, never policy. Core renders nothing itself; {@code playerNotices} is
 * the reference shape for the client half of the diagnostics contract - a
 * consumer that surfaces player-visible notices reads this flag (or its own
 * copy) client-side. Registering it from the common constructor is the
 * documented idiom: CLIENT specs simply never load on a dedicated server.</p>
 *
 * <p>Values are read <b>live</b> through {@link ModConfigSpec.ConfigValue#get()}
 * on every use - never cached - so a reload needs no re-derivation for core's
 * readers ({@code docs/config.md}, reload section). Reads go through
 * {@link ConfigDiagnostics#readOrDefault}, so an unreadable value falls back
 * loudly instead of failing an activation attempt.</p>
 */
public final class CoreConfig {

    /** The server key controlling eligibility refusal debug logging. */
    public static final String REFUSAL_LOG_KEY = "diagnostics.refusalLog";

    /** The client reference key for player-visible diagnostics notices. */
    public static final String PLAYER_NOTICES_KEY = "diagnostics.playerNotices";

    /** Eligibility refusal debug-log modes (read live, see class javadoc). */
    public enum RefusalLogMode {
        /** Log the first refusal per key, suppress repeats (default, spam-safe). */
        ONCE_PER_KEY,
        /** Log every refusal - for debugging one stuck activation. */
        EVERY,
        /** Log nothing. */
        OFF
    }

    private static final ModConfigSpec.Builder SERVER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT = new ModConfigSpec.Builder();

    /** How eligibility refusals reach the debug log (server-set, synced). */
    public static final ModConfigSpec.EnumValue<RefusalLogMode> REFUSAL_LOG =
            SERVER.comment("Eligibility refusal debug logging: once-per-key suppresses repeats "
                            + "(default), every logs each refusal, off logs none.")
                    .defineEnum(REFUSAL_LOG_KEY, RefusalLogMode.ONCE_PER_KEY);

    /** Client preference: surface diagnostics notices to the player. */
    public static final ModConfigSpec.BooleanValue PLAYER_NOTICES =
            CLIENT.comment("Show diagnostics notices (e.g. an invalid config value) in-game. "
                            + "Client preference only - never feeds a server decision.")
                    .define(PLAYER_NOTICES_KEY, true);

    /** Core's SERVER spec - reference registration for the shared contract. */
    public static final ModConfigSpec SERVER_SPEC = SERVER.build();

    /** Core's CLIENT spec - reference registration for the shared contract. */
    public static final ModConfigSpec CLIENT_SPEC = CLIENT.build();

    private CoreConfig() {
    }

    /**
     * The refusal-log mode as core's readers see it: read live and normalized
     * through the diagnostics helper, so it is reload-aware (the next refusal
     * after a {@code ModConfigEvent.Reloading} uses the new value) and never
     * throws. The value supplier counts as the AC4 re-derivation demonstration.
     */
    public static RefusalLogMode refusalLogMode() {
        return ConfigDiagnostics.readOrDefault(REFUSAL_LOG_KEY, () -> REFUSAL_LOG.get(),
                RefusalLogMode.ONCE_PER_KEY);
    }
}
