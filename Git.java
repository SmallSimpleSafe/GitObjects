import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.text.SimpleDateFormat;

/**
 *
 * The Git class models a Git repository <p>
 * <p>
 * The inner classes represent Git entities: <br>
 * Branch, Commit, Tree, and Blob
 *
 * @author  Akif Eyler
 * @see     Git documents
 */
public class Git {

    final File root; //git repository
    final Exec X;
    final Map<String, Entry> OBJ = new LinkedHashMap<>();
    int nc, nt, nb; //number of each object type in OBJ
    int count, pass; 
    
    /**
     * Internal data uses full SHA <br>
     * SHA is abrreviated to M=6 chars in reports
     */
    final static public int M = 6; //abbrev default is 7 chars
    //final static String ABBREV = "--abbrev="+M; for cat-file command

    final static String COMMIT = "commit", TREE = "tree", BLOB = "blob",
        ROOT = "root", LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /** Reads a Git repository in the current folder */
    public Git() { this(new File(".")); }
    /** Reads a Git repository residing in File f */
    public Git(File f) {
        try {
          root = f.isDirectory()? f.getCanonicalFile(): f.getParentFile();
	    } catch (IOException x)  {
	      throw new RuntimeException(x);
	    }
	    File obj = new File(new File(root, ".git"), "objects");
        if (!obj.isDirectory()) 
          throw new RuntimeException(root+": not a Git repository");
        X = new Exec(root); readObjects();
    }
    /** Returns the SHA of the current Branch */
    public Branch currentHEAD() { 
        String[] BRANCH = {"git", "branch", "-v"};
        for (String s : X.execute(BRANCH))
            if (s.charAt(0) != ' ') return new Branch(s);
        return null; 
    }
    /** Returns an array of Branches in the repo */
    public Branch[] getAllBranches() {
        String[] BRANCH = {"git", "branch", "-v", "-a"};
        List<Branch> L = new ArrayList<>();
        for (String s : X.execute(BRANCH)) 
            try {
                L.add(new Branch(s));
            } catch (Exception x)  { //continue
            }
        System.out.println(L.size()+"  branches");
        return L.toArray(new Branch[0]);
    }
    /** Returns an array of Commits in the repo -- unused objects included */
    public Commit[] getAllCommits() {
        List<Commit> L = new ArrayList<>();
        for (Entry e : OBJ.values()) 
            if (e instanceof Commit) L.add((Commit)e);
        return L.toArray(new Commit[0]);
    }
    /** Returns an array of Entries in the repo -- unused objects included */
    public Entry[] getAllObjects() {
        return OBJ.values().toArray(new Entry[0]);
    }
    /** Returns the Git object with given SHA */
    public Entry getObject(String h) {
        if (h.length() < 40) h = X.getFullSHA(h);
        return OBJ.get(h);
    }
    /** Factory method to make Git objects -- not public */
    Entry newObject(String type, String h, int size) {
            Entry e = null;
            if (type.equals(COMMIT)) {
                nc++; e = new Commit(h, size);
            } else if (type.equals(TREE)) {
                nt++; e = new Tree(h, size);
            } else if (type.equals(BLOB)) {
                nb++; e = new Blob(h, size);
            }
            if (OBJ.put(h, e) != null) 
                System.out.println("** COLLISION AT "+h+" **");
            return e;
    }
    void readObjects() {
        String[] BATCH = 
        {"git", "cat-file", "--batch-check", "--batch-all-objects"};
        nc = 0; nt = 0; nb = 0; OBJ.clear();
        String[] out = X.execute(BATCH);
        System.out.println(out.length+" objects read");
        for (String s : out) try {
            String[] a = s.split(" ");
            String h = a[0]; String type = a[1]; 
            int k = Integer.parseInt(a[2]);
            newObject(type, h, k);
	    } catch (RuntimeException x)  {
	        System.out.printf("%s in%n%s%n", x, s);
        }
        System.out.print(OBJ.size()+" objects  "+nc+" commits  ");
        System.out.println(nt+" trees  "+nb+" blobs ");
    }
    Blob getBlob(String h) {
        Blob e = (Blob)OBJ.get(h);
        if (e != null) return e;
        return (Blob)newObject(BLOB, h, X.getObjectSize(h));
    }
    Tree getTree(String h) {
        Tree e = (Tree)OBJ.get(h);
        if (e != null) return e;
        return (Tree)newObject(TREE, h, 0); //size ignored
    }
    /** Returns and prints the Commit with given SHA */
    public Commit getCommit(String h) {
        Commit c = (Commit)getObject(h);
        if (c != null && c.name != null) return c;
        byte[] ba = X.getObjectData(h); 
        if (c == null) 
            c = (Commit)newObject(COMMIT, h, ba.length);
        String[] a = new String(ba).split("\n");
        int p = 0; String tree = null;
        if (a[p].startsWith(TREE)) {
            tree = a[p].substring(5, 45); p++;
        }
        String parent = null, par2 = null;
        while (a[p].startsWith("parent")) {
            if (parent == null) parent = a[p].substring(7, 47);
            else par2 = a[p].substring(7, 47);
            p++;
        }
        String author = null;
        long time = 0;
        if (a[p].startsWith("author")) {
            int j = a[p].indexOf(">")+1;
            if (j <= 7) j = a[p].length();
            author = a[p].substring(7, j); 
            int k = a[p].length();
            int i = k - 16;
            while (a[p].charAt(i) == ' ') i++;
            String tStr = a[p].substring(i, k-6);
            time = 1000*Long.parseLong(tStr); //msec
            p++;
        }
        while (a[p].length() > 0) p++;
        String name = a[p+1];
        
        c.name = name; c.hTree = tree; c.time = time; 
        c.hPar1 = parent; c.hPar2 = par2; c.author = author;
        c.date = FORM.format(time);
        //System.out.println(c); 
        return c;
    }
    Tree makeTree(String h, String nn) {
        String[] LSTREE = {"git", "ls-tree", "-l", h};
        String[] sa = X.execute(LSTREE); 
        Tree t = getTree(h); //t.data.clear();
        if (t.data.size() > 0) return t;
        for (String s : sa) { 
            int k = s.indexOf(32);   //first space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String type = s.substring(k+1, i);
            String hash = s.substring(i+1, i+41);
            //String size = s.substring(i+41, j); not used
            String name = s.substring(j+1);
            Entry x = null;
            if (type.equals(TREE)) {
                x = makeTree(hash, name);
            } else if (type.equals(BLOB)) {
                x = getBlob(hash);
            } else continue;  //submodules not implemented
            t.add(x, name); 
        }
        System.out.println(trim(h)+" "+nn+": "+sa.length);
        return t;
    }
    /** Returns the name of the root directory */
    public String toString() { return root.getName(); }

