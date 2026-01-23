package com.hyperhomes.gui.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for the Home Detail page.
 * Contains data sent from UI interactions.
 */
public class HomeDetailData {

    /** The button/action that triggered the event */
    public String button;

    /** The home name */
    public String homeName;

    /** New name for renaming (if applicable) */
    public String newName;

    /** Codec for serialization/deserialization */
    public static final BuilderCodec<HomeDetailData> CODEC = BuilderCodec
            .builder(HomeDetailData.class, HomeDetailData::new)
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
                    new KeyedCodec<>("NewName", Codec.STRING),
                    (data, value) -> data.newName = value,
                    data -> data.newName
            )
            .build();

    public HomeDetailData() {
    }
}
