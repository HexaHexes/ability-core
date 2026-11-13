package org.bitwisemadness.abilitycore.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.bitwisemadness.abilitycore.AbilityCore;
import org.bitwisemadness.abilitycore.api.MasteryDeclarations;
import org.bitwisemadness.abilitycore.api.MasterySpec;
import org.bitwisemadness.abilitycore.api.MasteryTarget;
import org.bitwisemadness.abilitycore.config.CoreConfig;
import org.bitwisemadness.abilitycore.mastery.MasteryAttachments;
import org.bitwisemadness.abilitycore.mastery.MasteryLevels;
import org.bitwisemadness.abilitycore.mastery.MasteryOverlays;

/**
 * The admin mastery commands (owner's brief): get, set and add mastery
 * LEVELS for an ability on the given players - levels convert through
 * the same table/formula the display reads, so an admin's "5" is the
 * level the pane would show. Writes land in the player's stored XP;
 * display stays solicited (docs/sync.md), so the next pane open queries
 * the new truth - no push. Op level 2, like vanilla's game-master
 * commands. The executors are public: they are the logic the dispatcher
 * wraps, and the fixture calls them directly (a mock player never
 * resolves through {@code EntityArgument}).
 */
@EventBusSubscriber(modid = AbilityCore.MOD_ID)
public final class MasteryCommand {

    private MasteryCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * The command tree: {@code /abilitycore mastery get|set|add} with
     * declared-ability suggestions.
     *
     * @param dispatcher the server's dispatcher
     */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("abilitycore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mastery")
                        .then(Commands.literal("get")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(abilityArgument().executes(ctx -> get(ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                abilityId(ctx))))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(abilityArgument()
                                                .then(Commands.argument("level",
                                                        IntegerArgumentType.integer(0, 1000))
                                                        .executes(ctx -> set(ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx,
                                                                        "targets"),
                                                                abilityId(ctx),
                                                                IntegerArgumentType.getInteger(
                                                                        ctx, "level")))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(abilityArgument()
                                                .then(Commands.argument("levels",
                                                        IntegerArgumentType.integer(-1000, 1000))
                                                        .executes(ctx -> add(ctx.getSource(),
                                                                EntityArgument.getPlayers(ctx,
                                                                        "targets"),
                                                                abilityId(ctx),
                                                                IntegerArgumentType.getInteger(
                                                                        ctx, "levels")))))))));
    }

    /** The ability argument: a resource location suggesting declared ids. */
    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> abilityArgument() {
        return Commands.argument("ability", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        MasteryDeclarations.ids().stream().map(ResourceLocation::toString),
                        builder));
    }

    private static ResourceLocation abilityId(CommandContext<CommandSourceStack> context) {
        return ResourceLocationArgument.getId(context, "ability");
    }

    /**
     * {@code /abilitycore mastery get <targets> <ability>}: each target's
     * level, XP and progress into the next level.
     *
     * @return how many players were reported
     */
    public static int get(CommandSourceStack source, Collection<ServerPlayer> targets,
            ResourceLocation abilityId) {
        if (!MasteryDeclarations.has(abilityId)) {
            source.sendFailure(notDeclared(abilityId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            double xp = xpOf(player, abilityId);
            int level = MasteryLevels.level(xp);
            String progress = Math.round(MasteryLevels.progress(xp) * 100.0) + "%";
            source.sendSuccess(() -> Component.translatable("ability_core.command.mastery_get",
                    player.getDisplayName(), abilityId.toString(), level, xpText(xp), progress),
                    false);
        }
        return targets.size();
    }

    /**
     * {@code /abilitycore mastery set <targets> <ability> <level>}: hold
     * exactly the XP that reaches the level, clamped into a HARD cap.
     *
     * @return how many players were written
     */
    public static int set(CommandSourceStack source, Collection<ServerPlayer> targets,
            ResourceLocation abilityId, int level) {
        if (!MasteryDeclarations.has(abilityId)) {
            source.sendFailure(notDeclared(abilityId));
            return 0;
        }
        int applied = clampLevel(abilityId, level);
        double xp = MasteryLevels.xpForLevel(applied);
        for (ServerPlayer player : targets) {
            setXp(player, abilityId, xp);
            int actual = MasteryLevels.level(xp);
            source.sendSuccess(() -> Component.translatable("ability_core.command.mastery_set",
                    player.getDisplayName(), abilityId.toString(), actual, xpText(xp)), false);
        }
        return targets.size();
    }

    /**
     * {@code /abilitycore mastery add <targets> <ability> <levels>}:
     * move the target's level by N (negative counts down, floors at 0),
     * priced between the current and target levels so mid-level progress
     * survives; clamped into a HARD cap.
     *
     * @return how many players were written
     */
    public static int add(CommandSourceStack source, Collection<ServerPlayer> targets,
            ResourceLocation abilityId, int levels) {
        if (!MasteryDeclarations.has(abilityId)) {
            source.sendFailure(notDeclared(abilityId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            double xp = xpOf(player, abilityId);
            int current = MasteryLevels.level(xp);
            int target = clampLevel(abilityId, current + levels);
            double updated = Math.max(0.0,
                    xp + MasteryLevels.xpForLevel(target) - MasteryLevels.xpForLevel(current));
            setXp(player, abilityId, updated);
            int level = MasteryLevels.level(updated);
            source.sendSuccess(() -> Component.translatable("ability_core.command.mastery_add",
                    player.getDisplayName(), abilityId.toString(), levels, level,
                    xpText(updated)), false);
        }
        return targets.size();
    }

    /** The asked level inside the ability's HARD cap (SOFT keeps raw). */
    private static int clampLevel(ResourceLocation abilityId, int level) {
        int clamped = Math.max(0, level);
        MasterySpec spec = MasteryOverlays.specFor(abilityId);
        if (spec != null && spec.maxLevel() > 0 && clamped > spec.maxLevel()
                && CoreConfig.masteryCapMode(abilityId) == CoreConfig.CapMode.HARD) {
            return spec.maxLevel();
        }
        return clamped;
    }

    private static double xpOf(ServerPlayer player, ResourceLocation abilityId) {
        return MasteryAttachments.data(player).xp(MasteryTarget.ability(abilityId).key());
    }

    private static void setXp(ServerPlayer player, ResourceLocation abilityId, double xp) {
        MasteryAttachments.data(player).setXp(MasteryTarget.ability(abilityId).key(), xp);
    }

    private static Component notDeclared(ResourceLocation abilityId) {
        return Component.translatable("ability_core.command.mastery_not_declared",
                abilityId.toString());
    }

    /** Admin-output XP text: two decimals, never locale-shaped. */
    private static String xpText(double xp) {
        return String.format(java.util.Locale.ROOT, "%.2f", xp);
    }
}
