package cn.blockforge.apothworkbench;

import cn.blockforge.apothworkbench.client.ClientLang;
import cn.blockforge.apothworkbench.command.WorkbenchCommand;
import cn.blockforge.apothworkbench.data.WorkbenchService;
import cn.blockforge.apothworkbench.net.WorkbenchNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(WB.MODID)
public class ApothWorkbench {

    public ApothWorkbench() {
        WorkbenchNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        WorkbenchCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        WorkbenchService.get(event.getServer()).ensurePackOnStart();
    }

    /**
     * 工作台打开时世界是暂停的（单人），挂起的重载只能等恢复 tick 时补上。
     * 暂停期间这个事件不会触发，所以恢复后的第一 tick 就会执行重载。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        WorkbenchService svc = WorkbenchService.peek();
        if (svc != null && svc.hasPending()) {
            svc.processPending();
        }
    }

    /** 客户端每 tick 检查语言表是否被资源重载换掉，是的话重新注入自定义词条名 */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientLang::tick);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        WorkbenchService.clear();
    }
}
