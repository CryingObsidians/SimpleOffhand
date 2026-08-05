package com.halfinity.mixin;

import com.halfinity.config.SimpleOffhandConfig;
import com.halfinity.event.OffhandArmHideEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 混入 ItemInHandRenderer，拦截副手空手臂渲染。
 * 当主手持有配置列表中的物品且副手为空时，跳过副手手臂渲染。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    /**
     * 在 submitArmWithItem 方法执行前拦截。
     * 判断是否满足隐藏条件，若满足则跳过渲染或发布事件供其他模组控制。
     */
    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onRenderArmWithEmptyHand(
            AbstractClientPlayer player,
            float frameInterp,
            float xRot,
            InteractionHand hand,
            float attack,
            ItemStack itemStack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo ci
    ) {
        // 检查模组总开关
        if (!SimpleOffhandConfig.isModEnabled()) {
            return;
        }

        // 仅当副手为空且玩家可见时处理
        if (itemStack.isEmpty() && !player.isInvisible()) {

            // 仅处理副手
            if (hand == InteractionHand.OFF_HAND) {
                ItemStack mainHand = player.getMainHandItem();
                if (!mainHand.isEmpty()) {
                    // 检查主手物品是否在配置列表中
                    boolean inList = false;
                    List<String> items = SimpleOffhandConfig.getTwoHandItemList();
                    for (String id : items) {
                        try {
                            Identifier loc = Identifier.parse(id);
                            Item targetItem = BuiltInRegistries.ITEM.get(loc)
                                    .map(Holder::value)
                                    .orElse(null);
                            if (targetItem != null && mainHand.getItem() == targetItem) {
                                inList = true;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    if (inList) {
                        // 发布事件，其他模组可监听并取消隐藏
                        OffhandArmHideEvent event = new OffhandArmHideEvent(player, true);
                        NeoForge.EVENT_BUS.post(event);

                        // 默认行为：隐藏副手手臂（跳过渲染）
                        if (!event.isCanceled()) {
                            return;
                        }
                    }
                }
            }

            // 执行默认的空手手臂渲染
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();

            poseStack.pushPose();
            this.renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
            poseStack.popPose();

            // 取消原方法，避免重复渲染
            ci.cancel();
        }
    }

    @Shadow
    private void renderPlayerArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float inverseArmHeight,
            float attackValue,
            HumanoidArm arm
    ) {}
}