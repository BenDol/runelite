package net.runelite.client.plugins.datadump.data;

import lombok.AllArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
public class Position {
	int x,y,z;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Position position = (Position) o;
		return x == position.x &&
				y == position.y &&
				z == position.z;
	}

	@Override
	public int hashCode() {
		return Objects.hash(x, y, z);
	}

	@Override
	public String toString() {
		return String.valueOf(hashCode());
	}
}



