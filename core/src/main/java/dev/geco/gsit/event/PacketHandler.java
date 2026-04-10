package dev.geco.gsit.event;

import org.bukkit.entity.Player;

public interface PacketHandler {

    void setupPlayerPacketHandlers();

    void setupPlayerPacketHandler(Player player);

    void removePlayerPacketHandlers();

    void removePlayerPacketHandler(Player player);

}