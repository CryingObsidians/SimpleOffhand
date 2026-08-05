package com.halfinity.event;

import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 副手手臂隐藏事件。
 * 当主手持有配置列表中的物品且副手为空时触发。
 * 其他模组可监听此事件，通过 setCanceled(true) 取消隐藏行为。
 */
public class OffhandArmHideEvent extends Event implements ICancellableEvent {

    private final AbstractClientPlayer player;
    private final boolean defaultHide;

    public OffhandArmHideEvent(AbstractClientPlayer player, boolean defaultHide) {
        this.player = player;
        this.defaultHide = defaultHide;
    }

    /** 获取当前玩家 */
    public AbstractClientPlayer getPlayer() {
        return player;
    }

    /** 获取默认是否隐藏（始终为 true） */
    public boolean isDefaultHide() {
        return defaultHide;
    }
}