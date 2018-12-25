package fr.gouv.etalab.mastodon.client;
/* Copyright 2017 Thomas Schneider
 *
 * This file is a part of Mastalab
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastalab is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastalab; if not,
 * see <http://www.gnu.org/licenses>. */
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.text.Html;
import android.text.SpannableString;

import com.google.common.io.ByteStreams;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import fr.gouv.etalab.mastodon.R;
import fr.gouv.etalab.mastodon.activities.MediaActivity;
import fr.gouv.etalab.mastodon.activities.TootActivity;
import fr.gouv.etalab.mastodon.client.Entities.Account;
import fr.gouv.etalab.mastodon.client.Entities.Attachment;
import fr.gouv.etalab.mastodon.client.Entities.Error;
import fr.gouv.etalab.mastodon.helper.Helper;
import fr.gouv.etalab.mastodon.interfaces.OnDownloadInterface;
import fr.gouv.etalab.mastodon.interfaces.OnRetrieveAttachmentInterface;
import fr.gouv.etalab.mastodon.sqlite.AccountDAO;
import fr.gouv.etalab.mastodon.sqlite.Sqlite;


/**
 * Created by Thomas on 17/11/2017.
 * Manage http queries
 */

public class HttpsConnection {



    private HttpsURLConnection httpsURLConnection;
    private HttpURLConnection httpURLConnection;
    private String since_id, max_id;
    private Context context;
    private int CHUNK_SIZE = 4096;
    private SharedPreferences sharedpreferences;
    private Proxy proxy;


    public HttpsConnection(Context context){
        this.context = context;
        sharedpreferences = context.getSharedPreferences(Helper.APP_PREFS, Context.MODE_PRIVATE);
        boolean proxyEnabled = sharedpreferences.getBoolean(Helper.SET_PROXY_ENABLED, false);
        int type = sharedpreferences.getInt(Helper.SET_PROXY_TYPE, 0);
        proxy = null;
        if( proxyEnabled ){
            try {
                String host = sharedpreferences.getString(Helper.SET_PROXY_HOST, "127.0.0.1");
                int port = sharedpreferences.getInt(Helper.SET_PROXY_PORT, 8118);
                if( type == 0 )
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                else
                    proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
                final String login = sharedpreferences.getString(Helper.SET_PROXY_LOGIN, null);
                final String pwd = sharedpreferences.getString(Helper.SET_PROXY_PASSWORD, null);
                if( login != null) {
                    Authenticator authenticator = new Authenticator() {
                        public PasswordAuthentication getPasswordAuthentication() {
                            assert pwd != null;
                            return (new PasswordAuthentication(login,
                                    pwd.toCharArray()));
                        }
                    };
                    Authenticator.setDefault(authenticator);
                }
            }catch (Exception e){
                proxy = null;
            }

        }
    }


