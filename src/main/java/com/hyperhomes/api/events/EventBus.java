package com.hyperhomes.api.events;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple event bus for HyperHomes events.
 */
public final class EventBus {

    private static final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    private EventBus() {}

    /**
     * Registers an event handler.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param <T>        the event type
     */
    public static <T> void register(@NotNull Class<T> eventClass, @NotNull Consumer<T> handler) {
        handlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * Unregisters an event handler.
     *
     * @param eventClass the event class
     * @param handler    the handler
     * @param <T>        the event type
     */
    public static <T> void unregister(@NotNull Class<T> eventClass, @NotNull Consumer<T> handler) {
        List<Consumer<?>> list = handlers.get(eventClass);
        if (list != null) {
            list.remove(handler);
        }
    }

    /**
     * Fires an event.
     *
     * @param event the event
     * @param <T>   the event type
     */
    @SuppressWarnings("unchecked")
    public static <T> void fire(@NotNull T event) {
        List<Consumer<?>> list = handlers.get(event.getClass());
        if (list != null) {
            for (Consumer<?> handler : list) {
                try {
                    ((Consumer<T>) handler).accept(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Clears all handlers.
     */
    public static void clear() {
        handlers.clear();
    }
}
