package com.hyperhomes.gui.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for Admin pages.
 * Contains data sent from UI interactions.
 */
public class AdminData {

    /** The button/action that triggered the event */
    public String button;

    /** Value field for settings updates */
    public String value;

    /** Target player for admin actions */
    public String targetPlayer;

    /** Home name for admin actions */
    public String homeName;

    /** Codec for serialization/deserialization */
    public static final BuilderCodec<AdminData> CODEC = BuilderCodec
            .builder(AdminData.class, AdminData::new)
            .addField(
                    new KeyedCodec<>("Button", Codec.STRING),
                    (data, value) -> data.button = value,
                    data -> data.button
            )
            .addField(
                    new KeyedCodec<>("Value", Codec.STRING),
                    (data, value) -> data.value = value,
                    data -> data.value
            )
            .addField(
                    new KeyedCodec<>("TargetPlayer", Codec.STRING),
                    (data, value) -> data.targetPlayer = value,
                    data -> data.targetPlayer
            )
            .addField(
                    new KeyedCodec<>("HomeName", Codec.STRING),
                    (data, value) -> data.homeName = value,
                    data -> data.homeName
            )
            .build();

    public AdminData() {
    }
}
