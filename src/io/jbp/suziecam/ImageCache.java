package io.jbp.suziecam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

class ImageCacheReader extends AsyncTask<String, Drawable, Exception>
{
  private static final String TAG = "ImageCacheReader";
  Context ctx;
  String url, basename;
  ImageCache.CompleteCallback callback;
  
  ImageCacheReader(Context ctx, ImageCache.CompleteCallback cb)
  {
    this.ctx = ctx;
    callback = cb;
  }
  
  private void readNetwork() throws Exception
  {
    
    AndroidHttpClient client = AndroidHttpClient.newInstance(Config.HTTP_USER_AGENT);
    HttpResponse resp = client.execute(new HttpGet(url));
    
    InputStream is = resp.getEntity().getContent();
    File cacheFile = new File(ctx.getCacheDir(), basename);
    FileOutputStream fos = new FileOutputStream(cacheFile);
    while (true)
    {
      int bb = is.read();
      if (bb == -1)
        break;
      fos.write(bb);
    }
    fos.close();
    is.close();
    client.close();
    
    readCache();
  }
  
  private boolean haveCache() throws Exception
  {
    return ImageCache.haveCache(ctx, basename);
  }
  
  private void readCache() throws Exception
  {
    File cacheFile = new File(ctx.getCacheDir(), basename);
    Log.v(TAG, "reading image " + cacheFile.getPath());
    Drawable d = Drawable.createFromPath(cacheFile.getPath());
    publishProgress(d);
  }
  
  private void setUrl(String url)
  {
    this.url = url;
    basename = new File(url).getName();
  }
  
  @Override
  protected Exception doInBackground(String... uri)
  {
    try
    {
      setUrl(uri[0]);
      if (haveCache())
      {
        readCache();
      } else {
        readNetwork();
      }
    } catch (Exception e) {
      return e;
    }
    return null;
  }
  
  @Override
  protected void onProgressUpdate(Drawable... ds)
  {
    Drawable d = ds[0];
    callback.onComplete(d);
  }
  
  @Override
  protected void onPostExecute(Exception failure)
  {
    if (failure != null)
      callback.onFailure(failure);
  }
}

public class ImageCache
{
  public interface CompleteCallback
  {
    public void onComplete(Drawable d);
    public void onFailure(Exception e);
  }

  private static final String TAG = "ImageCache";
  
  public static void startRead(Context context, JSONObject details, CompleteCallback resultHandler)
  {
    String url = Config.BASE_URL + "/" + details.optString("filename");
    ImageCacheReader rd = new ImageCacheReader(context, resultHandler);
    rd.execute(url);
  }
  
  public static boolean haveCache(Context ctx, String basename)
  {
    File cacheFile = new File(ctx.getCacheDir(), basename);
    Log.v(TAG, "check cache location " + cacheFile.getPath() + " -> " + cacheFile.exists());
    return cacheFile.exists();
  }
}
