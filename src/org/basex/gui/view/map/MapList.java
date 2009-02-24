package org.basex.gui.view.map;

import org.basex.data.Data;
import org.basex.util.IntList;
import org.basex.util.Token;

/**
 * stores an integer array of pre values and their corresponding weights, sizes 
 * and number of children.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Joerg Hauser
 */
public class MapList extends IntList{
  /** Weights array. */
  public double[] weights;
  /** Sizes array. */
  public long[] sizes;
  /** Number of children Array. */
  public int[] nrchilds;

  /**
   * Constructor, specifying an initial list size.
   */
  public MapList() {
  }
  
  /**
   * Constructor, specifying an initial list size.
   * @param is initial size of the list
   */
  public MapList(final int is) {
    super(is);
  }

  /**
   * Constructor, specifying an initial array.
   * @param v initial list values
   */
  public MapList(final int[] v) {
    super(v);
  }
  
  @Override
  public void sort() {
    if(weights == null) return;
    boolean switched = true;
    int n = size;
    do {
      switched = false;
      for(int i = 0; i < size - 1; i++) {
        if(weights[i] < weights[i + 1]) {
          // switch entries
          int tmp = list[i];
          list[i] = list[i + 1];
          list[i + 1] = tmp;
          // switch weights
          double wtmp = weights[i];
          weights[i] = weights[i + 1];
          weights[i + 1] = wtmp;
          // switch sizes
          long stmp = sizes[i];
          sizes[i] = sizes[i + 1];
          sizes[i + 1] = stmp;
          // switch number of childs
          int ctmp = nrchilds[i];
          nrchilds[i] = nrchilds[i + 1];
          nrchilds[i + 1] = ctmp;
          
          switched = true;
        }
      }
      n--;
    } while (n >= 0 && switched);
  }
  
  /**
   * Calculates the weights of each list entry and stores it in an extra list.
   * @param parsize reference size
   * @param parchilds reference number of nodes
   * @param data reference
   */
  public void initWeights(final long parsize, final int parchilds,
      final Data data) {
    weights = new double[list.length];

    for(int i = 0; i < size - 1; i++) {
      weights[i] = MapLayout.calcWeight(sizes[i], nrchilds[i], parsize,
          parchilds, data);
    }
  }
  
  /**
   * Inits the sizes and number of child of each nodes in this list.
   * @param data reference
   */
  public void initChilds(final Data data) {
    nrchilds = new int[list.length];
    sizes = new long[list.length];

    for(int i = 0; i < size - 1; i++) {
      sizes[i] = data.fs != null ? 
          Token.toLong(data.attValue(data.sizeID, list[i])) : 0;
      nrchilds[i] = list[i + 1] - list[i];
    }
  }
}
