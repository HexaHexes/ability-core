package org.bitwisemadness.abilitycore.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Core's reference configuration (REQ-CFG-01/02, issue #9) - the small but
 * real SERVER + CLIENT pair that exercises the shared contract
 * ({@code docs/config.md}). Client-preference <em>keys</em> stay per-repo;
 * core ships the pattern and the helpers, not anybody's display settings.
 *
 * <p><b>SERVER</b> ({@code ability_core-server.toml}): auto-synced, the only
 * authority channel, per-world overridable via
 * {@code <world>/serverconfig/}. Core's server keys are its gameplay policy
 * - health-cost lethality, banned currencies, integral rounding, cooldown
 * persistence, the menu's undeclared-ability rule, the area-radius and
 * activation-rate ceilings - plus its two log modes (the once-per-key spam
 * guard shipped with REQ-CORE-07 AC5): operators debugging a stuck
 * activation switch it to {@code EVERY}, noisy servers to {@code OFF}.</p>
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

    /** Server key: a health cost may reduce the caster to 0 (kill). */
    public static final String HEALTH_COSTS_CAN_KILL_KEY = "gameplay.healthCostsCanKill";

    /** Server key: resource ids whose costs are refused everywhere. */
    public static final String DISABLED_RESOURCES_KEY = "gameplay.disabledResources";

    /** Server key: how a fractional cost rounds against an integral pool. */
    public static final String INTEGRAL_COST_ROUNDING_KEY = "gameplay.integralCostRounding";

    /** Server key: persist core-tracked cooldowns across rejoins/restarts. */
    public static final String PERSIST_EXTERNAL_COOLDOWNS_KEY =
            "gameplay.persistExternalCooldowns";

    /** Server key: whether the menu lists abilities with no visibility rule. */
    public static final String UNDECLARED_ABILITIES_KEY = "menu.undeclaredAbilities";

    /** Server key: the largest client-proposed area radius accepted. */
    public static final String MAX_AREA_RADIUS_KEY = "targeting.maxAreaRadius";

    /** Server key: activations allowed per player per second (0 = none). */
    public static final String ACTIVATION_RATE_KEY = "network.activationRatePerSecond";

    /** Server key: how malformed activation payloads reach the log. */
    public static final String MALFORMED_PAYLOAD_LOG_KEY = "diagnostics.malformedPayloadLog";

    // --- mastery / experience policy (REQ-CORE-12) ---

    /** Server key: the mastery system master switch. */
    public static final String MASTERY_ENABLED_KEY = "mastery.enabled";

    /** Server key: global multiplier on every routed XP grant. */
    public static final String MASTERY_GAIN_MULTIPLIER_KEY = "mastery.globalGainMultiplier";

    /** Server key: the level ceiling every curve stops at. */
    public static final String MASTERY_MAX_LEVEL_KEY = "mastery.maxLevel";

    /** Server key: XP for the first level step (curve fallback). */
    public static final String MASTERY_CURVE_BASE_KEY = "mastery.levelCurveBase";

    /** Server key: per-step growth of the XP curve (1 = linear). */
    public static final String MASTERY_CURVE_GROWTH_KEY = "mastery.levelCurveGrowth";

    /** Server key: default cost reduction per mastery level. */
    public static final String MASTERY_COST_PER_LEVEL_KEY = "mastery.costReductionPerLevel";

    /** Server key: cap on the default cost reduction. */
    public static final String MASTERY_MAX_COST_KEY = "mastery.maxCostReduction";

    /** Server key: default cast-time reduction per mastery level. */
    public static final String MASTERY_TIME_PER_LEVEL_KEY = "mastery.castTimeReductionPerLevel";

    /** Server key: cap on the default cast-time reduction. */
    public static final String MASTERY_MAX_TIME_KEY = "mastery.maxCastTimeReduction";

    /** Server key: default power increase per mastery level. */
    public static final String MASTERY_POWER_PER_LEVEL_KEY = "mastery.powerIncreasePerLevel";

    /** Server key: cap on the default power increase. */
    public static final String MASTERY_MAX_POWER_KEY = "mastery.maxPowerIncrease";

    /** Server key: default duration increase per mastery level. */
    public static final String MASTERY_DURATION_PER_LEVEL_KEY = "mastery.durationIncreasePerLevel";

    /** Server key: cap on the default duration increase. */
    public static final String MASTERY_MAX_DURATION_KEY = "mastery.maxDurationIncrease";

    /** Eligibility refusal debug-log modes (read live, see class javadoc). */
    public enum RefusalLogMode {
        /** Log the first refusal per key, suppress repeats (default, spam-safe). */
        ONCE_PER_KEY,
        /** Log every refusal - for debugging one stuck activation. */
        EVERY,
        /** Log nothing. */
        OFF
    }

    /** How a fractional cost rounds against an integral pool (hunger, XP). */
    public enum RoundingMode {
        /** Round up - never under-charge what the check approved (default). */
        CEIL,
        /** Round down - never over-charge a fractional cost. */
        FLOOR,
        /** Round to the nearest whole unit. */
        NEAREST
    }

    /** Whether the menu lists an ability that declared no visibility rule. */
    public enum UndeclaredVisibility {
        /** List it - the never-guess default (hiding would punish adopters). */
        VISIBLE,
        /** Hide it - a pack's choice for provider-bar-exclusive abilities. */
        HIDDEN
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

    // --- gameplay policy (server, auto-synced) ---

    /** Whether a health-priced ability may kill its caster (default yes). */
    public static final ModConfigSpec.BooleanValue HEALTH_COSTS_CAN_KILL =
            SERVER.comment("Whether a health cost may reduce the caster to 0 and kill them. "
                            + "Off = a payment that would reach 0 is refused as unaffordable "
                            + "instead; nothing is charged, nothing partial.")
                    .define(HEALTH_COSTS_CAN_KILL_KEY, true);

    /** Resource ids whose costs fail closed everywhere (pack-wide ban). */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RESOURCES =
            SERVER.comment("Resource ids whose costs are refused everywhere, as if no provider "
                            + "had declared them (fail-closed, 'no provider'). Empty = none.")
                    .defineListAllowEmpty(DISABLED_RESOURCES_KEY, List.of(),
                            value -> value instanceof String text && !text.isBlank());

    /** Rounding applied to fractional costs on integral pools. */
    public static final ModConfigSpec.EnumValue<RoundingMode> INTEGRAL_COST_ROUNDING =
            SERVER.comment("How a fractional cost rounds against an integral pool (hunger, XP): "
                            + "ceil never under-charges (default), floor never over-charges, "
                            + "nearest rounds to the closest unit.")
                    .defineEnum(INTEGRAL_COST_ROUNDING_KEY, RoundingMode.CEIL);

    /** Whether core-tracked cooldowns survive a rejoin or restart. */
    public static final ModConfigSpec.BooleanValue PERSIST_EXTERNAL_COOLDOWNS =
            SERVER.comment("Persist core-tracked (EXTERNAL) cooldowns on the player, so "
                            + "rejoining or restarting a world does not reset them. Off = "
                            + "in-memory only (the shipped behaviour).")
                    .define(PERSIST_EXTERNAL_COOLDOWNS_KEY, false);

    /** Menu listing rule for abilities with no declared visibility handler. */
    public static final ModConfigSpec.EnumValue<UndeclaredVisibility> UNDECLARED_ABILITIES =
            SERVER.comment("Whether the ability menu lists an ability that declared no "
                            + "visibility rule: visible keeps the never-guess default, hidden "
                            + "lets a pack keep provider-bar-exclusive abilities out.")
                    .defineEnum(UNDECLARED_ABILITIES_KEY, UndeclaredVisibility.VISIBLE);

    /** The largest client-proposed area radius the server accepts. */
    public static final ModConfigSpec.DoubleValue MAX_AREA_RADIUS =
            SERVER.comment("The largest area-target radius a client may propose (blocks). A "
                            + "larger hint is refused as an invalid target; the spec's own "
                            + "range is the hard ceiling no value can exceed.")
                    .defineInRange(MAX_AREA_RADIUS_KEY, 64.0, 1.0, 4096.0);

    /** Activations one player may send per second (0 = unlimited). */
    public static final ModConfigSpec.IntValue ACTIVATION_RATE =
            SERVER.comment("Activation requests one player may send per second (0 = unlimited). "
                            + "Over the ceiling the extra payloads are dropped.")
                    .defineInRange(ACTIVATION_RATE_KEY, 0, 0, 1000);

    /** How malformed activation payloads reach the debug log. */
    public static final ModConfigSpec.EnumValue<RefusalLogMode> MALFORMED_PAYLOAD_LOG =
            SERVER.comment("Malformed activation payload logging (unknown id, bad source, "
                            + "unparseable target): once-per-key suppresses repeats (default), "
                            + "every logs each one, off logs none.")
                    .defineEnum(MALFORMED_PAYLOAD_LOG_KEY, RefusalLogMode.ONCE_PER_KEY);

    // --- mastery / experience policy (REQ-CORE-12, read live) ---

    /** The mastery system master switch (default on; off = fully inert). */
    public static final ModConfigSpec.BooleanValue MASTERY_ENABLED =
            SERVER.comment("Master switch for the experience/mastery system (REQ-CORE-12). "
                            + "Off = no awards, no storage writes, every effect multiplier 1.0.")
                    .define(MASTERY_ENABLED_KEY, true);

    /** Global multiplier on every routed XP grant (awardTo stays literal). */
    public static final ModConfigSpec.DoubleValue MASTERY_GAIN_MULTIPLIER =
            SERVER.comment("Global multiplier applied to every share-routed XP award "
                            + "(direct awardTo grants stay literal). 0 disables routed gains "
                            + "without disabling effects.")
                    .defineInRange(MASTERY_GAIN_MULTIPLIER_KEY, 1.0, 0.0, 100.0);

    /** The level ceiling every mastery curve stops at. */
    public static final ModConfigSpec.IntValue MASTERY_MAX_LEVEL =
            SERVER.comment("Highest mastery level any target can reach; XP beyond the cap "
                            + "keeps accumulating but grants no further levels.")
                    .defineInRange(MASTERY_MAX_LEVEL_KEY, 100, 1, 1000);

    /** XP for the first level step (the config fallback curve). */
    public static final ModConfigSpec.DoubleValue MASTERY_CURVE_BASE =
            SERVER.comment("XP required for the first level step; later steps cost "
                            + "base x growth^n (datapack level tables override this formula).")
                    .defineInRange(MASTERY_CURVE_BASE_KEY, 100.0, 1.0, 1.0e9);

    /** Per-step growth of the XP curve (1 = linear). */
    public static final ModConfigSpec.DoubleValue MASTERY_CURVE_GROWTH =
            SERVER.comment("Growth factor between level steps: 1 = flat steps (default "
                            + "curve shape is linear), above 1 = each level costs more.")
                    .defineInRange(MASTERY_CURVE_GROWTH_KEY, 1.15, 1.0, 10.0);

    /** Default cost reduction per mastery level. */
    public static final ModConfigSpec.DoubleValue MASTERY_COST_PER_LEVEL =
            SERVER.comment("Default COST effect: this fraction off the casting cost per "
                            + "aggregate mastery level, capped by maxCostReduction.")
                    .defineInRange(MASTERY_COST_PER_LEVEL_KEY, 0.005, 0.0, 1.0);

    /** Cap on the default cost reduction (always < 1 so a cost never hits 0). */
    public static final ModConfigSpec.DoubleValue MASTERY_MAX_COST =
            SERVER.comment("Cap on the default cost reduction (0.95 = a cost can never "
                            + "drop below 5% of its nominal amount).")
                    .defineInRange(MASTERY_MAX_COST_KEY, 0.25, 0.0, 0.95);

    /** Default cast-time reduction per mastery level. */
    public static final ModConfigSpec.DoubleValue MASTERY_TIME_PER_LEVEL =
            SERVER.comment("Default CAST_TIME effect: this fraction off the cast time per "
                            + "aggregate mastery level, capped by maxCastTimeReduction.")
                    .defineInRange(MASTERY_TIME_PER_LEVEL_KEY, 0.005, 0.0, 1.0);

    /** Cap on the default cast-time reduction. */
    public static final ModConfigSpec.DoubleValue MASTERY_MAX_TIME =
            SERVER.comment("Cap on the default cast-time reduction (same 5% floor rule "
                            + "as costs).")
                    .defineInRange(MASTERY_MAX_TIME_KEY, 0.25, 0.0, 0.95);

    /** Default power increase per mastery level. */
    public static final ModConfigSpec.DoubleValue MASTERY_POWER_PER_LEVEL =
            SERVER.comment("Default POWER effect: this fractional bonus per aggregate "
                            + "mastery level, capped by maxPowerIncrease.")
                    .defineInRange(MASTERY_POWER_PER_LEVEL_KEY, 0.01, 0.0, 10.0);

    /** Cap on the default power increase. */
    public static final ModConfigSpec.DoubleValue MASTERY_MAX_POWER =
            SERVER.comment("Cap on the default power bonus (0.5 = at most +50%).")
                    .defineInRange(MASTERY_MAX_POWER_KEY, 0.5, 0.0, 1000.0);

    /** Default duration increase per mastery level. */
    public static final ModConfigSpec.DoubleValue MASTERY_DURATION_PER_LEVEL =
            SERVER.comment("Default DURATION effect: this fractional bonus per aggregate "
                            + "mastery level, capped by maxDurationIncrease.")
                    .defineInRange(MASTERY_DURATION_PER_LEVEL_KEY, 0.01, 0.0, 10.0);

    /** Cap on the default duration increase. */
    public static final ModConfigSpec.DoubleValue MASTERY_MAX_DURATION =
            SERVER.comment("Cap on the default duration bonus (0.5 = at most +50%).")
                    .defineInRange(MASTERY_MAX_DURATION_KEY, 0.5, 0.0, 1000.0);

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

    /** Whether a health cost may kill: read live, default yes. */
    public static boolean healthCostsCanKill() {
        return ConfigDiagnostics.readOrDefault(HEALTH_COSTS_CAN_KILL_KEY,
                () -> HEALTH_COSTS_CAN_KILL.get(), true);
    }

    /**
     * The resource ids a pack has banned: read live as the declared list
     * (never cached), empty when none - the caller treats a member
     * exactly like an absent provider (fail-closed refusal).
     */
    public static List<String> disabledResources() {
        List<? extends String> read = ConfigDiagnostics.readOrDefault(DISABLED_RESOURCES_KEY,
                () -> DISABLED_RESOURCES.get(), List.of());
        return List.copyOf(read);
    }

    /** Whether an id is banned by {@link #disabledResources()} (live). */
    public static boolean resourceDisabled(String resourceId) {
        return resourceId != null && disabledResources().contains(resourceId);
    }

    /** The integral-cost rounding policy: read live, default {@code ceil}. */
    public static RoundingMode integralCostRounding() {
        return ConfigDiagnostics.readOrDefault(INTEGRAL_COST_ROUNDING_KEY,
                () -> INTEGRAL_COST_ROUNDING.get(), RoundingMode.CEIL);
    }

    /** Whether core-tracked cooldowns persist: read live, default off. */
    public static boolean persistExternalCooldowns() {
        return ConfigDiagnostics.readOrDefault(PERSIST_EXTERNAL_COOLDOWNS_KEY,
                () -> PERSIST_EXTERNAL_COOLDOWNS.get(), false);
    }

    /** The menu's undeclared-ability rule: read live, default visible. */
    public static UndeclaredVisibility undeclaredAbilities() {
        return ConfigDiagnostics.readOrDefault(UNDECLARED_ABILITIES_KEY,
                () -> UNDECLARED_ABILITIES.get(), UndeclaredVisibility.VISIBLE);
    }

    /** The area-radius ceiling: read live, default 64 blocks. */
    public static double maxAreaRadius() {
        return ConfigDiagnostics.readOrDefault(MAX_AREA_RADIUS_KEY,
                () -> MAX_AREA_RADIUS.get(), 64.0);
    }

    /** The per-player activation ceiling: read live, 0 = unlimited. */
    public static int activationRatePerSecond() {
        return ConfigDiagnostics.readOrDefault(ACTIVATION_RATE_KEY,
                () -> ACTIVATION_RATE.get(), 0);
    }

    /** The malformed-payload log mode: read live, default once-per-key. */
    public static RefusalLogMode malformedPayloadLog() {
        return ConfigDiagnostics.readOrDefault(MALFORMED_PAYLOAD_LOG_KEY,
                () -> MALFORMED_PAYLOAD_LOG.get(), RefusalLogMode.ONCE_PER_KEY);
    }

    // --- mastery readers (REQ-CORE-12; all live, all default-safe) ---

    /** Whether the mastery system runs at all: read live, default on. */
    public static boolean masteryEnabled() {
        return ConfigDiagnostics.readOrDefault(MASTERY_ENABLED_KEY,
                () -> MASTERY_ENABLED.get(), true);
    }

    /** The global routed-gain multiplier: read live, default 1.0. */
    public static double masteryGlobalGainMultiplier() {
        return ConfigDiagnostics.readOrDefault(MASTERY_GAIN_MULTIPLIER_KEY,
                () -> MASTERY_GAIN_MULTIPLIER.get(), 1.0);
    }

    /** The level ceiling: read live, default 100. */
    public static int masteryMaxLevel() {
        return ConfigDiagnostics.readOrDefault(MASTERY_MAX_LEVEL_KEY,
                () -> MASTERY_MAX_LEVEL.get(), 100);
    }

    /** The curve's first-step XP: read live, default 100. */
    public static double masteryLevelCurveBase() {
        return ConfigDiagnostics.readOrDefault(MASTERY_CURVE_BASE_KEY,
                () -> MASTERY_CURVE_BASE.get(), 100.0);
    }

    /** The curve's growth factor: read live, default 1.15. */
    public static double masteryLevelCurveGrowth() {
        return ConfigDiagnostics.readOrDefault(MASTERY_CURVE_GROWTH_KEY,
                () -> MASTERY_CURVE_GROWTH.get(), 1.15);
    }

    /** Default cost reduction per level: read live, default 0.005. */
    public static double masteryCostReductionPerLevel() {
        return ConfigDiagnostics.readOrDefault(MASTERY_COST_PER_LEVEL_KEY,
                () -> MASTERY_COST_PER_LEVEL.get(), 0.005);
    }

    /** Default cost reduction cap: read live, default 0.25. */
    public static double masteryMaxCostReduction() {
        return ConfigDiagnostics.readOrDefault(MASTERY_MAX_COST_KEY,
                () -> MASTERY_MAX_COST.get(), 0.25);
    }

    /** Default cast-time reduction per level: read live, default 0.005. */
    public static double masteryCastTimeReductionPerLevel() {
        return ConfigDiagnostics.readOrDefault(MASTERY_TIME_PER_LEVEL_KEY,
                () -> MASTERY_TIME_PER_LEVEL.get(), 0.005);
    }

    /** Default cast-time reduction cap: read live, default 0.25. */
    public static double masteryMaxCastTimeReduction() {
        return ConfigDiagnostics.readOrDefault(MASTERY_MAX_TIME_KEY,
                () -> MASTERY_MAX_TIME.get(), 0.25);
    }

    /** Default power increase per level: read live, default 0.01. */
    public static double masteryPowerIncreasePerLevel() {
        return ConfigDiagnostics.readOrDefault(MASTERY_POWER_PER_LEVEL_KEY,
                () -> MASTERY_POWER_PER_LEVEL.get(), 0.01);
    }

    /** Default power increase cap: read live, default 0.5. */
    public static double masteryMaxPowerIncrease() {
        return ConfigDiagnostics.readOrDefault(MASTERY_MAX_POWER_KEY,
                () -> MASTERY_MAX_POWER.get(), 0.5);
    }

    /** Default duration increase per level: read live, default 0.01. */
    public static double masteryDurationIncreasePerLevel() {
        return ConfigDiagnostics.readOrDefault(MASTERY_DURATION_PER_LEVEL_KEY,
                () -> MASTERY_DURATION_PER_LEVEL.get(), 0.01);
    }

    /** Default duration increase cap: read live, default 0.5. */
    public static double masteryMaxDurationIncrease() {
        return ConfigDiagnostics.readOrDefault(MASTERY_MAX_DURATION_KEY,
                () -> MASTERY_MAX_DURATION.get(), 0.5);
    }
}
