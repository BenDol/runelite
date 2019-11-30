package net.runelite.client.plugins.datadump.object;

import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.client.plugins.datadump.data.Position;

import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
public class GameObject {

    int id;

    int region_id;
    String name;
    Position position;
    List<String> actions;

    public GameObject(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameObject that = (GameObject) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
