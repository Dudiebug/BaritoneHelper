package dev.dudie.buddybot.world;

import dev.dudie.buddybot.entity.BuddyBotEntity;
import dev.dudie.buddybot.logic.RescueAbility;
import dev.dudie.buddybot.logic.RescueMath;
import dev.dudie.buddybot.logic.Vector3d;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Physical, best-effort rescues. It deliberately never changes or cancels damage. */
public final class RescueController {
    private static final int TEMPORARY_LIFETIME = 20 * 12;
    private final BuddyBotEntity bot;
    private final List<TemporaryBlock> temporaryBlocks = new ArrayList<>();

    public RescueController(BuddyBotEntity bot) { this.bot = bot; }

    public void tick(ServerLevel level, ServerPlayer owner) {
        cleanupExpired(level);
        if (bot.rescueCooldown() > 0 || bot.distanceToSqr(owner) > square(bot.tier().range())) return;

        if (bot.tier().supports(RescueAbility.PEARL_REPOSITION) && bot.distanceToSqr(owner) > 18 * 18) {
            pearlToward(level, owner);
            return;
        }

        if (longFall(level, owner)) {
            rescueFall(level, owner);
            return;
        }
        if ((owner.isUnderWater() && owner.getAirSupply() < 80) || owner.isInWall()) {
            rescueTrapped(level, owner);
            return;
        }
        if ((owner.isOnFire() || owner.isInLava()) && bot.tier().supports(RescueAbility.HAZARD_COVER)) {
            placeTemporary(level, owner.blockPosition(), Blocks.WATER.defaultBlockState());
            coverHazards(level, owner);
            throwPotion(level, owner, Potions.FIRE_RESISTANCE);
            bot.startRescueCooldown(30);
            return;
        }
        if (bot.tier().supports(RescueAbility.SUPPORT_POTION) && owner.getHealth() < owner.getMaxHealth() * 0.45F) {
            throwPotion(level, owner, Potions.HEALING);
            bot.startRescueCooldown(80);
            return;
        }
        if (bot.tier().supports(RescueAbility.SUPPORT_POTION)
                && (owner.hasEffect(MobEffects.POISON) || owner.hasEffect(MobEffects.WITHER))) {
            throwPotion(level, owner, Potions.REGENERATION);
            bot.startRescueCooldown(100);
            return;
        }
        if (bot.tier().supports(RescueAbility.EXPLOSION_SHIELD) && nearbyExplosion(level, owner)) {
            shield(level, owner);
            return;
        }
        bodyBlockProjectile(level, owner);
        discourageCliff(level, owner);
        if (bot.tier().supports(RescueAbility.CATCH_PLATFORM)) bridgeGap(level, owner);
    }

    private boolean longFall(ServerLevel level, ServerPlayer owner) {
        if (owner.getDeltaMovement().y >= -0.55) return false;
        return owner.getY() - highestLanding(level, owner) > 6.0;
    }

    private void rescueFall(ServerLevel level, ServerPlayer owner) {
        BlockPos landing = landingPosition(level, owner);
        if (landing == null) return;
        BlockPos target = landing.above();
        if (bot.tier().supports(RescueAbility.FALL_CLUTCH)) {
            BlockState clutch = level.dimension() == LevelKeys.NETHER
                    ? Blocks.TWISTING_VINES.defaultBlockState() : Blocks.WATER.defaultBlockState();
            if (placeTemporary(level, target, clutch)) {
                bot.startRescueCooldown(45);
                return;
            }
        }
        if (bot.tier().supports(RescueAbility.SLOW_FALLING)) {
            throwPotion(level, owner, Potions.SLOW_FALLING);
        }
        if (placeTemporary(level, target, Blocks.COBWEB.defaultBlockState())) {
            bot.startRescueCooldown(45);
            return;
        }
        if (bot.tier().supports(RescueAbility.CATCH_PLATFORM)) {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                placeTemporary(level, target.offset(x, 0, z), Blocks.COBBLESTONE.defaultBlockState());
            bot.startRescueCooldown(60);
        }
    }

    private void pearlToward(ServerLevel level, ServerPlayer owner) {
        var velocity = RescueMath.ballisticVelocity(
                new Vector3d(bot.getX(), bot.getEyeY(), bot.getZ()),
                new Vector3d(owner.getX(), owner.getY() + 1.0, owner.getZ()), 1.5, 0.03);
        if (velocity.isEmpty()) return;
        Vector3d v = velocity.get();
        ThrownEnderpearl pearl = new ThrownEnderpearl(level, bot);
        pearl.setDeltaMovement(v.x(), v.y(), v.z());
        level.addFreshEntity(pearl);
        bot.startRescueCooldown(60);
    }

