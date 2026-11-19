package cn.blockforge.apothworkbench.command;

import cn.blockforge.apothworkbench.data.WorkbenchService;
import cn.blockforge.apothworkbench.net.SnapshotPacket;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class WorkbenchCommand {

    private WorkbenchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(build("apothwb"));
        dispatcher.register(build("awb"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> open(ctx.getSource()))
                .then(Commands.literal("save").executes(ctx -> save(ctx.getSource())))
                .then(Commands.literal("refresh").executes(ctx -> refresh(ctx.getSource())))
                .then(Commands.literal("export").executes(ctx -> export(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            WorkbenchService svc = WorkbenchService.get(source.getServer());
            int size = svc.list().size();
            if (size == 0) {
                source.sendFailure(Component.literal("没有扫描到任何词条，请确认神化（Apotheosis）已安装并已加载数据包"));
                return 0;
            }
            WorkbenchNetwork.sendTo(player, SnapshotPacket.of(svc, true));
            source.sendSuccess(() -> Component.literal("已打开神化工作台（" + size + " 条词条）"), false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }
    }

    private static int save(CommandSourceStack source) {
        WorkbenchService svc = WorkbenchService.get(source.getServer());
        String msg = svc.saveAndReload();
        source.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int refresh(CommandSourceStack source) {
        WorkbenchService.get(source.getServer()).reloadFromDisk();
        source.sendSuccess(() -> Component.literal("已重新扫描词条数据"), false);
        return 1;
    }

    private static int export(CommandSourceStack source) {
        String msg = WorkbenchService.get(source.getServer()).exportKubeJs();
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
