package net.msrandom.resourcefixer;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ResourceFixerTweaker implements ITweaker {
    public static final CompletableFuture<?>[] COMPLETABLE_FUTURES_DUMMY = new CompletableFuture<?>[0];

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    private static Object get(Object receiver, Field field) throws NoSuchFieldException, IllegalAccessException {
        field.setAccessible(true);
        return field.get(receiver);
    }

    private static Path getCommonPath(List<Path> paths) {
        Path commonDirectory = paths.get(0);
        while (true) {
            boolean done = true;
            for (Path directory : paths) {
                if (!directory.startsWith(commonDirectory)) {
                    done = false;
                    break;
                }
            }
            if (done) break;
            commonDirectory = commonDirectory.getParent();
        }
        return commonDirectory;
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        try {
            // Get the URL path first, so that we break out via exception if we can't use reflection.
            Object ucp = get(classLoader, URLClassLoader.class.getDeclaredField("ucp"));
            @SuppressWarnings("unchecked")
            List<URL> path = (List<URL>) get(ucp, ucp.getClass().getDeclaredField("path"));

            Map<String, List<Path>> classpathFiles = new HashMap<>();
            Map<Path, String> fileToId = new HashMap<>();
            String modClasses = System.getenv("MOD_CLASSES");
            if (modClasses != null) {
                for (String output : modClasses.split(":")) {
                    String[] paths = output.split("%%");
                    if (paths.length != 0) {
                        String id = paths.length > 1 ? paths[0] : null;
                        Path file = Paths.get(paths[paths.length - 1]);
                        classpathFiles.computeIfAbsent(id, key -> new ArrayList<>()).add(file);
                        fileToId.put(file, id);
                    }
                }
            }

            Set<String> processedIds = new HashSet<>();
            Set<String> failedIds = new HashSet<>();
            List<URL> fixedPath = new ArrayList<>();
            for (URL url : path) {
                try {
                    Path entry = Paths.get(url.toURI());
                    if (!fileToId.containsKey(entry)) {
                        fixedPath.add(url);
                        continue;
                    }

                    String id = fileToId.get(entry);
                    if (failedIds.contains(id)) {
                        fixedPath.add(url);
                        continue;
                    }

                    List<Path> paths = classpathFiles.get(id);
                    if (paths.size() == 1) {
                        fixedPath.add(url);
                        continue;
                    }

                    if (processedIds.contains(id)) continue;

                    Path redirectedOutput = getCommonPath(paths).resolve(id == null ? "redirectedOutput" : id + "RedirectedOutput");
                    try {
                        List<CompletableFuture<Void>> futures = new ArrayList<>();
                        for (Path directory : paths) {
                            if (Files.exists(directory)) {
                                Files.walk(directory).filter(Files::isRegularFile).map(file -> CompletableFuture.runAsync(() -> {
                                    try {
                                        MessageDigest md5 = MessageDigest.getInstance("MD5");
                                        Path newLocation = redirectedOutput.resolve(directory.relativize(file));
                                        if (Files.exists(newLocation)) {
                                            md5.update(Files.readAllBytes(file));
                                            byte[] fileMd5 = md5.digest();
                                            md5.update(Files.readAllBytes(newLocation));
                                            if (!Arrays.equals(fileMd5, md5.digest())) {
                                                Files.copy(file, newLocation, StandardCopyOption.REPLACE_EXISTING);
                                            }
                                        } else {
                                            Files.createDirectories(newLocation.getParent());
                                            Files.copy(file, newLocation);
                                        }
                                    } catch (IOException | NoSuchAlgorithmException e) {
                                        throw new RuntimeException(e); // throw outside the lambda
                                    }
                                })).forEach(futures::add);
                            }
                        }

                        CompletableFuture.allOf(futures.toArray(COMPLETABLE_FUTURES_DUMMY)).join();
                        processedIds.add(id);
                        fixedPath.add(redirectedOutput.toUri().toURL());
                    } catch (IOException | RuntimeException e) {
                        failedIds.add(id);
                        fixedPath.add(url);
                    }
                } catch (URISyntaxException e) {
                    fixedPath.add(url);
                }
            }

            if (path.size() != fixedPath.size()) {
                path.clear();
                path.addAll(fixedPath);
                classLoader.getSources().clear();
                classLoader.getSources().addAll(fixedPath);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget() {
        return null;
    }

    @SuppressWarnings("ZeroLengthArrayAllocation")
    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
