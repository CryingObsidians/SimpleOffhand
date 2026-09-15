package com.halfinity.mixin;

import com.halfinity.config.SimpleOffhandConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 让空着的副手也画出手臂。
 *
 * <p>原版 {@code ItemInHandRenderer#submitArmWithItem} 的空手分支是这样的：</p>
 *
 * <pre>{@code
 * if (itemStack.isEmpty()) {
 *     if (isMainHand && !player.isInvisible()) {   // ← 只有主手才画
 *         this.renderPlayerArm(...);
 *     }
 * }
 * }</pre>
 *
 * <p>也就是说副手空着时原版什么都不画。本模组就是把这个 {@code isMainHand} 限制去掉，
 * 于是副手空着时也会画出副手手臂（即副手版 22w13oneblockatatime 的双手可见效果）。</p>
 *
 * <p>唯一的例外：主手拿着配置里的“双手物品”（默认 {@code minecraft:filled_map}）时不这么做。
 * 那种情况下原版自己会走 {@code renderTwoHandedMap}，本来就把两只手都画在地图后面，
 * 再补一条手臂会和地图叠在一起，所以保持原版行为。</p>
 *
 * <p>这里用 {@code @Inject(at = HEAD)} 而不是改写整个方法：只补上原版缺的那一种情况，
 * 其余分支（有物品、弩、地图、摇手动画等）原封不动地交给原版执行。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("SimpleOffhand");

    /**
     * 只在第一次真正走到「副手 + 空手」这条分支时记一条日志。
     *
     * <p>注入的方法名或参数类型写错**不会编译报错**，要到运行期才抛
     * Mixin apply failed。这条日志让「换版本后注入点到底有没有生效」有一个
     * 可以直接在 log 里看到的证据：进游戏、空着副手看第一人称，日志里出现这一行
     * 就说明注入成功且渲染路径确实走到了。</p>
     *
     * <p>用 AtomicBoolean 保证只记一次，渲染线程上也只是一个 boolean 判断，
     * 不会每帧刷屏。</p>
     */
    private static final AtomicBoolean LOGGED_FIRST_CALL = new AtomicBoolean(false);

    /**
     * 原版画裸手臂的方法，这里借它来完成实际渲染，避免把一整套手臂变换抄一遍。
     */
    @Shadow
    private void renderPlayerArm(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            float inverseArmHeight,
            float attackValue,
            HumanoidArm arm
    ) {
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void simpleoffhand$renderEmptyOffhandArm(
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
        // 只接管“副手 + 空手”这一种情况，其余全部交回原版
        if (hand != InteractionHand.OFF_HAND || !itemStack.isEmpty()) {
            return;
        }

        if (!SimpleOffhandConfig.isEnabled()) {
            return;
        }

        // 原版在望远镜缩放时整个方法的渲染都会被跳过，这里保持一致
        if (player.isScoping() || player.isInvisible()) {
            return;
        }

        // 主手拿着双手物品（默认地图）时保持原版逻辑
        if (SimpleOffhandConfig.isTwoHandedItem(player.getMainHandItem())) {
            return;
        }

        // 位置、换弹高度、挥手动画都交给原版计算，这里只决定“画哪只手”
        poseStack.pushPose();
        this.renderPlayerArm(
                poseStack,
                submitNodeCollector,
                lightCoords,
                inverseArmHeight,
                attack,
                player.getMainArm().getOpposite()
        );
        poseStack.popPose();

        if (LOGGED_FIRST_CALL.compareAndSet(false, true)) {
            LOGGER.info("Offhand arm rendering active (injection applied).");
        }
    }
}
