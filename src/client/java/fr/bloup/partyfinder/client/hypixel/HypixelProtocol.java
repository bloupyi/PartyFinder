/*
 * Minimal client for the Hypixel Mod API protocol (party info and location event), adapted from
 * HypixelDev/ModAPI and HypixelDev/FabricModAPI.
 *
 * Copyright (c) Hypixel Inc. Licensed under the MIT License:
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions: The above copyright
 * notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.
 */
package fr.bloup.partyfinder.client.hypixel;

import fr.bloup.partyfinder.Partyfinder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Built-in implementation used when the Hypixel Mod API mod is not installed. */
final class HypixelProtocol implements HypixelBridge.Transport {
    private static final String HELLO = "hypixel:hello";
    private static final String PARTY_INFO = "hypixel:party_info";
    private static final String REGISTER = "hypixel:register";
    private static final String LOCATION = "hyevent:location";

    private static final int PARTY_INFO_VERSION = 2;
    private static final int REGISTER_VERSION = 1;
    private static final int LOCATION_VERSION = 1;
    private static final int MAX_PARTY = 100;
    private static final int MAX_STRING = 256;
    private static final HypixelBridge.Role[] ROLES = HypixelBridge.Role.values();

    private boolean onHypixel;

    /** Raw bytes of a Hypixel plugin message; decoding happens on the client thread. */
    private record Raw(CustomPacketPayload.Type<Raw> type, byte[] data) implements CustomPacketPayload {
        static StreamCodec<ByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> type) {
            return CustomPacketPayload.codec((value, buf) -> buf.writeBytes(value.data()), buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new Raw(type, data);
            });
        }
    }

    private static CustomPacketPayload.Type<Raw> type(String id) {
        return new CustomPacketPayload.Type<>(Identifier.parse(id));
    }

    @Override
    public void init() {
        for (String id : new String[]{HELLO, PARTY_INFO, LOCATION}) {
            CustomPacketPayload.Type<Raw> type = type(id);
            StreamCodec<ByteBuf, Raw> codec = Raw.codec(type);
            PayloadTypeRegistry.clientboundPlay().register(type, codec);
            PayloadTypeRegistry.clientboundConfiguration().register(type, codec);
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handle(id, payload.data()));
            ClientConfigurationNetworking.registerGlobalReceiver(type, (payload, context) -> handle(id, payload.data()));
        }
        for (String id : new String[]{PARTY_INFO, REGISTER}) {
            CustomPacketPayload.Type<Raw> type = type(id);
            StreamCodec<ByteBuf, Raw> codec = Raw.codec(type);
            PayloadTypeRegistry.serverboundPlay().register(type, codec);
            PayloadTypeRegistry.serverboundConfiguration().register(type, codec);
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> onHypixel = false);
    }

    @Override
    public void requestParty() {
        send(PARTY_INFO, buf -> buf.writeVarInt(PARTY_INFO_VERSION));
    }

    private void handle(String id, byte[] data) {
        Minecraft.getInstance().execute(() -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
            try {
                if (id.equals(HELLO)) {
                    onHypixel = true;
                    send(REGISTER, out -> {
                        out.writeVarInt(REGISTER_VERSION);
                        out.writeVarInt(1);
                        out.writeUtf(LOCATION, 20);
                        out.writeVarInt(LOCATION_VERSION);
                    });
                    return;
                }
                // Every Hypixel response starts with a success flag, then an error id on failure.
                if (!buf.readBoolean()) {
                    Partyfinder.LOGGER.debug("Hypixel {} error {}", id, buf.readVarInt());
                    return;
                }
                if (id.equals(PARTY_INFO)) readParty(buf);
                else if (id.equals(LOCATION)) readLocation(buf);
            } catch (RuntimeException e) {
                Partyfinder.LOGGER.warn("Malformed Hypixel packet {}: {}", id, e.getMessage());
            }
        });
    }

    private static void readParty(FriendlyByteBuf buf) {
        if (buf.readVarInt() != PARTY_INFO_VERSION) return;
        boolean inParty = buf.readBoolean();
        Map<UUID, HypixelBridge.Role> members = new LinkedHashMap<>();
        if (inParty) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_PARTY) return;
            for (int i = 0; i < count; i++) {
                UUID uuid = buf.readUUID();
                int role = buf.readVarInt();
                members.put(uuid, role >= 0 && role < ROLES.length ? ROLES[role] : HypixelBridge.Role.MEMBER);
            }
        }
        HypixelBridge.onParty(inParty, members);
    }

    private static void readLocation(FriendlyByteBuf buf) {
        if (buf.readVarInt() != LOCATION_VERSION) return;
        buf.readUtf(MAX_STRING);
        String serverType = buf.readBoolean() ? buf.readUtf(MAX_STRING) : null;
        if (buf.readBoolean()) buf.readUtf(MAX_STRING);
        String mode = buf.readBoolean() ? buf.readUtf(MAX_STRING) : null;
        String map = buf.readBoolean() ? buf.readUtf(MAX_STRING) : null;
        HypixelBridge.onLocation(serverType, mode, map);
    }

    private void send(String id, Consumer<FriendlyByteBuf> writer) {
        if (!onHypixel) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        Raw payload = new Raw(type(id), data);
        if (Minecraft.getInstance().getConnection() != null) ClientPlayNetworking.send(payload);
        else ClientConfigurationNetworking.send(payload);
    }
}
