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

import arc.files.Fi;

import com.xpdustry.claj.server.plugin.Plugins;
import com.xpdustry.claj.server.util.EventLoop;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajVars {
  public static ClajRelay relay;
  public static ClajControl control;
  public static String serverVersion;
  
  public static Fi workingDirectory = new Fi("", arc.Files.FileType.local);
  public static Fi pluginsDirectory = workingDirectory.child("plugins");
  
  public static Plugins plugins;
  public static EventLoop loop;
  public static NetworkSpeed networkSpeed;
}
