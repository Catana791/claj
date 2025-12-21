package com.xpdustry.claj.server.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetSocketAddress;

import arc.func.Intf;
import arc.net.Connection;
import arc.util.serialization.Base64Coder;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter.OutputType;


public class Strings extends arc.util.Strings {
  
  public static boolean isVersionAtLeast(String currentVersion, String newVersion) {
    return isVersionAtLeast(currentVersion, newVersion, 0);
  }
  /** 
   * Compare if the {@code newVersion} is greater than the {@code currentVersion}, e.g. "v146" > "124.1". <br>
   * {@code maxDepth} limits the number of comparisons of version segments, allowing sub-versions to be ignored. 
   * (default is 0)
   * 
   * @apiNote can handle multiple dots in the version and the 'v' prefix, 
   *          and it's very fast because it only does one iteration.
   */
  public static boolean isVersionAtLeast(String currentVersion, String newVersion, int maxDepth) {
    if (maxDepth < 1) maxDepth = Integer.MAX_VALUE;
    
    int last1 = currentVersion.startsWith("v") ? 1 : 0, 
        last2 = newVersion.startsWith("v") ? 1 : 0, 
        len1 = currentVersion.length(), 
        len2 = newVersion.length(),
        dot1 = 0, dot2 = 0, 
        p1 = 0, p2 = 0;
    
    while ((dot1 != -1 && dot2 != -1) && (last1 < len1 && last2 < len2) && maxDepth-- > 0) {
      dot1 = currentVersion.indexOf('.', last1);
      dot2 = newVersion.indexOf('.', last2);
      if (dot1 == -1) dot1 = len1;
      if (dot2 == -1) dot2 = len2;
      
      p1 = parseInt(currentVersion, 10, 0, last1, dot1);
      p2 = parseInt(newVersion, 10, 0, last2, dot2);
      last1 = dot1+1;
      last2 = dot2+1;

      if (p1 != p2) return p2 > p1;
    }
    if (maxDepth <= 0) return p2 > p1;

    // Continue iteration on newVersion to see if it's just leading zeros.
    while (dot2 != -1 && last2 < len2) {
      dot2 = newVersion.indexOf('.', last2);
      if (dot2 == -1) dot2 = len2;
      
      p2 = parseInt(newVersion, 10, 0, last2, dot2);
      last2 = dot2+1;
      
      if (p2 > 0) return true;
    }
    
    return false;
  }
  
  /** Taken from the {@link String#repeat(int)} method of JDK 11 */
  public static String repeat(String str, int count) {
      if (count < 0) throw new IllegalArgumentException("count is negative: " + count);
      if (count == 1) return str;

      final byte[] value = str.getBytes();
      final int len = value.length;
      if (len == 0 || count == 0)  return "";
      if (Integer.MAX_VALUE / count < len) throw new OutOfMemoryError("Required length exceeds implementation limit");
      if (len == 1) {
          final byte[] single = new byte[count];
          java.util.Arrays.fill(single, value[0]);
          return new String(single);
      }
      
      final int limit = len * count;
      final byte[] multiple = new byte[limit];
      System.arraycopy(value, 0, multiple, 0, len);
      int copied = len;
      for (; copied < limit - copied; copied <<= 1) 
          System.arraycopy(multiple, 0, multiple, copied, copied);
      System.arraycopy(multiple, 0, multiple, copied, limit - copied);
      return new String(multiple);
  }

  public static <T> int best(Iterable<T> list, Intf<T> intifier) {
    int best = 0;
    
    for (T i : list) {
      int s = intifier.get(i);
      if (s > best) best = s;
    }
    
    return best;
  }
  
  public static <T> int best(T[] list, Intf<T> intifier) {
    int best = 0;
    
    for (T i : list) {
      int s = intifier.get(i);
      if (s > best) best = s;
    }
    
    return best;
  }
  
  public static int bestLength(Iterable<? extends String> list) {
    return best(list, str -> str.length());
  }
  
  public static int bestLength(String... list) {
    return best(list, str -> str.length());
  }

  public static String normalise(String str) {
    return stripGlyphs(stripColors(str)).trim();
  }

  /** @return whether the specified string mean true */
  public static boolean isTrue(String str) {
    switch (str.toLowerCase()) {
      case "1": case "true": case "on": 
      case "enable": case "activate": case "yes":
               return true;
      default: return false;
    }
  }
  
