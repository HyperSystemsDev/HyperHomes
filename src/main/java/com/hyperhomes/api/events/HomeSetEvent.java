package com.hyperhomes.api.events;

import com.hyperhomes.data.Home;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player sets a home.
 */
public record HomeSetEvent(
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    @NotNull Home home,
    boolean isNew
) {}
