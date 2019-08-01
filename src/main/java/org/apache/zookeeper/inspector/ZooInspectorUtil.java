package org.apache.zookeeper.inspector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.zip.GZIPInputStream;
import javax.swing.tree.TreePath;

public class ZooInspectorUtil
{
  /**
   * convert TreePath to ZNodePath
   * @param treePath
   * @return
   */
  public static String treePathToZnodePath(TreePath treePath)
  {
    if (treePath == null) {
      return null;
    }

    Object[] objects = treePath.getPath();
    if (objects.length == 1) {
      return "/";
    }

    String znodePath = "";
    for (int i = 1; i < objects.length; i++)
    {
      znodePath += ("/" + objects[i].toString());
    }
    return znodePath;
  }

  public static List<String> treePathToZnodePath(TreePath[] treePaths) {
    if (treePaths == null || treePaths.length == 0) {
      return Collections.emptyList();
    }

    List<String> znodePaths = new ArrayList<String>();
    for (TreePath treePath : treePaths) {
      znodePaths.add(treePathToZnodePath(treePath));
    }
    return znodePaths;
  }

  public static boolean isCompressed(byte[] bytes) {
    if ((bytes == null) || (bytes.length < 2)) {
      return false;
    }
    return ((bytes[0] == (byte) (GZIPInputStream.GZIP_MAGIC)) && (bytes[1] == (byte) (
        GZIPInputStream.GZIP_MAGIC >> 8)));
  }

  public static byte[] uncompress(ByteArrayInputStream bais) throws IOException {
    GZIPInputStream gzipInputStream = new GZIPInputStream(bais);
    byte[] buffer = new byte[1024];
    int length;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    while ((length = gzipInputStream.read(buffer)) != -1) {
      baos.write(buffer, 0, length);
    }
    gzipInputStream.close();
    baos.close();
    byte[] uncompressedBytes = baos.toByteArray();
    return uncompressedBytes;
  }
}
