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

import arc.ApplicationListener;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.Log;


/** Save periodically the registered {@link Saveable}s and also when the application exit. */
public class Autosaver implements ApplicationListener {
  /** Called when a save fail. */
  public Cons2<Saveable, Throwable> errorHandler;

  /** Adds to the {@code normal} priority. */
  public void add(Saveable saveable) {
    add(saveable, SavePriority.normal);
  }

  public void add(Saveable saveable, SavePriority priority) {
    remove(saveable);
    priority.saves.add(saveable);
  }

  public void remove(Saveable saveable) {
    for (SavePriority p : SavePriority.all) {
      if (p.saves.remove(saveable)) break;
    }
  }

  /** Don't do that! */
  public void clear() {
    for (SavePriority p : SavePriority.all) clear(p);
  }

  /** Don't do that! */
  public void clear(SavePriority priority) {
    priority.saves.clear();
  }

  public boolean has(Saveable saveable) {
    return priorityOf(saveable) != null;
  }

  public boolean has(Saveable saveable, SavePriority priority) {
    return priority.saves.contains(saveable);
  }

  public SavePriority priorityOf(Saveable saveable) {
    for (SavePriority p : SavePriority.all) {
      if (has(saveable, p)) return p;
    }
    return null;
  }

  public boolean saveNeeded() {
    for (SavePriority p : SavePriority.all) {
      if (p.saves.contains(Saveable::modified)) return true;
    }
    return false;
  }

  /** Save all registered things now, only if modified. */
  public boolean save() {
    if (!saveNeeded()) return false;
    for (SavePriority p : SavePriority.all) {
      p.saves.each(Saveable::modified, s -> {
        try { s.save(); }
        catch (Throwable t) {
          Log.err("Failed to save " + s.name(), t);
          if (errorHandler != null) errorHandler.get(s, t);
        }
      });
    }
    return true;
  }

  /** @see #save() */
  @Override
  public void update() {
    save();
  }

  /** @see #save() */
  @Override
  public void dispose() {
    save();
  }


  /** Defines a things that can be saved by the {@link Autosaver}. */
  public interface Saveable {
    /** Used for logging. */
    String name();
    boolean modified();
    void save();
  }


  /** Defines the order to save things. */
  public enum SavePriority {
    /** Highest save priority. Commonly the things that manages settings. */
    high,
    /** Default save priority. Commonly for settings things. */
    normal,
    /** Lowest save priority, for things that should be saved last. */
    low;

    static final SavePriority[] all = values();
    // More simple to store the saveable things here.
    // Because the priority should not be modified after registration.
    final Seq<Saveable> saves = new Seq<>();
  }
}
