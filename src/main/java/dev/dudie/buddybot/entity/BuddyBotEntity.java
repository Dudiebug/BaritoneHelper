package dev.dudie.buddybot.entity;

import dev.dudie.buddybot.BuddyBot;
import dev.dudie.buddybot.item.BuddyBotItem;
import dev.dudie.buddybot.logic.BuddyBotTier;
import dev.dudie.buddybot.logic.Cooldown;
import dev.dudie.buddybot.world.RescueController;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

public final class BuddyBotEntity extends TamableAnimal {
    private BuddyBotTier tier = BuddyBotTier.BASIC;
    private int rescueCooldown;
    private final RescueController rescues = new RescueController(this);

    public BuddyBotEntity(EntityType<? extends BuddyBotEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ARMOR, 6.0);
    }

    public void bindTo(Player owner, BuddyBotTier tier) {
        tame(owner);
        setOwnerUUID(owner.getUUID());
        this.tier = tier;
        setCustomName(Component.translatable("entity.buddybot.buddy_bot.name", tierLabel(tier)));
        setCustomNameVisible(true);
    }

    public BuddyBotTier tier() { return tier; }
    public int rescueCooldown() { return rescueCooldown; }
    public void startRescueCooldown(int ticks) { rescueCooldown = Cooldown.start(rescueCooldown, ticks); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.15, 5.0F, 2.0F));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        rescueCooldown = Cooldown.tick(rescueCooldown);
        if (!(level() instanceof ServerLevel level)) return;
        ServerPlayer owner = serverOwner(level);
        if (owner == null) {
            setTarget(null);
            getNavigation().stop();
            return;
        }

        if (owner.level() != level) {
            if (tickCount % 20 == 0) {
                // Temporary edits belong to the source dimension; clean them before the entity is copied.
                rescues.restoreAll();
                Entity moved = changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                        (ServerLevel) owner.level(), owner.position().add(1, 0, 1),
                        net.minecraft.world.phys.Vec3.ZERO, owner.getYRot(), owner.getXRot(),
                        net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                if (moved instanceof BuddyBotEntity replacement) replacement.updateOwnerRecord(owner);
            }
            return;
        }

        updateOwnerRecord(owner);
        if (distanceToSqr(owner) > (double) tier.range() * tier.range()) {
            getNavigation().moveTo(owner, 1.35);
        }
        LivingEntity attacker = level.getEntitiesOfClass(Mob.class,
                        owner.getBoundingBox().inflate(tier.range()),
                        mob -> mob.getTarget() == owner)
                .stream().min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
        if (attacker != null && canAttack(attacker)) setTarget(attacker);
        else if (getTarget() != null && getTarget().isDeadOrDying()) setTarget(null);
        rescues.tick(level, owner);
    }

    private @Nullable ServerPlayer serverOwner(ServerLevel current) {
        UUID owner = getOwnerUUID();
        return owner == null ? null : current.getServer().getPlayerList().getPlayer(owner);
    }

    private void updateOwnerRecord(ServerPlayer owner) {
        owner.getData(BuddyBot.ACTIVE_BUDDY).set(getUUID(), level().dimension().location().toString(), blockPosition());
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Player) && super.canAttack(target);
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        return !(target instanceof Player) && super.wantsToAttack(target, owner);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player.isShiftKeyDown() && isOwnedBy(player)) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.getData(BuddyBot.ACTIVE_BUDDY).clear();
                ItemStack item = new ItemStack(itemForTier());
                if (!player.getInventory().add(item)) player.drop(item, false);
                rescues.restoreAll();
                discard();
                gameEvent(GameEvent.ENTITY_DISMOUNT, player);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && level() instanceof ServerLevel level) {
            ServerPlayer owner = serverOwner(level);
            if (owner != null) owner.getData(BuddyBot.ACTIVE_BUDDY).clear();
            rescues.restoreAll();
        }
        super.die(source);
    }

    private BuddyBotItem itemForTier() {
        return switch (tier) {
            case BASIC -> BuddyBot.BUDDY_BOT.get();
            case MK2 -> BuddyBot.BUDDY_BOT_MK2.get();
            case MK3 -> BuddyBot.BUDDY_BOT_MK3.get();
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("BuddyTier", tier.name());
        tag.putInt("RescueCooldown", rescueCooldown);
        tag.put("TemporaryBlocks", rescues.save(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try { tier = BuddyBotTier.valueOf(tag.getString("BuddyTier")); }
        catch (IllegalArgumentException ignored) { tier = BuddyBotTier.BASIC; }
        rescueCooldown = tag.getInt("RescueCooldown");
        rescues.load(level().registryAccess(), tag.getList("TemporaryBlocks", CompoundTag.TAG_COMPOUND));
    }

    private static String tierLabel(BuddyBotTier tier) {
        return switch (tier) { case BASIC -> "BuddyBot"; case MK2 -> "BuddyBot Mk II"; case MK3 -> "BuddyBot Mk III"; };
    }

    @Override public boolean isFood(ItemStack stack) { return false; }

    @Override
    public @Nullable BuddyBotEntity getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob other) {
        return null;
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                   MobSpawnType reason, @Nullable SpawnGroupData data) {
        return super.finalizeSpawn(level, difficulty, reason, data);
    }
}
