package org.basex.query.value.seq;

import java.util.*;

import org.basex.data.*;
import org.basex.query.util.ft.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.util.list.*;

/**
 * This class stores full-text positions and database nodes in ascending order.
 * Instances of this class are processed in the GUI to reference currently opened, marked,
 * and copied database nodes.
 *
 * @author BaseX Team, BSD License
 * @author Christian Gruen
 */
public final class DBNodes extends DBNodeSeq {
  /** Full-text position data (can be {@code null}). */
  private FTPosData ftpos;
  /** Sorted PRE values (can be {@code null}). */
  private int[] sorted;

  /**
   * Constructor, specifying a database and PRE values.
   * @param data data reference
   * @param pres PRE values
   */
  public DBNodes(final Data data, final int... pres) {
    this(data, false, pres);
  }

  /**
   * Constructor, specifying a database, PRE values and full-text positions.
   * @param all PRE values reference all documents of the database
   * @param data data reference
   * @param pres PRE values
   */
  public DBNodes(final Data data, final boolean all, final int... pres) {
    super(pres, data, all ? NodeType.DOCUMENT_NODE : NodeType.NODE, all);
  }

  /**
   * Assigns full-text position data.
   * @param ft full-text positions
   * @return self reference
   */
  public DBNodes ftpos(final FTPosData ft) {
    ftpos = ft;
    return this;
  }

  /**
   * Returns full-text position data.
   * @return position data
   */
  public FTPosData ftpos() {
    return ftpos;
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  /**
   * Returns {@code null} if the PRE values reference all documents of the database.
   * @return self reference or {@code null}
   */
  public DBNodes discardDocs() {
    if(all) return null;

    final IntList docs = data.resources.docs();
    final int[] ps = pres;
    final int pl = ps.length;
    if(pl != docs.size()) return this;

    int c = -1;
    while(++c < pl && ps[c] == docs.get(c));
    return c < pl ? this : null;
  }

  /**
   * Checks if the specified node is contained in the array.
   * @param pre PRE value
   * @return true if the node was found
   */
  public boolean contains(final int pre) {
    return find(pre) >= 0;
  }

  /**
   * Returns the position of the specified node or the negative value - 1 of
   * the position where it should have been found.
   * @param pre PRE value
   * @return position, or {@code -1}
   */
  public int find(final int pre) {
    sort();
    return Arrays.binarySearch(sorted, pre);
  }

  /**
   * Adds or removes the specified PRE node.
   * @param pre PRE value
   */
  public void toggle(final int pre) {
    final int[] n = { pre };
    pres = contains(pre) ? except(pres, n) : union(pres, n);
    size = pres.length;
    sorted = null;
  }

  /**
   * Merges the specified array with the existing PRE nodes.
   * @param pre PRE value
   */
  public void union(final int[] pre) {
    pres = union(pres, pre);
    size = pres.length;
    sorted = null;
  }

  /**
   * Merges two sorted integer arrays via union.
   * Note that the input arrays must be sorted.
   * @param pres1 first set
   * @param pres2 second set
   * @return resulting set
   */
  private static int[] union(final int[] pres1, final int[] pres2) {
    final int al = pres1.length, bl = pres2.length;
    final IntList il = new IntList();
    int a = 0, b = 0;
    while(a != al && b != bl) {
      final int d = pres1[a] - pres2[b];
      il.add(d <= 0 ? pres1[a++] : pres2[b++]);
      if(d == 0) ++b;
    }
    while(a != al) il.add(pres1[a++]);
    while(b != bl) il.add(pres2[b++]);
    return il.finish();
  }

  /**
   * Subtracts the second from the first array.
   * Note that the input arrays must be sorted.
   * @param pres1 first set
   * @param pres2 second set
   * @return resulting set
   */
  private static int[] except(final int[] pres1, final int[] pres2) {
    final int al = pres1.length, bl = pres2.length;
    final IntList il = new IntList();
    int a = 0, b = 0;
    while(a != al && b != bl) {
      final int d = pres1[a] - pres2[b];
      if(d < 0) il.add(pres1[a]);
      else ++b;
      if(d <= 0) ++a;
    }
    while(a != al) il.add(pres1[a++]);
    return il.finish();
  }

  /**
   * Creates a sorted node array. If the original array is already sorted,
   * the same reference is used.
   */
  private void sort() {
    if(sorted != null) return;
    int min = Integer.MIN_VALUE;
    for(final int pre : pres) {
      if(pre < min) {
        sorted = Arrays.copyOf(pres, pres.length);
        Arrays.sort(sorted);
        return;
      }
      min = pre;
    }
    sorted = pres;
  }

  /**
   * Returns a sorted PRE value.
   * @param index index of PRE value
   * @return PRE value
   */
  public int sorted(final int index) {
    return sorted[index];
  }

  @Override
  public DBNode itemAt(final long index) {
    final int pre = pres[(int) index];
    return ftpos == null ? new DBNode(data, pre) : new FTPosNode(data, pre, ftpos);
  }

  @Override
  public boolean equals(final Object obj) {
    return this == obj || obj instanceof final DBNodes n && data == n.data &&
        Arrays.equals(pres, n.pres) && Objects.equals(ftpos, n.ftpos);
  }
}
