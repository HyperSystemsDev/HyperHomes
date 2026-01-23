package com.hyperhomes.api.events;

import com.hyperhomes.model.Home;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player deletes a home.
 */
public record HomeDeleteEvent(
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    @NotNull Home home
) {}
