import java.io.*;
import java.util.*;
import java.security.MessageDigest;


/**
 * The Exec class has methods for calling Git <p>
 * Uses java.lang.ProcessBuilder to make a Process <br>
 * and java.security.MessageDigest to calculate SHA
 *
 * @author  Akif Eyler
 * @see     java.lang.ProcessBuilder
 * @see     java.security.MessageDigest
 */
 public class Exec {

    final File root; //git repository
    final ProcessBuilder PB = new ProcessBuilder();
    final byte[] buf = new byte[MB];
    
    /** limit for printing is 5 KBytes -- larger Entries are not printed */
    final static public int LARGE = 5*1024;
    /** maximum buffer size for accessing Git data (5 MBytes) */
    final static public int MB = LARGE*1024;
    final static MessageDigest MD;  //SHA encoder
    static {
        try {
            MD = MessageDigest.getInstance("SHA-1");          
        } catch (java.security.NoSuchAlgorithmException x) {
            throw new RuntimeException(x);
        }
    } 

    /** Makes an instance in the current folder */
    public Exec() { this(new File(".")); }
    /** Makes an instance residing in File f */
    public Exec(File f) {
       try {
          root = f.isDirectory()? f.getCanonicalFile(): f.getParentFile();
          PB.directory(root);
	   } catch (IOException x) {
	      throw new RuntimeException(x);
	   }
    }
    /** returns the bytes of Object h -- 4 digits may suffice */
    public byte[] getObjectData(String h) { //
        String[] CATF = {"git", "cat-file", "-p", h};
        int n = exec(CATF);
        if (n <= 1) return new byte[0];
        byte[] ba = new byte[n];
        System.arraycopy(buf, 0 , ba, 0, n);
        return ba;
    }
    /** returns the size of Object h -- 4 digits may suffice */
    public int getObjectSize(String h) {
        String[] CATF = {"git", "cat-file", "-s", h};
        int n = exec(CATF);
        String s = new String(buf, 0, n-1); //skip LF
        return Integer.parseInt(s);
    }
    /** returns the type of Object h -- 4 digits may suffice */
    public String getObjectType(String h) {
        String[] CATF = {"git", "cat-file", "-t", h};
        int n = exec(CATF);
        return new String(buf, 0, n-1); //skip LF
    }
    /** prints the bytes of Object h -- 4 digits may suffice */
    public void printObjectData(String h) {
        int n = getObjectSize(h);
        if (n > LARGE) 
            System.out.println("Data is large: "+n);
        else {
            for (byte b : getObjectData(h)) 
                System.out.print((char)b);
            System.out.println();
        }
    }
    /** returns the full SHA of Object h -- even if h is full-length */
    public String getFullSHA(String h) {
        //if (h.length() == 40) return h;
        String t = getObjectType(h);
        byte[] b = getObjectData(h);
        return calculateSHA(t, b);
    }
    /** 
     * Executes the command indicated by the sequence of Strings <p>
     * Examples: execute("ls");   execute("git", "status"); <p>
     * <p>
     * invokes private method exec() and splits the result into lines
     * */
    public String[] execute(String... a) { 
        int n = exec(a);
        if (n <= 1) return new String[0]; 
        return new String(buf, 0, n).split("\n");         
    }
    void waitFor(Process p, int n, int d) { //nd msec
        InputStream in  = p.getInputStream();
        InputStream err = p.getErrorStream();
        try { 
            while (in.available()==0 && err.available()==0 && n>0) {
                Thread.sleep(d); n--;  //wait for p, at most d msec
            }
            //System.out.println("in.available()="+in.available()+" "+n);
        } catch (IOException x) {
            throw new RuntimeException(x);
        } catch (InterruptedException x) {
            //should not happen
        }
    }
    int exec(String... a) { //fill buf & return number of bytes read
        Process p = null;
        try { 
            //p = Runtime.getRuntime().exec(a);
            PB.command(a); p = PB.start();
            waitFor(p, 100, 10);  //wait at most 1000 msec
            
            //This requires Java 8 and is too coarse -- polls each second
            //p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    
            InputStream err = p.getErrorStream();
            if (err.available() > 0) {
                int n = err.read(buf, 0, buf.length);
                String msg = new String(buf, 0, n);
                throw new RuntimeException(msg);
            }
            InputStream in = p.getInputStream();
            int n = in.read(buf, 0, buf.length);
            waitFor(p, 5, 10);
            while (in.available() > 0) {
                n += in.read(buf, n, buf.length-n);
                waitFor(p, 5, 10);
            }
            return n;
        } catch (IOException x) {
            throw new RuntimeException(x);
        } finally {
            p.destroy(); 
        }
    }
    int exec(byte[] ba, String... a) {
        int k = exec(a);
        int n = Math.min(k, ba.length);
        System.arraycopy(buf, 0 , ba, 0, n);
        return n;
    }
    /** 
     * Returns the SHA of a file by two methods <p>
     * 1. invoke static method toSHA(File) <br>
     * 2. call shasum in an external Process
     */
    public void toSHA2(File f) throws IOException {
        System.out.println(f.getName());
        String h = toSHA(f); //IOException
        System.out.println(h);
        String[] SHA = {"shasum", f.toString()};
        byte[] ba = new byte[40];
        exec(ba, SHA); //skip LF
        String s = new String(ba);
        System.out.println(s+"  "+s.equals(h));
    }
    /** Verifies and saves the Blob h */
    public void saveBlob(String h, String name) {
        byte[] b = getObjectData(h);
        String s = calculateSHA("blob", b);
        System.out.println(h+"  blob "+b.length);
        System.out.println(s+"  "+s.startsWith(h));
        saveToFile(b, new File(name));
    }
    /** Returns the name of the root directory */
    public String toString() { return root.getName(); }
    
    static String toHex(byte b) {
        if (b > 15) return Integer.toHexString(b);
        if (b < 0) return Integer.toHexString(b+256);
        return "0"+Integer.toHexString(b); //single digit
    }
    static String toHex(byte[] buf) {
        String hash = "";
        for (int i=0; i<buf.length; i++) hash += toHex(buf[i]);
        return hash;
    }
    /** Returns the SHA for the byte array given */
    public static String toSHA(byte[] ba) {
        return toHex(MD.digest(ba));  //java.security.MessageDigest
    }
    /** Returns the SHA for File f */
    public static String toSHA(File f) throws IOException {
        return toSHA(toArray(new FileInputStream(f))); 
    }
    /** Returns the SHA for a Git object -- basis for verification */
    public static String calculateSHA(String type, byte[] b) {
           byte[] a = (type+" "+b.length).getBytes();
           byte[] ab = new byte[a.length+1+b.length];
           System.arraycopy(a, 0, ab, 0, a.length);  //char 0
           System.arraycopy(b, 0, ab, a.length+1, b.length);
           return toSHA(ab);
    }
    /** Saves the bytes into File f -- overwrites f without warning */
    public static void saveToFile(byte[] b, File f) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(b); out.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
    /** Reads the bytes available into a byte array */
    public static byte[] toArray(InputStream in) throws IOException {
        int n = in.available();
        if (n == 0) return new byte[0];
        byte[] buf = new byte[n];
        n = in.read(buf);
        if (n == buf.length) return buf;
        else return Arrays.copyOf(buf, n);
    }
    /** Makes an instance in the current folder */
    public static void main(String[] args) throws IOException {
        Exec G = new Exec();
        G.execute("dir"); //sample shell command
        G.execute("git", "branch", "-v"); //local branches
        G.toSHA2(new File("sss.jar"));
        //saveBlob("64fc", "test.jar"); //sss.jar V2.10
    }
}
