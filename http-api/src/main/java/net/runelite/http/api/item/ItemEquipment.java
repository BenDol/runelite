package net.runelite.http.api.item;

import lombok.Data;

import java.util.Map;

@Data
public class ItemEquipment {

    private int attack_stab;
    private int attack_slash;
    private int attack_crush;
    private int attack_magic;
    private int attack_ranged;
    private int defence_stab;
    private int defence_slash;
    private int defence_crush;
    private int defence_magic;
    private int defence_ranged;
    private int melee_strength;
    private int ranged_strength;
    private int magic_damage;
    private int prayer;
    private String slot;
    private String attack_speed;

    private Map<String, String> requirements;
}
