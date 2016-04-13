package com.rnfs;

import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.SparseArray;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class RNFSManager extends ReactContextBaseJavaModule {

  private static final String NSDocumentDirectoryPath = "NSDocumentDirectoryPath";
  private static final String NSPicturesDirectoryPath = "NSPicturesDirectoryPath";
  private static final String NSCachesDirectoryPath = "NSCachesDirectoryPath";
  private static final String NSDocumentDirectory = "NSDocumentDirectory";

  private static final String NSFileTypeRegular = "NSFileTypeRegular";
  private static final String NSFileTypeDirectory = "NSFileTypeDirectory";
  
  private SparseArray<Downloader> downloaders = new SparseArray<Downloader>();

  public RNFSManager(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public String getName() {
    return "RNFSManager";
  }

  @ReactMethod
  public void writeFile(String filepath, String base64Content, ReadableMap options, Callback callback) {
    try {
      byte[] bytes = Base64.decode(base64Content, Base64.DEFAULT);

      FileOutputStream outputStream = new FileOutputStream(filepath);
      outputStream.write(bytes);
      outputStream.close();

      callback.invoke(null, true, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }
  
  @ReactMethod
  public void exists(String filepath, Callback callback) {
    try {
      File file = new File(filepath);
      callback.invoke(null, file.exists());
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void readFile(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      FileInputStream inputStream = new FileInputStream(filepath);
      byte[] buffer = new byte[(int)file.length()];
      inputStream.read(buffer);

      String base64Content = Base64.encodeToString(buffer, Base64.NO_WRAP);

      callback.invoke(null, base64Content);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void readResource(String filepath, Callback callback) {
    try {
      InputStream inputStream = getReactApplicationContext().getClass().getResourceAsStream(filepath);
      StringBuilder responseStrBuilder = new StringBuilder();

      if (inputStream != null) {
        String inputStr;
        BufferedReader streamReader = new BufferedReader(
                new InputStreamReader(inputStream, "UTF-8"));
        while ((inputStr = streamReader.readLine()) != null)
          responseStrBuilder.append(inputStr);
        inputStream.close();

      }

      String content = responseStrBuilder.toString();

      callback.invoke(null, content);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void moveFile(String filepath, String destPath, Callback callback) {
    try {
      File from = new File(filepath);
      File to = new File(destPath);
      from.renameTo(to);

      callback.invoke(null, true, destPath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void readDir(String directory, Callback callback) {
    try {
      File file = new File(directory);

      if (!file.exists()) throw new Exception("Folder does not exist");

      File[] files = file.listFiles();

      WritableArray fileMaps = Arguments.createArray();

      for (File childFile : files) {
        WritableMap fileMap = Arguments.createMap();

        fileMap.putString("name", childFile.getName());
        fileMap.putString("path", childFile.getAbsolutePath());
        fileMap.putInt("size", (int)childFile.length());
        fileMap.putInt("type", childFile.isDirectory() ? 1 : 0);

        fileMaps.pushMap(fileMap);
      }

      callback.invoke(null, fileMaps);

    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void stat(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      WritableMap statMap = Arguments.createMap();

      statMap.putInt("ctime", (int)(file.lastModified() / 1000));
      statMap.putInt("mtime", (int)(file.lastModified() / 1000));
      statMap.putInt("size", (int)file.length());
      statMap.putInt("type", file.isDirectory() ? 1 : 0);

      callback.invoke(null, statMap);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  @ReactMethod
  public void unlink(String filepath, Callback callback) {
    try {
      File file = new File(filepath);

      if (!file.exists()) throw new Exception("File does not exist");

      boolean success = DeleteRecursive(file);

      callback.invoke(null, success, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private boolean DeleteRecursive(File fileOrDirectory) {
    if (fileOrDirectory.isDirectory()) {
      for (File child : fileOrDirectory.listFiles()) {
        DeleteRecursive(child);
      }
    }

    return fileOrDirectory.delete();
  }

  @ReactMethod
  public void mkdir(String filepath, Boolean excludeFromBackup, Callback callback) {
    try {
      File file = new File(filepath);

      file.mkdirs();

      boolean success = file.exists();

      callback.invoke(null, success, filepath);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }

  private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    reactContext
    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
    .emit(eventName, params);
  }

  @ReactMethod
  public void downloadFile(String urlStr, final String filepath, final int jobId, final Callback callback) {
    try {
      File file = new File(filepath);
      URL url = new URL(urlStr);

      DownloadParams params = new DownloadParams();
      
      params.src = url;
      params.dest = file;
      
      params.onTaskCompleted = new DownloadParams.OnTaskCompleted() {
        public void onTaskCompleted(DownloadResult res) {
          if (res.exception == null) {
            WritableMap infoMap = Arguments.createMap();
            
            infoMap.putInt("jobId", jobId);
            infoMap.putInt("statusCode", res.statusCode);
            infoMap.putInt("bytesWritten", res.bytesWritten);
            
            callback.invoke(null, infoMap);
          } else {
            callback.invoke(makeErrorPayload(res.exception));
          }
        }
      };
      
      params.onDownloadBegin = new DownloadParams.OnDownloadBegin() {
        public void onDownloadBegin(int statusCode, int contentLength, Map<String, String> headers) {
          WritableMap headersMap = Arguments.createMap();
          
          for (Map.Entry<String, String> entry : headers.entrySet()) {
            headersMap.putString(entry.getKey(), entry.getValue());
          }
          
          WritableMap data = Arguments.createMap();
          
          data.putInt("jobId", jobId);
          data.putInt("statusCode", statusCode);
          data.putInt("contentLength", contentLength);
          data.putMap("headers", headersMap);
          
          sendEvent(getReactApplicationContext(), "DownloadBegin-" + jobId, data);
        }
      };
      
      params.onDownloadProgress = new DownloadParams.OnDownloadProgress() {
        public void onDownloadProgress(int contentLength, int bytesWritten) {
          WritableMap data = Arguments.createMap();
          data.putInt("contentLength", contentLength);
          data.putInt("bytesWritten", bytesWritten);

          sendEvent(getReactApplicationContext(), "DownloadProgress-" + jobId, data);
        }
      };

      Downloader downloader = new Downloader();
      
      downloader.execute(params);
      
      this.downloaders.put(jobId, downloader);
    } catch (Exception ex) {
      ex.printStackTrace();
      callback.invoke(makeErrorPayload(ex));
    }
  }
  
  @ReactMethod
  public void stopDownload(int jobId) {
    Downloader downloader = this.downloaders.get(jobId);
    
    if (downloader != null) {
      downloader.stop(); 
    }
  }

  @ReactMethod
  public void pathForBundle(String bundleNamed, Callback callback) {
    // TODO: Not sure what equilivent would be?
  }

  private WritableMap makeErrorPayload(Exception ex) {
    WritableMap error = Arguments.createMap();
    error.putString("message", ex.getMessage());
    return error;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put(NSDocumentDirectory, 0);
    constants.put(NSDocumentDirectoryPath, this.getReactApplicationContext().getFilesDir().getAbsolutePath());
    constants.put(NSPicturesDirectoryPath, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
    constants.put(NSCachesDirectoryPath, this.getReactApplicationContext().getCacheDir().getAbsolutePath());
    constants.put(NSFileTypeRegular, 0);
    constants.put(NSFileTypeDirectory, 1);
    return constants;
  }
}
