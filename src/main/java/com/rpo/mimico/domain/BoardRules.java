package com.rpo.mimico.domain;

import java.util.Set;

public final class BoardRules {

    public static final int BOARD_END = 52;
    public static final int ROUND_SECONDS = 60;
    public static final Set<Integer> SPECIAL_TILES = Set.of(5, 11, 17, 23, 29, 35, 40, 44, 48, 51);

    private BoardRules() {
    }

    public static int advance(int position, int diceValue) {
        return Math.min(position + diceValue, BOARD_END);
    }

    public static boolean isSpecial(int tile) {
        return SPECIAL_TILES.contains(tile);
    }
}
