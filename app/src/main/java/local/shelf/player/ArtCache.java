package local.shelf.player;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Bounded, atomic artwork cache; contains no passwords or tokens. */
final class ArtCache {
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static File file(File root,String key) throws Exception {File dir=new File(root,"artwork");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Artwork cache unavailable");return new File(dir,hex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
    static synchronized byte[] read(File root,String key){try{File f=file(root,key);if(!f.isFile()||f.length()>6*1024*1024)return null;f.setLastModified(System.currentTimeMillis());return Files.readAllBytes(f.toPath());}catch(Exception e){return null;}}
    static synchronized void write(File root,String key,byte[] bytes){if(bytes.length>6*1024*1024)return;try{File target=file(root,key);File tmp=File.createTempFile("art",".tmp",target.getParentFile());try{Files.write(tmp.toPath(),bytes);Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{tmp.delete();}
        File[] files=target.getParentFile().listFiles();if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=Arrays.stream(files).mapToLong(File::length).sum();for(File f:files){long size=f.length();if(total>64*1024*1024L&&f.delete())total-=size;}
    }catch(Exception ignored){}}
}
