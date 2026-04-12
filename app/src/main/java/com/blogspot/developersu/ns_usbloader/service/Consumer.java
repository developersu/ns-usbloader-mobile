package com.blogspot.developersu.ns_usbloader.service;

/**
 * Since Android added java.util.function.Consumer in API level 24 and I want API 15
 * */
public interface Consumer<T> {
    void accept(T argument);
}