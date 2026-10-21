package annina.sparkstrength.role.partyanimal;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * Pure helpers for the Partygoer's room list: which flood side of a door is the room, where its centre is, and the
 * order rooms are listed in.
 * 派对客房间列表的纯辅助逻辑：门的哪一侧泛洪结果是房间、房间中心在哪，以及列表排序。
 */
public final class PartyRoomRules {
    /** A side that fills this many cells is open space (a corridor), not a cabin. / 填满这么多格的一侧是开放空间（走廊），不是客房。 */
    public static final int ROOM_CELL_CAP = 400;

    /** "Room 2" before "Room 10"; otherwise case-insensitive. / “Room 2”排在“Room 10”之前；其余忽略大小写。 */
    public static final Comparator<String> NATURAL_ORDER = PartyRoomRules::compareNatural;

    private PartyRoomRules() {
    }

    /**
     * The smaller of the door's flood sides that stayed under the cap, or null when every side is open space.
     * 门两侧泛洪结果中未达上限且较小的那一侧；所有侧都是开放空间时为 null。
     */
    public static <T extends Collection<?>> T pickInterior(Iterable<T> sides) {
        T best = null;
        for (T side : sides) {
            if (side.isEmpty() || side.size() >= ROOM_CELL_CAP) {
                continue;
            }
            if (best == null || side.size() < best.size()) {
                best = side;
            }
        }
        return best;
    }

    /**
     * The cell nearest the interior's horizontal centroid; ties go to the cell visited first. {@code cells} maps a
     * cell key to its {x, z}.
     * 离内部水平质心最近的格子；距离相同取先访问到的。cells 为 格子键 -> {x, z}。
     */
    public static long centreCell(Map<Long, int[]> cells) {
        double sumX = 0.0;
        double sumZ = 0.0;
        for (int[] xz : cells.values()) {
            sumX += xz[0] + 0.5;
            sumZ += xz[1] + 0.5;
        }
        double cx = sumX / cells.size();
        double cz = sumZ / cells.size();
        long best = Long.MIN_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Long, int[]> entry : cells.entrySet()) {
            double dx = entry.getValue()[0] + 0.5 - cx;
            double dz = entry.getValue()[1] + 0.5 - cz;
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance - 1.0E-9) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }

    static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int byLength = Integer.compare(na.length(), nb.length());
                if (byLength != 0) {
                    return byLength;
                }
                int byDigits = na.compareTo(nb);
                if (byDigits != 0) {
                    return byDigits;
                }
                continue;
            }
            int byChar = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (byChar != 0) {
                return byChar;
            }
            i++;
            j++;
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