    /** Branch has a name and the SHA of the Commit it marks */
    public class Branch {
       final String hLast, name; Commit last;
       Branch(String s) { 
          int i = 2; while (s.charAt(i) != ' ') i++;
          int j = i; while (s.charAt(j) == ' ') j++;
          int k = j; while (s.charAt(k) != ' ') k++;
          String n = s.substring(2, i);
          if (n.startsWith("remotes/")) n = s.substring(10, i);
          name = n; hLast = s.substring(j, k);
          last = getCommit(hLast);
          System.out.println(hLast+"  "+n);
       }
       /** Returns the name and the SHA of this Branch */
       public String toString() { return name+" "+trim(hLast); }
       /** Returns the latest Commit in this Branch */
       public Commit getLatestCommit() { return last; }
       /** Returns an array of Commits in this Branch -- backwards */
       public Commit[] printAllCommits() {
          List<Commit> L = new ArrayList<>();
          Commit c = getLatestCommit();
          while (true) {
             L.add(c); System.out.println(c);
             if (c.hPar1 == null) break;
             c = (Commit)c.getParent1();
          }
          return L.toArray(new Commit[0]);
       }
    }

    /** 
     * Entry is a model of Git objects <p>
     * every object has type, SHA, and size in Git <br>
     */
    public abstract class Entry {
       final String type, hash; int size;
       //empty objects may have length 1 --> store it as 0
       Entry(String t, String h, int k) { 
           type = t; hash = h; size = (k==1? 0 : k);
       }
       /** prints this Entry into std out */
       public void print() { System.out.println(this); }
       /** verifies this Entry using SHA */
       public void verify() { saveTo(null, ROOT); }
       /** verifies and saves this Entry into the given folder */
       public abstract void saveTo(File dir, String nam);
    }

