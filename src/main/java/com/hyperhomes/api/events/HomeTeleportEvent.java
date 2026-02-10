package com.hyperhomes.api.events;

import com.hyperhomes.data.Home;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player teleports to a home.
 */
public record HomeTeleportEvent(
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    @NotNull Home home
) {}
