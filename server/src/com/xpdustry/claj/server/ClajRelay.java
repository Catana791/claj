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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;

import arc.ApplicationListener;
import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.net.*;
import arc.struct.*;
import arc.util.*;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.net.NetListenerFilter;
import com.xpdustry.claj.common.net.ServerReceiver;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;
import com.xpdustry.claj.common.util.AddressUtil;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.ClajEvents.*;
import com.xpdustry.claj.server.util.NetworkSpeed;
import com.xpdustry.claj.server.util.StaleConnectionsCleaner;


public class ClajRelay extends Server implements ApplicationListener {
  protected boolean closed;

  /**
   * Keeps a cache of packets received from connections that are not yet in a room. (queue of 3 packets)<br>
   * Because sometimes {@link ClajPackets.RoomJoinPacket} comes after {@link Packets.ConnectPacket},
   * so the server will ignore this essential packet and the client will waits until the timeout.
   * <p>
   * <strong>Fixed in new versions but kept for compatibility.</strong>
   */
  private final IntMap<RawPacket[]> packetQueue = new IntMap<>();
  /** Size of the packet queue. */
  private final int packetQueueSize = 3;
  //TODO: make the room host calculate the idle instead of the server, this will save bandwidth.
  /** Keeps a cache of already notified idling connection, to avoid packet spamming. */
  private final IntSet notifiedIdle = new IntSet();
  /** As server version will not change at runtime, cache the serialized packet to avoid re-serialization. */
  private ByteBuffer versionBuff;

  protected final ServerReceiver receiver;
  /** To easily get the room of a connection. */
  public final IntMap<Long> conToRoom = new IntMap<>();
  /** List of created rooms. */
  public final LongMap<ClajRoom> rooms = new LongMap<>();
  /** Read/Write speed. */
  public final NetworkSpeed networkSpeed;

  public ClajRelay() { this(null); }
  public ClajRelay(NetworkSpeed speedCalculator) {
    super(32768, 32768, new ClajServerSerializer(speedCalculator));
    networkSpeed = speedCalculator;
    receiver = new ServerReceiver(this, Core.app::post);
    //TODO: very useful?
    StaleConnectionsCleaner.init(this, 10 * 1000, RoomCreationRequestPacket.class, RoomJoinPacket.class);

    setDiscoveryHandler((c, r) -> {
      if (versionBuff == null)
        versionBuff = ByteBuffer.allocate(5).put(ClajNet.id).putInt(ClajVars.version.majorVersion);
      r.respond((ByteBuffer)versionBuff.rewind());
    });

    receiver.setFilter(new NetListenerFilter() {
      @Override
      public boolean connected(Connection connection) {
        String id = AddressUtil.encodeId(connection);
        String ip = AddressUtil.get(connection);
        connection.setName("Connection " + id); // fix id format in stacktraces

        if (isClosed() || ClajConfig.blacklist.contains(ip)) {
          connection.close(DcReason.closed);
          Log.warn("Connection @ (@) rejected " +
                   (isClosed() ? "because of a closed server." : "for a blacklisted address."), id, ip);
          return false;
        }

        Log.debug("Connection @ (@) received.", id, ip);
        ClajConnection con = new ClajConnection(connection, ip, id);
        connection.setArbitraryData(con);
        return true;
      }

      @Override
      public boolean disconnected(Connection connection, DcReason reason) {
        ClajConnection con = toClajCon(connection);
        String id = con != null ? con.sid : AddressUtil.encodeId(connection);
        String ip = con != null ? con.address : AddressUtil.get(connection);

        Log.debug("Connection @ (@) lost: @.", id, ip, reason);
        notifiedIdle.remove(connection.getID());

        // Avoid searching for a room if it was an invalid connection or just a ping
        return con != null;
      }

      @Override
      public boolean received(Connection connection, Object object) {
        ClajConnection con = toClajCon(connection);
        if (con == null) return false;

        // Compatibility for the xzxADIxzx's version
        if (object instanceof String) {
          if (ClajConfig.warnDeprecated) {
            Core.app.post(() -> {
              con.send("[scarlet][[CLaJ Server]:[] Your CLaJ version is obsolete! "
                     + "Please upgrade it by installing the 'claj' mod, in the mod browser.");
              con.close(DcReason.error);
              Log.warn("Connection @ tried to create a room but has an incompatible version.", con.sid);
              Events.fire(new RoomCreationRejectedEvent(con, CloseReason.obsoleteClient));
            });
          } else con.close(DcReason.error);
          return false;
        }

        notifiedIdle.remove(con.id);
        ClajRoom room = find(con); // Not thread-safe but shouldn't be a problem
        // Simple packet spam protection, ignored for room hosts
        boolean isRated = ClajConfig.spamLimit > 0 && (room == null || !room.isHost(con)) &&
                          !con.packetRate.allow(3000L, ClajConfig.spamLimit);

        if (isRated) {
          Core.app.post(() -> {
            if (room != null) {
              room.message(MessageType.packetSpamming);
              room.disconnected(con, DcReason.closed);
            }

            con.close();
            Log.warn("Connection @ (@) disconnected for packet spamming.", con.sid, con.address);
            Events.fire(new ClientKickedEvent(con));
          });
        }

        return !isRated;
      }

      /** Ignores if the connection idle state was already notified to the room host. */
      @Override
      public boolean idle(Connection connection) {
        return toClajCon(connection) != null && notifiedIdle.add(connection.getID());
      }
    });

    receiver.handle(Connect.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
       if (con != null) Events.fire(new ClientConnectedEvent(con));
    });
    receiver.handle(Disconnect.class, (c, p) -> {
      ClajRoom room = find(c);
      ClajConnection con = toClajCon(c);
      packetQueue.remove(c.getID());

      Events.fire(new ClientDisonnectedEvent(con, p.reason, room));
      if (room == null || con == null) return;

      conToRoom.remove(con.id);
      room.disconnected(con, p.reason);
      // Remove the room if it was the host
      if (room.isHost(con)) {
        for (ClajConnection cc : room.clients.values()) conToRoom.remove(cc.id);
        rooms.remove(room.id);
        Log.info("Room @ closed because connection @ (the host) has disconnected.", room.sid, con.sid);
        Events.fire(new RoomClosedEvent(room));
      } else Log.info("Connection @ left the room @.", con.sid, room.sid);
    });
    receiver.handle(Idle.class, (c, p) -> {
      ClajRoom room = find(c);
      if (room != null) room.idle(c);
      // No event for that, this is received to many times
    });

