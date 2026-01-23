package com.hyperhomes.gui.data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Event data for the Homes List page.
 * Contains data sent from UI interactions.
 */
public class HomesListData {

    /** The button/action that triggered the event */
    public String button;

    /** The home name when a home is selected */
    public String homeName;

    /** Current page number (for pagination) */
    public int page;

    /** Codec for serialization/deserialization */
    public static final BuilderCodec<HomesListData> CODEC = BuilderCodec
            .builder(HomesListData.class, HomesListData::new)
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
                    new KeyedCodec<>("Page", Codec.STRING),
                    (data, value) -> {
                        try {
                            data.page = value != null ? Integer.parseInt(value) : 0;
                        } catch (NumberFormatException e) {
                            data.page = 0;
                        }
                    },
                    data -> String.valueOf(data.page)
            )
            .build();

    public HomesListData() {
    }
}
