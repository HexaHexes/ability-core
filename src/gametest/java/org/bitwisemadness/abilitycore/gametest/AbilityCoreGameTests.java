package org.bitwisemadness.abilitycore.gametest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.registry.AbilityRegistries;

/**
 * Smoke tests: the mod loads, the GameTest harness runs, and the core ability
 * registry exists in a loaded game (REQ-CORE-01 wiring evidence).
 *
 * <p>Keep the {@code expectedTests} count in {@code build.gradle} in sync with
 * the number of {@code @GameTest} methods here, or the harness fails the run.</p>
 */
@GameTestHolder(AbilityCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityCoreGameTests {

    private AbilityCoreGameTests() {
    }

    /** The mod is loaded and the empty structure template resolves. */
    @GameTest(template = "empty")
    public static void mod_loads(GameTestHelper helper) {
        helper.succeed();
    }

    /**
     * The ability registry was created by NewRegistryEvent with the exported
     * key - the foundation every provider/consumer issue builds on.
     */
    @GameTest(template = "empty")
    public static void ability_registry_is_registered(GameTestHelper helper) throws GameTestAssertException {
        var registry = AbilityRegistries.registry();
        if (registry == null) {
            throw new GameTestAssertException("ability registry was not created during mod loading");
        }
        if (!AbilityRegistries.ABILITY_REGISTRY_KEY.equals(registry.key())) {
            throw new GameTestAssertException("ability registry has unexpected key: "
                    + registry.key());
        }
        // Registry mechanics sanity: ids resolve as resource locations and the
        // registry is enumerable (content may be empty at this stage).
        registry.keySet().forEach(location -> {
            if (location == null) {
                throw new GameTestAssertException("registry entry with null location");
            }
        });
        helper.succeed();
    }

    /**
     * The pipeline's built-in refusal path works against the real loaded
     * registry: an unknown id answers REFUSED_UNAVAILABLE with no steps run
     * (REQ-CORE-02 integration evidence).
     */
    @GameTest(template = "empty")
    public static void pipeline_refuses_unknown_ability(GameTestHelper helper) throws GameTestAssertException {
        var pipeline = new org.bitwisemadness.abilitycore.activation.ActivationPipeline(
                new org.bitwisemadness.abilitycore.activation.ActivationHandlerRegistry());
        var request = org.bitwisemadness.abilitycore.api.ActivationRequest.of(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "definitely_not_registered"),
                org.bitwisemadness.abilitycore.api.ActivationSource.DIRECT_SLOT);
        // player is legitimately absent: no step may execute before the RESOLVE refusal
        var result = pipeline.run(request, null);
        if (result.outcome() != org.bitwisemadness.abilitycore.api.ActivationOutcome.REFUSED_UNAVAILABLE) {
            throw new GameTestAssertException("expected REFUSED_UNAVAILABLE, got " + result.outcome());
        }
        helper.succeed();
    }

    /**
     * COOLDOWN-phase integration: an EXTERNAL-cooldown ability is refused
     * while its tracker reports an active cooldown (REQ-CORE-05 wiring
     * evidence - the step is registered on the canonical pipeline).
     */
    @GameTest(template = "empty")
    public static void external_cooldown_gate_is_registered(GameTestHelper helper) throws GameTestAssertException {
        var cooldownId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_cooled");
        // RESOLVE runs before COOLDOWN: the id must be registered - install a
        // fixture registry (test-support API), then restore the real one.
        var previous = AbilityRegistries.registry();
        var fixture = new MappedRegistry<org.bitwisemadness.abilitycore.api.AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
        Registry.register(fixture, cooldownId, new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                "tensura.skill.gametest_cooled", null,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                org.bitwisemadness.abilitycore.api.ActivationType.INSTANT, List.of(),
                org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC, Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);
        // ADAPTER runs before COOLDOWN: register a native-charging handler (an
        // EXTERNAL-cooldown ability can be natively charged - the axes are orthogonal)
        AbilityCore.HANDLERS.register(cooldownId, (req, player) ->
                org.bitwisemadness.abilitycore.api.ActivationResult.ACTED);
        org.bitwisemadness.abilitycore.api.CooldownOwners.declareExternal(cooldownId, 100L,
                new org.bitwisemadness.abilitycore.api.CooldownTracker() {
                    @Override
                    public long remainingTicks(net.minecraft.resources.ResourceLocation abilityId,
                            net.minecraft.server.level.ServerPlayer player) {
                        return 999L; // permanently cooling for this test
                    }

                    @Override
                    public void start(net.minecraft.resources.ResourceLocation abilityId,
                            net.minecraft.server.level.ServerPlayer player, long durationTicks) {
                        // no-op: nothing activates in this test
                    }
                });
        var result = AbilityCore.PIPELINE.run(
                org.bitwisemadness.abilitycore.api.ActivationRequest.of(cooldownId,
                        org.bitwisemadness.abilitycore.api.ActivationSource.DIRECT_SLOT), null);
        if (result.outcome() != org.bitwisemadness.abilitycore.api.ActivationOutcome.REFUSED_COOLDOWN) {
            throw new GameTestAssertException("expected REFUSED_COOLDOWN, got " + result.outcome());
        }
        org.bitwisemadness.abilitycore.api.CooldownOwners.remove(cooldownId);
        AbilityCore.HANDLERS.remove(cooldownId);
        AbilityRegistries.installRegistryForTests(previous);
        helper.succeed();
    }

    /**
     * Mode-change intent round-trip (issue #113): an intent against an
     * unregistered ability refuses REFUSED_NO_ADAPTER without guessing, and
     * a registered provider handler's ACTED reaches the caller - the exact
     * signal consumers apply their per-slot update on. The intent never
     * enters the activation pipeline, so no charging or cooldown can run.
     */
    @GameTest(template = "empty")
    public static void mode_change_intent_round_trip(GameTestHelper helper)
            throws GameTestAssertException {
        var modeId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_modechange");
        var intent = new org.bitwisemadness.abilitycore.api.ModeChangeRequest(modeId,
                org.bitwisemadness.abilitycore.api.ActivationRequest.NO_SLOT);

        // undeclared id: the refusal vocabulary, never a silent success
        var refused = org.bitwisemadness.abilitycore.api.ModeChangeHandlers.run(intent, null);
        if (refused.outcome()
                != org.bitwisemadness.abilitycore.api.ActivationOutcome.REFUSED_NO_ADAPTER) {
            throw new GameTestAssertException("expected REFUSED_NO_ADAPTER, got "
                    + refused.outcome());
        }

        // registered handler: its answer passes through untouched
        org.bitwisemadness.abilitycore.api.ModeChangeHandlers.register(modeId,
                (request, player) -> org.bitwisemadness.abilitycore.api.ActivationResult.ACTED);
        var acted = org.bitwisemadness.abilitycore.api.ModeChangeHandlers.run(
                new org.bitwisemadness.abilitycore.api.ModeChangeRequest(modeId, 3), null);
        if (acted.outcome() != org.bitwisemadness.abilitycore.api.ActivationOutcome.ACTED) {
            throw new GameTestAssertException("expected ACTED, got " + acted.outcome());
        }

        org.bitwisemadness.abilitycore.api.ModeChangeHandlers.remove(modeId);
        helper.succeed();
    }

    /**
     * Mastery end-to-end (REQ-CORE-12): an EXTERNAL fixture ability with a
     * declared cost runs the real pipeline - the declared COST effect halves
     * the priced charge (health drops by 2, not 4), the ACTIVATION award
     * lands split 50/50 between the ability's own target and its category
     * share, an undeclared trigger grants nothing, the view reads back
     * the stored XP, and the toggle channel (MENU + NO_SLOT) is the one
     * shape that reaches ACTED without granting (MENU with a slot does).
     * Cleanup restores every seam.
     */
    @GameTest(template = "empty")
    public static void mastery_awards_and_discounts_the_charge(GameTestHelper helper)
            throws GameTestAssertException {
        var abilityId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_mastery");
        var tree = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "tensura", "mastery_tree");

        // RESOLVE needs a registered id: install a fixture registry, then restore
        var previous = AbilityRegistries.registry();
        var fixture = new MappedRegistry<org.bitwisemadness.abilitycore.api.AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
        Registry.register(fixture, abilityId, new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                "tensura.skill.gametest_mastery", null,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                org.bitwisemadness.abilitycore.api.ActivationType.INSTANT,
                List.of(new org.bitwisemadness.abilitycore.api.ResourceCost("ability_core:health", 4.0)),
                org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC, Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);

        // EXTERNAL: core validates the discounted price and charges it (CHARGE phase)
        AbilityCore.HANDLERS.register(abilityId, (req, player) ->
                org.bitwisemadness.abilitycore.api.ActivationResult.ACTED,
                org.bitwisemadness.abilitycore.api.ChargingModel.EXTERNAL);
        org.bitwisemadness.abilitycore.api.MasteryDeclarations.declare(abilityId,
                org.bitwisemadness.abilitycore.api.MasterySpec.gained(10.0)
                        .withShare(org.bitwisemadness.abilitycore.api.MasteryTarget.category(tree), 0.5)
                        .withEffect(org.bitwisemadness.abilitycore.api.MasteryAxis.COST,
                                "gametest:half"));
        org.bitwisemadness.abilitycore.api.MasteryEffectHandlers.declare(
                org.bitwisemadness.abilitycore.api.MasteryAxis.COST, "gametest:half",
                (player, id, view) -> 0.5);

        var server = helper.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                "MasteryProbe");
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                profile, net.minecraft.server.level.ClientInformation.createDefault());
        double healthBefore = player.getHealth();

        var result = AbilityCore.PIPELINE.run(
                org.bitwisemadness.abilitycore.api.ActivationRequest.of(abilityId,
                        org.bitwisemadness.abilitycore.api.ActivationSource.DIRECT_SLOT), player);
        if (!result.isSuccess()) {
            throw new GameTestAssertException("expected success, got " + result.outcome());
        }

        // the COST effect halved the charge: 4.0 nominal -> 2.0 deducted
        double charged = healthBefore - player.getHealth();
        if (Math.abs(charged - 2.0) > 1.0e-4) {
            throw new GameTestAssertException("expected a 2.0 mastery-discounted charge, got "
                    + charged);
        }

        // the award landed split 50/50: own target + category share
        var data = org.bitwisemadness.abilitycore.mastery.MasteryAttachments.data(player);
        double own = data.xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                .ability(abilityId).key());
        double category = data.xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                .category(tree).key());
        if (Math.abs(own - 5.0) > 1.0e-9 || Math.abs(category - 5.0) > 1.0e-9) {
            throw new GameTestAssertException(
                    "expected a 5/5 split, got own=" + own + " category=" + category);
        }

        // an undeclared trigger grants nothing (declared intent filter)
        org.bitwisemadness.abilitycore.api.MasteryAwards.award(player, abilityId,
                org.bitwisemadness.abilitycore.api.MasteryTrigger.HIT);
        double afterHit = data.xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                .ability(abilityId).key());
        if (Math.abs(afterHit - 5.0) > 1.0e-9) {
            throw new GameTestAssertException("HIT was not declared but granted XP: " + afterHit);
        }

        // the view reads the stored state back (attachment -> curve -> aggregate)
        var view = org.bitwisemadness.abilitycore.api.MasteryView.of(player, abilityId);
        if (Math.abs(view.ownXp() - 5.0) > 1.0e-9) {
            throw new GameTestAssertException("view ownXp expected 5.0, got " + view.ownXp());
        }

        // the toggle channel (MENU + NO_SLOT, Toggle's wire shape) reaches
        // ACTED through the pipeline but grants nothing - flipping a switch
        // is not activating the ability (REQ-CORE-12's one skip)
        var toggle = AbilityCore.PIPELINE.run(
                org.bitwisemadness.abilitycore.api.ActivationRequest.of(abilityId,
                        org.bitwisemadness.abilitycore.api.ActivationSource.MENU),
                player);
        if (!toggle.isSuccess()) {
            throw new GameTestAssertException("toggle-shaped run expected success, got "
                    + toggle.outcome());
        }
        double afterToggle = data.xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                .ability(abilityId).key());
        if (Math.abs(afterToggle - 5.0) > 1.0e-9) {
            throw new GameTestAssertException(
                    "the toggle channel must grant nothing, got " + afterToggle);
        }

        // the rule keys on the shape, not the source alone: MENU naming a
        // real slot is a menu activation and still awards
        var menuSlotted = AbilityCore.PIPELINE.run(
                org.bitwisemadness.abilitycore.api.ActivationRequest.of(abilityId,
                                org.bitwisemadness.abilitycore.api.ActivationSource.MENU)
                        .withSlot(1),
                player);
        if (!menuSlotted.isSuccess()) {
            throw new GameTestAssertException("menu run expected success, got "
                    + menuSlotted.outcome());
        }
        double afterMenu = data.xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                .ability(abilityId).key());
        if (Math.abs(afterMenu - 10.0) > 1.0e-9) {
            throw new GameTestAssertException(
                    "a MENU activation with a slot must grant the gain, got " + afterMenu);
        }

        org.bitwisemadness.abilitycore.api.MasteryDeclarations.remove(abilityId);
        org.bitwisemadness.abilitycore.api.MasteryEffectHandlers.remove(
                org.bitwisemadness.abilitycore.api.MasteryAxis.COST, "gametest:half");
        AbilityCore.HANDLERS.remove(abilityId);
        AbilityRegistries.installRegistryForTests(previous);
        helper.succeed();
    }

    /**
     * The mastery query round-trip (REQ-CORE-12's solicited exchange, the
     * payload the phase-3 display consumes): a granted player's snapshot
     * carries one entry per stored target with the WIRE kind vocabulary
     * and SERVER-truth levels, an unknown player answers with the echo
     * and no entries, and the request handler relays the builder's answer
     * through its reply channel (inline work, correlated by uuid).
     */
    @GameTest(template = "empty")
    public static void mastery_query_answers_with_stored_entries(GameTestHelper helper)
            throws GameTestAssertException {
        var abilityId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_query");
        var tree = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "tensura", "mastery_tree");

        // fixture registry + declared spec (share 50/50), as the mastery e2e
        var previous = AbilityRegistries.registry();
        var fixture = new MappedRegistry<org.bitwisemadness.abilitycore.api.AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
        Registry.register(fixture, abilityId, new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                "tensura.skill.gametest_query", null,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                org.bitwisemadness.abilitycore.api.ActivationType.INSTANT,
                List.of(), org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC, Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);
        org.bitwisemadness.abilitycore.api.MasteryDeclarations.declare(abilityId,
                org.bitwisemadness.abilitycore.api.MasterySpec.gained(10.0)
                        .withShare(org.bitwisemadness.abilitycore.api.MasteryTarget.category(tree), 0.5)
                        .withEffect(org.bitwisemadness.abilitycore.api.MasteryAxis.COST,
                                "query:quarter")
                        // the default-curve axis the v6 slice asserts run
                        // against (the constant COST handler reads 0)
                        .withEffect(org.bitwisemadness.abilitycore.api.MasteryAxis.POWER));
        // a distinct handler name: the award gametest declares its own
        // concurrently in the same batch (duplicate names would collide)
        org.bitwisemadness.abilitycore.api.MasteryEffectHandlers.declare(
                org.bitwisemadness.abilitycore.api.MasteryAxis.COST, "query:quarter",
                (caster, id, view) -> 0.25);

        var server = helper.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                "QueryProbe");
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                profile, net.minecraft.server.level.ClientInformation.createDefault());
        // twenty awards: baseGain 10 each, half routed to the share, so
        // BOTH pools clear the curve's 100 XP first step and the v6
        // slice has two distinct reads to subtract
        for (int i = 0; i < 20; i++) {
            org.bitwisemadness.abilitycore.api.MasteryAwards.award(player, abilityId,
                    org.bitwisemadness.abilitycore.api.MasteryTrigger.ACTIVATION);
        }

        // the online answer: echo + one entry per stored target, server level
        String uuid = player.getUUID().toString();
        var online = org.bitwisemadness.abilitycore.sync.MasteryQueryResponse.of(uuid, player);
        if (!uuid.equals(online.targetPlayer())) {
            throw new GameTestAssertException("the answer must echo the requested uuid, got "
                    + online.targetPlayer());
        }
        if (online.entries().size() != 2) {
            throw new GameTestAssertException("expected the ability and category entries, got "
                    + online.entries());
        }
        var abilityEntry = online.entries().stream()
                .filter(e -> "ABILITY".equals(e.kind()) && abilityId.toString().equals(e.id()))
                .findFirst().orElse(null);
        var categoryEntry = online.entries().stream()
                .filter(e -> "CATEGORY".equals(e.kind()) && tree.toString().equals(e.id()))
                .findFirst().orElse(null);
        if (abilityEntry == null || categoryEntry == null) {
            throw new GameTestAssertException(
                    "the wire kind vocabulary must name both entries: " + online.entries());
        }
        double expectedHalf = 100.0; // 20 awards x baseGain 10, split 50/50
        if (Math.abs(abilityEntry.xp() - expectedHalf) > 1.0e-9
                || Math.abs(categoryEntry.xp() - expectedHalf) > 1.0e-9) {
            throw new GameTestAssertException("expected the 100/100 split in the snapshot, got "
                    + abilityEntry.xp() + "/" + categoryEntry.xp());
        }
        int expectedLevel = org.bitwisemadness.abilitycore.mastery.MasteryLevels
                .level(expectedHalf);
        if (abilityEntry.level() != expectedLevel) {
            throw new GameTestAssertException("levels must come from the server's curve, got "
                    + abilityEntry.level() + " expected " + expectedLevel);
        }
        double expectedProgress = org.bitwisemadness.abilitycore.mastery.MasteryLevels
                .progress(expectedHalf);
        if (abilityEntry.progress() < 0.0 || abilityEntry.progress() > 1.0
                || Math.abs(abilityEntry.progress() - expectedProgress) > 1.0e-9) {
            throw new GameTestAssertException(
                    "the snapshot must carry the server's in-level progress, got "
                            + abilityEntry.progress() + " expected " + expectedProgress);
        }
        // the ability entry carries its CURRENT effect bonuses (the pane's
        // mastery-bonuses section): the declared axes, server-evaluated,
        // each with its v6 mastery-ability slice
        if (abilityEntry.bonuses().size() != 2) {
            throw new GameTestAssertException(
                    "expected the COST and POWER bonuses, got "
                            + abilityEntry.bonuses());
        }
        var bonus = abilityEntry.bonuses().get(0);
        if (!"COST".equals(bonus.axis()) || Math.abs(bonus.multiplier() - 0.25) > 1.0e-9) {
            throw new GameTestAssertException(
                    "the bonus must be the handler's live answer, got " + bonus);
        }
        if (bonus.masteryBonus() != 0.0) {
            throw new GameTestAssertException(
                    "a view-ignoring handler's slice must be 0, got " + bonus);
        }
        // POWER rides the default curve, so own and aggregate reads part:
        // the slice is exactly full minus own-level (channel v6)
        var power = abilityEntry.bonuses().get(1);
        if (!"POWER".equals(power.axis())) {
            throw new GameTestAssertException(
                    "axes ride in declaration order, got " + abilityEntry.bonuses());
        }
        double ownLevel = abilityEntry.level();
        // Full contributions: the 0.5 share routes XP only - the
        // aggregate sums the category's whole level (MasteryView)
        double aggregate = ownLevel + categoryEntry.level();
        // the default curve's own shape, read through the same config
        // readers the curve uses (never a second literal copy)
        double expectedTotal = 1.0 + org.bitwisemadness.abilitycore.config.CoreConfig
                .masteryMaxPowerIncrease() * (1.0 - Math.exp(
                        -org.bitwisemadness.abilitycore.config.CoreConfig
                                .masteryPowerIncreasePerLevel() * aggregate));
        double expectedOwn = 1.0 + org.bitwisemadness.abilitycore.config.CoreConfig
                .masteryMaxPowerIncrease() * (1.0 - Math.exp(
                        -org.bitwisemadness.abilitycore.config.CoreConfig
                                .masteryPowerIncreasePerLevel() * ownLevel));
        if (Math.abs(power.multiplier() - expectedTotal) > 1.0e-9) {
            throw new GameTestAssertException(
                    "POWER must be the curve at the aggregate, got " + power.multiplier()
                            + " expected " + expectedTotal);
        }
        if (Math.abs(power.masteryBonus() - (expectedTotal - expectedOwn)) > 1.0e-9) {
            throw new GameTestAssertException(
                    "the mastery slice must be full minus own-level, got "
                            + power.masteryBonus() + " expected "
                            + (expectedTotal - expectedOwn));
        }
        if (power.masteryBonus() <= 0.0) {
            throw new GameTestAssertException(
                    "a fed share must contribute a positive POWER slice, got "
                            + power.masteryBonus());
        }
        if (!categoryEntry.bonuses().isEmpty()) {
            throw new GameTestAssertException(
                    "only ability entries carry bonuses, got " + categoryEntry.bonuses());
        }

        // the offline answer: echo kept, no entries, never an error
        String unknown = java.util.UUID.randomUUID().toString();
        var offline = org.bitwisemadness.abilitycore.sync.MasteryQueryResponse.of(unknown, null);
        if (!unknown.equals(offline.targetPlayer()) || !offline.entries().isEmpty()) {
            throw new GameTestAssertException(
                    "an unknown player must answer echo + empty, got " + offline);
        }

        // the handler's relay: inline work, reply captured (the target is not
        // on this list, so the honest offline answer comes back through reply)
        var captured = new AtomicReference<org.bitwisemadness.abilitycore.sync.MasteryQueryResponse>();
        var context = new net.neoforged.neoforge.network.handling.IPayloadContext() {
            @Override
            public net.neoforged.neoforge.common.extensions.ICommonPacketListener listener() {
                return refusingListener();
            }

            @Override
            public net.minecraft.world.entity.player.Player player() {
                return player;
            }

            @Override
            public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable task) {
                task.run();
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }

            @Override
            public <T> java.util.concurrent.CompletableFuture<T> enqueueWork(
                    java.util.function.Supplier<T> supplier) {
                return java.util.concurrent.CompletableFuture.completedFuture(supplier.get());
            }

            @Override
            public net.minecraft.network.protocol.PacketFlow flow() {
                return net.minecraft.network.protocol.PacketFlow.SERVERBOUND;
            }

            @Override
            public void handle(
                    net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
                // nothing: the handler answers through reply
            }

            @Override
            public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
                // configuration-phase only: irrelevant to this payload
            }

            @Override
            public void reply(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
                captured.set((org.bitwisemadness.abilitycore.sync.MasteryQueryResponse) p);
            }
        };
        org.bitwisemadness.abilitycore.sync.MasteryQueryRequest.handle(
                new org.bitwisemadness.abilitycore.sync.MasteryQueryRequest(unknown), context);
        var relayed = captured.get();
        if (relayed == null) {
            throw new GameTestAssertException("the handler must answer through reply()");
        }
        if (!unknown.equals(relayed.targetPlayer()) || !relayed.entries().isEmpty()) {
            throw new GameTestAssertException(
                    "the relayed answer must echo + stay empty for a list-absent player, got "
                            + relayed);
        }

        org.bitwisemadness.abilitycore.api.MasteryDeclarations.remove(abilityId);
        org.bitwisemadness.abilitycore.api.MasteryEffectHandlers.remove(
                org.bitwisemadness.abilitycore.api.MasteryAxis.COST, "query:quarter");
        AbilityRegistries.installRegistryForTests(previous);
        helper.succeed();
    }

    /**
     * The cap switch (REQ-CORE-12): an ability capped at level 2 under
     * the shipped SOFT default keeps its level climbing while gains
     * beyond the cap arrive at the reduced factor; flipping the
     * per-integration config to HARD stops the DISPLAYED level at 2 and
     * pins the bar full instead. XP itself is never destroyed either
     * way - the cap shapes the number and the gain rate, not the pool.
     */
    @GameTest(template = "empty")
    public static void masteryCapsSoftenAndClamp(GameTestHelper helper)
            throws GameTestAssertException {
        var abilityId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_cap");

        // fixture registry + a capped declaration (the round-trip pattern)
        var previous = AbilityRegistries.registry();
        var fixture = new MappedRegistry<org.bitwisemadness.abilitycore.api.AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
        Registry.register(fixture, abilityId, new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                "tensura.skill.gametest_cap", null,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tensura", "skill"),
                org.bitwisemadness.abilitycore.api.ActivationType.INSTANT,
                List.of(), org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC, Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);
        org.bitwisemadness.abilitycore.api.MasteryDeclarations.declare(abilityId,
                org.bitwisemadness.abilitycore.api.MasterySpec.gained(10.0).withMaxLevel(2));

        var server = helper.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                "CapProbe");
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                profile, net.minecraft.server.level.ClientInformation.createDefault());
        String uuid = player.getUUID().toString();
        List<String> originalModes =
                List.copyOf(org.bitwisemadness.abilitycore.config.CoreConfig
                        .MASTERY_CAP_MODES.get());
        try {
            // below the cap: the full gain (no shares - self keeps it all)
            org.bitwisemadness.abilitycore.api.MasteryAwards.award(player, abilityId,
                    org.bitwisemadness.abilitycore.api.MasteryTrigger.ACTIVATION, 10.0);
            double own = org.bitwisemadness.abilitycore.mastery.MasteryAttachments
                    .data(player)
                    .xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                            .ability(abilityId).key());
            if (Math.abs(own - 10.0) > 1.0e-9) {
                throw new GameTestAssertException(
                        "below the cap the gain must land whole, got " + own);
            }

            // push past the cap, then probe the reduced rate
            org.bitwisemadness.abilitycore.api.MasteryAwards.award(player, abilityId,
                    org.bitwisemadness.abilitycore.api.MasteryTrigger.ACTIVATION, 10_000.0);
            org.bitwisemadness.abilitycore.api.MasteryAwards.award(player, abilityId,
                    org.bitwisemadness.abilitycore.api.MasteryTrigger.ACTIVATION, 10.0);
            own = org.bitwisemadness.abilitycore.mastery.MasteryAttachments
                    .data(player)
                    .xp(org.bitwisemadness.abilitycore.api.MasteryTarget
                            .ability(abilityId).key());
            if (Math.abs(own - 10_015.0) > 1.0e-9) {
                throw new GameTestAssertException(
                        "SOFT mode must reduce the beyond-cap gain to half (10 -> 5), "
                                + "XP itself never lost: expected 10015, got " + own);
            }

            // SOFT display: the level keeps climbing past the cap
            int rawLevel = org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(own);
            if (rawLevel <= 2) {
                throw new GameTestAssertException(
                        "SOFT mode must keep the level climbing, got " + rawLevel);
            }
            var soft = org.bitwisemadness.abilitycore.sync.MasteryQueryResponse
                    .of(uuid, player);
            var softEntry = soft.entries().stream()
                    .filter(e -> "ABILITY".equals(e.kind()) && abilityId.toString().equals(e.id()))
                    .findFirst().orElse(null);
            if (softEntry == null || softEntry.level() != rawLevel) {
                throw new GameTestAssertException(
                        "SOFT display must show the raw level " + rawLevel + ", got "
                                + (softEntry == null ? "no entry" : softEntry.level()));
            }

            // HARD display: the level stops at the cap and the bar pins full
            org.bitwisemadness.abilitycore.config.CoreConfig.MASTERY_CAP_MODES
                    .set(List.of("*=hard"));
            try {
                var hard = org.bitwisemadness.abilitycore.sync.MasteryQueryResponse
                        .of(uuid, player);
                var hardEntry = hard.entries().stream()
                        .filter(e -> "ABILITY".equals(e.kind())
                                && abilityId.toString().equals(e.id()))
                        .findFirst().orElse(null);
                if (hardEntry == null || hardEntry.level() != 2) {
                    throw new GameTestAssertException(
                            "HARD display must stop at the cap (2), got "
                                    + (hardEntry == null ? "no entry" : hardEntry.level()));
                }
                if (Math.abs(hardEntry.progress() - 1.0) > 1.0e-9) {
                    throw new GameTestAssertException(
                            "HARD display must pin the bar full, got " + hardEntry.progress());
                }
            } finally {
                org.bitwisemadness.abilitycore.config.CoreConfig.MASTERY_CAP_MODES
                        .set(originalModes);
            }
        } finally {
            org.bitwisemadness.abilitycore.api.MasteryDeclarations.remove(abilityId);
            AbilityRegistries.installRegistryForTests(previous);
        }
        helper.succeed();
    }

    /**
     * The shipped level table loads end to end (REQ-CORE-12's datapack
     * override): this pack ships data/ability_core/mastery/levels.json,
     * and after the server's real reload the exactly-one rule installed
     * it - thresholds [50, 125, 250, 425] answer level and progress
     * where the config formula (base 100, growth 1.15) would not.
     */
    @GameTest(template = "empty")
    public static void mastery_levels_table_loads_from_the_datapack(GameTestHelper helper)
            throws GameTestAssertException {
        if (!org.bitwisemadness.abilitycore.mastery.MasteryLevels.hasTable()) {
            throw new GameTestAssertException(
                    "the shipped data/ability_core/mastery/levels.json must install on reload");
        }
        var levelAt49 = org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(49.0);
        if (levelAt49 != 0) {
            throw new GameTestAssertException(
                    "49 XP must sit below the first threshold, got level " + levelAt49);
        }
        var levelAt50 = org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(50.0);
        if (levelAt50 != 1) {
            throw new GameTestAssertException(
                    "the table must make 50 XP level 1 (the formula wants 100), got "
                            + levelAt50);
        }
        var capped = org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(10_000.0);
        if (capped != 4) {
            throw new GameTestAssertException(
                    "past the table the level must pin at its length 4, got " + capped);
        }
        var progress = org.bitwisemadness.abilitycore.mastery.MasteryLevels.progress(75.0);
        if (Math.abs(progress - (25.0 / 75.0)) > 1.0e-9) {
            throw new GameTestAssertException(
                    "progress at 75 XP must be (75-50)/(125-50), got " + progress);
        }
        helper.succeed();
    }

    /**
     * A listener that refuses every call: the handler's contract needs only
     * player/enqueueWork/reply, so consulting the listener would be contract
     * drift this stub names loudly - and it satisfies the interface's
     * {@code @Nonnull} honestly instead of returning null.
     */
    private static net.neoforged.neoforge.common.extensions.ICommonPacketListener
            refusingListener() {
        String where = "the mastery query must never consult the packet listener";
        return new net.neoforged.neoforge.common.extensions.ICommonPacketListener() {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public void send(
                    net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public void disconnect(net.minecraft.network.chat.Component reason) {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public net.minecraft.network.Connection getConnection() {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public net.minecraft.util.thread.ReentrantBlockableEventLoop<?> getMainThreadEventLoop() {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public net.neoforged.neoforge.network.connection.ConnectionType getConnectionType() {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public net.minecraft.network.protocol.PacketFlow flow() {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public net.minecraft.network.ConnectionProtocol protocol() {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public void onDisconnect(net.minecraft.network.DisconnectionDetails details) {
                throw new UnsupportedOperationException(where);
            }

            @Override
            public boolean isAcceptingMessages() {
                throw new UnsupportedOperationException(where);
            }
        };
    }

    /**
     * The admin mastery commands (owner's brief): the tree registers
     * under /abilitycore mastery get|set|add, and the executors move a
     * player through levels on the SAME curve the pane shows. A mock
     * player never resolves through EntityArgument, so the fixture
     * calls the executors the dispatcher wraps.
     */
    @GameTest(template = "empty")
    public static void admin_commands_manage_mastery_levels(GameTestHelper helper)
            throws GameTestAssertException {
        var abilityId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                AbilityCore.MOD_ID, "gametest_admin_cmd");
        var previous = AbilityRegistries.registry();
        var fixture = new MappedRegistry<org.bitwisemadness.abilitycore.api.AbilityDefinition>(
                AbilityRegistries.ABILITY_REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(),
                false);
        Registry.register(fixture, abilityId,
                new org.bitwisemadness.abilitycore.api.AbilityDefinition(
                        "tensura.skill.gametest_admin", null,
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "tensura", "skill"),
                        org.bitwisemadness.abilitycore.api.ActivationType.INSTANT,
                        List.of(), org.bitwisemadness.abilitycore.api.ConsumptionStrategies.ALL,
                        org.bitwisemadness.abilitycore.api.AmountResolvers.STATIC,
                        Set.of(), Map.of()));
        AbilityRegistries.installRegistryForTests(fixture);
        org.bitwisemadness.abilitycore.api.MasteryDeclarations.declare(abilityId,
                org.bitwisemadness.abilitycore.api.MasterySpec.gained(10.0));

        var server = helper.getLevel().getServer();
        // the Commands ctor fires the register event on server start
        var root = server.getCommands().getDispatcher().getRoot();
        var command = root.getChild("abilitycore");
        if (command == null || command.getChild("mastery") == null) {
            throw new GameTestAssertException(
                    "the abilitycore mastery command never registered");
        }
        for (String sub : new String[] {"get", "set", "add"}) {
            if (command.getChild("mastery").getChild(sub) == null) {
                throw new GameTestAssertException("missing mastery subcommand: " + sub);
            }
        }

        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                "AdminCmdProbe");
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                profile, net.minecraft.server.level.ClientInformation.createDefault());
        var source = server.createCommandSourceStack();
        var targets = List.of(player);
        var xpKey = org.bitwisemadness.abilitycore.api.MasteryTarget.ability(abilityId).key();
        var store = org.bitwisemadness.abilitycore.mastery.MasteryAttachments.data(player);
        // this pack ships data/ability_core/mastery/levels.json = [50, 125,
        // 250, 425] - four levels - so the conversion prices from the table
        // and clamps past its data (the reload test pins the install)
        if (org.bitwisemadness.abilitycore.command.MasteryCommand.set(
                source, targets, abilityId, 3) != 1) {
            throw new GameTestAssertException("set must report one player");
        }
        double afterSet = store.xp(xpKey);
        if (org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(afterSet) != 3
                || Math.abs(afterSet - 250.0) > 1.0e-9) {
            throw new GameTestAssertException(
                    "set 3 must price the table's third threshold (250), got " + afterSet);
        }

        org.bitwisemadness.abilitycore.command.MasteryCommand.add(
                source, targets, abilityId, 1);
        double afterAdd = store.xp(xpKey);
        if (org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(afterAdd) != 4
                || Math.abs(afterAdd - 425.0) > 1.0e-9) {
            throw new GameTestAssertException(
                    "add 1 must reach the table's fourth threshold (425), got " + afterAdd);
        }

        org.bitwisemadness.abilitycore.command.MasteryCommand.add(
                source, targets, abilityId, 5);
        double clamped = store.xp(xpKey);
        if (org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(clamped) != 4) {
            throw new GameTestAssertException(
                    "past the table's data the level clamps at 4, got "
                            + org.bitwisemadness.abilitycore.mastery.MasteryLevels.level(clamped));
        }

        org.bitwisemadness.abilitycore.command.MasteryCommand.add(
                source, targets, abilityId, -10);
        double floored = store.xp(xpKey);
        if (floored != 0.0) {
            throw new GameTestAssertException(
                    "add -10 must floor the pool at 0, got " + floored);
        }

        double beforeRead = store.xp(xpKey);
        if (org.bitwisemadness.abilitycore.command.MasteryCommand.get(
                source, targets, abilityId) != 1) {
            throw new GameTestAssertException("get must report one player");
        }
        if (store.xp(xpKey) != beforeRead) {
            throw new GameTestAssertException("get is read-only");
        }

        var undeclared = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "tensura", "not_declared_for_commands");
        if (org.bitwisemadness.abilitycore.command.MasteryCommand.set(
                source, targets, undeclared, 3) != 0) {
            throw new GameTestAssertException("an undeclared id must fail loudly");
        }

        AbilityRegistries.installRegistryForTests(previous);
        helper.succeed();
    }
}
