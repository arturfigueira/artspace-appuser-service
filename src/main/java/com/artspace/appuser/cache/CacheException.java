package com.artspace.appuser.cache;

public class CacheException extends RuntimeException{

  public CacheException(String message, Throwable cause) {
    super(message, cause);
  }

  public CacheException(Throwable cause) {
    super(cause);
  }
}