    /** 
     * All the information about the commit: <p>
     * what: SHA and pointer to the Tree (contents) <br>
     * when: time in msec and as date string <br>
     * after: the parents (0, 1, or 2 SHA links) <br>
     * who: the author (name and e-mail)
     */
    public class Commit extends Entry {
       String hTree; //Tree data; 
       long time; String date;
       String name, hPar1, hPar2, author;
       Commit(String h, int k) { super(COMMIT, h, k); }
       /** returns the actual data (folder structure) in this Commit */
       public Tree getTree() { 
           if (hTree == null) return null;
           return makeTree(hTree, ROOT);
       }
       /** returns the previous Commit */
       public Commit getParent1() { 
           if (hPar1 == null) return null;
           return getCommit(hPar1);
       }
       /** a merge Commit has two parents */
       public Commit getParent2() { 
           if (hPar2 == null) return null;
           return getCommit(hPar2);
       }
       /** returns the author */
       public String getAuthor() { return author; }
       /** returns the time in msec */
       public long getTime() { return time; }
       /** the data (folder structure) in a Node for displaying */
       public Node toTreeNode() {
           Node n = new Node(this);
           Node[] na = { getTree().toTreeNode(ROOT, n) };
           n.setData(na); return n;
       }
       /**  */
       public void print() {
           System.out.println("commit "+trim(hash)+"     "+name);
           System.out.println(date+"  "+author);
           System.out.print("parent "+trim(hPar1)+"     ");
           byte[] a = X.getObjectData(hTree); 
           int k = a.length == 0? 0 : new String(a).split("\n").length; 
           System.out.println("tree "+trim(hTree)+"  "+k+" items"); 
           System.out.println(LINE+LINE);
       }
       /** returns SHA and name */
       public String toString() { return trim(hash)+" -- "+name; }
       /**  */
       public void saveTo(File dir, String nam) { 
           System.out.println(this);
           count = 0; pass = 0; 
           getTree().saveTo(dir, nam);
           System.out.print(count+" blobs ");
           System.out.println(dir==null? pass+" OK" : " written");
       }
    }

    /** 
     * Tree represents a folder <p>
     * It contains Blobs (files) and Trees (sub-folders) <br>
     * the children are stored in an ArrayList
     */
    public class Tree extends Entry {
       final List<Entry> data = new ArrayList<>();
       final List<String> name = new ArrayList<>();
       Tree(String h, int k) { super(TREE, h, k); }
       void add(Entry e, String n) {
           data.add(e); name.add(n); 
       } 
       public String toString() {
           return trim(hash)+":  ["+name.size()+"]  "; 
       }
       /** get the i<sup>th</sup> Name */
       public String getNameAt(int i) { return name.get(i); }
       /** get the i<sup>th</sup> Entry */
       public Entry getChildAt(int i) { return data.get(i); }
       /** number of Entries under this Tree */
       public int getChildCount() { return data.size(); }
       /**  */
       public void print() {
           System.out.println(this);
           for (int i=0; i<name.size(); i++)
               System.out.println(data.get(i)+name.get(i));
       }
       /**  */
       public void saveTo(File dir, String nam) {
           System.out.println(this+nam);
           File f = null;
           if (dir != null) {
              f = new File(dir, nam);
              if (f.exists()) 
                 throw new RuntimeException("cannot overwrite "+f);
              if (!f.mkdir()) 
                 throw new RuntimeException("cannot mkdir "+f);
           }
           for (int i=0; i<name.size(); i++)
               data.get(i).saveTo(f, name.get(i));
       }
       /** makes a TreeNode -- not public */
       Node toTreeNode(String nam, Node par) {
           //System.out.println(nam+"  "+data.size());
           if (data.size() == 0) makeTree(hash, nam);
           List<Node> L = new ArrayList<>();
           Node t = new Node(this, this+nam, par);
           for (int i=0; i<data.size(); i++) {
               Entry e = data.get(i); 
               String n = name.get(i);
               Node x = null;
               if (e instanceof Blob)
                  x = new Node(e, e+n, t);
               if (e instanceof Tree)
                  x =  ((Tree)e).toTreeNode(n, t);
               L.add(x);
           }
           Node[] na = new Node[L.size()];
           t.setData(L.toArray(na)); return t;
       }
    }

    /** 
     * Blob represents a binary file <p>
     * Most methods inherit the default behavior <br>
     * 
     */
    public class Blob extends Entry {
       byte[] data;
       Blob(String h, int k) { super(BLOB, h, k); }
       /** returns SHA, name, and file size (uncompressed) */
       public String toString() {
           return trim(hash)+" ("+size+") "; 
       }
       /** prints true if data size and SHA come out as expected */
       public void saveTo(File dir, String nam) {
           count++; 
           if (data == null) data = X.getObjectData(hash);
           boolean OK = (data.length == size);
           if (OK && size > 0)
             OK = X.calculateSHA(BLOB, data).startsWith(hash);
           if (OK) pass++;
           System.out.println(trim(hash)+" "+OK+" "+size+" "+nam);
           if (dir != null) X.saveToFile(data, new File(dir, nam));
           if (data.length > Exec.LARGE) data = null; //discard large data
       }
    }

    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    /** 
     * Reads a Git repository in the current folder <br>
     * Finds the latest Commit in the current Branch <br>
     * and prints its Tree -- all Blobs recursively
     */
    public static void main(String[] args) {
        Git G = new Git(); 
        G.getAllBranches();
        Branch b = G.currentHEAD();
        b.printAllCommits();
        //b.getLatestCommit().verify();
        //.getTree().print();
    }
}
