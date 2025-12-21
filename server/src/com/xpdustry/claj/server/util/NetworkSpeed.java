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

package com.xpdustry.claj.server.util;

import arc.math.WindowedMean;
import arc.util.Time;


/** Calculate speed of an arbitrary thing, per seconds. E.g. network speed; in bytes per seconds. */
public class NetworkSpeed {
  protected final WindowedMean upload, download;
  protected long lastUpload, lastDownload, uploadAccum, downloadAccum;
  
  public NetworkSpeed(int windowSec) {
    upload = new WindowedMean(windowSec);
    download = new WindowedMean(windowSec);
  }
  
  public void addDownloadMark() {
    addDownloadMark(1);
  }
  
  public void addDownloadMark(int count) {
    if (Time.timeSinceMillis(lastDownload) >= 1000) {
      lastDownload = Time.millis();
      download.add(downloadAccum);
      downloadAccum = 0;
    }
    downloadAccum += count;
  }
  
  public void addUploadMark() {
    addUploadMark(1);
  }
  
  public void addUploadMark(int count) {
    if (Time.timeSinceMillis(lastUpload) >= 1000) {
      lastUpload = Time.millis();
      upload.add(uploadAccum);
      uploadAccum = 0;
    }
    uploadAccum += count;
  }
  
  /** Number of things per second. E.g. bytes per seconds */
  public float downloadSpeed() {
    return download.mean();
  }
  
  /** Number of things per second. E.g. bytes per seconds */
  public float uploadSpeed() {
    return upload.mean();
  }
}
