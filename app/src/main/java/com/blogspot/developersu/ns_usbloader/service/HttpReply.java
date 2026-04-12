package com.blogspot.developersu.ns_usbloader.service;

import static java.util.TimeZone.getTimeZone;

import com.blogspot.developersu.ns_usbloader.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class HttpReply {

    private static HttpReply instance;

    public static HttpReply getInstance() {
        if (instance == null) {
            instance = new HttpReply();
        }
        return instance;
    }

    private static final String CODE_200 =
                                            "HTTP/1.0 200 OK\r\n" +
                                            "Server: NS-USBloader-M-v."+ BuildConfig.VERSION_NAME+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Content-type: application/octet-stream\r\n" +
                                            "Accept-Ranges: bytes\r\n" +
                                            "Content-Range: bytes 0-%d/%d\r\n" +
                                            "Content-Length: %d\r\n" +
                                            "Last-Modified: Thu, 01 Jan 1970 00:00:00 GMT\r\n\r\n";
    private static final String CODE_206 =
                                            "HTTP/1.0 206 Partial Content\r\n"+
                                            "Server: NS-USBloader-M-v."+BuildConfig.VERSION_NAME+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Content-type: application/octet-stream\r\n"+
                                            "Accept-Ranges: bytes\r\n"+
                                            "Content-Range: bytes %d-%d/%d\r\n"+
                                            "Content-Length: %d\r\n"+
                                            "Last-Modified: Thu, 01 Jan 1970 00:00:00 GMT\r\n\r\n";
    private static final String CODE_400 =
                                            "HTTP/1.0 400 invalid range\r\n"+
                                            "Server: NS-USBloader-M-v."+BuildConfig.VERSION_NAME+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Connection: close\r\n"+
                                            "Content-Type: text/html;charset=utf-8\r\n"+
                                            "Content-Length: 0\r\n\r\n";
    private static final String CODE_404 =
                                            "HTTP/1.0 404 Not Found\r\n"+
                                            "Server: NS-USBloader-M-v."+BuildConfig.VERSION_NAME+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Connection: close\r\n"+
                                            "Content-Type: text/html;charset=utf-8\r\n"+
                                            "Content-Length: 0\r\n\r\n";
    private static final String CODE_416 =
                                            "HTTP/1.0 416 Requested Range Not Satisfiable\r\n"+
                                            "Server: NS-USBloader-M-v."+BuildConfig.VERSION_NAME+"\r\n" +
                                            "Date: %s\r\n" +
                                            "Connection: close\r\n"+
                                            "Content-Type: text/html;charset=utf-8\r\n"+
                                            "Content-Length: 0\r\n\r\n";

    private final SimpleDateFormat dateFormat;

    private HttpReply() {
        dateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(getTimeZone("UTC"));
    }

    public String get200(long nspFileSize){
        return String.format(Locale.US, CODE_200, getTime(), nspFileSize-1, nspFileSize, nspFileSize);
    }
    public String get206(long nspFileSize, long startPos, long endPos){
        return String.format(Locale.US, CODE_206, getTime(), startPos, endPos, nspFileSize, endPos-startPos+1);
    }
    public String get404(){
        return String.format(Locale.US, CODE_404, getTime());
    }
    public String get416(){
        return String.format(Locale.US, CODE_416, getTime());
    }
    public String get400(){
        return String.format(Locale.US, CODE_400, getTime());
    }

    private String getTime(){
        return dateFormat.format(Calendar.getInstance().getTime());
    }
}
