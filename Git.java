import java.io.*;
import java.util.*;
import javax.swing.tree.TreeNode;
import java.text.SimpleDateFormat;

class Git {

    final File root; //git repository
    final Exec X;
    final Map<String, Entry> OBJ = new LinkedHashMap<>();
    int nc, nt, nb; //number of each object type in OBJ
    int count, pass; 
    
    final static int M = 6; //hash length -- abbrev default is 7 chars
    //final static String ABBREV = "--abbrev="+M;
    //use full SHA -- do not abrreviate
    final static String COMMIT = "commit", TREE = "tree", BLOB = "blob";
    final static String LINE = "============================";
    final static SimpleDateFormat 
        FORM = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    public Git() { this(new File(".")); }
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
    public Entry[] getAllObjects() {
        return OBJ.values().toArray(new Entry[0]);
    }
    public Entry getObject(String h) {
        if (h.length() < 40) h = X.getFullSHA(h);
        return OBJ.get(h);
    }
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
        for (String s : X.execute(BATCH)) try {
            String[] a = s.split(" ");
            String h = a[0]; String type = a[1]; 
            int k = Integer.parseInt(a[2]);
            newObject(type, h, k);
	    } catch (RuntimeException x)  {
	        System.out.printf("%s in%n%s%n", x, s);
        }
        System.out.print(OBJ.size()+" objects  "+nc+" commits  ");
        System.out.println(nt+" trees  "+nb+" blobs");
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
    public Commit getCommit(String h) {
        if (h.length() < 40) h = X.getFullSHA(h); 
        Commit c = (Commit)OBJ.get(h);
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
        c.print(); return c;
    }
    public Branch[] getAllBranches() {
        String[] BRANCH = {"git", "branch", "-v", "-a"};
        List<Branch> L = new ArrayList<>();
        for (String s : X.execute(BRANCH))
            L.add(new Branch(s));
        System.out.println(L.size()+"  branches");
        return L.toArray(new Branch[0]);
    }
    public Branch currentHEAD() { 
        String[] BRANCH = {"git", "branch", "-v"};
        for (String s : X.execute(BRANCH))
            if (s.charAt(0) != ' ') return new Branch(s);
        return null; 
    }
    Tree makeTree(String h) {
        String[] LSTREE = {"git", "ls-tree", "-l", h};
        String[] sa = X.execute(LSTREE); 
        Tree p = getTree(h); p.list.clear();
        //System.out.println(trim(h)+" "+p.name);
        for (String s : sa) { 
            int k = s.indexOf(32);   //first space
            int i = s.indexOf(32, k+1); //second space
            int j = s.indexOf(9, i+1);  //find TAB
            String type = s.substring(k+1, i);
            String hash = s.substring(i+1, i+41);
            //String size = s.substring(i+41, j); not used
            String name = s.substring(j+1);
            //System.out.println(type+" "+size+" "+name);
            Entry e = null;
            if (type.equals(TREE)) e = makeTree(hash);
            else if (type.equals(BLOB)) e = getBlob(hash);
            else continue;  //submodules not implemented
            p.add(e, name, p); //System.out.println(e+size);
        }
        return p;
    }
    public String toString() { return root.getName(); }

