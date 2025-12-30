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

package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import arc.Events;
import arc.math.Mathf;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.net.FrameworkMessage.*;
import arc.net.NetListener;
import arc.net.NetSerializer;
import arc.net.Server;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.LongMap;
import arc.util.Log;
import arc.util.Ratekeeper;
import arc.util.Structs;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.ClajEvents.*;
import com.xpdustry.claj.server.util.NetworkSpeed;
import com.xpdustry.claj.server.util.StaleConnectionsCleaner;


public class ClajRelay extends Server implements NetListener {
  protected boolean closed;
  /** 
   * Keeps a cache of packets received from connections that are not yet in a room. (queue of 3 packets)<br>
   * Because sometimes {@link ClajPackets.RoomJoinPacket} comes after {@link Packets.ConnectPacket}, 
   * when the client connection is slow, so the server will ignore this essential packet and the client
   * will waits until the timeout.
   */
  protected final IntMap<ByteBuffer[]> packetQueue = new IntMap<>();
  /** Size of the packet queue. */
  protected final int packetQueueSize = 3;
  /** Keeps a cache of already notified idling connection, to avoid packet spamming. */
  protected final IntSet notifiedIdle = new IntSet();
  /** List of created rooms. */
  public final LongMap<ClajRoom> rooms = new LongMap<>();
  /** Read/Write speed. */
  public final NetworkSpeed networkSpeed;
  
  public ClajRelay() { this(null); }
  public ClajRelay(NetworkSpeed speedCalculator) {
    super(32768, 16384, new Serializer(speedCalculator));
    networkSpeed = speedCalculator;
    addListener(this);
    StaleConnectionsCleaner.init(this, 10 * 1000, RoomCreationRequestPacket.class, RoomJoinPacket.class);
  }

  @Override
  public void run() {
    closed = false;
    super.run();
  }
  
  @Override
  public void stop() {
    closed = true;
    
    // Notify stopping
    Events.fire(new ServerStoppingEvent());
    
    if (ClajConfig.warnClosing && !rooms.isEmpty()) {
      Log.info("Notifying rooms that the server is closing...");
      
      try {
        // Notify all rooms that the server will be closed
        for (ClajRoom r : rooms.values()) r.message(MessageType.serverClosing); 
      
        // Yea we needs a new thread... because we can't uses arc.util.Timer
        Threads.thread(() -> {
          // Give time to message to be send to all clients
          try { Thread.sleep(2000); }
          catch (InterruptedException ignored) {}
          closeRooms();
          super.stop();
        });
        return;
      } catch (Throwable ignored) {}
    }

    closeRooms();
    super.stop();
  }
  
  public boolean isClosed() {
    return closed;
  }
  
  public void closeRooms() {
    try { 
      for (ClajRoom r : rooms.values()) r.close(CloseReason.serverClosed);
    } catch (Throwable ignored) {}
    rooms.clear();  
  }
  
  public void rejectRoomCreation(Connection connection, CloseReason reason) {
    RoomClosedPacket p = new RoomClosedPacket();
    p.reason = reason;
    connection.sendTCP(p);
    connection.close(DcReason.closed);
    Events.fire(new RoomCreationRejectedEvent(connection, p.reason));
  }
  
  @Override
  public void connected(Connection connection) {
    connection.setName("Connection " + Strings.conIDToString(connection)); // fix id format in stacktraces
    
    if (isClosed() && ClajConfig.blacklist.contains(Strings.getIP(connection))) {
      connection.close(DcReason.closed);
      return;
    }
  
    Log.debug("Connection @ received.", Strings.conIDToString(connection));
    connection.setArbitraryData(new Ratekeeper());
    Events.fire(new ClientConnectedEvent(connection));
  }
  
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    Log.debug("Connection @ lost: @.", Strings.conIDToString(connection), reason);
    notifiedIdle.remove(connection.getID());
    packetQueue.remove(connection.getID());
    
    // Avoid searching for a room if it was an invalid connection or just a ping
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;
    
