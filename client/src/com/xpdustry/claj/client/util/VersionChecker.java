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

package com.xpdustry.claj.client.util;

import arc.func.Cons;
import arc.func.ConsT;
import arc.util.Http;
import arc.util.Http.HttpResponse;
import arc.util.Log;
import arc.util.serialization.JsonReader;

import mindustry.Vars;
import mindustry.mod.Mod;
import mindustry.mod.Mods;

import com.xpdustry.claj.common.util.Strings;


public class VersionChecker {
  public static String keyToFind = "tag_name";
  public static String repoLinkFormat = "https://github.com/@/releases/latest";
  public static String repoApiLinkFormat = Vars.ghApi + "/repos/@/releases/latest";
  
  public static <T extends Mod> UpdateState checkFor(T mod) { return checkFor(mod, true); }
  public static <T extends Mod> UpdateState checkFor(T mod, boolean promptStatus) {
    Mods.LoadedMod load = Vars.mods.getMod(mod.getClass());
    if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
    return checkFor(load.meta, promptStatus);
  }
  
  public static UpdateState checkFor(Mods.ModMeta mod) { return checkFor(mod, true); }
  /** 
   * Check for update using the "{@code version}" and "{@code repo}" properties 
   * in the mod/plugin definition ({@code <plugin/mod>.[h]json}).
   * <p>
   * The github repo must be formatted like that "{@code <username>/<repo-name>}".<br>
   * The version must be formatted like that "{@code 146.2}" and can starts with "{@code v}", 
   * but must not contains letters, like "{@code beta}" or "{@code -dev}".
   * 
   * @return the update state of the mod
   */
  public static UpdateState checkFor(Mods.ModMeta mod, boolean promptStatus) {
    return checkFor(mod.repo, mod.version, mod.displayName, promptStatus);
  }
  
  public static UpdateState checkFor(String repo, String version, String name, boolean promptStatus) {
    if (promptStatus) Log.info("Checking for updates...");
    
    if (repo == null || repo.isEmpty() || repo.indexOf('/') == -1) {
      if (promptStatus) Log.warn("No repo found for an update.");
      return UpdateState.missing;
    } else if (version == null || version.isEmpty()) {
      if (promptStatus) Log.warn("No current version found for an update.");
      return UpdateState.missing;
    }
    
    UpdateState[] status = {UpdateState.error};
    Cons<Throwable> fail = failure(th -> {}, promptStatus);
    Http.get(Strings.format(repoApiLinkFormat, repo)).timeout(5000).error(fail)
        .block(process(s -> status[0] = s, fail, repo, version, name, promptStatus));
    
    return status[0];
  }
  
  public static <T extends Mod> void checkAsyncFor(T mod, Cons<UpdateState> success, Cons<Throwable> failure) { 
    checkAsyncFor(mod, true, success, failure); 
  }
  
  public static <T extends Mod> void checkAsyncFor(T mod, boolean promptStatus, Cons<UpdateState> success,
                                                   Cons<Throwable> failure) {
    Mods.LoadedMod load = Vars.mods.getMod(mod.getClass());
    if(load == null) throw new IllegalArgumentException("Mod is not loaded yet (or missing)!");
    checkAsyncFor(load.meta, promptStatus, success, failure);
  }
  
  public static void checkAsyncFor(Mods.ModMeta mod, Cons<UpdateState> success, Cons<Throwable> failure) { 
    checkAsyncFor(mod, true, success, failure); 
  }
  
  public static void checkAsyncFor(Mods.ModMeta mod, boolean promptStatus, Cons<UpdateState> success, 
                              Cons<Throwable> failure) {
    checkAsyncFor(mod.repo, mod.version, mod.displayName, promptStatus, success, failure);
  }
  
  public static void checkAsyncFor(String repo, String version, String name, boolean promptStatus, 
                                   Cons<UpdateState> success, Cons<Throwable> failure) {
    if (promptStatus) Log.info("Checking for updates...");
    
    if (repo == null || repo.isEmpty() || repo.indexOf('/') == -1) {
      if (promptStatus) Log.warn("No repo found for an update.");
      success.get(UpdateState.missing);
      return;
    } else if (version == null || version.isEmpty()) {
      if (promptStatus) Log.warn("No current version found for an update.");
      success.get(UpdateState.missing);
      return;
    }
    
    Cons<Throwable> fail = failure(th -> {
      success.get(UpdateState.error);
      failure.get(th);
    }, promptStatus);
    
    Http.get(Strings.format(repoApiLinkFormat, repo)).timeout(5000).error(fail)
        .submit(process(success, fail, repo, version, name, promptStatus));
  }
  
  private static Cons<Throwable> failure(Cons<Throwable> callback, boolean promptStatus) {
    return failure -> {
      if (failure == null) {
        if (promptStatus) Log.err("Unable to check for updates: no content received.");
      } else {
        String message = failure.getMessage();
        if (message != null && message.contains("not found: tag_name")) {
          if (promptStatus) {
            Log.err("Unable to check for updates: invalid Json or missing key 'tag_name'.");
            Log.err("Error: @", failure.getLocalizedMessage());
          }  
        } else if (promptStatus) 
          Log.err("Unable to check for updates: @", failure.getLocalizedMessage());
      }
      
      callback.get(failure);
    };
  }
  
  private static ConsT<HttpResponse, Exception> process(
      Cons<UpdateState> success, Cons<Throwable> failure, String repo,
      String version, String name, boolean promptStatus
  ) {
    return s -> {
      String content = s.getResultAsString().trim();
      if (content.isEmpty()) {
        failure.get(null);
        return;
      }
      
      // Extract the version
      String tagName;
      try { tagName = new JsonReader().parse(content).getString(keyToFind); } 
      catch (Exception e) {
        failure.get(e);
        return;
      }  
      
      // Compare the version
      if (promptStatus) Log.info("Found version: @. Current version: @", tagName, version);
      if (Strings.isVersionAtLeast(version, tagName)) {
        if (promptStatus) Log.info("Check out this link to upgrade @: @", name, 
                                      Strings.format(repoLinkFormat, repo));
        success.get(UpdateState.outdated);
      } else {
        if (promptStatus) Log.info("Already up-to-date, no need to update.");
        success.get(UpdateState.uptodate);
      }  
    };
  }
  
  
  public static enum UpdateState {
    /** "version" or/and "repo" properties are missing in the mod/plugin definition. */
    missing,
    /** Error while checking for updates. */
    error, 
    /** No new updates found, it's the latest version. */
    uptodate,
    /** An update was found, the mod/plugin needs to be upgraded. */
    outdated
  }
}
