/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025  Xpdustry
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

import java.io.IOException;
import java.nio.ByteBuffer;

import arc.Core;
import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.NetListener;
import arc.net.NetSerializer;
import arc.net.Server;
import arc.util.Ratekeeper;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.net.ArcNetProvider.PacketSerializer;
import mindustry.net.Net.NetProvider;
import mindustry.net.Packets.KickReason;
import mindustry.net.NetConnection;

import com.xpdustry.claj.client.proxy.*;
import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.util.Structs;


public class ClajProxy extends ProxyClient {
  /** Contant value saying that no room is created. This should be handled as an invalid id. */
  public static final long UNCREATED_ROOM = -1;
  
  private static final NetListener mindustryServerDispatcher = getMindustryServerDispatcher();
  /** No-op rate-keeper to prevent the local mindustry server from life blacklisting the claj server. */
  private static final Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };

  protected Cons<Long> roomCreated;
  protected Cons<CloseReason> roomClosed;
  protected CloseReason closeReason;
  protected long roomId = UNCREATED_ROOM;

  public ClajProxy() {
    super(32768, 16384, new Serializer(), mindustryServerDispatcher);
  }

  public static NetListener getMindustryServerDispatcher() {
    NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");
    Server server = Reflect.get(provider, "server");
    return Reflect.get(server, "dispatchListener");
  }
  
  /** This method must be used instead of others connect methods */
  public void connect(String host, int udpTcpPort, Cons<Long> roomCreatedCallback, 
                      Cons<CloseReason> roomClosedCallback) throws IOException {
    roomCreated = roomCreatedCallback;
    roomClosed = roomClosedCallback;
    try { connect(defaultTimeout, host, udpTcpPort, udpTcpPort); }
    catch (IOException e) {
      runRoomClose();
      throw e;
    }
  }
  
  protected void runRoomCreated() {
    if (roomCreated != null) 
      Core.app.post(() -> roomCreated.get(roomId));
  }
  
  /** This also resets room id and removes callbacks. */
  protected void runRoomClose() {
    roomId = UNCREATED_ROOM;
    if (roomClosed != null) 
      Core.app.post(() -> roomClosed.get(closeReason));
    roomCreated = null;
    roomClosed = null;
  }
  
  /** {@code -1} means no room created. */
  public long roomId() {
    return roomId;
  }
  
  public Iterable<NetConnection> getMindustryConnections() {
    return Structs.generator(getConnections(), c -> c.getArbitraryData() instanceof NetConnection,
                             c -> (NetConnection)c.getArbitraryData());
  }
  
  public void kickAllConnections(KickReason reason) {
    for (NetConnection con : getMindustryConnections()) 
      con.kick(reason);
  }

  @Override
  public void close() {
    if (isConnected()) {
      // Kick player before
      kickAllConnections(KickReason.serverClose);
      sendTCP(makeRoomClosePacket());
    }
    super.close();
  }

  @Override
  public void connected(Connection connection) {
    super.connected(connection);
    // Request the room link
    sendTCP(makeRoomCreatePacket(Main.getMeta().version));
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    runRoomClose();
    super.disconnected(connection, reason);
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!(object instanceof Packet p)) return;
    p.handled(); //TODO: temporary, need to use ClajNet

    if (object instanceof ClajPopupPacket popup) {
      // UI#showText place the title to the wrong side =/
      //Vars.ui.showText("[scarlet][[CLaJ Server][] ", popup.message);
      Vars.ui.showOkText("[scarlet][[CLaJ Server][] ", popup.message, () -> {});
      
    } else if (object instanceof ClajTextMessagePacket text) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " + text.message);
    
    } else if (object instanceof ClajMessagePacket msg) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " +
                       Core.bundle.get("claj.message." + Strings.camelToKebab(msg.message.name())));
      //TODO: make a system to reconnect to another server when the current one is closing.
    
    } else if (object instanceof RoomClosedPacket close) {
      closeReason = close.reason;
      
    } else if (object instanceof RoomLinkPacket link) {
      // Ignore if the room id is received twice
      if (roomId != -1) return;
      
      roomId = link.roomId;
      // -1 is not allowed since it's used to specify an uncreated room
      if (roomId != -1) runRoomCreated();    
      
    } else if (object instanceof ConnectionWrapperPacket) {
      // Ignore packets until the room id is received
      if (roomId == -1) return;
      
      else if (object instanceof ConnectionJoinPacket join) {
        if (getConnection(join.conID) != null) return;
        // Check if the link is the right
        if (join.roomId != roomId) {
          // cannot use #close(VirtualConnection, DcReason) since connection is not yet created.
          sendTCP(makeConClosePacket(join.conID, DcReason.error));
          return;
        }
        
        Core.app.post(() -> {
          VirtualConnection con = conConnected(join.conID);
          // Change the packet rate and chat rate to a no-op version
          ((NetConnection)con.getArbitraryData()).packetRate = noopRate;
          ((NetConnection)con.getArbitraryData()).chatRate = noopRate;
        });
        
      } else if (object instanceof ConnectionPacketWrapPacket wrap) {
        Core.app.post(() -> conReceived(wrap.conID, wrap.object));

      } else if (object instanceof ConnectionIdlingPacket idle) {
        Core.app.post(() -> conIdle(idle.conID));

      } else if (object instanceof ConnectionClosedPacket close) {
        Core.app.post(() -> conDisconnected(close.conID, close.reason));
      }
    }
  }
  
  protected Object makeRoomCreatePacket(String version) {
    RoomCreationRequestPacket p = new RoomCreationRequestPacket();
    p.version = version;
    return p;
  }
  
  protected Object makeRoomClosePacket() {
    return new RoomClosureRequestPacket();
  }
  
  @Override
  protected Object makeConWrapPacket(int conId, Object object, boolean tcp) { 
    ConnectionPacketWrapPacket p = new ConnectionPacketWrapPacket();
    p.conID = conId;
    p.isTCP = tcp;
    p.object = object;
    return p;
  }

  @Override
  protected Object makeConClosePacket(int conId, DcReason reason) { 
    ConnectionClosedPacket p = new ConnectionClosedPacket();
    p.conID = conId;
    p.reason = reason;
    return p;
  }
  
  
  public static class Serializer implements NetSerializer {
    public static final PacketSerializer arcSerializer = new PacketSerializer();
    
    static {
      ConnectionPacketWrapPacket.readContent = (p, r) -> p.object = arcSerializer.read(r.buffer);
      ConnectionPacketWrapPacket.writeContent = (p, w) -> arcSerializer.write(w.buffer, p.object);
    }
    
    //maybe faster without ThreadLocal?
    protected final ByteBufferInput read = new ByteBufferInput();
    protected final ByteBufferOutput write = new ByteBufferOutput();

    @Override
    public Object read(ByteBuffer buffer) {
      switch (buffer.get()) {
        case ClajNet.frameworkId: 
          return arcSerializer.readFramework(buffer);
        
        case ClajNet.oldId:
          throw new ArcNetException("Received a packet from the old CLaJ protocol");
          
        case ClajNet.id:
          read.buffer = buffer;
          Packet packet = ClajNet.newPacket(buffer.get());
          packet.read(read);
          return packet;
          
        default:
          buffer.position(buffer.position()-1);
          throw new ArcNetException("Unknown protocol id: " + buffer.get());
      }
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ByteBuffer buf) {
        buffer.put(buf);
        
      } else if (object instanceof FrameworkMessage framework) {
        buffer.put(ClajNet.frameworkId);
        arcSerializer.writeFramework(buffer, framework);

      } else if (object instanceof Packet packet) {
        write.buffer = buffer;
        if (!(object instanceof RawPacket)) 
          buffer.put(ClajNet.id).put(ClajNet.getId(packet));
        packet.write(write);
        
      } else {
        throw new ArcNetException("Unknown packet type: " + object.getClass());
      }
    }
  }
}