    private void throwPotion(ServerLevel level, ServerPlayer owner,
                             net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potionType) {
        ThrownPotion potion = new ThrownPotion(level, bot);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, potionType));
        Vec3 delta = owner.position().subtract(potion.position());
        potion.shoot(delta.x, delta.y + delta.horizontalDistance() * 0.15, delta.z, 0.75F, 1.0F);
        level.addFreshEntity(potion);
    }

    private void discourageCliff(ServerLevel level, ServerPlayer owner) {
        if (!owner.onGround()) return;
        double[] drops = new double[8];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            drops[i++] = dropBelow(level, owner.blockPosition().offset(dx * 2, 0, dz * 2), 10);
        }
        if (RescueMath.isDangerousCliff(drops)) {
            Vec3 look = owner.getLookAngle();
            BlockPos ahead = owner.blockPosition().offset((int) Math.signum(look.x) * 2, 0,
                    (int) Math.signum(look.z) * 2);
            if (placeTemporary(level, ahead, Blocks.COBWEB.defaultBlockState())) bot.startRescueCooldown(80);
        }
    }

    private void bodyBlockProjectile(ServerLevel level, ServerPlayer owner) {
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class,
                owner.getBoundingBox().inflate(8), p -> p.isAlive() && p.getOwner() != owner && p.getOwner() != bot);
        for (Projectile projectile : projectiles) {
            Vec3 toOwner = owner.getEyePosition().subtract(projectile.position());
            if (projectile.getDeltaMovement().dot(toOwner) > 0.15) {
                Vec3 midpoint = projectile.position().add(toOwner.scale(0.7));
                bot.getNavigation().moveTo(midpoint.x, midpoint.y, midpoint.z, 1.65);
                break;
            }
        }
    }

    private void rescueTrapped(ServerLevel level, ServerPlayer owner) {
        for (BlockPos pos : List.of(owner.blockPosition().above(), owner.blockPosition(), owner.blockPosition().above(2))) {
            BlockState state = level.getBlockState(pos);
            if (safeToBreak(level, pos, state)) {
                LivingDestroyBlockEvent event = new LivingDestroyBlockEvent(bot, pos, state);
                if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
                    level.destroyBlock(pos, false, bot);
                    bot.startRescueCooldown(40);
                    return;
                }
            }
        }
    }

    private boolean safeToBreak(ServerLevel level, BlockPos pos, BlockState state) {
        return editsAllowed(level) && !state.isAir() && level.getBlockEntity(pos) == null
                && state.getDestroySpeed(level, pos) >= 0
                && state.canEntityDestroy(level, pos, bot)
                && (state.is(BlockTags.LEAVES) || state.is(BlockTags.DIRT)
                    || state.is(Blocks.GLASS) || state.is(Blocks.ICE) || state.is(Blocks.SNOW)
                    || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL));
    }

    private boolean nearbyExplosion(ServerLevel level, ServerPlayer owner) {
        AABB area = owner.getBoundingBox().inflate(6);
        return !level.getEntitiesOfClass(PrimedTnt.class, area, Entity::isAlive).isEmpty()
                || !level.getEntitiesOfClass(EndCrystal.class, area, Entity::isAlive).isEmpty()
                || !level.getEntitiesOfClass(Creeper.class, area,
                        creeper -> creeper.isAlive() && creeper.getSwellDir() > 0).isEmpty();
    }

    private void coverHazards(ServerLevel level, ServerPlayer owner) {
        BlockPos center = owner.blockPosition();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos hazard = center.offset(dx, -1, dz);
            BlockState state = level.getBlockState(hazard);
            if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)) {
                placeTemporary(level, hazard.above(), Blocks.COBBLESTONE.defaultBlockState());
            }
        }
    }

    private void bridgeGap(ServerLevel level, ServerPlayer owner) {
        Vec3 delta = owner.position().subtract(bot.position());
        double horizontal = delta.horizontalDistance();
        if (horizontal < 3 || horizontal > 10 || Math.abs(delta.y) > 2) return;
        int placed = 0;
        for (int step = 1; step < Math.ceil(horizontal) && placed < 5; step++) {
            double fraction = step / horizontal;
            BlockPos floor = BlockPos.containing(bot.getX() + delta.x * fraction,
                    owner.getY() - 1, bot.getZ() + delta.z * fraction);
            if (level.getBlockState(floor).isAir() && level.getBlockState(floor.below()).isAir()
                    && placeTemporary(level, floor, Blocks.COBBLESTONE.defaultBlockState())) placed++;
        }
        if (placed > 0) bot.startRescueCooldown(40);
    }

    private void shield(ServerLevel level, ServerPlayer owner) {
        Vec3 direction = owner.position().subtract(bot.position());
        Direction facing = Direction.getNearest(direction.x, 0, direction.z);
        BlockPos base = owner.blockPosition().relative(facing.getOpposite());
        for (int y = 0; y < 3; y++) placeTemporary(level, base.above(y), Blocks.OBSIDIAN.defaultBlockState());
        bot.startRescueCooldown(80);
    }

    private boolean placeTemporary(ServerLevel level, BlockPos pos, BlockState placed) {
        if (!editsAllowed(level) || !level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) return false;
        BlockSnapshot original = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, placed, Block.UPDATE_ALL)) return false;
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(original,
                level.getBlockState(pos.below()), bot);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            original.restore();
            return false;
        }
        temporaryBlocks.removeIf(entry -> entry.pos.equals(pos));
        temporaryBlocks.add(new TemporaryBlock(pos.immutable(), placed, bot.tickCount + TEMPORARY_LIFETIME));
        return true;
    }

    private boolean editsAllowed(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private void cleanupExpired(ServerLevel level) {
        Iterator<TemporaryBlock> it = temporaryBlocks.iterator();
        while (it.hasNext()) {
            TemporaryBlock entry = it.next();
            if (bot.tickCount >= entry.expires) {
                if (level.getBlockState(entry.pos).equals(entry.placed)) level.removeBlock(entry.pos, false);
                it.remove();
            }
        }
    }

    public void restoreAll() {
        if (bot.level() instanceof ServerLevel level) {
            for (TemporaryBlock entry : temporaryBlocks)
                if (level.getBlockState(entry.pos).equals(entry.placed)) level.removeBlock(entry.pos, false);
        }
        temporaryBlocks.clear();
    }

    public ListTag save(HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (TemporaryBlock entry : temporaryBlocks) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("pos", entry.pos.asLong());
            tag.putString("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(entry.placed.getBlock()).toString());
            tag.putInt("expires", Math.max(0, entry.expires - bot.tickCount));
            list.add(tag);
        }
        return list;
    }

    public void load(HolderLookup.Provider provider, ListTag list) {
        temporaryBlocks.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            var id = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("block"));
            if (id == null) continue;
            Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
            temporaryBlocks.add(new TemporaryBlock(BlockPos.of(tag.getLong("pos")), block.defaultBlockState(),
                    bot.tickCount + tag.getInt("expires")));
        }
    }

    private BlockPos landingPosition(ServerLevel level, ServerPlayer owner) {
        BlockPos.MutableBlockPos cursor = owner.blockPosition().mutable();
        for (int y = cursor.getY(); y >= level.getMinBuildHeight(); y--) {
            cursor.setY(y);
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return cursor.immutable();
        }
        return null;
    }

    private double highestLanding(ServerLevel level, ServerPlayer owner) {
        double half = owner.getBbWidth() / 2.0;
        double[] ys = new double[4];
        int i = 0;
        for (double dx : new double[]{-half, half}) for (double dz : new double[]{-half, half})
            ys[i++] = surfaceY(level, owner.getX() + dx, owner.getY(), owner.getZ() + dz);
        return RescueMath.highestLandingY(ys);
    }

    private double surfaceY(ServerLevel level, double x, double fromY, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(fromY), (int) Math.floor(z)).mutable();
        for (int y = pos.getY(); y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y + 1.0;
        }
        return level.getMinBuildHeight();
    }

    private double dropBelow(ServerLevel level, BlockPos pos, int limit) {
        for (int d = 0; d <= limit; d++) {
            BlockPos test = pos.below(d);
            if (!level.getBlockState(test).getCollisionShape(level, test).isEmpty()) return d;
        }
        return limit + 1;
    }

    private static double square(double n) { return n * n; }

    private record TemporaryBlock(BlockPos pos, BlockState placed, int expires) {}

    private static final class LevelKeys {
        private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> NETHER =
                net.minecraft.world.level.Level.NETHER;
    }
}
