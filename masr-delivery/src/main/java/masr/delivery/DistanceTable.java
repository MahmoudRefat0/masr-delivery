package masr.delivery;

import java.math.BigDecimal;
import java.util.*;
public final class DistanceTable {
    private final Map<String, BigDecimal> distances;
    public DistanceTable(Map<String, BigDecimal> distances) { this.distances = Map.copyOf(distances); }
    public static String key(String a, String b) {
        String x = a.trim().toLowerCase(Locale.ROOT), y = b.trim().toLowerCase(Locale.ROOT);
        return x.compareTo(y) <= 0 ? x + "|" + y : y + "|" + x;
    }
    public BigDecimal km(String from, String to) {
        if (from.equalsIgnoreCase(to)) return new BigDecimal("2");
        BigDecimal value = distances.get(key(from, to));
        if (value == null) throw new ValidationException("No distance configured for " + from + " to " + to + ".");
        return Money.positive(value, "Distance");
    }
    public static DistanceTable demo() {
        Map<String, BigDecimal> map = new HashMap<>();
        String[] names = {"Maadi", "Dokki", "Faisal", "Nasr City", "Heliopolis"};
        int[][] values = {{2,14,12,18,25},{14,2,6,20,24},{12,6,2,25,30},{18,20,25,2,8},{25,24,30,8,2}};
        for (int i=0;i<names.length;i++) for (int j=i+1;j<names.length;j++) map.put(key(names[i],names[j]), BigDecimal.valueOf(values[i][j]));
        return new DistanceTable(map);
    }
}
