package local.shelf.player;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Bounded, atomic artwork cache; contains no passwords or tokens. */
final class ArtCache {
    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = Character.forDigit((bytes[i] & 0xff) >>> 4, 16);
            result[i * 2 + 1] = Character.forDigit(bytes[i] & 0x0f, 16);
        }
        return new String(result);
    }
    private static File file(File root,String key) throws Exception {File dir=new File(root,"artwork");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Artwork cache unavailable");return new File(dir,hex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
    static synchronized byte[] read(File root,String key){try{File f=file(root,key);if(!f.isFile()||f.length()>6*1024*1024)return null;f.setLastModified(System.currentTimeMillis());return Files.readAllBytes(f.toPath());}catch(Exception e){return null;}}
    static synchronized void write(File root,String key,byte[] bytes){if(bytes.length>6*1024*1024)return;try{File target=file(root,key);File tmp=File.createTempFile("art",".tmp",target.getParentFile());try{Files.write(tmp.toPath(),bytes);Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}finally{tmp.delete();}
        File[] files=target.getParentFile().listFiles();if(files==null)return;
        long total=Arrays.stream(files).mapToLong(File::length).sum();
        long budget=64*1024*1024L;
        if(total<=budget)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(File f:files){
            if(total<=budget)break;
            long size=f.length();
            if(f.delete())total-=size;
        }
    }catch(Exception ignored){}}
}
