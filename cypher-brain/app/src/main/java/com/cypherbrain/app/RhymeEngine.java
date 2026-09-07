package com.cypherbrain.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RhymeEngine {
    private final Map<String, List<String>> families = new LinkedHashMap<>();

    public RhymeEngine(Context context) {
        load(context);
    }

    private void load(Context context) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("rhyme_families.tsv")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\t");
                if (p.length < 3) continue;
                List<String> words = new ArrayList<>();
                for (int i = 1; i < p.length; i++) words.add(p[i]);
                families.put(norm(p[0]), words);
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar rhyme_families.tsv", e);
        }
    }

    public List<String> rhymePack(String phrase, int count) {
        String target = lastWord(phrase);
        if (target.isEmpty()) return Collections.emptyList();

        String normalized = norm(target);
        String bestFamily = null;
        int bestScore = Integer.MIN_VALUE;

        for (String key : families.keySet()) {
            int score = suffixScore(normalized, key) * 10 + vowelTailScore(normalized, key);
            if (score > bestScore) {
                bestScore = score;
                bestFamily = key;
            }
        }

        List<String> out = new ArrayList<>();
        if (bestFamily != null && bestScore >= 10) out.addAll(families.get(bestFamily));

        if (out.size() < count) {
            List<Candidate> candidates = new ArrayList<>();
            for (List<String> family : families.values()) {
                for (String word : family) {
                    if (norm(word).equals(normalized)) continue;
                    int score = phoneticScore(normalized, norm(word));
                    candidates.add(new Candidate(word, score));
                }
            }
            candidates.sort((a, b) -> Integer.compare(b.score, a.score));
            for (Candidate c : candidates) {
                if (!out.contains(c.word)) out.add(c.word);
                if (out.size() >= count) break;
            }
        }

        if (out.size() > count) return new ArrayList<>(out.subList(0, count));
        return out;
    }

    public String phonemePattern(String phrase) {
        String w = norm(lastWord(phrase));
        if (w.isEmpty()) return "—";
        StringBuilder vowels = new StringBuilder();
        char previous = 0;
        for (char c : w.toCharArray()) {
            if ("aeiou".indexOf(c) >= 0 && c != previous) {
                if (vowels.length() > 0) vowels.append('-');
                vowels.append(c);
                previous = c;
            }
        }
        return vowels.length() == 0 ? w : vowels.toString();
    }

    private int phoneticScore(String a, String b) {
        int suffix = commonSuffix(a, b);
        String va = vowelTail(a);
        String vb = vowelTail(b);
        int vowel = commonSuffix(va, vb);
        int lengthPenalty = Math.abs(a.length() - b.length());
        return suffix * 5 + vowel * 4 - lengthPenalty;
    }

    private int suffixScore(String word, String family) {
        if (word.endsWith(family)) return family.length();
        return commonSuffix(word, family);
    }

    private int vowelTailScore(String word, String family) {
        return commonSuffix(vowelTail(word), vowelTail(family));
    }

    private String vowelTail(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) if ("aeiou".indexOf(c) >= 0) out.append(c);
        return out.toString();
    }

    private int commonSuffix(String a, String b) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int n = 0;
        while (i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
            n++; i--; j--;
        }
        return n;
    }

    private String lastWord(String phrase) {
        if (phrase == null) return "";
        String cleaned = norm(phrase).replaceAll("[^a-z0-9ñ ]", " ").trim();
        if (cleaned.isEmpty()) return "";
        String[] p = cleaned.split("\\s+");
        return p[p.length - 1];
    }

    private String norm(String s) {
        String n = Normalizer.normalize(s == null ? "" : s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").trim();
    }

    private static class Candidate {
        final String word;
        final int score;
        Candidate(String word, int score) { this.word = word; this.score = score; }
    }
}
