package cn.ussshenzhou.notenoughbandwidth.indextype;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class ConnectionIndexTable {
    private final Map<String, Integer> namespaceToIndex;
    private final Map<Integer, String> indexToNamespace;
    private final Map<Integer, Map<String, Integer>> pathToIndexByNamespace;
    private final Map<Integer, Map<Integer, String>> indexToPathByNamespace;
    private final Map<String, IndexPair> typeToIndex;

    private ConnectionIndexTable(Map<String, Integer> namespaceToIndex,
                                 Map<Integer, String> indexToNamespace,
                                 Map<Integer, Map<String, Integer>> pathToIndexByNamespace,
                                 Map<Integer, Map<Integer, String>> indexToPathByNamespace,
                                 Map<String, IndexPair> typeToIndex) {
        this.namespaceToIndex = namespaceToIndex;
        this.indexToNamespace = indexToNamespace;
        this.pathToIndexByNamespace = pathToIndexByNamespace;
        this.indexToPathByNamespace = indexToPathByNamespace;
        this.typeToIndex = typeToIndex;
    }

    public static ConnectionIndexTable create(Iterable<String> types) {
        Map<String, Integer> namespaceToIndex = new HashMap<String, Integer>();
        Map<Integer, String> indexToNamespace = new HashMap<Integer, String>();
        Map<Integer, Map<String, Integer>> pathToIndexByNamespace = new HashMap<Integer, Map<String, Integer>>();
        Map<Integer, Map<Integer, String>> indexToPathByNamespace = new HashMap<Integer, Map<Integer, String>>();
        Map<String, IndexPair> typeToIndex = new HashMap<String, IndexPair>();

        int nextNamespaceIndex = 1;
        for (String rawType : types) {
            if (rawType == null || rawType.isEmpty()) {
                continue;
            }
            String[] split = splitType(rawType);
            String namespace = split[0];
            String path = split[1];
            Integer namespaceIndex = namespaceToIndex.get(namespace);
            if (namespaceIndex == null) {
                namespaceIndex = Integer.valueOf(nextNamespaceIndex++);
                namespaceToIndex.put(namespace, namespaceIndex);
                indexToNamespace.put(namespaceIndex, namespace);
                pathToIndexByNamespace.put(namespaceIndex, new HashMap<String, Integer>());
                indexToPathByNamespace.put(namespaceIndex, new HashMap<Integer, String>());
            }
            Map<String, Integer> pathToIndex = pathToIndexByNamespace.get(namespaceIndex);
            Map<Integer, String> indexToPath = indexToPathByNamespace.get(namespaceIndex);
            Integer pathIndex = pathToIndex.get(path);
            if (pathIndex == null) {
                pathIndex = Integer.valueOf(pathToIndex.size());
                pathToIndex.put(path, pathIndex);
                indexToPath.put(pathIndex, path);
            }
            typeToIndex.put(rawType, new IndexPair(namespaceIndex.intValue(), pathIndex.intValue()));
        }

        return new ConnectionIndexTable(namespaceToIndex, indexToNamespace, pathToIndexByNamespace, indexToPathByNamespace, typeToIndex);
    }

    public boolean contains(String type) {
        return type != null && typeToIndex.containsKey(type);
    }

    public IndexPair getCheckedIndex(String type) {
        IndexPair pair = typeToIndex.get(type);
        if (pair == null) {
            throw new IndexOutOfBoundsException("Missing NEB type index for " + type);
        }
        return pair;
    }

    @Nullable
    public String getIdentifierOrNull(int namespaceIndex, int pathIndex) {
        String namespace = indexToNamespace.get(Integer.valueOf(namespaceIndex));
        if (namespace == null) {
            return null;
        }
        Map<Integer, String> paths = indexToPathByNamespace.get(Integer.valueOf(namespaceIndex));
        if (paths == null) {
            return null;
        }
        String path = paths.get(Integer.valueOf(pathIndex));
        if (path == null) {
            return null;
        }
        return namespace + ":" + path;
    }

    private static String[] splitType(String type) {
        int separator = type.indexOf(':');
        if (separator < 0) {
            return new String[]{"minecraft", type};
        }
        return new String[]{type.substring(0, separator), type.substring(separator + 1)};
    }

    public static final class IndexPair {
        private final int namespaceIndex;
        private final int pathIndex;

        public IndexPair(int namespaceIndex, int pathIndex) {
            this.namespaceIndex = namespaceIndex;
            this.pathIndex = pathIndex;
        }

        public int namespaceIndex() {
            return namespaceIndex;
        }

        public int pathIndex() {
            return pathIndex;
        }
    }
}