    ClajRoom room = find(connection);
    
    if (room != null) {
      room.disconnected(connection, reason);
      // Remove the room if it was the host
      if (connection == room.host) {
        rooms.remove(room.id);
        Log.info("Room @ closed because connection @ (the host) has disconnected.", room.idString, 
                 Strings.conIDToString(connection));
        Events.fire(new RoomClosedEvent(room));
      } else Log.info("Connection @ left the room @.",  Strings.conIDToString(connection), room.idString);  
    }
    
    Events.fire(new ClientDisonnectedEvent(connection, reason, room));
  }
  
  @Override
  public void received(Connection connection, Object object) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper rate)) return;
    
    // Compatibility for the xzxADIxzx's version
    if (object instanceof String && ClajConfig.warnDeprecated) {
      connection.sendTCP("[scarlet][[CLaJ Server]:[] Your CLaJ version is obsolete! "
                       + "Please upgrade it by installing the 'claj' mod, in the mod browser.");
      connection.close(DcReason.error);
      Log.warn("Rejected room creation of connection @ for incompatible version.", Strings.conIDToString(connection));
      Events.fire(new RoomCreationRejectedEvent(connection, CloseReason.obsoleteClient));
      return;
    }
    
    if (!(object instanceof Packet p)) return;
    p.handled(); //TODO: temporary, need to use ClajNet
    notifiedIdle.remove(connection.getID());
    ClajRoom room = find(connection);
    
    // Simple packet spam protection, ignored for room hosts
    if ((room == null || room.host != connection) && 
        ClajConfig.spamLimit > 0 && !rate.allow(3000L, ClajConfig.spamLimit)) {

      if (room != null) {
        room.message(MessageType.packetSpamming);
        room.disconnected(connection, DcReason.closed);
      }
      
      connection.close(DcReason.closed);   
      Log.warn("Connection @ disconnected for packet spamming.", Strings.conIDToString(connection));
      Events.fire(new ClientKickedEvent(connection));

    } else if (object instanceof RoomJoinPacket join) {
      // Disconnect from a potential another room.
      if (room != null) {
        // Ignore if it's the host of another room
        if (room.host == connection) {
          room.message(MessageType.alreadyHosting);
          Log.warn("Connection @ tried to join the room @ but is already hosting the room @.", 
                   Strings.conIDToString(connection), 
                   Strings.longToBase64(join.roomId), room.idString);
          Events.fire(new ActionDeniedEvent(connection, MessageType.alreadyHosting));
          return;
        }
        room.disconnected(connection, DcReason.closed);
      }

      room = get(join.roomId);
      if (room != null) {
        room.connected(connection);
        Log.info("Connection @ joined the room @.", Strings.conIDToString(connection), room.idString);
        
        // Send the queued packets of connections to room host
        ByteBuffer[] queue = packetQueue.remove(connection.getID());
        if (queue != null) {
          Log.debug("Sending queued packets of connection @ to room host.", Strings.conIDToString(connection));
          for (int i=0; i<queue.length; i++) {
            if (queue[i] != null) room.received(connection, queue[i]);
          }
        }
        
        Events.fire(new ConnectionJoinedEvent(connection, room));

      //TODO: make a limit to avoid room searching; e.g. if more than 100 in one minute, ignore request for 10 min
      } else connection.close(DcReason.error);

    } else if (object instanceof RoomCreationRequestPacket create) {
      // Ignore room creation requests when the server is closing
      if (isClosed()) {
        rejectRoomCreation(connection, CloseReason.serverClosed);
        return;
      }
      
      // Check the version of client
      String version = create.version;
      // Ignore the last part of version, the minor part. (versioning format: 2.major.minor)
      // The minor part is used when no changes have been made to the protocol itself. (sending/receiving way)
      if (version == null || Strings.isVersionAtLeast(version, ClajVars.version, 2)) {
        rejectRoomCreation(connection, CloseReason.outdatedVersion);
        Log.warn("Rejected room creation of connection @ for outdated version. (was: @)", 
                 Strings.conIDToString(connection), version);
        return;
      }
      
      // Ignore if the connection is already in a room or hold one
      if (room != null) {
        room.message(MessageType.alreadyHosting);
        Log.warn("Connection @ tried to create a room but is already hosting the room @.", 
                 Strings.conIDToString(connection), room.idString);
        Events.fire(new ActionDeniedEvent(connection, MessageType.alreadyHosting));
        return;
      }

      room = new ClajRoom(newRoomId(), connection);
      rooms.put(room.id, room);
      room.create();
      Log.info("Room @ created by connection @.", room.idString, Strings.conIDToString(connection));
      Events.fire(new RoomCreatedEvent(room));

    } else if (object instanceof RoomClosureRequestPacket) {
      // Only room host can close the room
      if (room == null) return;
      if (room.host != connection) {
        room.message(MessageType.roomClosureDenied);
        Log.warn("Connection @ tried to close the room @ but is not the host.", Strings.conIDToString(connection),
                 room.idString);
        Events.fire(new ActionDeniedEvent(connection, MessageType.roomClosureDenied));
        return;
      }

      rooms.remove(room.id);
      room.close();
      Log.info("Room @ closed by connection @ (the host).", room.idString, Strings.conIDToString(connection));
      Events.fire(new RoomClosedEvent(room));
    
    } else if (object instanceof ConnectionClosedPacket close) {
      // Only room host can request a connection closing
      if (room == null) return;
      if (room.host != connection) {
        room.message(MessageType.conClosureDenied);
        Log.warn("Connection @ tried to close the connection @ but is not the host of room @.", 
                 Strings.conIDToString(connection), 
                 Strings.conIDToString(close.conID), room.idString);
        Events.fire(new ActionDeniedEvent(connection, MessageType.conClosureDenied));
        return;
      }
      
      int conID = close.conID;
      Connection con = Structs.find(getConnections(), c -> c.getID() == conID);
      DcReason reason = close.reason;
      
      // Ignore when trying to close itself or closing one that not in the same room
      if (con == connection || !room.contains(con)) {
        Log.warn("Connection @ (room @) tried to close a connection from another room.", 
                 Strings.conIDToString(connection), room.idString);
        return;
      }
      
      if (con != null) {
        Log.info("Connection @ (room @) closed the connection @.", Strings.conIDToString(connection), 
                 room.idString, Strings.conIDToString(con));
        room.disconnectedQuietly(con, reason);
        con.close(reason);
        // An event for this is useless, #disconnected() will trigger it
      }
      
    // Ignore if the connection is not in a room
    } else if (room != null) {
      if (room.host == connection && (object instanceof ConnectionWrapperPacket wrapper))
        notifiedIdle.remove(wrapper.conID);
      
      room.received(connection, object);

    // Puts in queue; if full, future packets will be ignored.
    } else if (object instanceof RawPacket raw) {
      ByteBuffer[] queue = packetQueue.get(connection.getID(), () -> new ByteBuffer[packetQueueSize]);
      
      for (int i=0; i<queue.length; i++) {
        if (queue[i] == null) {
          queue[i] = ByteBuffer.wrap(raw.data);
          break;
        }
      }
    }
  }
  
  //TODO: make the room host calculate the idle instead of the server, this will save bandwidth.
  /** Does nothing if the connection idle state was already notified to the room host. */
  @Override
  public void idle(Connection connection) {
    if (!(connection.getArbitraryData() instanceof Ratekeeper)) return;
    if (!notifiedIdle.add(connection.getID())) return;

    ClajRoom room = find(connection);
    if (room != null) room.idle(connection);
  }
  
  public long newRoomId() {
    long id;
    /* re-roll if -1 because it's used to specify an uncreated room. */ 
    do { id = Mathf.rand.nextLong(); } 
    while (id == -1 || rooms.containsKey(id));
    return id;
  }
  
  public ClajRoom get(long roomId) {
    return rooms.get(roomId);
  }
  
  /** Try to find a room using the base64 encoded id. */
  public ClajRoom get(String encodedRoomId) {
    try { return get(Strings.base64ToLong(encodedRoomId)); }
    catch (Exception ignored) { return null; } 
  }
  
  public ClajRoom find(Connection con) {
    for (ClajRoom r : rooms.values()) {
      if (r.contains(con)) return r;
    }
    return null;  
  }


  public static class Serializer implements NetSerializer {
    static {
      ConnectionPacketWrapPacket.readContent = (p, r) -> p.raw = new RawPacket().r(r);
      ConnectionPacketWrapPacket.writeContent = (p, w) -> p.raw.w(w);
    }
    
    //maybe faster without ThreadLocal?
    private final ByteBufferInput read = new ByteBufferInput();
    private final ByteBufferOutput write = new ByteBufferOutput();
    private final NetworkSpeed networkSpeed;
    private int lastPos;
    
    /** @param networkSpeed is for debugging, sets to null to disable it */
    public Serializer(NetworkSpeed networkSpeed) {
      this.networkSpeed = networkSpeed;
    }
    
    @Override
    public Object read(ByteBuffer buffer) {
      if (networkSpeed != null) networkSpeed.addDownloadMark(buffer.remaining());
      read.buffer = buffer;
      
      switch (buffer.get()) {
        case ClajNet.frameworkId: 
          return readFramework(buffer);
        
        case ClajNet.oldId:
          if (!ClajConfig.warnDeprecated) break;
          return Strings.readUTF(read);
          
        case ClajNet.id:
          Packet packet = ClajNet.newPacket(buffer.get());
          packet.read(read);
          return packet;
      }

      // Non-claj packets are saved as raw buffer, to avoid re-serialization
      buffer.position(buffer.position()-1);
      return new RawPacket().r(read);
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (networkSpeed != null) lastPos = buffer.position();
      write.buffer = buffer;

      if (object instanceof ByteBuffer buf) {
        buffer.put(buf);
        
      } else if (object instanceof FrameworkMessage framework) {
        buffer.put(ClajNet.frameworkId);
        writeFramework(buffer, framework);
        
      } else if (object instanceof String str && ClajConfig.warnDeprecated) {
        buffer.put(ClajNet.oldId);
        Strings.writeUTF(write, str);         
        
      } else if (object instanceof Packet packet) {
        if (!(object instanceof RawPacket)) 
          buffer.put(ClajNet.id).put(ClajNet.getId(packet));
        packet.write(write);
      }
      
      if (networkSpeed != null) networkSpeed.addUploadMark(buffer.position() - lastPos);
    }

    public void writeFramework(ByteBuffer buffer, FrameworkMessage message) {
      if (message instanceof Ping ping) buffer.put((byte)0).putInt(ping.id).put(ping.isReply ? (byte)1 : 0);
      else if (message instanceof DiscoverHost) buffer.put((byte)1);
      else if (message instanceof KeepAlive) buffer.put((byte)2);
      else if (message instanceof RegisterUDP udp) buffer.put((byte)3).putInt(udp.connectionID);
      else if (message instanceof RegisterTCP tcp) buffer.put((byte)4).putInt(tcp.connectionID);
    }

    public FrameworkMessage readFramework(ByteBuffer buffer) {
      byte id = buffer.get();

      if (id == 0) {
        Ping p = new Ping();
        p.id = buffer.getInt();
        p.isReply = buffer.get() == 1;
        return p;
      } else if (id == 1) {
        return FrameworkMessage.discoverHost;
      } else if (id == 2) {
        return FrameworkMessage.keepAlive;
      } else if (id == 3) {
        RegisterUDP p = new RegisterUDP();
        p.connectionID = buffer.getInt();
        return p;
      } else if (id == 4) {
        RegisterTCP p = new RegisterTCP();
        p.connectionID = buffer.getInt();
        return p;
      } else {
        throw new RuntimeException("Unknown framework message!");
      }
    }
  }
}
