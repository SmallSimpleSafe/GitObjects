import java.io.File;
import java.util.*;
import javax.swing.tree.TreeNode;

/**
 *
 * The Node class models a Git object as a TreeNode <p>
 * <p>
 * On top of SHA, type, and size defined in Git.Entry,
 * we also record name and parent for displaying as a Tree <br>
 * (name and parent is valid only for a particular Commit)
 *
 * @author  Akif Eyler
 * @see     Git documents
 */
public class Node implements TreeNode {
       final Git.Entry ent;
       final String str;
       Node par = null;
       Node[] data = new Node[0];
       Node(Git.Entry e, String s, Node p) { 
           ent = e; str = s; par = p; 
       }
       Node(Git.Commit c) { 
           this(c, c.toString(), null); 
       }
       void setParent(Node p) { par = p; }
       void setData(Node[] a) { data = a; }
       /** returns the Git object in this Node */
       public Git.Entry getObject() { return ent; }
       /** human-readable name */
       public String toString() { return str; }
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
       /** the Commit that contains this Node */
       public Git.Entry getRoot() { 
           Node c = this; 
           while (c != null) {
               Node p = c.par;
               if (p == null) break;
               c = p;
           }
           return c.getObject();
       }
}
