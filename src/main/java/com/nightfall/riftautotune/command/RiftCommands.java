package com.nightfall.riftautotune.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nightfall.riftautotune.client.RiftClientManager;
import com.nightfall.riftautotune.core.HardwareTier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Client commands: /riftautotune benchmark | apply | status | reset | profile &lt;tier&gt;. */
public final class RiftCommands {

    private RiftCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RiftClientManager mgr) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("riftautotune")
                .then(Commands.literal("benchmark").executes(ctx -> {
                    feedback(ctx, "Re-running benchmark...");
                    mgr.commandBenchmark();
                    return 1;
                }))
                .then(Commands.literal("apply").executes(ctx -> {
                    feedback(ctx, "Re-applying tuned settings.");
                    mgr.commandApply();
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    feedback(ctx, "§bRiftAutoTune status");
                    for (String line : mgr.statusLines()) {
                        feedback(ctx, " " + line);
                    }
                    return 1;
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    mgr.commandReset();
                    feedback(ctx, "Cleared saved profile. Re-benchmark on next world join.");
                    return 1;
                }))
                .then(Commands.literal("dh")
                        .then(dhNode(mgr, "on", com.nightfall.riftautotune.client.DhSessionGuard.UserOverride.FORCE_ON,
                                "DH forced ON (guard auto-off cleared)."))
                        .then(dhNode(mgr, "off", com.nightfall.riftautotune.client.DhSessionGuard.UserOverride.FORCE_OFF,
                                "DH forced OFF."))
                        .then(dhNode(mgr, "auto", com.nightfall.riftautotune.client.DhSessionGuard.UserOverride.AUTO,
                                "DH guard back to automatic.")))
                .then(Commands.literal("profile")
                        .then(tierNode(mgr, "minimum", HardwareTier.MINIMUM))
                        .then(tierNode(mgr, "low", HardwareTier.LOW))
                        .then(tierNode(mgr, "medium", HardwareTier.MEDIUM))
                        .then(tierNode(mgr, "high", HardwareTier.HIGH))
                        .then(tierNode(mgr, "veryhigh", HardwareTier.VERY_HIGH))
                        .then(tierNode(mgr, "ultra", HardwareTier.ULTRA))
                        .then(tierNode(mgr, "extreme", HardwareTier.EXTREME)));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dhNode(
            RiftClientManager mgr, String name,
            com.nightfall.riftautotune.client.DhSessionGuard.UserOverride mode, String message) {
        return Commands.literal(name).executes(ctx -> {
            mgr.commandDhOverride(mode);
            feedback(ctx, message);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tierNode(
            RiftClientManager mgr, String name, HardwareTier tier) {
        return Commands.literal(name).executes(ctx -> {
            mgr.commandForceProfile(tier);
            feedback(ctx, "Forced profile: " + tier);
            return 1;
        });
    }

    private static void feedback(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }
}
