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

package com.xpdustry.claj.api;

import com.xpdustry.claj.common.status.GameState;


public class ClajRoom {
  public final long roomId;
  public boolean isPublic;
  public boolean isProtected;
  /** Only presents if the room is public and the server has retrieved his state. */
  public GameState state;
  /** The link to the room. */
  public ClajLink link;

  public ClajRoom(long roomId) {
    this.roomId = roomId;
  }

  public ClajRoom(long roomId, boolean isPublic, boolean isProtected, GameState state, ClajLink link) {
    this.roomId = roomId;
    this.isPublic = isPublic;
    this.isProtected = isProtected;
    this.state = state;
    this.link = link;
  }
}