    class Branch {
       String hash, name;
       Branch(String s) { 
          int i = 2; while (s.charAt(i) != ' ') i++;
          int j = i; while (s.charAt(j) == ' ') j++;
          int k = j; while (s.charAt(k) != ' ') k++;
          String n = s.substring(2, i);
          if (n.startsWith("remotes/")) n = s.substring(10, i);
          name = n;
          hash = s.substring(j, k);
          System.out.println(hash+"  "+n);
       }
       public String toString() { return name+" "+trim(hash); }
       public Commit getLatestCommit() { return getCommit(hash); }
       public Commit[] getAllCommits() {
          List<Commit> L = new ArrayList<>();
          Commit c = getLatestCommit(); L.add(c);
          while (c.hPar1 != null) {
             Commit p = (Commit)c.getParent();
             L.add(p); c = p;
          }
          return L.toArray(new Commit[0]);
       }
    }
    abstract class Entry implements TreeNode {
       String type, hash; int size;
       String name; Entry parent;
       Entry(String t, String h, int k) { 
           type = t; hash = h; size = k;
       }
       public boolean getAllowsChildren() { return !isLeaf(); }
       public TreeNode getParent() { return parent; }
       public String getName() { return name; }
       public Entry getRoot() { 
           Entry c = this; 
           while (!(c instanceof Commit)) {
               Entry p = c.parent;
               if (p == null) break;
               c = p;
           }
           return c;
       }
       public Enumeration<Entry> children() { return null; }
       public void print() { System.out.println(this); }
       public abstract void verify();
       public abstract void saveTo(File dir);
    }
    class Commit extends Entry {
       String hTree; Tree data; 
       long time; String date;
       String hPar1, hPar2, author;
       Commit(String h, int k) { super(COMMIT, h, k); }
       public TreeNode getParent() { 
           if (hPar1 == null) return null;
           if (parent == null) parent = getCommit(hPar1);
           return parent;
       }
       public Commit getParent2() { 
           if (hPar2 == null) return null;
           return getCommit(hPar2);
       }
       public Tree getTree() {
           if (data == null) {
               data = makeTree(hTree);
               data.name ="root";
               data.parent = this;
           }
           return data;
       }
       public boolean isLeaf() { return false; }
       public int getChildCount() { return 1; }
       public TreeNode getChildAt(int i) { 
           return (i == 0? getTree() : null);
       }
       public int getIndex(TreeNode n) { 
           return (n == getTree()? 0 : -1);
       }
       public void print() {
           System.out.println("commit "+trim(hash)+"     "+name);
           System.out.println(date+"  "+author);
           System.out.print("parent "+trim(hPar1)+"     ");
           String[] t = new String(X.getObjectData(hTree)).split("\n");
           System.out.println("tree "+trim(hTree)+"  "+t.length+" items");
           System.out.println(LINE+LINE);
       }
       public String toString() { return trim(hash)+" -- "+name; }
       public void verify() {
           //verify bytes by SHA
           String h = X.getFullSHA(hash);
           System.out.println(trim(hash)+" = "+trim(h));
           count = 0; pass = 0; getTree().verify();
           System.out.println(count+" blobs, "+pass+" OK");
       }
       public void saveTo(File d) { 
           count = 0; getTree().saveTo(d);
           System.out.println(count+" blobs written");
       }
    }
    class Tree extends Entry {
       List<Entry> list = new ArrayList<>();
       Tree(String h, int k) { super(TREE, h, k); }
       void add(Entry e, String n, Tree p) { 
           list.add(e); e.name = n; e.parent = p; 
       } 
       public boolean isLeaf() { return false; }
       public int getChildCount() { return list.size(); }
       public TreeNode getChildAt(int i) { return list.get(i); }
       public int getIndex(TreeNode n) { return list.indexOf(n); }
       public String toString() {
           return trim(hash)+" "+name+": "+list.size(); 
       }
       public void print() {
           super.print();  //System.out.println(this);
           for (Entry e : list) e.print();
       }
       public void verify() {
           for (Entry e : list) e.verify();
       }
       public void saveTo(File d) {
           File f = new File(d, name);
           System.out.println(trim(hash)+"  "+f);
           if (f.exists()) 
             throw new RuntimeException("cannot overwrite "+f);
           if (!f.mkdir()) 
             throw new RuntimeException("cannot mkdir "+f);
           for (Entry e : list) e.saveTo(f);
       }
    }
    class Blob extends Entry {
       byte[] data;
       Blob(String h, int k) { super(BLOB, h, k); }
       public boolean isLeaf() { return true; }
       public int getChildCount() { return 0; }
       public TreeNode getChildAt(int i) { return null; }
       public int getIndex(TreeNode n) { return -1; }
       public String toString() {
           return trim(hash)+" "+name+" ("+size+")"; 
       }
       public void verify() {
           count++; 
           if (data == null) data = X.getObjectData(hash);
           boolean OK = X.calculateSHA(BLOB, data).startsWith(hash);
           if (OK) pass++;
           System.out.println(trim(hash)+" "+OK+" "+size+" "+name);
       }
       public void saveTo(File d) {
           if (data == null) data = X.getObjectData(hash);
           byte[] b = data; count++;
           System.out.println(trim(hash)+" "+(size == b.length)+" "+name);
           X.saveToFile(b, new File(d, name));
       }
    }

    static String trim(String h) { 
        return (h!=null && h.length()>M? h.substring(0, M) : h); 
    }
    public static void main(String[] args) {
        Git G = new Git(); 
        G.getAllBranches();
        Branch b = G.currentHEAD();
        //b.getAllCommits();  //.verify();
        b.getLatestCommit().getTree().print();
    }
}
