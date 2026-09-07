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
        Collections.addAll(stop,
                "yo", "tu", "tú", "el", "la", "los", "las", "un", "una", "de", "del",
                "a", "y", "o", "que", "en", "con", "por", "para", "mi", "mis", "me", "te",
                "se", "es", "soy", "eres", "somos", "son", "como", "pero", "si", "no", "ya",
                "lo", "le", "al", "este", "esta", "eso", "esa", "aqui", "aquí", "ahi", "ahí",
                "muy", "mas", "más", "esto", "ese", "esa", "unos", "unas", "porque"
        );
        load(context);
    }

    private void load(Context context) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open("clusters.tsv")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] p = line.split("\\t");
                if (p.length < 4) continue;

                Cluster c = new Cluster(p[0].trim(), p[1].trim());
                c.nodes.add(new Node(c.root, "concepto"));

                for (int i = 2; i < p.length; i++) {
                    String token = p[i].trim();
                    if (token.isEmpty()) continue;

                    int equals = token.indexOf('=');
                    String relation;
                    String value;
                    if (equals > 0 && equals < token.length() - 1) {
                        relation = token.substring(0, equals).trim();
                        value = token.substring(equals + 1).trim();
                    } else {
                        relation = "asociacion";
                        value = token;
                    }
                    if (!value.isEmpty()) c.nodes.add(new Node(value, relation));
                }

                if (c.nodes.size() < 4) continue;
                clusters.add(c);
                for (Node node : c.nodes) {
                    reverse.computeIfAbsent(norm(node.value), k -> new ArrayList<>()).add(c);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar clusters.tsv", e);
        }
    }

    /**
     * Number of ordered semantic bridges available inside the domains.
     * Each ordered pair A/B is connected through one shared semantic root.
     */
    public int potentialConnections() {
        int n = 0;
        for (Cluster c : clusters) {
            n += c.nodes.size() * (c.nodes.size() - 1);
        }
        return n;
    }

    public int domainCount() {
        return clusters.size();
    }

    public List<String> allNodes() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Cluster c : clusters) {
            for (Node node : c.nodes) out.add(node.value);
        }
        return new ArrayList<>(out);
    }

    public String bestConceptFromPhrase(String phrase) {
        if (phrase == null || phrase.trim().isEmpty()) return "";

        String cleaned = norm(phrase).replaceAll("[^a-z0-9ñ ]", " ");
        String[] tokens = cleaned.trim().split("\\s+");

        // Prefer exact graph concepts, scanning backwards because the last
        // useful noun/reference in a freestyle phrase is often the punch anchor.
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.length() < 3 || stop.contains(t)) continue;
            String exact = canonicalFor(t);
            if (exact != null) return exact;
        }

        // Handle short fragments/plurals that contain a known concept.
        String best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t.length() < 4 || stop.contains(t)) continue;
            for (String key : reverse.keySet()) {
                if (key.length() < 4) continue;
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

        // If the word is not in the knowledge graph, keep it as a free concept.
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (tokens[i].length() >= 3 && !stop.contains(tokens[i])) return tokens[i];
        }
        return tokens.length == 0 ? "" : tokens[tokens.length - 1];
    }

    private String canonicalFor(String normalized) {
        List<Cluster> cs = reverse.get(normalized);
        if (cs == null || cs.isEmpty()) return null;
        for (Cluster c : cs) {
            Node node = findNode(c, normalized);
            if (node != null) return node.value;
        }
        return null;
    }

    public List<String> bridgesFor(String concept, int limit) {
        return bridgesFor(concept, limit, 2);
    }

    public List<String> bridgesFor(String concept, int limit, int complexity) {
        if (concept == null || concept.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        complexity = Math.max(1, Math.min(3, complexity));
        String key = norm(concept);
        List<Cluster> source = reverse.get(key);
        if (source == null || source.isEmpty()) source = nearestClusters(key);
        if (source.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> results = new LinkedHashSet<>();

        for (Cluster c : source) {
            int sourceIndex = indexOf(c, key);
            if (sourceIndex < 0) sourceIndex = 0;

            int n = c.nodes.size();
            int stride = complexity == 1 ? 1 : (complexity == 2 ? 2 : 3);

            for (int step = 1; step < n && results.size() < limit; step++) {
                int firstIndex = (sourceIndex + step * stride) % n;
                Node first = c.nodes.get(firstIndex);
                if (norm(first.value).equals(key)) continue;

                if (complexity == 1) {
                    results.add(concept + " → " + typed(first));
                    continue;
                }

                Node second = pickDifferentRelation(c, firstIndex, first.relation, stride);
                if (second == null || norm(second.value).equals(key)) continue;

                if (complexity == 2) {
                    results.add(concept + " → " + typed(first) + " → " + typed(second));
                    continue;
                }

                // NICHO: if the first concept also belongs to another universe,
                // jump domains (Ferrari: auto→lujo, Kubrick: horror→cine, etc.).
                Cluster cross = differentClusterFor(first.value, c);
                if (cross != null) {
                    int crossIndex = indexOf(cross, norm(first.value));
                    Node crossTarget = pickDifferentRelation(cross, Math.max(0, crossIndex), "", 3);
                    if (crossTarget != null && !norm(crossTarget.value).equals(norm(first.value))) {
                        results.add(
                                concept + " → " + typed(first) +
                                " ⇢ " + cross.root.toUpperCase(Locale.ROOT) +
                                " → " + typed(crossTarget)
                        );
                        continue;
                    }
                }

                Node third = pickDifferentRelation(c, indexOf(c, norm(second.value)), second.relation, 4);
                if (third != null && !norm(third.value).equals(key)) {
                    results.add(
                            concept + " → " + typed(first) +
                            " → " + typed(second) +
                            " → " + typed(third)
                    );
                }
            }

            if (results.size() >= limit) break;
        }

        return new ArrayList<>(results);
    }

    public List<String> directAssociations(String concept, int limit) {
        if (concept == null || concept.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        String key = norm(concept);
        List<Cluster> source = reverse.get(key);
        if (source == null || source.isEmpty()) source = nearestClusters(key);
        if (source.isEmpty()) return Collections.emptyList();

        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (Cluster c : source) {
            int idx = indexOf(c, key);
            if (idx < 0) idx = 0;

            // When the detected concept is a member rather than the root,
            // make the semantic universe visible immediately.
            if (idx != 0 && out.size() < limit) {
                out.add("UNIVERSO · " + c.root);
            }

            for (int dist = 1; dist < c.nodes.size() && out.size() < limit; dist++) {
                int r = (idx + dist) % c.nodes.size();
                int l = (idx - dist + c.nodes.size()) % c.nodes.size();

                Node right = c.nodes.get(r);
                if (!norm(right.value).equals(key)) out.add(displayTyped(right));

                if (out.size() < limit) {
                    Node left = c.nodes.get(l);
                    if (!norm(left.value).equals(key)) out.add(displayTyped(left));
                }
            }
        }

        return new ArrayList<>(out);
    }

    private String typed(Node node) {
        if ("concepto".equals(node.relation)) return "UNIVERSO: " + node.value;
        return node.relation.toUpperCase(Locale.ROOT) + ": " + node.value;
    }

    private String displayTyped(Node node) {
        return typed(node).replace(':', '·');
    }

    private Node pickDifferentRelation(Cluster c, int from, String avoidRelation, int offset) {
        if (c == null || c.nodes.isEmpty()) return null;
        int n = c.nodes.size();

        for (int k = 1; k < n; k++) {
            Node node = c.nodes.get((Math.max(0, from) + k * Math.max(1, offset)) % n);
            if ("concepto".equals(node.relation)) continue;
            if (!node.relation.equals(avoidRelation)) return node;
        }

        for (Node node : c.nodes) {
            if (!"concepto".equals(node.relation)) return node;
        }
        return null;
    }

    private Cluster differentClusterFor(String nodeValue, Cluster current) {
        List<Cluster> cs = reverse.get(norm(nodeValue));
        if (cs == null) return null;
        for (Cluster c : cs) {
            if (c != current) return c;
        }
        return null;
    }

    private List<Cluster> nearestClusters(String key) {
        List<ScoredCluster> scored = new ArrayList<>();

        for (Cluster c : clusters) {
            int score = 999;
            for (Node node : c.nodes) {
                String nn = norm(node.value);
                if (nn.contains(key) || key.contains(nn)) {
                    score = Math.min(score, Math.abs(nn.length() - key.length()));
                }
            }
            if (score < 999) scored.add(new ScoredCluster(c, score));
        }

        scored.sort(Comparator.comparingInt(a -> a.score));
        List<Cluster> out = new ArrayList<>();
        for (int i = 0; i < Math.min(2, scored.size()); i++) {
            out.add(scored.get(i).cluster);
        }
        return out;
    }

    private int indexOf(Cluster c, String key) {
        for (int i = 0; i < c.nodes.size(); i++) {
            if (norm(c.nodes.get(i).value).equals(key)) return i;
        }
        return -1;
    }

    private Node findNode(Cluster c, String key) {
        int idx = indexOf(c, key);
        return idx >= 0 ? c.nodes.get(idx) : null;
    }

    public static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}+", "");
        return n.trim();
    }

    private static final class Node {
        final String value;
        final String relation;

        Node(String value, String relation) {
            this.value = value;
            this.relation = relation;
        }
    }

    private static final class Cluster {
        final String name;
        final String root;
        final List<Node> nodes = new ArrayList<>();

        Cluster(String name, String root) {
            this.name = name;
            this.root = root;
        }
    }

    private static final class ScoredCluster {
        final Cluster cluster;
        final int score;

        ScoredCluster(Cluster cluster, int score) {
            this.cluster = cluster;
            this.score = score;
        }
    }
}
