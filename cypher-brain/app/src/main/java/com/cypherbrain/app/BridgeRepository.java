package com.cypherbrain.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BridgeRepository {
    private final List<Cluster> clusters = new ArrayList<>();
    private final Map<String, List<Cluster>> reverse = new HashMap<>();
    private final Set<String> stop = new HashSet<>();

    public BridgeRepository(Context context) {
        Collections.addAll(stop, "yo","tu","tú","el","la","los","las","un","una","de","del","a","y","o","que","en","con","por","para","mi","mis","me","te","se","es","soy","eres","somos","son","como","pero","si","no","ya","lo","le","al","este","esta","eso","esa","aqui","aquí","ahi","ahí","muy","mas","más");
        load(context);
    }

    private void load(Context context) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("clusters.tsv")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\t");
                if (p.length < 5) continue;
                Cluster c = new Cluster(p[0]);
                for (int i = 1; i < p.length; i++) {
                    String node = p[i].trim();
                    if (!node.isEmpty()) c.nodes.add(node);
                }
                clusters.add(c);
                for (String node : c.nodes) {
                    reverse.computeIfAbsent(norm(node), k -> new ArrayList<>()).add(c);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar clusters.tsv", e);
        }
    }

    public int potentialConnections() {
        int n = 0;
        for (Cluster c : clusters) n += c.nodes.size() * (c.nodes.size() - 1);
        return n;
    }

    public List<String> allNodes() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Cluster c : clusters) out.addAll(c.nodes);
        return new ArrayList<>(out);
    }

    public String bestConceptFromPhrase(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) return "";
        String cleaned = norm(phrase).replaceAll("[^a-z0-9ñ ]", " ");
        String[] tokens = cleaned.trim().split("\\s+");

        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.length() < 3 || stop.contains(t)) continue;
            String exact = canonicalFor(t);
            if (exact != null) return exact;
        }

        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.length() < 4 || stop.contains(t)) continue;
            for (String key : reverse.keySet()) {
                if (key.contains(t) || t.contains(key)) {
                    int score = Math.abs(key.length() - t.length());
                    if (score < bestScore) {
                        bestScore = score;
                        best = canonicalFor(key);
                    }
                }
            }
        }
        if (best != null) return best;

        for (int i = tokens.length - 1; i >= 0; i--) {
            if (tokens[i].length() >= 3 && !stop.contains(tokens[i])) return tokens[i];
        }
        return tokens.length == 0 ? "" : tokens[tokens.length - 1];
    }

    private String canonicalFor(String normalized) {
        List<Cluster> cs = reverse.get(normalized);
        if (cs == null || cs.isEmpty()) return null;
        for (String node : cs.get(0).nodes) if (norm(node).equals(normalized)) return node;
        return null;
    }

    public List<String> bridgesFor(String concept, int limit) {
        if (concept == null || concept.isEmpty()) return Collections.emptyList();
        String key = norm(concept);
        List<Cluster> source = reverse.get(key);
        if (source == null || source.isEmpty()) {
            source = nearestClusters(key);
        }
        if (source.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (Cluster c : source) {
            int idx = indexOf(c, key);
            if (idx < 0) idx = 0;
            int n = c.nodes.size();
            for (int jump = 1; jump < n && results.size() < limit; jump++) {
                String a = c.nodes.get((idx + jump) % n);
                String b = c.nodes.get((idx + jump + 1) % n);
                String d = c.nodes.get((idx + jump + 2) % n);
                if (norm(a).equals(key)) continue;
                results.add(concept + " → " + a + " → " + b + " → " + d);
            }
            if (results.size() >= limit) break;
        }
        return new ArrayList<>(results);
    }

    public List<String> directAssociations(String concept, int limit) {
        if (concept == null || concept.isEmpty()) return Collections.emptyList();
        String key = norm(concept);
        List<Cluster> source = reverse.get(key);
        if (source == null || source.isEmpty()) source = nearestClusters(key);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Cluster c : source) {
            int idx = indexOf(c, key);
            if (idx < 0) idx = 0;
            for (int dist = 1; dist < c.nodes.size() && out.size() < limit; dist++) {
                int r = (idx + dist) % c.nodes.size();
                int l = (idx - dist + c.nodes.size()) % c.nodes.size();
                out.add(c.nodes.get(r));
                if (out.size() < limit) out.add(c.nodes.get(l));
            }
        }
        return new ArrayList<>(out);
    }

    private List<Cluster> nearestClusters(String key) {
        List<ScoredCluster> scored = new ArrayList<>();
        for (Cluster c : clusters) {
            int score = 999;
            for (String node : c.nodes) {
                String nn = norm(node);
                if (nn.contains(key) || key.contains(nn)) score = Math.min(score, Math.abs(nn.length() - key.length()));
            }
            if (score < 999) scored.add(new ScoredCluster(c, score));
        }
        scored.sort(Comparator.comparingInt(a -> a.score));
        List<Cluster> out = new ArrayList<>();
        for (int i = 0; i < Math.min(2, scored.size()); i++) out.add(scored.get(i).cluster);
        return out;
    }

    private int indexOf(Cluster c, String key) {
        for (int i = 0; i < c.nodes.size(); i++) if (norm(c.nodes.get(i)).equals(key)) return i;
        return -1;
    }

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        return n.trim();
    }

    private static class Cluster {
        final String name;
        final List<String> nodes = new ArrayList<>();
        Cluster(String name) { this.name = name; }
    }

    private static class ScoredCluster {
        final Cluster cluster;
        final int score;
        ScoredCluster(Cluster c, int s) { cluster = c; score = s; }
    }
}
