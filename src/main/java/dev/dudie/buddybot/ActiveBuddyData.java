package dev.dudie.buddybot;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ActiveBuddyData implements INBTSerializable<CompoundTag> {
    private UUID uuid;
    private String dimension = "";
    private BlockPos position = BlockPos.ZERO;

    public Optional<UUID> uuid() { return Optional.ofNullable(uuid); }
    public String dimension() { return dimension; }
    public BlockPos position() { return position; }

    public void set(UUID uuid, String dimension, BlockPos position) {
        this.uuid = uuid;
        this.dimension = dimension;
        this.position = position.immutable();
    }

    public void clear() {
        uuid = null;
        dimension = "";
        position = BlockPos.ZERO;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (uuid != null) tag.putUUID("uuid", uuid);
        tag.putString("dimension", dimension);
        tag.putLong("position", position.asLong());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        uuid = tag.hasUUID("uuid") ? tag.getUUID("uuid") : null;
        dimension = tag.getString("dimension");
        position = BlockPos.of(tag.getLong("position"));
    }
}