    receiver.handle(RoomCreationRequestPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);

      // Ignore room creation requests when the server is closing
      if (isClosed()) {
        rejectRoomCreation(con, CloseReason.serverClosed);
        Log.warn("Connection @ tried to create a room but the server is closed.", con.sid);
        return;
      } else if (p.version != ClajVars.version.majorVersion) {
        boolean isGreater = p.version > ClajVars.version.majorVersion;
        rejectRoomCreation(con, isGreater ? CloseReason.outdatedServer : CloseReason.outdatedClient);
        Log.warn("Connection @ tried to create a room but has " + (isGreater ? "a too recent" : "an outdated") +
                 " version. (was: @)", con.sid, p.version);
        return;

      //TODO: blacklisted implementations

      // Ignore if the connection is already in a room or hold one
      } else if (room != null) {
        room.message(MessageType.alreadyHosting);
        Log.warn("Connection @ tried to create a room but is already hosting the room @.", con.sid, room.sid);
        Events.fire(new ActionDeniedEvent(con, room, MessageType.alreadyHosting));
        return;
      }

      room = newRoom(con, p.type);
      rooms.put(room.id, room);
      conToRoom.put(con.id, room.id);
      room.create();
      Log.info("Room @ created by connection @.", room.sid, con.sid);
      Events.fire(new RoomCreatedEvent(room));
    });
    receiver.handle(RoomClosureRequestPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);

      if (checkRoomHost(con, room, MessageType.roomClosureDenied,
                        "Connection @ tried to close the room @ but is not the host.")) return;
      rooms.remove(room.id);
      room.close();
      Log.info("Room @ closed by connection @ (the host).", room.sid, con.sid);
      Events.fire(new RoomClosedEvent(room));
    });
    receiver.handle(RoomJoinPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);

      // Disconnect from a potential another room.
      if (room != null) {
        // Ignore if it's the host of another room
        if (room.isHost(con)) {
          room.message(MessageType.alreadyHosting);
          Log.warn("Connection @ tried to join the room @ but is already hosting the room @.", con.sid,
                   Strings.longToBase64(p.roomId), room.sid);
          Events.fire(new ActionDeniedEvent(con, room, MessageType.alreadyHosting));
          return;
        }
        room.disconnected(con, DcReason.closed);
      }

      room = get(p.roomId);

      if (isClosed()) {
        rejectRoomJoin(con, room, p.roomId, RejectReason.serverClosing);
        Log.warn("Connection @ tried to join the room @ but the server is closed.", con.sid,
                 room == null ? Strings.longToBase64(p.roomId) : room.sid);
        return;
      } else if (room == null) {
        rejectRoomJoin(con, room, p.roomId, RejectReason.roomNotFound);
        Log.warn("Connection @ tried to join the room @ but it doesn't exist.", con.sid,
                 Strings.longToBase64(p.roomId));
        return;
      // Limit to avoid room searching
      } else if (ClajConfig.joinLimit > 0 && !con.joinRate.allow(60000L, ClajConfig.joinLimit)) {
        // Act same way as not found
        rejectRoomJoin(con, room, RejectReason.roomNotFound);
        Log.warn("Connection @ tried to join the room @ but was rate limited.", con.sid, room.sid);
        return;
      } else if (!room.type.equals(p.type)) {
        rejectRoomJoin(con, room, RejectReason.incompatible);
        Log.warn("Connection @ tried to join the room @ but has an incompatible type. (was: @, need: @)",
                 con.sid, room.sid, p.type, room.type);
        return;
      } else if (room.isProtected && !p.withPassword) {
        rejectRoomJoin(con, room, RejectReason.passwordRequired);
        Log.warn("Connection @ tried to join the room @ but a password is needed.", con.sid, room.sid);
        return;
      } else if (room.isProtected && room.password != p.password) {
        rejectRoomJoin(con, room, RejectReason.invalidPassword);
        Log.warn("Connection @ tried to join the room @ but used the wrong password.", con.sid, room.sid);
        return;
      }

      conToRoom.put(con.id, room.id);
      room.connected(con);
      Log.info("Connection @ joined the room @.", con.sid, room.sid);

      // Send the queued packets of connections to room host
      RawPacket[] queue = packetQueue.remove(con.id);
      if (queue != null) {
        Log.debug("Sending queued packets of connection @ to room host.", con.sid);
        for (RawPacket element : queue) {
          if (element != null) room.received(c, element);
        }
      }

      Events.fire(new ConnectionJoinAcceptedEvent(con, room));
    });
    receiver.handle(RoomConfigPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);

      if (checkRoomHost(con, room, MessageType.configureDenied,
                        "Connection @ tried to confgure the room @ but is not the host.")) return;
      room.setConfiguration(p.isPublic, p.isProtected, p.password);
      Log.info("Connection @ (the host) changed configuration of room @.", con.sid, room.sid);
      Events.fire(new ConfigurationChangedEvent(room));
    });
    receiver.handle(RoomListRequestPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);
      //TODO
      //TODO: prepare packet in another thread or in multiple tasks?
      //      this can prevent some kind of attack by spamming this request.
    });
    receiver.handle(RoomInfoRequestPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);
      //TODO
      // Async request, put request in a queue if state is too old
    });
    receiver.handle(RoomStatePacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);

      if (checkRoomHost(con, room, MessageType.statingDenied,
                        "Connection @ tried to set state of room @ but is not the host.")) return;
      room.setState(p.state);
      Log.info("Connection @ (the host) changed the state of room @.", con.sid, room.sid); //TODO: debug?
      Events.fire(new StateChangedEvent(room));
    });

    receiver.handle(ConnectionClosedPacket.class, (c, p) -> {
      ClajConnection con = toClajCon(c);
      ClajRoom room = find(c);
      String tsid = AddressUtil.encodeId(p.conID);

      if (checkRoomHost(con, room, MessageType.conClosureDenied,
                        "Connection @ from room @ tried to close connection @ but is not the host.", tsid)) return;
      Connection target = Structs.find(getConnections(), cc -> cc.getID() == p.conID);

      // Ignore when trying to close itself or closing one that not in the same room
      if (target == null) {
        Log.warn("Connection @ from room @ tried to close a not found connection.", con.sid, room.sid);
        //TODO: an event for that?
        return ;
      } else if (target == c || !room.contains(target)) {
        Log.warn("Connection @ from room @ tried to close a connection from another room.", con.sid, room.sid);
        //TODO: warn the room?
        Events.fire(new ActionDeniedEvent(con, room, MessageType.conClosureDenied));
        return;
      }

      Log.info("Connection @ from room @ closed connection @.", con.sid, room.sid, tsid);
      room.disconnectedQuietly(target, p.reason);
      target.close(p.reason);
      // An event for this is useless, disconnect handler will trigger it
    });
    //TODO: keep these two to the server thread for optimization?
    receiver.handle(ConnectionPacketWrapPacket.class, (c, p) -> {
      ClajRoom room = find(c);
      if (room == null) return;
      if (room.isHost(c)) notifiedIdle.remove(p.conID); // not thread-safe but i don't care
      room.received(c, p);
    });
    receiver.handle(RawPacket.class, (c, p) -> {
      ClajRoom room = find(c);
      if (room != null) {
        room.received(c, p);
        return;
      }

      RawPacket[] queue = packetQueue.get(c.getID(), () -> new RawPacket[packetQueueSize]);
      for (int i=0; i<queue.length; i++) {
        if (queue[i] == null) {
          queue[i] = p;
          break;
        }
      }
    });
  }

  @Override
  public void init() {
    Events.on(ClajEvents.ServerLoadedEvent.class, e -> host(ClajVars.port));
  }

  /** At this point it's too late to notify closure. */
  @Override
  public void dispose() {
    if (!closed) {
      closed = true;
      Events.fire(new ServerStoppingEvent());
      closeRooms();
      super.stop();
    }
    try { super.dispose(); }
    catch (Exception ignored) {}
  }

  public void host(int port) throws RuntimeException {
    try { bind(port, port); }
    catch (BindException e) { throw new RuntimeException(
      "Port " + port + " already in use! Make sure no other servers are running on the same port."); }
    catch (IOException e) { throw new UncheckedIOException(e); }

    Threads.daemon("CLaJ Relay", () -> {
      try { run(); }
      catch (Throwable th) {
        if(!(th instanceof ClosedSelectorException)) {
          Threads.throwAppException(th);
          return;
        }
      }
      Log.info("Server closed.");
    });
  }

  @Override
  public void run() {
    closed = false;
    super.run();
  }

  @Override
  public void stop() { stop(null); }
  /** Call twice to force stop.     w */
  public void stop(Runnable stopped) {
    if (closed) {
      super.stop();
      return;
    }
    closed = true;

    // Notify stopping
    Events.fire(new ServerStoppingEvent());

    if (ClajConfig.warnClosing && !rooms.isEmpty()) {
      Log.info("Notifying server closure to rooms...");

      try {
        // Notify all rooms that the server will be closed
        for (ClajRoom r : rooms.values())
          r.message(MessageType.serverClosing);

        Timer.schedule(() -> {
          closeRooms();
          super.stop();
          if (stopped != null) stopped.run();
        }, 2);
        return;
      } catch (Throwable ignored) {}
    }

    closeRooms();
    super.stop();
    if (stopped != null) stopped.run();
  }

  public boolean isClosed() {
    return closed;
  }

  public void closeRooms() {
    try {
      for (ClajRoom r : rooms.values()) r.close(CloseReason.serverClosed);
    } catch (Throwable ignored) {}
    rooms.clear();
    conToRoom.clear();
  }

  protected boolean checkRoomHost(ClajConnection con, ClajRoom room, MessageType errType, String errMsg) {
    return checkRoomHost(con, room, errType, errMsg, null);
  }
  protected boolean checkRoomHost(ClajConnection con, ClajRoom room, MessageType errType, String errMsg, Object extra) {
    if (room == null || con == null) return true;
    // Only room host can close it
    if (!room.isHost(con)) {
      room.message(errType);
      if (extra == null) Log.warn(errMsg, con.sid, room.sid);
      else Log.warn(errMsg, con.sid, room.sid, extra);
      Events.fire(new ActionDeniedEvent(con, room, errType));
      return true;
    }
    return false;
  }

  public void rejectRoomCreation(ClajConnection connection, CloseReason reason) {
    RoomClosedPacket p = new RoomClosedPacket();
    p.reason = reason;
    connection.send(p);
    Events.fire(new RoomCreationRejectedEvent(connection, reason));
    connection.close();
  }

  public void rejectRoomJoin(ClajConnection connection, ClajRoom room, RejectReason reason) {
    rejectRoomJoin(connection, room, room.id, reason);
  }
  protected void rejectRoomJoin(ClajConnection connection, ClajRoom room, long roomId, RejectReason reason) {
    RoomJoinDeniedPacket p = new RoomJoinDeniedPacket();
    p.roomId = room == null ? roomId : room.id;
    p.reason = reason;
    connection.send(p);
    Events.fire(new ConnectionJoinRejectedEvent(connection, room, reason));
    connection.close();
  }

  public long newRoomId() {
    long id;
    /* re-roll if -1 because it's used to specify an uncreated room. */
    do { id = Mathf.rand.nextLong(); }
    while (id == -1 || rooms.containsKey(id));
    return id;
  }

  public ClajRoom newRoom(ClajConnection host, ClajType type) {
    return new ClajRoom(newRoomId(), host, type);
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
    Long id = conToRoom.get(con.getID());
    return id != null ? get(id) : null;
  }

  public ClajRoom find(ClajConnection con) {
    Long id = conToRoom.get(con.id);
    return id != null ? get(id) : null;
  }

  public ClajConnection toClajCon(Connection connection) {
    return connection.getArbitraryData() instanceof ClajConnection con ? con : null;
  }
}
