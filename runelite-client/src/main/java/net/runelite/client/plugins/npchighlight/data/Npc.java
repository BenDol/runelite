package net.runelite.client.plugins.npchighlight.data;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class Npc {
	final int id;
	final String name;
	final int combat_level;
	final int health;
	final int direction;
	final Region region;
	final Position position;

	boolean intractable, visible;
	List<String> actions;
	List<Integer> configs;
	String head_icon;
}