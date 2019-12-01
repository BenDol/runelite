package net.runelite.client.plugins.datadump.data;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Region {
	int id;
	String name;

	@Override
	public String toString() {
		return name;
	}
}