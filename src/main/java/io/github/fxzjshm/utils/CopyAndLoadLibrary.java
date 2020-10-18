/*
 * Copyright 2020 fxzjshm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.fxzjshm.utils;

import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * If one library cannot be loaded directly by dlopen() on android, copy it (and its dependencies) and then load.
 * See full usage sample at
 * https://github.com/fxzjshm/Cellular-automaton/blob/master/android/src/main/java/com/entermoor/cellular_automaton/AndroidLauncher.java
 *
 * @author fxzjshm
 * @version 0.0.1-SNAPSHOT
 */
public class CopyAndLoadLibrary {
    /**
     * Change LD_LIBRARY_PATH at runtime is useless, so here it only represents a list of paths to search.
     */
    public static String LD_LIBRARY_PATH = "/vendor/lib64:/vendor/lib64/hw:/odm/lib64:/system/lib64:" +
            "/system/vendor/lib64:/system/lib64/drm:/system/lib64/extractors:/system/lib64/hw:/product/lib64:" +
            "/system/framework:/system/app:/system/priv-app:/vendor/framework:/vendor/app:/vendor/priv-app:" +
            "/system/vendor/framework:/system/vendor/app:/system/vendor/priv-app:/odm/framework:" +
            "/odm/app:/odm/priv-app:/oem/app:/product/framework:/product/app:/product/priv-app:" +
            "/data:/mnt/expand:/apex/com.android.runtime/lib64/bionic:/system/lib64/bootstrap";
    public static String[] searchPaths = LD_LIBRARY_PATH.split(":");

    /**
     * Main function to load a library.
     *
     * @param so_path       path to the .so file to be loaded
     * @param targetDirPath the destination the .so file to be copied to
     */
    public static void copyAndLoadLibrary(String so_path, String targetDirPath) throws IOException, InterruptedException {
        System.loadLibrary("CopyAndLoadLibrary-android-jni");

        Log.d("copyAndLoadLibrary", so_path);
        File so_file = new File(so_path);
        String so_name = so_file.getName(), target = targetDirPath + "/" + so_name;
        File targetFile = new File(target);
        if (!(targetFile.exists() && targetFile.length() == so_file.length())) {
            Runtime.getRuntime().exec(new String[]{"cp", so_path, targetDirPath + "/"}).waitFor();
            Log.i("copyAndLoadLibrary", "copied " + so_path + " to " + target);
        } else {
            Log.d("copyAndLoadLibrary", "skipped copying " + so_name + " as it exists");
        }
        String libFileName;
        while (true) {
            try {
                load(target);
                Log.d("copyAndLoadLibrary", "load " + target + " succeeded");

                break;
            } catch (UnsatisfiedLinkError e) {
                String message = e.getMessage();
                Log.d("copyAndLoadLibrary", "load " + target + " failed: " + message);

                // dlopen failed: library "*" needed or dlopened by "*" is not accessible for the namespace "*"
                // dlopen failed: library "*" not found
                if (message.startsWith("dlopen failed: library")) {
                    int l = message.indexOf('\"'), r = message.indexOf('\"', l + 1);
                    libFileName = message.substring(l + 1, r);
                    if (libFileName.equals(so_name)) {
                        throw e;
                    }
                } else {
                    // dlopen failed: "/data/data/com.entermoor.cellular_automaton/files/libOpenCL.so" is 32-bit instead of 64-bit
                    throw e;
                }
            }
            copyAndLoadLibrary(lookupLibrary(libFileName), targetDirPath);
        }
        Log.d("copyAndLoadLibrary", "will load " + target);
        load(target);
    }

    /**
     * Java wrapper of the dlopen() function in C/C++
     *
     * @throws UnsatisfiedLinkError for error detection in {@link CopyAndLoadLibrary#copyAndLoadLibrary}
     */
    public static long load(String target) {
        long ret = __dlopen(target);
        if (ret == 0) throw new UnsatisfiedLinkError(dlerr());
        Log.d("load", "loaded " + target);
        return ret;
    }

    /**
     * lookup the file in {@link CopyAndLoadLibrary#searchPaths}
     */
    public static String lookupLibrary(String libFileName) {
        Log.d("lookupLibrary", libFileName);
        for (String dirPath : searchPaths) {
            String soPath = dirPath + "/" + libFileName;
            File soFile = new File(soPath);
            if (!soFile.exists()) continue;
            return soPath;
        }
        throw new RuntimeException("Cannot find " + libFileName);
    }

    /**
     * Java wrapper of the dlopen() function in C/C++, without throwing exceptions
     */
    public static native long __dlopen(String filename);

    /**
     * Java wrapper of the dlerr() function in C/C++
     */
    public static native String dlerr();
}
