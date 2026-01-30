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

import arc.Files;
import arc.files.Fi;
import arc.struct.ObjectSet;

import com.xpdustry.claj.server.util.JsonSettings;


public class ClajConfig {
  public static final String fileName = "config.json";

  protected static JsonSettings settings;

  /** Debug log level enabled or not */
  public static boolean debug = false;
  /** Limit for packet count sent within 3 sec that will lead to a disconnect. Ignored for room hosts. */
  public static int spamLimit = 300;
  /** Limit of room join requests per minute. The server will act as if the room had not been found. */
  public static int joinLimit = 20;
  /** Warn a client that trying to create a room, that it's CLaJ version is deprecated. */
  public static boolean warnDeprecated = true;
  /** Warn all clients when the server is closing */
  public static boolean warnClosing = true;
  /** Simple ip blacklist */
  public static ObjectSet<String> blacklist = new ObjectSet<>();


  @SuppressWarnings("unchecked")
  public static void load() {
    // Load file
    if (settings == null)
      settings = new JsonSettings(new Fi(fileName, Files.FileType.local), true, true, true, false);
    settings.load();

    // Load values
    debug = settings.getBool("debug", false);
    spamLimit = settings.getInt("spam-limit", 300);
    joinLimit = settings.getInt("join-limit", 20);
    warnDeprecated = settings.getBool("warn-deprecated", true);
    warnClosing = settings.getBool("warn-closing", true);
    blacklist = settings.get("blacklist", ObjectSet.class, String.class, ObjectSet::new);

    // Will create the file of not existing yet.
    save();
  }

  public static void save() {
    if (settings == null) return;

    settings.put("debug", debug);
    settings.put("spam-limit", spamLimit);
    settings.put("join-limit", joinLimit);
    settings.put("warn-deprecated", warnDeprecated);
    settings.put("warn-closing", warnClosing);
    settings.put("blacklist", String.class, blacklist.toSeq());

    // Save file
    settings.save();
  }
}
