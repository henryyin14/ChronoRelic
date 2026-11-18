package com.example.chronorelic.item;

import com.example.chronorelic.ChronoRelicMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChronoWatchItem extends Item {

    private static final Map<UUID, AnchorData> ANCHORS = new ConcurrentHashMap<>();

    private record AnchorData(
            ResourceKey<Level> dimension,
            Vec3 pos,
            float yRot,
            float xRot,
            float health,
            int foodLevel,
            float saturation
    ) {}

    public ChronoWatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        // Shift + 右键：刻录时空锚点
        if (player.isShiftKeyDown()) {
            ANCHORS.put(player.getUUID(), new AnchorData(
                    level.dimension(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getHealth(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel()
            ));

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        25, 0.4, 0.6, 0.4, 0.05
                );
            }
            level.playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS,
                    0.8F, 1.2F
            );
            serverPlayer.sendSystemMessage(Component.translatable("message.chronorelic.anchor_set"), true);
            return InteractionResult.SUCCESS;
        }

        // 直接右键：主动触发时空回溯
        return executeRewind(serverPlayer, stack, false) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static boolean tryTriggerDeathSave(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ChronoRelicMod.CHRONO_WATCH) && !player.getCooldowns().isOnCooldown(stack)) {
                return executeRewind(player, stack, true);
            }
        }
        return false;
    }

    private static boolean executeRewind(ServerPlayer player, ItemStack stack, boolean isDeathSave) {
        AnchorData anchor = ANCHORS.get(player.getUUID());
        if (anchor == null) {
            if (!isDeathSave) {
                player.sendSystemMessage(Component.translatable("message.chronorelic.no_anchor"), true);
            }
            return false;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (!serverLevel.dimension().equals(anchor.dimension())) {
            if (!isDeathSave) {
                player.sendSystemMessage(Component.translatable("message.chronorelic.wrong_dimension"), true);
            }
            return false;
        }

        // 1. 原地释放声波冲击并震飞周围 5 格生物
        Vec3 origin = player.position();
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, origin.x, origin.y + 1.0, origin.z, 1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.playSound(null, origin.x, origin.y, origin.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.3F);

        List<LivingEntity> nearbyEntities = serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate(5.0),
                entity -> entity != player && entity.isAlive()
        );
        for (LivingEntity target : nearbyEntities) {
            Vec3 push = target.position().subtract(origin);
            Vec3 horizontal = push.lengthSqr() > 1.0E-4 ? push.normalize().scale(1.6) : new Vec3(0.0, 0.0, 0.0);
            target.push(horizontal.x, 0.45, horizontal.z);
            target.hurtMarked = true;
        }

        // 2. 传送回锚点并恢复状态
        player.teleportTo(anchor.pos().x, anchor.pos().y, anchor.pos().z);
        player.setYRot(anchor.yRot());
        player.setXRot(anchor.xRot());

        player.setHealth(Math.max(anchor.health(), 6.0F));
        player.getFoodData().setFoodLevel(anchor.foodLevel());
        player.getFoodData().setSaturation(anchor.saturation());
        player.removeAllEffects();
        player.resetFallDistance();

        serverLevel.sendParticles(
                ParticleTypes.PORTAL,
                anchor.pos().x, anchor.pos().y + 1.0, anchor.pos().z,
                40, 0.4, 0.6, 0.4, 0.1
        );
        serverLevel.playSound(
                null,
                anchor.pos().x, anchor.pos().y, anchor.pos().z,
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                1.0F, 1.1F
        );

        // 3. 设置冷却并发送多语言提示（主动 10 秒 = 200 ticks，濒死保命 45 秒 = 900 ticks）
        player.getCooldowns().addCooldown(stack, isDeathSave ? 900 : 200);
        if (isDeathSave) {
            player.sendSystemMessage(Component.translatable("message.chronorelic.death_prevented"), true);
        } else {
            player.sendSystemMessage(Component.translatable("message.chronorelic.rewound"), true);
        }

        return true;
    }
}