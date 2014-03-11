package io.jbp.suziecam;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.CharBuffer;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.content.Context;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.JsonReader;

class InfoCacheReader extends AsyncTask<String, JSONObject, Exception>
{
  Context ctx;
  String section;
  InfoCache.CompleteCallback callback;
  
  InfoCacheReader(Context ctx, String section, InfoCache.CompleteCallback cb)
  {
    this.ctx = ctx;
    this.section = section;
    callback = cb;
  }
  
  private void readNetwork(String uri) throws Exception
  {
    AndroidHttpClient client = AndroidHttpClient.newInstance(Config.HTTP_USER_AGENT);
    HttpResponse resp = client.execute(new HttpGet(uri));
    String respString = EntityUtils.toString(resp.getEntity(), "UTF-8");
    client.close();
    writeCache(respString);
    JSONObject json = new JSONObject(respString);
    json.put("origin", "network");
    publishProgress(json);
  }
  
  private boolean haveCache() throws Exception
  {
    File cacheFile = new File(ctx.getCacheDir(), Config.META_CACHE_FILE);
    return cacheFile.exists() &&
        cacheFile.lastModified() > (System.currentTimeMillis() - Config.META_CACHE_SECONDS * 1000);
  }
  
  private void writeCache(String newData) throws Exception
  {
    File cacheFile = new File(ctx.getCacheDir(), Config.META_CACHE_FILE);
    FileWriter fw = new FileWriter(cacheFile);
    fw.write(newData.toCharArray());
    fw.close();
  }
  
  private void readCache() throws Exception
  {
    File cacheFile = new File(ctx.getCacheDir(), Config.META_CACHE_FILE);
    byte[] data = new byte[(int) cacheFile.length()];
    FileInputStream fis = new FileInputStream(cacheFile);
    fis.read(data);
    fis.close();
    JSONObject json = new JSONObject(new String(data, "UTF-8"));
    json.put("origin", "cache");
    publishProgress(json);
  }
  
  @Override
  protected Exception doInBackground(String... uri)
  {
    try
    {
      if (haveCache())
      {
        readCache();
      } else {
        readNetwork(uri[0]);
      }
    } catch (Exception e) {
      return e;
    }
    return null;
  }
  
  @Override
  protected void onProgressUpdate(JSONObject... objs)
  {
    JSONObject result = objs[0];
    if (result.has(section))
      callback.onComplete(result.optJSONObject(section));
    else
      callback.onComplete(new JSONObject());
  }
  
  @Override
  protected void onPostExecute(Exception failure)
  {
    if (failure != null)
      callback.onFailure(failure);
  }
}

public class InfoCache
{
  public interface CompleteCallback
  {
    public void onComplete(JSONObject info);
    public void onFailure(Exception e);
  }
  
  private static String tagName(int section)
  {
    switch (section)
    {
    case R.string.title_minus0:
      return "latest";
    case R.string.title_minus1:
      return "minus_1";
    case R.string.title_minus5:
      return "minus_5";
    case R.string.title_minus15:
      return "minus_15";
    case R.string.title_minus30:
      return "minus_30";
    case R.string.title_minus60:
      return "minus_60";
    case R.string.title_minus120:
      return "minus_120";
    }
    
    return "invalid";
  }
  
  public static void startRead(Context context, int whichSection, CompleteCallback resultHandler)
  {
    InfoCacheReader rd = new InfoCacheReader(context, tagName(whichSection), resultHandler);
    rd.execute(Config.META_URL);
  }
}
