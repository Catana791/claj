/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.client;

import java.util.concurrent.ExecutorService;

import arc.Core;
import arc.net.NetListener;
import arc.net.Server;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.core.Version;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.net.ArcNetProvider.PacketSerializer;
import mindustry.net.Net.NetProvider;

import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.common.packets.ConnectionPacketWrapPacket;
import com.xpdustry.claj.common.status.*;


public class MindustryClajProvider implements ClajProvider {
  public static final NetProvider mindustryProvider;
  public static final Server mindustryServer;
  public static final NetListener mindustryServerDispatcher;
  public static final PacketSerializer mindustrySerializer;

  static {
    NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");
    mindustryProvider = provider;
    mindustryServer = Reflect.get(mindustryProvider, "server");
    mindustryServerDispatcher = Reflect.get(mindustryServer, "dispatchListener");
    mindustrySerializer = new PacketSerializer();
  }

  @Override
  public ExecutorService getExecutor() { 
    return Vars.mainExecutor; 
  }

  @Override
  public ClajProxy newProxy() { 
    return new MindustryClajProxy(this); 
  }

  @Override
  public NetListener getConnectionListener() {
    return mindustryServerDispatcher; 
  }

  @Override
  public String getVersion() { 
    return Main.getMeta().version; 
  }

  @Override
  public GameState getRoomState(ClajProxy proxy) { 
    return new GameState(
      proxy.roomId(),
      Vars.player.name, 
      Vars.state.map.name(), 
      Vars.state.wave, 
      Core.settings.getInt("totalPlayers", Groups.player.size()), 
      Vars.netServer.admins.getPlayerLimit(), 
      Version.build, 
      Version.type, 
      MindustryGamemode.all[Vars.state.rules.mode().ordinal()], 
      Vars.state.rules.modeName
    );
  }
  
  @Override
  public void connectClient(String host, int port, Runnable success) {
    Vars.logic.reset();
    Vars.net.reset();
    Vars.netClient.beginConnecting();
    Vars.net.connect(host, port, () -> {
      if (!Vars.net.client()) return;
      if (success != null) success.run();
    });
  }

  @Override
  public ConnectionPacketWrapPacket.Serializer getPacketWrapperSerializer() { 
    return new ConnectionPacketWrapPacket.Serializer() {
      @Override
      public void read(ConnectionPacketWrapPacket packet, ByteBufferInput read) {
        packet.object = mindustrySerializer.read(read.buffer);
      }

      @Override
      public void write(ConnectionPacketWrapPacket packet, ByteBufferOutput write) {
        mindustrySerializer.write(write.buffer, packet.object);
      }
    }; 
  }

  @Override
  public void showTextMessage(String text) {
    Call.sendMessage("[scarlet][[CLaJ Server]:[] " + text);
  }

  @Override
  public void showMessage(MessageType message) {
    Call.sendMessage("[scarlet][[CLaJ Server]:[] " +
        Core.bundle.get("claj.message." + Strings.camelToKebab(message.name())));
  }

  @Override
  public void showPopup(String text) {
    // UI#showText place the title to the wrong side =/
    //Vars.ui.showText("[scarlet][[CLaJ Server][] ", text);
    Vars.ui.showOkText("[scarlet][[CLaJ Server][] ", text, () -> {});
  }
}