    @SuppressWarnings("ConstantConditions")
    public String get(String urlConnection, int timeout, HashMap<String, String> paramaters, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {


        if( urlConnection.startsWith("https://")) {
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            URL url = new URL(urlConnection + "?" + postData);
            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setRequestProperty("http.keepAlive", "false");
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setRequestMethod("GET");
            String response;
            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                response = converToString(httpsURLConnection.getInputStream());
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpsURLConnection.getInputStream().close();
            return response;
        }else {
            Map<String,Object> params = new LinkedHashMap<>();
            if( paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            URL url = new URL(urlConnection + "?" + postData);
            if( proxy !=null )
                httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
            else
                httpURLConnection = (HttpURLConnection)url.openConnection();
            httpURLConnection.setConnectTimeout(timeout * 1000);
            httpURLConnection.setRequestProperty("http.keepAlive", "false");
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            if( token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setRequestMethod("GET");
            String response;
            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                response = converToString(httpURLConnection.getInputStream());
            }else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpURLConnection.getInputStream().close();
            return response;
        }
    }



    public String get(String urlConnection) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        if( urlConnection.startsWith("https://")) {
            URL url = new URL(urlConnection);
            if( proxy !=null )
                httpsURLConnection = (HttpsURLConnection)url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection)url.openConnection();
            httpsURLConnection.setConnectTimeout(30 * 1000);
            httpsURLConnection.setRequestProperty("http.keepAlive", "false");
            httpsURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36");
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            httpsURLConnection.setRequestMethod("GET");
            String response;
            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpsURLConnection.getInputStream());
            }else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpsURLConnection.getInputStream().close();
            return response;
        }else{
            URL url = new URL(urlConnection);
            if( proxy !=null )
                httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
            else
                httpURLConnection = (HttpURLConnection)url.openConnection();
            httpURLConnection.setConnectTimeout(30 * 1000);
            httpURLConnection.setRequestProperty("http.keepAlive", "false");
            httpURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36");
            httpURLConnection.setRequestMethod("GET");
            String response;
            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpURLConnection.getInputStream());
            }else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpURLConnection.getInputStream().close();
            return response;
        }
    }



    public String post(String urlConnection, int timeout, JSONObject paramaters, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        if( urlConnection.startsWith("https://")) {
            URL url = new URL(urlConnection);
            String postData = paramaters.toString();
            byte[] postDataBytes = postData.getBytes("UTF-8");

            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setDoOutput(true);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            httpsURLConnection.setRequestMethod("POST");
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));


            httpsURLConnection.getOutputStream().write(postDataBytes);
            String response;
            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpsURLConnection.getInputStream());
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpsURLConnection.getInputStream().close();
            return response;
        }else {
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.keys();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            if (proxy != null)
                httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
            else
                httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpURLConnection.setConnectTimeout(timeout * 1000);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestMethod("POST");
            if (token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            httpURLConnection.getOutputStream().write(postDataBytes);
            String response;
            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpURLConnection.getInputStream());
            } else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpURLConnection.getInputStream().close();
            return response;
        }

    }

    /***
     * Download method which works for http and https connections
     * @param downloadUrl String download url
     * @param listener OnDownloadInterface, listener which manages progress
     */
    public void download(final String downloadUrl, final OnDownloadInterface listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                URL url;
                HttpsURLConnection httpsURLConnection = null;
                HttpURLConnection httpURLConnection = null;
                if (downloadUrl.startsWith("https://")) {
                    try {
                        url = new URL(downloadUrl);
                        if( proxy !=null )
                            httpsURLConnection = (HttpsURLConnection)url.openConnection(proxy);
                        else
                            httpsURLConnection = (HttpsURLConnection)url.openConnection();
                        httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                        int responseCode = httpsURLConnection.getResponseCode();

                        // always check HTTP response code first
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            String fileName = "";
                            String disposition = httpsURLConnection.getHeaderField("Content-Disposition");

                            if (disposition != null) {
                                // extracts file name from header field
                                int index = disposition.indexOf("filename=");
                                if (index > 0) {
                                    fileName = disposition.substring(index + 10,
                                            disposition.length() - 1);
                                }
                            } else {
                                // extracts file name from URL
                                fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1,
                                        downloadUrl.length());
                            }
                            // opens input stream from the HTTP connection
                            InputStream inputStream = httpsURLConnection.getInputStream();
                            File saveDir = context.getCacheDir();
                            final String saveFilePath = saveDir + File.separator + fileName;

                            // opens an output stream to save into file
                            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                            int bytesRead;
                            byte[] buffer = new byte[CHUNK_SIZE];
                            int contentSize = httpsURLConnection.getContentLength();
                            int downloadedFileSize = 0;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                downloadedFileSize += bytesRead;
                                if (context instanceof MediaActivity) {
                                    final int currentProgress = (downloadedFileSize * 100) / contentSize;
                                    ((MediaActivity) context).runOnUiThread(new Runnable() {
                                        public void run() {
                                            listener.onUpdateProgress(currentProgress>0?currentProgress:101);
                                        }
                                    });
                                }
                            }
                            outputStream.close();
                            inputStream.close();
                            if (context instanceof TootActivity)
                                ((TootActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(saveFilePath, downloadUrl, null);
                                    }
                                });
                            if (context instanceof MediaActivity)
                                ((MediaActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(saveFilePath, downloadUrl, null);
                                    }
                                });
                        } else {
                            final Error error = new Error();
                            error.setError(String.valueOf(responseCode));
                            if (context instanceof TootActivity)
                                ((TootActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(null, downloadUrl, error);
                                    }
                                });
                            if (context instanceof MediaActivity)
                                ((MediaActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(null, downloadUrl, error);
                                    }
                                });

                        }
                    } catch (IOException e) {
                        Error error = new Error();
                        error.setError(context.getString(R.string.toast_error));
                    }

                } else {
                    try {
                        url = new URL(downloadUrl);
                        if( proxy !=null )
                            httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
                        else
                            httpURLConnection = (HttpURLConnection)url.openConnection();
                        httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                        int responseCode = httpURLConnection.getResponseCode();

                        // always check HTTP response code first
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            String fileName = "";
                            String disposition = httpURLConnection.getHeaderField("Content-Disposition");

                            if (disposition != null) {
                                // extracts file name from header field
                                int index = disposition.indexOf("filename=");
                                if (index > 0) {
                                    fileName = disposition.substring(index + 10,
                                            disposition.length() - 1);
                                }
                            } else {
                                // extracts file name from URL
                                fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1,
                                        downloadUrl.length());
                            }
                            // opens input stream from the HTTP connection
                            InputStream inputStream = httpURLConnection.getInputStream();
                            File saveDir = context.getCacheDir();
                            final String saveFilePath = saveDir + File.separator + fileName;

                            // opens an output stream to save into file
                            FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                            int bytesRead;
                            byte[] buffer = new byte[CHUNK_SIZE];
                            int contentSize = httpURLConnection.getContentLength();
                            int downloadedFileSize = 0;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                                downloadedFileSize += bytesRead;
                                if (context instanceof MediaActivity) {
                                    final int currentProgress = (downloadedFileSize * 100) / contentSize;
                                    ((MediaActivity) context).runOnUiThread(new Runnable() {
                                        public void run() {
                                            listener.onUpdateProgress(currentProgress>0?currentProgress:101);
                                        }
                                    });
                                }
                            }
                            outputStream.close();
                            inputStream.close();
                            if (context instanceof TootActivity)
                                ((TootActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(saveFilePath, downloadUrl, null);
                                    }
                                });
                            if (context instanceof MediaActivity)
                                ((MediaActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(saveFilePath, downloadUrl, null);
                                    }
                                });
                        } else {
                            final Error error = new Error();
                            error.setError(String.valueOf(responseCode));
                            if (context instanceof TootActivity)
                                ((TootActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(null, downloadUrl, error);
                                    }
                                });
                            if (context instanceof MediaActivity)
                                ((MediaActivity) context).runOnUiThread(new Runnable() {
                                    public void run() {
                                        listener.onDownloaded(null, downloadUrl, error);
                                    }
                                });

                        }
                    } catch (IOException e) {
                        Error error = new Error();
                        error.setError(context.getString(R.string.toast_error));
                    }

                }
            }
        }).start();
    }



    public InputStream getPicture(final String downloadUrl) {
        if( downloadUrl.startsWith("https://")) {
            try {
                URL url = new URL(downloadUrl);
                if (proxy != null)
                    httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
                else
                    httpsURLConnection = (HttpsURLConnection) url.openConnection();
                httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
                httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                int responseCode = httpsURLConnection.getResponseCode();
                // always check HTTP response code first
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // opens input stream from the HTTP connection
                    return httpsURLConnection.getInputStream();
                }
                httpsURLConnection.getInputStream().close();
            } catch (IOException | NoSuchAlgorithmException | KeyManagementException ignored) {
            }
            if (httpsURLConnection != null)
                try {
                    httpsURLConnection.getInputStream().close();
                } catch (Exception ignored) { }
            return null;
        }else {
            try {
                URL url = new URL(downloadUrl);
                if (proxy != null)
                    httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
                else
                    httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                int responseCode = httpURLConnection.getResponseCode();
                // always check HTTP response code first
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // opens input stream from the HTTP connection
                    return httpURLConnection.getInputStream();
                }
                httpURLConnection.getInputStream().close();
            } catch (IOException ignored) {
            }
            if (httpURLConnection != null)
                try {
                    httpURLConnection.getInputStream().close();
                } catch (Exception ignored) { }
            return null;
        }
    }

    enum imageType{
        AVATAR,
        BANNER
    }

    @SuppressWarnings("SameParameterValue")
    private void patchImage(String urlConnection, int timeout, imageType it, InputStream image, String fileName, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        String twoHyphens = "--";
        String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
        String lineEnd = "\r\n";
        if( urlConnection.startsWith("https://")) {
            HttpsURLConnection httpsURLConnection;
            URL url = new URL(urlConnection);
            int lengthSentImage = 0;
            byte[] pixelsImage = new byte[0];
            if( image != null) {
                ByteArrayOutputStream ous = null;
                try {
                    try {
                        byte[] buffer = new byte[CHUNK_SIZE];
                        ous = new ByteArrayOutputStream();
                        int read;
                        while ((read = image.read(buffer)) != -1) {
                            ous.write(buffer, 0, read);
                        }
                        ous.flush();
                    } finally {
                        if (ous != null)
                            ous.close();
                    }
                } catch (FileNotFoundException ignored) {
                } catch (IOException ignored) {
                }
                pixelsImage = ous.toByteArray();

                lengthSentImage = pixelsImage.length;
                lengthSentImage += 2 * (twoHyphens + boundary + twoHyphens + lineEnd).getBytes().length;
                if( it == imageType.AVATAR)
                    lengthSentImage += ("Content-Disposition: form-data; name=\"avatar\";filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                else
                    lengthSentImage += ("Content-Disposition: form-data; name=\"header\";filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                lengthSentImage += 2 * (lineEnd).getBytes().length;
            }



            int lengthSent = lengthSentImage + (twoHyphens + boundary + twoHyphens + lineEnd).getBytes().length;
            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            httpsURLConnection.setDoInput(true);
            httpsURLConnection.setDoOutput(true);
            httpsURLConnection.setUseCaches(false);
            if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT ){
                httpsURLConnection.setRequestMethod("PATCH");
            }else {
                httpsURLConnection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                httpsURLConnection.setRequestMethod("POST");
            }
            httpsURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpsURLConnection.setRequestProperty("Cache-Control", "no-cache");
            httpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpsURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+ boundary);
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setFixedLengthStreamingMode(lengthSent);

            OutputStream outputStream = httpsURLConnection.getOutputStream();
            outputStream.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes("UTF-8"));
            if(lengthSentImage > 0){
                DataOutputStream request = new DataOutputStream(outputStream);
                int totalSize = pixelsImage.length;
                int bytesTransferred = 0;
                request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                if( it == imageType.AVATAR)
                    request.writeBytes("Content-Disposition: form-data; name=\"avatar\";filename=\""+fileName+"\"" + lineEnd);
                else
                    request.writeBytes("Content-Disposition: form-data; name=\"header\";filename=\""+fileName+"\"" + lineEnd);

                request.writeBytes(lineEnd);
                while (bytesTransferred < totalSize) {
                    int nextChunkSize = totalSize - bytesTransferred;
                    if (nextChunkSize > CHUNK_SIZE) {
                        nextChunkSize = CHUNK_SIZE;
                    }
                    request.write(pixelsImage, bytesTransferred, nextChunkSize);
                    bytesTransferred += nextChunkSize;
                    request.flush();
                }
                request.writeBytes(lineEnd);
                request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                request.flush();
            }

            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                new String(ByteStreams.toByteArray(httpsURLConnection.getInputStream()));
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                try {
                    httpsURLConnection.getInputStream().close();
                }catch (Exception ignored){}

                throw new HttpsConnectionException(responseCode, error);
            }
            httpsURLConnection.getInputStream().close();
        }else {
            HttpURLConnection httpURLConnection;
            URL url = new URL(urlConnection);

            int lengthSentImage = 0;
            byte[] pixelsImage = new byte[0];
            if( image != null) {
                ByteArrayOutputStream ous = null;
                try {
                    try {
                        byte[] buffer = new byte[CHUNK_SIZE];
                        ous = new ByteArrayOutputStream();
                        int read;
                        while ((read = image.read(buffer)) != -1) {
                            ous.write(buffer, 0, read);
                        }
                        ous.flush();
                    } finally {
                        if (ous != null)
                            ous.close();
                    }
                } catch (FileNotFoundException ignored) {
                } catch (IOException ignored) {
                }
                pixelsImage = ous.toByteArray();

                lengthSentImage = pixelsImage.length;
                lengthSentImage += 2 * (twoHyphens + boundary + twoHyphens + lineEnd).getBytes().length;
                if( it == imageType.AVATAR)
                    lengthSentImage += ("Content-Disposition: form-data; name=\"avatar\";filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                else
                    lengthSentImage += ("Content-Disposition: form-data; name=\"header\";filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                lengthSentImage += 2 * (lineEnd).getBytes().length;
            }



            int lengthSent = lengthSentImage + (twoHyphens + boundary + twoHyphens + lineEnd).getBytes().length;
            if (proxy != null)
                httpURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpURLConnection = (HttpsURLConnection) url.openConnection();
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpURLConnection.setConnectTimeout(timeout * 1000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setUseCaches(false);
            if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT ){
                httpURLConnection.setRequestMethod("PATCH");
            }else {
                httpURLConnection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                httpURLConnection.setRequestMethod("POST");
            }
            httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
            httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
            httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+ boundary);
            if (token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setFixedLengthStreamingMode(lengthSent);

            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes("UTF-8"));
            if(lengthSentImage > 0){
                DataOutputStream request = new DataOutputStream(outputStream);
                int totalSize = pixelsImage.length;
                int bytesTransferred = 0;
                request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                if( it == imageType.AVATAR)
                    request.writeBytes("Content-Disposition: form-data; name=\"avatar\";filename=\""+fileName+"\"" + lineEnd);
                else
                    request.writeBytes("Content-Disposition: form-data; name=\"header\";filename=\""+fileName+"\"" + lineEnd);

                request.writeBytes(lineEnd);
                while (bytesTransferred < totalSize) {
                    int nextChunkSize = totalSize - bytesTransferred;
                    if (nextChunkSize > CHUNK_SIZE) {
                        nextChunkSize = CHUNK_SIZE;
                    }
                    request.write(pixelsImage, bytesTransferred, nextChunkSize);
                    bytesTransferred += nextChunkSize;
                    request.flush();
                }
                request.writeBytes(lineEnd);
                request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                request.flush();
            }

            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                new String(ByteStreams.toByteArray(httpURLConnection.getInputStream()));
            } else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                try {
                    httpURLConnection.getInputStream().close();
                }catch (Exception ignored){}

                throw new HttpsConnectionException(responseCode, error);
            }
            httpURLConnection.getInputStream().close();
        }

    }




        @SuppressWarnings("SameParameterValue")
    void patch(String urlConnection, int timeout, HashMap<String, String> paramaters, InputStream avatar, String avatarName, InputStream header, String headerName, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        if( urlConnection.startsWith("https://")) {
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = (postData.toString()).getBytes("UTF-8");



            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT ){
                httpsURLConnection.setRequestMethod("PATCH");
            }else {
                httpsURLConnection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                httpsURLConnection.setRequestMethod("POST");
            }
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            httpsURLConnection.setDoOutput(true);


            OutputStream outputStream = httpsURLConnection.getOutputStream();
            outputStream.write(postDataBytes);
            if( avatar != null)
                patchImage(urlConnection,120,imageType.AVATAR, avatar,avatarName,token);
            if( header != null)
                patchImage(urlConnection,120,imageType.BANNER, header,headerName,token);
            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
               new String(ByteStreams.toByteArray(httpsURLConnection.getInputStream()));
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                try {
                    httpsURLConnection.getInputStream().close();
                }catch (Exception ignored){}
                throw new HttpsConnectionException(responseCode, error);
            }
            httpsURLConnection.getInputStream().close();
        }else {
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = (postData.toString()).getBytes("UTF-8");

            if (proxy != null)
                httpURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpURLConnection = (HttpsURLConnection) url.openConnection();
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpURLConnection.setConnectTimeout(timeout * 1000);
            if( Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT ){
                httpURLConnection.setRequestMethod("PATCH");
            }else {
                httpURLConnection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                httpURLConnection.setRequestMethod("POST");
            }
            if (token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
            httpURLConnection.setDoOutput(true);


            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(postDataBytes);
            if( avatar != null)
                patchImage(urlConnection,120,imageType.AVATAR, avatar,avatarName,token);
            if( header != null)
                patchImage(urlConnection,120,imageType.BANNER, header,headerName,token);
            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                new String(ByteStreams.toByteArray(httpURLConnection.getInputStream()));
            } else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                try {
                    httpURLConnection.getInputStream().close();
                }catch (Exception ignored){}

                throw new HttpsConnectionException(responseCode, error);
            }
            httpURLConnection.getInputStream().close();
        }

    }


    /**
     * Upload method - https only
     * @param inputStream InputStream of the file to upload
     * @param listener - OnRetrieveAttachmentInterface: listener to send information about attachment once uploaded.
     */
    public void upload(final InputStream inputStream, String fileName, String tokenUsed, final OnRetrieveAttachmentInterface listener) {

        if( Helper.getLiveInstanceWithProtocol(context).startsWith("https://")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        String twoHyphens = "--";
                        String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
                        String lineEnd = "\r\n";
                        String token;
                        if( tokenUsed == null)
                            token = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
                        else
                            token = tokenUsed;
                        SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                        Account account = new AccountDAO(context, db).getAccountByToken(token);
                        final URL url = new URL("https://" + account.getInstance() + "/api/v1/media");
                        ByteArrayOutputStream ous = null;
                        try {
                            try {
                                byte[] buffer = new byte[CHUNK_SIZE]; // or other buffer size
                                ous = new ByteArrayOutputStream();
                                int read;
                                while ((read = inputStream.read(buffer)) != -1) {
                                    ous.write(buffer, 0, read);
                                }
                                ous.flush();
                            } finally {
                                if (ous != null)
                                    ous.close();
                            }
                        } catch (FileNotFoundException ignored) {
                        } catch (IOException ignored) {
                        }
                        byte[] pixels = ous.toByteArray();

                        int lengthSent = pixels.length;
                        lengthSent += (twoHyphens + boundary + lineEnd).getBytes().length;
                        lengthSent += (twoHyphens + boundary + twoHyphens +lineEnd).getBytes().length;
                        lengthSent += ("Content-Disposition: form-data; name=\"file\"; filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                        lengthSent += 2 * (lineEnd).getBytes().length;

                        if (proxy != null)
                            httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
                        else
                            httpsURLConnection = (HttpsURLConnection) url.openConnection();
                        httpsURLConnection.setFixedLengthStreamingMode(lengthSent);

                        httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                        httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
                        httpsURLConnection.setDoInput(true);
                        httpsURLConnection.setDoOutput(true);
                        httpsURLConnection.setUseCaches(false);

                        httpsURLConnection.setRequestMethod("POST");
                        if (token != null)
                            httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
                        httpsURLConnection.setRequestProperty("Connection", "Keep-Alive");
                        httpsURLConnection.setRequestProperty("Cache-Control", "no-cache");
                        httpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        httpsURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);


                        DataOutputStream request = new DataOutputStream(httpsURLConnection.getOutputStream());

                        request.writeBytes(twoHyphens + boundary  + lineEnd);
                        request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""+fileName+"\"" + lineEnd);
                        request.writeBytes(lineEnd);

                        //request.write(pixels);

                        int totalSize = pixels.length;
                        int bytesTransferred = 0;


                        while (bytesTransferred < totalSize) {
                            int nextChunkSize = totalSize - bytesTransferred;
                            if (nextChunkSize > CHUNK_SIZE) {
                                nextChunkSize = CHUNK_SIZE;
                            }
                            request.write(pixels, bytesTransferred, nextChunkSize);
                            bytesTransferred += nextChunkSize;


                            final int progress = 100 * bytesTransferred / totalSize;
                            ((TootActivity) context).runOnUiThread(new Runnable() {
                                public void run() {
                                    listener.onUpdateProgress(progress>0?progress:101);
                                }
                            });
                            request.flush();
                        }
                        request.writeBytes(lineEnd);
                        request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                        request.flush();
                        request.close();


                        if (200 != httpsURLConnection.getResponseCode()) {
                            String error = null;
                            if( httpsURLConnection.getErrorStream() != null) {
                                InputStream stream = httpsURLConnection.getErrorStream();
                                if (stream == null) {
                                    stream = httpsURLConnection.getInputStream();
                                }
                                try (Scanner scanner = new Scanner(stream)) {
                                    scanner.useDelimiter("\\Z");
                                    error = scanner.next();
                                }catch (Exception ignored){}
                            }
                            int responseCode = httpsURLConnection.getResponseCode();
                            throw new HttpsConnectionException(responseCode, error);
                        }

                        InputStream responseStream = new BufferedInputStream(httpsURLConnection.getInputStream());

                        BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));

                        String response = converToString(httpsURLConnection.getInputStream());
                        ((TootActivity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onUpdateProgress(101);
                            }
                        });


                        final Attachment attachment = API.parseAttachmentResponse(new JSONObject(response));
                        responseStreamReader.close();
                        responseStream.close();
                        httpsURLConnection.getInputStream().close();

                        ((TootActivity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onRetrieveAttachment(attachment, null);
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        ((TootActivity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onUpdateProgress(101);
                            }
                        });
                        final Error error = new Error();
                        error.setError(e.getMessage());
                        if (httpsURLConnection != null)
                            try {
                                httpsURLConnection.getInputStream().close();
                            } catch (Exception ignored) { }
                        ((TootActivity) context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onRetrieveAttachment(null, error);
                            }
                        });

                    }
                }
            }).start();
        }else {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {

                        String twoHyphens = "--";
                        String boundary = "*****" + Long.toString(System.currentTimeMillis()) + "*****";
                        String lineEnd = "\r\n";
                        String token;
                        if( tokenUsed == null)
                            token = sharedpreferences.getString(Helper.PREF_KEY_OAUTH_TOKEN, null);
                        else
                            token = tokenUsed;
                        SQLiteDatabase db = Sqlite.getInstance(context, Sqlite.DB_NAME, null, Sqlite.DB_VERSION).open();
                        Account account = new AccountDAO(context, db).getAccountByToken(token);
                        final URL url = new URL("http://" + account.getInstance() + "/api/v1/media");
                        ByteArrayOutputStream ous = null;
                        try {
                            try {
                                byte[] buffer = new byte[CHUNK_SIZE]; // or other buffer size
                                ous = new ByteArrayOutputStream();
                                int read;
                                while ((read = inputStream.read(buffer)) != -1) {
                                    ous.write(buffer, 0, read);
                                }
                                ous.flush();
                            } finally {
                                if (ous != null)
                                    ous.close();
                            }
                        } catch (FileNotFoundException ignored) {
                        } catch (IOException ignored) {}
                        byte[] pixels = ous.toByteArray();

                        int lengthSent = pixels.length;
                        lengthSent += (twoHyphens + boundary + lineEnd).getBytes().length;
                        lengthSent += (twoHyphens + boundary + twoHyphens +lineEnd).getBytes().length;
                        lengthSent += ("Content-Disposition: form-data; name=\"file\";filename=\""+fileName+"\"" + lineEnd).getBytes().length;
                        lengthSent += 2 * (lineEnd).getBytes().length;

                        if( proxy !=null )
                            httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
                        else
                            httpURLConnection = (HttpURLConnection)url.openConnection();
                        httpURLConnection.setFixedLengthStreamingMode(lengthSent);

                        httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
                        httpURLConnection.setDoInput(true);
                        httpURLConnection.setDoOutput(true);
                        httpURLConnection.setUseCaches(false);

                        httpURLConnection.setRequestMethod("POST");
                        if (token != null)
                            httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
                        httpURLConnection.setRequestProperty("Connection", "Keep-Alive");
                        httpURLConnection.setRequestProperty("Cache-Control", "no-cache");
                        httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+ boundary);


                        DataOutputStream request = new DataOutputStream(httpURLConnection.getOutputStream());

                        request.writeBytes(twoHyphens + boundary + lineEnd);
                        request.writeBytes("Content-Disposition: form-data; name=\"file\";filename=\""+fileName+"\"" + lineEnd);
                        request.writeBytes(lineEnd);

                        //request.write(pixels);

                        int totalSize = pixels.length;
                        int bytesTransferred = 0;


                        while (bytesTransferred < totalSize) {
                            int nextChunkSize = totalSize - bytesTransferred;
                            if (nextChunkSize > CHUNK_SIZE) {
                                nextChunkSize = CHUNK_SIZE;
                            }
                            request.write(pixels, bytesTransferred, nextChunkSize);
                            bytesTransferred += nextChunkSize;


                            final int progress = 100 * bytesTransferred / totalSize;
                            ((TootActivity)context).runOnUiThread(new Runnable() {
                                public void run() {
                                    listener.onUpdateProgress(progress>0?progress:101);
                                }});
                            request.flush();
                        }
                        request.writeBytes(lineEnd);
                        request.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
                        request.flush();
                        request.close();


                        if (200 != httpURLConnection.getResponseCode()) {
                            String error = null;
                            if( httpURLConnection.getErrorStream() != null) {
                                InputStream stream = httpURLConnection.getErrorStream();
                                if (stream == null) {
                                    stream = httpURLConnection.getInputStream();
                                }
                                try (Scanner scanner = new Scanner(stream)) {
                                    scanner.useDelimiter("\\Z");
                                    error = scanner.next();
                                }
                            }
                            int responseCode = httpURLConnection.getResponseCode();
                            throw new HttpsConnectionException(responseCode, error);
                        }

                        InputStream responseStream = new BufferedInputStream(httpURLConnection.getInputStream());

                        BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));

                        String response = converToString(httpsURLConnection.getInputStream());
                        ((TootActivity)context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onUpdateProgress(101);
                            }});


                        final Attachment attachment = API.parseAttachmentResponse(new JSONObject(response));
                        responseStreamReader.close();
                        responseStream.close();
                        httpURLConnection.getInputStream().close();

                        ((TootActivity)context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onRetrieveAttachment(attachment, null);
                            }});
                    }catch (Exception e) {
                        ((TootActivity)context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onUpdateProgress(101);
                            }});
                        final Error error = new Error();
                        error.setError(e.getMessage());
                        if(httpURLConnection != null)
                            try {
                                httpsURLConnection.getInputStream().close();
                            } catch (Exception ignored) { }
                        ((TootActivity)context).runOnUiThread(new Runnable() {
                            public void run() {
                                listener.onRetrieveAttachment(null, error);
                            }});

                    }
                }
            }).start();
        }
    }

    public String put(String urlConnection, int timeout, HashMap<String, String> paramaters, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        if( urlConnection.startsWith("https://")) {
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            httpsURLConnection.setRequestMethod("PUT");
            httpsURLConnection.setDoInput(true);
            httpsURLConnection.setDoOutput(true);

            httpsURLConnection.getOutputStream().write(postDataBytes);
            String response;
            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpsURLConnection.getInputStream());
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpsURLConnection.getInputStream().close();
            return response;
        }else{
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            if (proxy != null)
                httpURLConnection = (HttpURLConnection) url.openConnection(proxy);
            else
                httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpURLConnection.setConnectTimeout(timeout * 1000);
            if (token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            httpURLConnection.setRequestMethod("PUT");
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);

            httpURLConnection.getOutputStream().write(postDataBytes);
            String response;
            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                response = converToString(httpURLConnection.getInputStream());
            } else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
            getSinceMaxId();
            httpURLConnection.getInputStream().close();
            return response;
        }

    }






    public int delete(String urlConnection, int timeout, HashMap<String, String> paramaters, String token) throws IOException, NoSuchAlgorithmException, KeyManagementException, HttpsConnectionException {
        if( urlConnection.startsWith("https://")) {
            URL url = new URL(urlConnection);
            Map<String, Object> params = new LinkedHashMap<>();
            if (paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String, Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            if (proxy != null)
                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
            else
                httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            httpsURLConnection.setSSLSocketFactory(new TLSSocketFactory());
            if (token != null)
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpsURLConnection.setRequestMethod("DELETE");
            httpsURLConnection.setConnectTimeout(timeout * 1000);
            httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            httpsURLConnection.getOutputStream().write(postDataBytes);

            if (httpsURLConnection.getResponseCode() >= 200 && httpsURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                httpsURLConnection.getInputStream().close();
                return httpsURLConnection.getResponseCode();
            } else {
                String error = null;
                if( httpsURLConnection.getErrorStream() != null) {
                    InputStream stream = httpsURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpsURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpsURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
        }else {
            URL url = new URL(urlConnection);
            Map<String,Object> params = new LinkedHashMap<>();
            if( paramaters != null) {
                Iterator it = paramaters.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry pair = (Map.Entry) it.next();
                    params.put(pair.getKey().toString(), pair.getValue());
                    it.remove();
                }
            }
            StringBuilder postData = new StringBuilder();
            for (Map.Entry<String,Object> param : params.entrySet()) {
                if (postData.length() != 0) postData.append('&');
                postData.append(param.getKey());
                postData.append('=');
                postData.append(String.valueOf(param.getValue()));
            }
            byte[] postDataBytes = postData.toString().getBytes("UTF-8");

            if( proxy !=null )
                httpURLConnection = (HttpURLConnection)url.openConnection(proxy);
            else
                httpURLConnection = (HttpURLConnection)url.openConnection();
            httpURLConnection.setRequestProperty("User-Agent", Helper.USER_AGENT);
            if( token != null)
                httpURLConnection.setRequestProperty("Authorization", "Bearer " + token);
            httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpURLConnection.setRequestMethod("DELETE");
            httpURLConnection.setConnectTimeout(timeout * 1000);
            httpURLConnection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

            httpURLConnection.getOutputStream().write(postDataBytes);


            if (httpURLConnection.getResponseCode() >= 200 && httpURLConnection.getResponseCode() < 400) {
                getSinceMaxId();
                httpURLConnection.getInputStream().close();
                return httpURLConnection.getResponseCode();
            }else {
                String error = null;
                if( httpURLConnection.getErrorStream() != null) {
                    InputStream stream = httpURLConnection.getErrorStream();
                    if (stream == null) {
                        stream = httpURLConnection.getInputStream();
                    }
                    try (Scanner scanner = new Scanner(stream)) {
                        scanner.useDelimiter("\\Z");
                        error = scanner.next();
                    }catch (Exception e){e.printStackTrace();}
                }
                int responseCode = httpURLConnection.getResponseCode();
                throw new HttpsConnectionException(responseCode, error);
            }
        }
    }

    public String getSince_id() {
        return since_id;
    }

    public String getMax_id() {
        return max_id;
    }


    private void getSinceMaxId(){
        if( Helper.getLiveInstanceWithProtocol(context) == null)
            return;
        if( Helper.getLiveInstanceWithProtocol(context).startsWith("https://")) {
            if (httpsURLConnection == null)
                return;
            Map<String, List<String>> map = httpsURLConnection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                if (entry.toString().startsWith("Link") || entry.toString().startsWith("link") ) {
                    Pattern patternMaxId = Pattern.compile("max_id=([0-9]{1,}).*");
                    Matcher matcherMaxId = patternMaxId.matcher(entry.toString());
                    if (matcherMaxId.find()) {
                        max_id = matcherMaxId.group(1);
                    }
                    if (entry.toString().startsWith("Link")) {
                        Pattern patternSinceId = Pattern.compile("since_id=([0-9]{1,}).*");
                        Matcher matcherSinceId = patternSinceId.matcher(entry.toString());
                        if (matcherSinceId.find()) {
                            since_id = matcherSinceId.group(1);
                        }

                    }
                }
            }
        }else {
            if (httpURLConnection == null)
                return;
            Map<String, List<String>> map = httpURLConnection.getHeaderFields();
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                if (entry.toString().startsWith("Link") || entry.toString().startsWith("link")) {
                    Pattern patternMaxId = Pattern.compile("max_id=([0-9]{1,}).*");
                    Matcher matcherMaxId = patternMaxId.matcher(entry.toString());
                    if (matcherMaxId.find()) {
                        max_id = matcherMaxId.group(1);
                    }
                    if (entry.toString().startsWith("Link")) {
                        Pattern patternSinceId = Pattern.compile("since_id=([0-9]{1,}).*");
                        Matcher matcherSinceId = patternSinceId.matcher(entry.toString());
                        if (matcherSinceId.find()) {
                            since_id = matcherSinceId.group(1);
                        }

                    }
                }
            }
        }
    }

    private String converToString(InputStream inputStream) throws IOException {
        java.util.Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }


    int getActionCode() {
        if( Helper.getLiveInstanceWithProtocol(context).startsWith("https://")) {
            try {
                return httpsURLConnection.getResponseCode();
            } catch (IOException e) {
                return -1;
            }
        }else {
            try {
                return httpURLConnection.getResponseCode();
            } catch (IOException e) {
                return -1;
            }
        }
    }

    public class HttpsConnectionException extends Exception {

        private int statusCode;
        private String message;
        HttpsConnectionException(int statusCode, String message) {
            this.statusCode = statusCode;
            SpannableString spannableString;
            if( message != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    spannableString = new SpannableString(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
                else
                    //noinspection deprecation
                    spannableString = new SpannableString(Html.fromHtml(message));
            }else {
                spannableString = new SpannableString(context.getString(R.string.toast_error));
            }
            this.message = spannableString.toString();
        }


        public int getStatusCode() {
            return statusCode;
        }

        @Override
        public String getMessage() {
            return message;
        }

    }
}
