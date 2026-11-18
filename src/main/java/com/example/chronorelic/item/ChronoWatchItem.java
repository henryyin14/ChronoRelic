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

    public record ChronoAnchor(
        ResourceKey<Level> dimension,
        Vec3 pos,
        float yaw,
        float pitch,
        float health,
        int foodLevel
    ) {}

    private static final Map<UUID, ChronoAnchor> ANCHORS = new ConcurrentHashMap<>();

    public ChronoWatchItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        if (serverPlayer.isShiftKeyDown()) {
            ChronoAnchor anchor = new ChronoAnchor(
                serverLevel.dimension(),
                serverPlayer.position(),
                serverPlayer.getYRot(),
                serverPlayer.getXRot(),
                serverPlayer.getHealth(),
                serverPlayer.getFoodData().getFoodLevel()
            );
            ANCHORS.put(serverPlayer.getUUID(), anchor);

            serverLevel.sendParticles(ParticleTypes.END_ROD,
                anchor.pos.x, anchor.pos.y + 1.0, anchor.pos.z,
                35, 0.5, 0.8, 0.5, 0.05);
            serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);

            serverPlayer.sendSystemMessage(
                Component.literal("§b[时渊怀表] §f已刻录当前时空锚点"), true);
            return InteractionResult.SUCCESS;
        }

        if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
            serverPlayer.sendSystemMessage(
                Component.literal("§c[时渊怀表] §7时空能量正在冷却中..."), true);
            return InteractionResult.FAIL;
        }

        if (executeRewind(serverPlayer, false)) {
            serverPlayer.getCooldowns().addCooldown(stack, 200);
            return InteractionResult.SUCCESS;
        } else {
            serverPlayer.sendSystemMessage(
                Component.literal("§e[时渊怀表] §7请先按住 Shift + 右键刻录锚点"), true);
            return InteractionResult.FAIL;
        }
    }

    public static boolean tryEmergencyRewind(ServerPlayer player) {
        ItemStack watchStack = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(ChronoRelicMod.CHRONO_WATCH)) {
                watchStack = s;
                break;
            }
        }

        if (watchStack.isEmpty() || player.getCooldowns().isOnCooldown(watchStack)) {
            return false;
        }

        if (executeRewind(player, true)) {
            player.getCooldowns().addCooldown(watchStack, 900);
            return true;
        }
        return false;
    }

    private static boolean executeRewind(ServerPlayer player, boolean isEmergency) {
        ChronoAnchor anchor = ANCHORS.get(player.getUUID());
        ServerLevel currentLevel = (ServerLevel) player.level();

        if (anchor == null || !anchor.dimension().equals(currentLevel.dimension())) {
            return false;
        }

        Vec3 oldPos = player.position();

        List<LivingEntity> nearby = currentLevel.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(5.0),
            e -> e != player && e.isAlive()
        );
        for (LivingEntity target : nearby) {
            Vec3 push = target.position().subtract(oldPos).normalize().scale(1.6);
            target.push(push.x, 0.55, push.z);
            target.hurtMarked = true;
        }

        currentLevel.sendParticles(ParticleTypes.SONIC_BOOM,
            oldPos.x, oldPos.y + 1.0, oldPos.z, 1, 0, 0, 0, 0);

        player.teleportTo(anchor.pos.x, anchor.pos.y, anchor.pos.z);
        player.setYRot(anchor.yaw);
        player.setXRot(anchor.pitch);
        player.resetFallDistance();
        player.setHealth(Math.max(anchor.health, 6.0F));
        player.getFoodData().setFoodLevel(anchor.foodLevel);
        player.removeAllEffects();

        currentLevel.sendParticles(ParticleTypes.PORTAL,
            anchor.pos.x, anchor.pos.y + 1.0, anchor.pos.z,
            50, 0.6, 0.9, 0.6, 0.1);
        currentLevel.playSound(null, player.blockPosition(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);

        String msg = isEmergency
            ? "§d[命运改写] §f怀表破碎虚空，已将你从死亡边缘拉回锚点！"
            : "§b[时渊怀表] §f时空已倒流至锚点";
        player.sendSystemMessage(Component.literal(msg), true);
        return true;
    }
}