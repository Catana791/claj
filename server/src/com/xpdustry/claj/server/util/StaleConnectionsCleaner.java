/**
 * This file is part of MoreCommands. The plugin that adds a bunch of commands to your server.
 * Copyright (c) 2025  ZetaMap
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

package com.xpdustry.claj.server.util;

import arc.net.*;
import arc.struct.*;


/** 
 * Kick stale connections. <br>
 * New connections will be kicked after x seconds if one of the specified packets is not received before the time. 
 */
public class StaleConnectionsCleaner {
  /**
   * Will add a new listener to the server to be able to kick stale connections. <br>
   * This assumes that the server receives enough packets from other clients to be able to periodically update the list. <br>
   * The worst scenario of this system is when no connection sends packets, the list will never be updated. <br>
   * But normally the server automatically handle this case. By default, {@code 12 seconds} between the last read.
   * 
   * @param server The server on which to add the listener.
   * @param timeout Time before kick if the {@link waitingPacket} is not received, in ms.
   * @param waitingPackets Packets classes to wait for.
   */
  public static void init(Server server, long timeout, Class<?>... waitingPackets) {
    server.addListener(new NetListener() {
      final ObjectMap<Connection, Long> connecting = new ObjectMap<>();
      // Avoid to use a list if there is only one packet to wait for
      final Class<?> waitingc = waitingPackets.length == 1 ? waitingPackets[0] : null;
      final ObjectSet<Class<?>> waitingl = waitingPackets.length == 1 ? null : ObjectSet.with(waitingPackets);
      boolean refreshing;
      long nextRefresh = Long.MAX_VALUE, ntimeout = timeout * 1_000_000L;

      public void connected(Connection connection) {
        connecting.put(connection, System.nanoTime() + ntimeout);
        recalculateRefresh();
      }

      public void disconnected(Connection connection, DcReason reason) {
        if (refreshing) return; // Refresher remove faster
        if (connecting.remove(connection) != null) 
          recalculateRefresh();
      }

      public void received(Connection connection, Object object) {
        if (waitingc != null ? waitingc == object.getClass() : waitingl.contains(object.getClass()) && 
            connecting.remove(connection) != null) 
          recalculateRefresh();
        cleanConnections();
      }

      private void cleanConnections() {
        long now = System.nanoTime();
        if (now < nextRefresh) return;

        refreshing = true;
        // Remove stale connections and recalculate the next refresh
        ObjectMap.Entries<Connection, Long> it = connecting.entries();
        long soonest = Long.MAX_VALUE;
        while (it.hasNext()) {
          ObjectMap.Entry<Connection, Long> e = it.next();
          if (e.value - now <= 0) {
            e.key.close(DcReason.timeout);
            it.remove();
          } else if (e.value < soonest) soonest = e.value;
        }
        nextRefresh = soonest;
        refreshing = false;
      }

      private void recalculateRefresh() {
        long soonest = Long.MAX_VALUE;
        if (!connecting.isEmpty()) {
          for (Long value : connecting.values()) {
            if (value < soonest) soonest = value;
          }  
        }
        nextRefresh = soonest;
      }
     });
   }
 }