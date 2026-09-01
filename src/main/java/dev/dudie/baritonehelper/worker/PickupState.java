package dev.dudie.baritonehelper.worker;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Durable state for the exactly-once worker pickup transaction. */
public enum PickupState {
    LIVE("live"),
    PENDING("pending"),
    COMMITTED("committed");

    public static final Codec<PickupState> CODEC =
            Codec.STRING.xmap(PickupState::fromSerialized, PickupState::serializedName);
    public static final StreamCodec<ByteBuf, PickupState> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(16).map(PickupState::fromSerialized, PickupState::serializedName);

    private final String serializedName;

    PickupState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean canTransitionTo(PickupState next) {
        if (next == this) {
            return true;
        }
        return switch (this) {
            case LIVE -> next == PENDING;
            case PENDING -> next == LIVE || next == COMMITTED;
            case COMMITTED -> false;
        };
    }

    public boolean terminal() {
        return this == COMMITTED;
    }

    public static PickupState fromSerialized(String value) {
        for (PickupState state : values()) {
            if (state.serializedName.equals(value) || state.name().equalsIgnoreCase(value)) {
                return state;
            }
        }
        return LIVE;
    }
}
