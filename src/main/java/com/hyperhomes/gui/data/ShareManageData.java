package com.hyperhomes.gui.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for the Share Manage page.
 * Contains data sent from UI interactions.
 */
public class ShareManageData {

    /** The button/action that triggered the event */
    public String button;

    /** The home name being managed */
    public String homeName;

    /** Target player name/UUID for sharing operations */
    public String targetPlayer;

    /** Text input value from the player name field */
    public String inputText;

    /** Codec for serialization/deserialization */
    public static final BuilderCodec<ShareManageData> CODEC = BuilderCodec
            .builder(ShareManageData.class, ShareManageData::new)
            .addField(
                    new KeyedCodec<>("Button", Codec.STRING),
                    (data, value) -> data.button = value,
                    data -> data.button
            )
            .addField(
                    new KeyedCodec<>("HomeName", Codec.STRING),
                    (data, value) -> data.homeName = value,
                    data -> data.homeName
            )
            .addField(
                    new KeyedCodec<>("TargetPlayer", Codec.STRING),
                    (data, value) -> data.targetPlayer = value,
                    data -> data.targetPlayer
            )
            .addField(
                    new KeyedCodec<>("PlayerNameInput", Codec.STRING),
                    (data, value) -> data.inputText = value,
                    data -> data.inputText
            )
            .build();

    public ShareManageData() {
    }
}
