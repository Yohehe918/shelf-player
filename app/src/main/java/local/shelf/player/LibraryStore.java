package local.shelf.player;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

/** Source-scoped local cache: a failed scan never deletes the last successful library. */
final class LibraryStore extends SQLiteOpenHelper {
    // Queued work may finish after the Activity closes; never reopen the database then.
    private boolean closed;
    @Override public synchronized void close(){closed=true;super.close();}
    LibraryStore(Context c){this(c,"library.db");}
    LibraryStore(Context c,String name){super(c.getApplicationContext(),name,null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE library(source TEXT NOT NULL,path TEXT NOT NULL,data TEXT NOT NULL,PRIMARY KEY(source,path))");
        db.execSQL("CREATE TABLE metadata(source TEXT NOT NULL,path TEXT NOT NULL,data TEXT NOT NULL,manual INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(source,path))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("Unsupported library upgrade");}
    synchronized void saveLibrary(String source,List<Catalog.Movie> movies) throws JSONException {
        if(closed)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            db.delete("library","source=?",new String[]{source});
            for(Catalog.Movie m:movies){JSONObject o=new JSONObject().put("name",m.title().name()).put("year",m.title().year()).put("season",m.title().season()).put("episode",m.title().episode()).put("poster",m.poster()).put("backdrop",m.backdrop()).put("subtitles",new JSONArray(m.subtitles())).put("discFolder",m.discFolder());
                ContentValues v=new ContentValues();v.put("source",source);v.put("path",m.path());v.put("data",o.toString());db.insertOrThrow("library",null,v);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized List<Catalog.Movie> library(String source){if(closed)return List.of();List<Catalog.Movie> out=new ArrayList<>();try(Cursor c=getReadableDatabase().query("library",new String[]{"path","data"},"source=?",new String[]{source},null,null,"path")){
        while(c.moveToNext())try{JSONObject o=new JSONObject(c.getString(1));
            String path=c.getString(0);
            String discFolder=o.isNull("discFolder")?null:o.getString("discFolder");
            // Pre-disc caches contain individual clips. The next scan replaces them atomically.
            if(discFolder==null&&Catalog.bluRayFolder(path)!=null)continue;
            List<String> subs=new ArrayList<>();JSONArray a=o.optJSONArray("subtitles");if(a!=null)for(int i=0;i<a.length();i++)subs.add(a.getString(i));out.add(new Catalog.Movie(path,new Catalog.Title(o.getString("name"),o.optString("year"),o.optInt("season"),o.optInt("episode")),o.isNull("poster")?null:o.getString("poster"),o.isNull("backdrop")?null:o.getString("backdrop"),subs,discFolder));}catch(JSONException ignored){}
    }return out;}
    synchronized Map<String,OnlineServices.Metadata> metadata(String source){if(closed)return Map.of();Map<String,OnlineServices.Metadata> out=new HashMap<>();try(Cursor c=getReadableDatabase().query("metadata",new String[]{"path","data"},"source=?",new String[]{source},null,null,null)){
        while(c.moveToNext())try{out.put(c.getString(0),OnlineServices.Metadata.fromJson(new JSONObject(c.getString(1))));}catch(JSONException ignored){}
    }return out;}
    synchronized void put(String source,String path,OnlineServices.Metadata metadata,boolean manual) throws JSONException {
        if(closed)return;SQLiteDatabase db=getWritableDatabase();ContentValues values=new ContentValues();values.put("source",source);values.put("path",path);values.put("data",metadata.json().toString());values.put("manual",manual?1:0);
        if(manual)db.insertWithOnConflict("metadata",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        else db.insertWithOnConflict("metadata",null,values,SQLiteDatabase.CONFLICT_IGNORE); // Never overwrite a user's correction.
    }
    synchronized void forget(String source,List<String> paths){if(closed)return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String path:paths)db.delete("metadata","source=? AND path=?",new String[]{source,path});db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
