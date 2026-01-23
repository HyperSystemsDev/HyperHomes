package com.hyperhomes.model;

import org.jetbrains.annotations.NotNull;

/**
 * A simple location record for temporary storage.
 *
 * @param world the world name
 * @param x     x coordinate
 * @param y     y coordinate
 * @param z     z coordinate
 * @param yaw   yaw rotation
 * @param pitch pitch rotation
 */
public record Location(
    @NotNull String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {
    /**
     * Creates a location from a Home.
     *
     * @param home the home
     * @return the location
     */
    public static Location fromHome(@NotNull Home home) {
        return new Location(home.world(), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
    }
}