  /** @return whether the specified string mean false */
  public static boolean isFalse(String str) {
    switch (str.toLowerCase()) {
      case "0": case "false": case "off": 
      case "disable": case "desactivate": case "no":
               return true;
      default: return false;
    }
  }

  public static String jsonPrettyPrint(JsonValue object, OutputType outputType) {
    StringWriter out = new StringWriter();
    try { jsonPrettyPrint(object, out, outputType, 0); } 
    catch (IOException ignored) { return ""; }
    return out.toString();
  }
  
  public static void jsonPrettyPrint(JsonValue object, Writer writer, OutputType outputType) throws IOException {
    jsonPrettyPrint(object, writer, outputType, 0);
  }
  
  /** 
   * Re-implementation of {@link JsonValue#prettyPrint(OutputType, Writer)}, 
   * because the ident isn't correct and the max object size before new line is too big.
   */
  public static void jsonPrettyPrint(JsonValue object, Writer writer, OutputType outputType, int indent) throws IOException {
    if (object.isObject()) {
      if (object.child == null) writer.append("{}");
      else {
        indent++;
        boolean newLines = needNewLine(object, 1);
        writer.append(newLines ? "{\n" : "{ ");
        for (JsonValue child = object.child; child != null; child = child.next) {
          if(newLines) writer.append(repeat("  ", indent));
          writer.append(outputType.quoteName(child.name));
          writer.append(": ");
          jsonPrettyPrint(child, writer, outputType, indent);
          if((!newLines || outputType != OutputType.minimal) && child.next != null) writer.append(',');
          writer.append(newLines ? '\n' : ' ');
        }
        if(newLines) writer.append(repeat("  ", indent - 1));
        writer.append('}');
      }
    } else if (object.isArray()) {
      if (object.child == null) writer.append("[]");
      else {
        indent++;
        boolean newLines = needNewLine(object, 1);
        writer.append(newLines ? "[\n" : "[ ");
        for (JsonValue child = object.child; child != null; child = child.next) {
          if (newLines) writer.append(repeat("  ", indent));
          jsonPrettyPrint(child, writer, outputType, indent);
          if ((!newLines || outputType != OutputType.minimal) && child.next != null) writer.append(',');
          writer.append(newLines ? '\n' : ' ');
        }
        if (newLines) writer.append(repeat("  ", indent - 1));
        writer.append(']');
      }
    } else if(object.isString()) writer.append(outputType.quoteValue(object.asString()));
    else if(object.isDouble()) writer.append(Double.toString(object.asDouble()));
    else if(object.isLong()) writer.append(Long.toString(object.asLong()));
    else if(object.isBoolean()) writer.append(Boolean.toString(object.asBoolean()));
    else if(object.isNull()) writer.append("null");
    else throw new arc.util.serialization.SerializationException("Unknown object type: " + object);
  }
  
  private static boolean needNewLine(JsonValue object, int maxChildren) {
    for (JsonValue child = object.child; child != null; child = child.next) 
      if (child.isObject() || child.isArray() || --maxChildren < 0) return true;
    return false;
  }
  
  public static String conIDToString(Connection con) {
    return "0x"+Integer.toHexString(con.getID());
  }
  
  public static String conIDToString(int conID) {
    return "0x"+Integer.toHexString(conID);
  }
  
  public static String getIP(Connection con) {
    InetSocketAddress a = con.getRemoteAddressTCP();
    return a == null ? null : a.getAddress().getHostAddress();
  }
  
  /** Encodes a long to an url-safe base64 string. */
  public static String longToBase64(long l) {
    byte[] result = new byte[Long.BYTES];
    for (int i=Long.BYTES-1; i>=0; i--) {
      result[i] = (byte)(l & 0xFF);
      l >>= 8;
    }
    return new String(Base64Coder.encode(result, Base64Coder.urlsafeMap));
  }
  
  public static long base64ToLong(String str) {
    byte[] b = Base64Coder.decode(str, Base64Coder.urlsafeMap);
    if (b.length != Long.BYTES) throw new IndexOutOfBoundsException("must be " + Long.BYTES + " bytes");
    long result = 0;
    for (int i=0; i<Long.BYTES; i++) {
      result <<= 8;
      result |= (b[i] & 0xFF);
    }
    return result;
  }
}
