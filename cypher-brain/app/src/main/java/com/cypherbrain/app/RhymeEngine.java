package com.cypherbrain.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fast, deterministic rhyme engine for the live overlay.
 *
 * Important design rule: a pack NEVER mixes unrelated families. Once a
 * family is selected, all ten suggestions come from that same family so the
 * rapper can chain several bars without the rhyme texture changing randomly.
 */
public final class RhymeEngine {
    private final Map<String, List<String>> families = new LinkedHashMap<>();

    public RhymeEngine(Context context) {
        load(context);
    }

    private void load(Context context) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open("rhyme_families.tsv")))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] p = line.split("\\t");
                if (p.length < 12) continue; // key + at least eleven candidates

                String key = norm(p[0]).replace(" ", "");
                if (key.isEmpty()) continue;

                LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (int i = 1; i < p.length; i++) {
                    String candidate = p[i].trim();
                    if (!candidate.isEmpty()) unique.add(candidate);
                }

                if (unique.size() >= 11) {
                    families.put(key, new ArrayList<>(unique));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar rhyme_families.tsv", e);
        }
    }

    public int familyCount() {
        return families.size();
    }

    /** Returns exactly count items when a valid corpus is loaded. */
    public List<String> rhymePack(String phrase, int count) {
        if (count <= 0 || families.isEmpty()) return Collections.emptyList();

        String target = norm(lastWord(phrase));
        if (target.isEmpty()) return Collections.emptyList();

        String familyKey = bestFamilyKey(target);
        if (familyKey == null) return Collections.emptyList();

        List<String> family = families.get(familyKey);
        if (family == null || family.isEmpty()) return Collections.emptyList();

        ArrayList<String> out = new ArrayList<>(count);

        // Prefer not to echo the exact word the opponent just used.
        for (String candidate : family) {
            if (!norm(candidate).equals(target)) {
                out.add(candidate);
                if (out.size() == count) return out;
            }
        }

        // Families are deliberately >= 11 entries, but keep a deterministic
        // same-family fallback in case the data is edited later.
        for (String candidate : family) {
            if (!out.contains(candidate)) {
                out.add(candidate);
                if (out.size() == count) return out;
            }
        }

        return out;
    }

    public String familyFor(String phrase) {
        String target = norm(lastWord(phrase));
        if (target.isEmpty()) return "—";
        String key = bestFamilyKey(target);
        return key == null ? "—" : key;
    }

    /**
     * Compact vowel-chain hint. It is not displayed as academic IPA; it is a
     * rapid visual memory cue for freestyle.
     */
    public String phonemePattern(String phrase) {
        String word = norm(lastWord(phrase));
        if (word.isEmpty()) return "—";

        String vowels = vowelSequence(word);
        if (vowels.isEmpty()) return word;

        // The last four vowel nuclei are enough to show a multisyllabic tail
        // without making the floating UI noisy.
        int start = Math.max(0, vowels.length() - 4);
        String tail = vowels.substring(start);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            if (i > 0) out.append('-');
            out.append(tail.charAt(i));
        }
        return out.toString();
    }

    private String bestFamilyKey(String target) {
        String bestKey = null;
        int bestScore = Integer.MIN_VALUE;

        for (Map.Entry<String, List<String>> entry : families.entrySet()) {
            String key = entry.getKey();
            int score = familyScore(target, key, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestKey = key;
            }
        }
        return bestKey;
    }

    private int familyScore(String target, String key, List<String> family) {
        int score = 0;

        // Exact orthographic tail dominates. Longer matches beat generic -ar,
        // -al, -on, etc.
        if (target.endsWith(key)) {
            score += 10000 + key.length() * 300;
        } else {
            score += commonSuffix(target, key) * 180;
        }

        String targetVowels = vowelSequence(target);
        String keyVowels = vowelSequence(key);
        score += commonSuffix(targetVowels, keyVowels) * 140;

        // Compare against representative actual words as well. This catches
        // equivalent Spanish spellings such as -ción/-sión whose sound tail is
        // close even when letters differ.
        int representativeBest = Integer.MIN_VALUE;
        int checked = 0;
        for (String candidate : family) {
            String n = norm(candidate).replace(" ", "");
            int s = commonSuffix(target, n) * 70;
            s += commonSuffix(targetVowels, vowelSequence(n)) * 95;
            s -= Math.abs(syllableApprox(target) - syllableApprox(n)) * 18;
            representativeBest = Math.max(representativeBest, s);
            if (++checked >= 6) break;
        }
        if (representativeBest != Integer.MIN_VALUE) score += representativeBest;

        return score;
    }

    private int syllableApprox(String word) {
        int groups = 0;
        boolean inVowel = false;
        for (int i = 0; i < word.length(); i++) {
            boolean vowel = isVowel(word.charAt(i));
            if (vowel && !inVowel) groups++;
            inVowel = vowel;
        }
        return Math.max(1, groups);
    }

    private String vowelSequence(String s) {
        StringBuilder out = new StringBuilder();
        boolean previousWasSameVowel = false;
        char previous = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isVowel(c)) {
                // Collapse only identical adjacent vowels; keep diphthong
                // information such as ia/ue/ai.
                previousWasSameVowel = c == previous;
                if (!previousWasSameVowel) out.append(c);
                previous = c;
            } else {
                previous = 0;
            }
        }
        return out.toString();
    }

    private boolean isVowel(char c) {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u';
    }

    private int commonSuffix(String a, String b) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int n = 0;
        while (i >= 0 && j >= 0 && a.charAt(i) == b.charAt(j)) {
            n++;
            i--;
            j--;
        }
        return n;
    }

    private String lastWord(String phrase) {
        if (phrase == null) return "";
        String cleaned = norm(phrase).replaceAll("[^a-z0-9ñ ]", " ").trim();
        if (cleaned.isEmpty()) return "";
        String[] parts = cleaned.split("\\s+");
        return parts[parts.length - 1];
    }

    private String norm(String s) {
        String n = Normalizer.normalize(
                s == null ? "" : s.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );
        return n.replaceAll("\\p{M}+", "").trim();
    }
}
