import java.io.File;
import java.util.*;
import javax.swing.tree.TreeNode;

/**
 *
 * The Node class models a Git object as a TreeNode <p>
 * <p>
 *
 * @author  Akif Eyler
 * @see     Git documents
 */
public class Node implements TreeNode {
       final Git.Entry ent;
       final String str;
       Node par;
       Node[] data;
       Node(Git.Entry e, String s, Node p) { ent = e; str = s; par = p; }
       Node(Git.Commit c) { 
           this(c, c.toString(), null); 
           data = new Node[1]; data[0] = c.toTree();
       }
       Node(Git.Tree t, String s) { 
           this(t, t+s, null); 
           data = t.list.toArray(new Node[0]); 
       }
       Node(Git.Blob b, String s) { 
           this(b, b+s, null); 
           data = new Node[0]; 
       }
       void setFields(String n, Node p) { str = n; par = p; }
       /** returns null (not used) */
       public Enumeration<Node> children() { return null; }
       /** false for Commit and Tree, true for Blob */
       public boolean isLeaf() { return (data.length == 0); }
       /** true for Commit and Tree, false for Blob */
       public boolean getAllowsChildren() { return !isLeaf(); }
       /** get the i<sup>th</sup> Node */
       public TreeNode getChildAt(int i) { return data[i]; }
       /** number of Nodes under this Node */
       public int getChildCount() { return data.length; }
       /** search for n within the children */
       public int getIndex(TreeNode node) { 
           for (int i=0; i<data.length; i++) 
              if (data[i].equals(node)) return i; 
           return -1; 
       }
       /** parent may be a Commit or a Tree */
       public TreeNode getParent() { return par; }
       /** human-readable name */
       public String toString() { return str; }
       /** the Commit that contains this Node */
       public Node getRoot() { 
           Node c = this; 
           while (c != null) {
               Node p = c.par;
               if (p == null) break;
               c = p;
           }
           return c;
       }
       /** prints this Entry into std out */
       public void print() { ent.print(); }
       /** verifies this Entry using SHA */
       public void verify() { ent.saveTo(null); }
       /** verifies and saves this Entry into the given folder */
       public void saveTo(File dir) { ent.saveTo(dir); }
}
