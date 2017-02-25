package com.wizzardo.francis.services;

import com.wizzardo.http.framework.di.Service;
import com.wizzardo.tools.cache.Cache;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by wizzardo on 25/02/17.
 */
public class ClassesService implements Service {
    Cache<String, Classes> classesCache = new Cache<>(60 * 60);

    public void load(String appName, Stream<String> stream) {
        Package defaultPackage = new Package();

        Set<ClassInfo> set = stream
                .filter(s -> s != null && !s.equals("null") && !s.isEmpty())
                .map(s -> {
                    int $ = s.indexOf('$');
                    if ($ != -1)
                        s = s.substring(0, $);

                    String[] parts = s.split("\\.");
                    int partsLength = parts.length - 1;
                    Package aPackage = defaultPackage;
                    for (int i = 0; i < partsLength; i++) {
                        aPackage = aPackage.getOrCreate(parts[i]);
                    }

                    ClassInfo classInfo = new ClassInfo(parts[partsLength], aPackage);
                    aPackage.add(classInfo);
                    return classInfo;
                })
                .filter(s -> !s.name.endsWith("]"))
                .collect(Collectors.toSet());

        ClassInfo[] classInfos = set.stream()
                .sorted(Comparator.comparingInt(o -> o.chars.length))
                .toArray(ClassInfo[]::new);

        Classes classes = new Classes(defaultPackage, classInfos);
        classesCache.put(appName, classes);
    }

    public List<ClassInfo> search(String target, int limit, String appName) {
        return search(target.toCharArray(), limit, classesCache.get(appName).classes);
    }

    protected List<ClassInfo> search(String target, int limit, ClassInfo[] data) {
        return search(target.toCharArray(), limit, data);
    }

    protected List<ClassInfo> search(char[] target, int limit, ClassInfo[] data) {
        List<ClassInfo> result = new ArrayList<>(limit);
        int tl = target.length;
        outer:
        for (ClassInfo classInfo : data) {
            char[] chars = classInfo.chars;
            int length = chars.length;
            if (length < tl)
                continue;

            int position = -1;
            for (int i = 0; i < tl; i++) {
                position = indexOf(chars, target[i], position + 1, length);
                if (position == -1)
                    continue outer;
            }

            result.add(classInfo);
            if (result.size() == limit)
                break;
        }

        return result;
    }

    protected int indexOf(char[] chars, char c, int from, int to) {
        for (int i = from; i < to; i++) {
            if (chars[i] == c)
                return i;
        }
        return -1;
    }

    public static class Classes {
        Package root;
        ClassInfo[] classes;

        public Classes(Package defaultPackage, ClassInfo[] classInfos) {
            root = defaultPackage;
            classes = classInfos;
        }
    }

    public static class Package {
        public final String name;
        public final Package parent;
        Map<String, Package> packages = new HashMap<>(16, 1);
        Map<String, ClassInfo> classes = new HashMap<>(32, 1);

        public Package() {
            name = null;
            parent = null;
        }

        public Package(String name, Package parent) {
            this.name = name;
            this.parent = parent;
        }

        public Package getOrCreate(String name) {
            return packages.computeIfAbsent(name, s -> new Package(s, this));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Package aPackage = (Package) o;

            if (name != null ? !name.equals(aPackage.name) : aPackage.name != null) return false;
            return parent != null ? parent.equals(aPackage.parent) : aPackage.parent == null;
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + (parent != null ? parent.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            if (parent == null || parent.name == null)
                return name;
            else
                return parent.toString() + "." + name;
        }

        void add(ClassInfo classInfo) {
            classes.put(classInfo.name, classInfo);
        }
    }

    public static class ClassInfo {
        public final String name;
        public final Package aPackage;
        final char[] chars;

        ClassInfo(String name, Package aPackage) {
            this.name = name;
            this.aPackage = aPackage;
            chars = name.toCharArray();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClassInfo classInfo = (ClassInfo) o;

            if (!name.equals(classInfo.name)) return false;
            return aPackage.equals(classInfo.aPackage);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + aPackage.hashCode();
            return result;
        }

        @Override
        public String toString() {
            if (aPackage.name == null)
                return name;
            else
                return aPackage.toString() + "." + name;
        }
    }

}
