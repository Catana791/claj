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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import arc.Core;
import arc.func.Cons;
import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.net.Server;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.Reflect;
import arc.util.Strings;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net.NetProvider;
import mindustry.net.NetConnection;


public class ClajProxy extends Client implements NetListener {
  public static int defaultTimeout = 5000; //ms
  /** No-op rate keeper, to avoid the player's server from life blacklisting the claj server. */
  private static final Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };
  
  /** For faster get */
  protected final IntMap<VirtualConnection> connectionsMap = new IntMap<>();
  /** For faster iteration */
  protected final Seq<VirtualConnection> connectionsArray = new Seq<>(false);
  protected final Server server;
  protected final NetListener serverDispatcher;
  protected Cons<Long> roomCreated;
  protected Cons<ClajPackets.RoomClosedPacket.CloseReason> roomClosed;
  protected ClajPackets.RoomClosedPacket.CloseReason closeReason;
  protected long roomId = -1;
  protected volatile boolean shutdown;

  public ClajProxy() {
    super(32768, 16384, new Serializer());
    addListener(this);
    
    NetProvider provider = Reflect.get(Vars.net, "provider");
    if (Vars.steam) provider = Reflect.get(provider, "provider");

    server = Reflect.get(provider, "server");
    //connections = Reflect.get(provider, "connections");
    serverDispatcher = Reflect.get(server, "dispatchListener");
  }

  /** This method must be used instead of others connect methods */
  public void connect(String host, int udpTcpPort, Cons<Long> roomCreatedCallback, 
                      Cons<ClajPackets.RoomClosedPacket.CloseReason> roomClosedCallback) throws IOException {
    roomCreated = roomCreatedCallback;
    roomClosed = roomClosedCallback;
    connect(defaultTimeout, host, udpTcpPort, udpTcpPort);
  }
  
  /** 
   * Redefine {@link #run()} and {@link #stop()} to handle exceptions and restart update loop if needed. <br>
   * And to handle connection idling.
   */
  @Override
  public void run() {
    shutdown = false;
    while(!shutdown) {
      try { 
        update(250); 
        // update idle
        connectionsArray.each(VirtualConnection::idling, VirtualConnection::notifyIdle0);
      } catch (IOException ex) { 
        closeRoom(); 
      } catch (ArcNetException ex) {
        // Re-throw the error if the room was not created yet, else just print it
        if (roomId == -1) {
          closeRoom();
          Reflect.set(Connection.class, this, "lastProtocolError", ex);
          throw ex;
        } else Log.err("Ignored Exception", ex);
      }
    }
  }
  
  /** Redefine {@link #run()} and {@link #stop()} to handle exceptions and restart update loop if needed. */
  @Override
  public void stop() {
    if(shutdown) return;
    closeRoom();
    shutdown = true;
    Reflect.<Selector>get(Client.class, this, "selector").wakeup();
  }
  
  public long roomId() {
    return roomId;
  }
  
  public boolean isRunning() {
    return !shutdown;
  }
  
  public void closeRoom() {
    roomId = -1;
    if (isConnected()) sendTCP(new ClajPackets.RoomClosureRequestPacket());
    close();
  }

  @Override
  public void connected(Connection connection) {
    // Request the room link
    ClajPackets.RoomCreationRequestPacket p = new ClajPackets.RoomCreationRequestPacket();
    p.version = Main.getMeta().version;
    sendTCP(p);
  }

  @Override
  public void disconnected(Connection connection, DcReason reason) {
    roomId = -1;
    if (roomClosed != null) roomClosed.get(closeReason);
    // We cannot communicate with the server anymore, so close all virtual connections
    connectionsArray.each(c -> c.closeQuietly(reason));
    connectionsMap.clear();
    connectionsArray.clear();
  }

  @Override
  public void received(Connection connection, Object object) {
    if (!(object instanceof ClajPackets.Packet)) {
      return;

    } else if (object instanceof ClajPackets.ClajPopupPacket popup) {
      // UI#showText place the title to the wrong side =/
      //Vars.ui.showText("[scarlet][[CLaJ Server][] ", popup.message);
      Vars.ui.showOkText("[scarlet][[CLaJ Server][] ", popup.message, () -> {});
      
    } else if (object instanceof ClajPackets.ClajMessagePacket msg) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " + msg.message);
    
    } else if (object instanceof ClajPackets.ClajMessage2Packet msg2) {
      Call.sendMessage("[scarlet][[CLaJ Server]:[] " +
                       Core.bundle.get("claj."
                           + "message." + Strings.camelToKebab(msg2.message.name())));
    
    } else if (object instanceof ClajPackets.RoomClosedPacket close) {
      closeReason = close.reason;
      
    } else if (object instanceof ClajPackets.RoomLinkPacket link) {
      // Ignore if the room id is received twice
      if (roomId != -1) return;
      
      roomId = link.roomId;
      // -1 is not allowed since it's used to specify an uncreated room
      if (roomId != -1 && roomCreated != null) roomCreated.get(roomId);      
      
    } else if (object instanceof ClajPackets.ConnectionWrapperPacket wrapper) {
      // Ignore packets until the room id is received
      if (roomId == -1) return;
      
      int id = wrapper.conID;
      VirtualConnection con = connectionsMap.get(id);
      
      if (con == null) {
        // Create a new connection
        if (object instanceof ClajPackets.ConnectionJoinPacket join) {
          // Check if the link is the right
          if (join.roomId != roomId) {
            ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
            p.conID = id;
            p.reason = DcReason.error;
            sendTCP(p);
            return;
          }

          final VirtualConnection con0 = new VirtualConnection(this, id);
          addConnection(con0);
          Core.app.post(() -> {
            con0.notifyConnected0();
            // Change the packet rate and chat rate to a no-op version
            ((NetConnection)con0.getArbitraryData()).packetRate = noopRate;
            ((NetConnection)con0.getArbitraryData()).chatRate = noopRate;
          });
        }

      } else if (object instanceof ClajPackets.ConnectionPacketWrapPacket wrap) {
        Core.app.post(() -> con.notifyReceived0(wrap.object));

      } else if (object instanceof ClajPackets.ConnectionIdlingPacket) {
        Core.app.post(() -> con.setIdle());

      } else if (object instanceof ClajPackets.ConnectionClosedPacket close) {
        Core.app.post(() -> con.closeQuietly(close.reason));
      }
    }
  }
  
  public void addConnection(VirtualConnection con) {
    connectionsMap.put(con.id, con);
    connectionsArray.add(con);
  }
  
  public void removeConnection(VirtualConnection con) {
    connectionsMap.remove(con.id);
    connectionsArray.remove(con);
  }

  public Seq<VirtualConnection> getConnections() {
    return connectionsArray;
  }
  
  
  public static class Serializer extends ArcNetProvider.PacketSerializer {
    @Override
    public Object read(ByteBuffer buffer) {
      if (buffer.get() == ClajPackets.id) {
        ClajPackets.Packet p = ClajPackets.newPacket(buffer.get());
        p.read(new ByteBufferInput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket wrap) // This one is special
          wrap.object = super.read(buffer);
        return p;
      }

      buffer.position(buffer.position()-1);
      return super.read(buffer);
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ClajPackets.Packet) {
        ClajPackets.Packet p = (ClajPackets.Packet)object;
        buffer.put(ClajPackets.id).put(ClajPackets.getId(p));
        p.write(new ByteBufferOutput(buffer));
        if (p instanceof ClajPackets.ConnectionPacketWrapPacket wrap) // This one is special
          super.write(buffer, wrap.object);
        return;
      }

      super.write(buffer, object);
    }
  }
 
  
  /** We can safely remove and hook things, the networking has been reverse engineered. */
  public static class VirtualConnection extends Connection {
    final Seq<NetListener> listeners = new Seq<>();
    final int id;
    /** 
     * A virtual connection is always connected until we closing it, 
     * so the proxy will notify the server to close the connection in turn,
     * or when the server notifies that the connection has been closed.
     */
    volatile boolean isConnected = true;
    /** The server will notify if the client is idling */
    volatile boolean isIdling = true;
    ClajProxy proxy;
    
    public VirtualConnection(ClajProxy proxy, int id) {
      this.proxy = proxy;
      this.id = id;
      addListener(proxy.serverDispatcher);
    }
 
    @Override
    public int sendTCP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = true;
      p.object = object;
      return proxy.sendTCP(p);
    }

    @Override
    public int sendUDP(Object object) {
      if(object == null) throw new IllegalArgumentException("object cannot be null.");
      isIdling = false;

      ClajPackets.ConnectionPacketWrapPacket p = new ClajPackets.ConnectionPacketWrapPacket();
      p.conID = id;
      p.isTCP = false;
      p.object = object;
      return proxy.sendUDP(p);
    }

    @Override
    public void close(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = isIdling = false;
      if(wasConnected) {
        ClajPackets.ConnectionClosedPacket p = new ClajPackets.ConnectionClosedPacket();
        p.conID = id;
        p.reason = reason;
        proxy.sendTCP(p);

        notifyDisconnected0(reason);
      }
    }
    
    /** 
     * Close the connection without notify the server about that. <br>
     * Common use is when the server itself saying to close the connection.
     */
    public void closeQuietly(DcReason reason) {
      boolean wasConnected = isConnected;
      isConnected = isIdling = false;
      if(wasConnected) 
        notifyDisconnected0(reason);
    }
  
    @Override
    public int getID() { return id; }
    @Override
    public boolean isConnected() { return isConnected; }
    @Override
    public void setKeepAliveTCP(int keepAliveMillis) {} // never used
    @Override
    public void setTimeout(int timeoutMillis) {} // never used
    @Override 
    public InetSocketAddress getRemoteAddressTCP() { return isConnected() ? proxy.getRemoteAddressTCP() : null; } 
    @Override
    public InetSocketAddress getRemoteAddressUDP() { return isConnected() ? proxy.getRemoteAddressUDP() : null; }
    @Override
    public int getTcpWriteBufferSize() { return proxy.getTcpWriteBufferSize(); } // never used
    @Override
    public boolean isIdle() { return isIdling; }
    @Override
    public void setIdleThreshold(float idleThreshold) {} // never used
    @Override
    public String toString() { return "Connection " + id; }
    
    /** @return {@link #isConnected()} {@code &&} {@link #isIdle()}. */
    public boolean idling() {
      return isConnected && isIdling;
    }
    
    /** Only used when sending world data */
    public void addListener(NetListener listener) {
      if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
      listeners.add(listener);
    }
    
    /** Only used when sending world data */
    public void removeListener(NetListener listener) {
      if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
      listeners.remove(listener);
    }
    
    public void notifyConnected0() {
      listeners.each(l -> l.connected(this));
    }
  
    public void notifyDisconnected0(DcReason reason) {
      proxy.removeConnection(this);
      listeners.each(l -> l.disconnected(this, reason));
    }
    
    public void setIdle() {
      isIdling = true;
    }
    
    public void notifyIdle0() {
      listeners.each(l -> isIdle(), l -> l.idle(this));
    }
    
    public void notifyReceived0(Object object) {
      listeners.each(l -> l.received(this, object));
    }
  }
}
