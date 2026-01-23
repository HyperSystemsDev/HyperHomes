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

    /** Text input value from the player name field (via PlayerNameInput key) */
    public String inputText;

    /** Text input value (alternative - via Value key from ValueChanged event) */
    public String value;

    /** Text input value (alternative - via Text key) */
    public String text;

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
            .addField(
                    new KeyedCodec<>("Value", Codec.STRING),
                    (data, v) -> data.value = v,
                    data -> data.value
            )
            .addField(
                    new KeyedCodec<>("Text", Codec.STRING),
                    (data, v) -> data.text = v,
                    data -> data.text
            )
            .build();

    public ShareManageData() {
    }

    /**
     * Gets the text input value from whichever field was populated.
     */
    public String getTextInput() {
        if (inputText != null && !inputText.isEmpty()) return inputText;
        if (value != null && !value.isEmpty()) return value;
        if (text != null && !text.isEmpty()) return text;
        return null;
    }
}
