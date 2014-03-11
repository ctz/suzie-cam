package io.jbp.suziecam;

public class Config
{
  public static final String BASE_URL = "http://delta.jbp.io/~jbp/camera/";
  public static final String META_URL = BASE_URL + "info.json";
  public static final String HTTP_USER_AGENT = "SuzieCam v1";
  public static final String META_CACHE_FILE = "info.json.cache";
  public static final int META_CACHE_SECONDS = 30;
  public static final String ISO8601DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
}
